package com.aistudio.pokiesvault.data.network

import com.aistudio.pokiesvault.data.model.AccountResult
import com.aistudio.pokiesvault.data.model.BonusView
import com.aistudio.pokiesvault.data.model.DomainHealthRecord
import com.aistudio.pokiesvault.data.model.PublicBonus
import com.aistudio.pokiesvault.data.model.PublicBonusKind
import com.aistudio.pokiesvault.data.model.PublicBonusSlot
import com.aistudio.pokiesvault.data.model.SiteStatus
import com.aistudio.pokiesvault.data.model.WalletView
import com.aistudio.pokiesvault.util.DomainUtil
import com.aistudio.pokiesvault.util.FormatUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.math.pow
import kotlin.random.Random

data class HostTarget(
    val domain: String,
    val host: String,
    val merchantId: String
)

data class PublicCatalogueResult(
    val domain: String,
    val ok: Boolean,
    val bonuses: List<PublicBonus>,
    val message: String? = null,
    val host: String? = null,
    val merchantId: String? = null
)

data class AccountFetchOutcome(
    val result: AccountResult,
    val discoveredTarget: HostTarget? = null,
    val congested: Boolean = false
)

class CasinoApiService {
    private val sharedPool = ConnectionPool(16, 2, TimeUnit.MINUTES)
    private val sharedDispatcher = Dispatcher().apply {
        maxRequests = 40
        maxRequestsPerHost = 8
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(sharedPool)
        .dispatcher(sharedDispatcher)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    private val probeClient: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(sharedPool)
        .dispatcher(sharedDispatcher)
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    private val userAgent =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    private val merchantIdPattern = Pattern.compile("MERCHANTID\\s*=\\s*(\\d+)")
    private val htmlTagRegex = Regex("<[^>]*>")
    private val nbspRegex = Regex("&nbsp;", RegexOption.IGNORE_CASE)
    private val whitespaceRegex = Regex("\\s+")
    private val phoneRegex = Regex("^[0-9+]{6,20}$")
    private val birthdayRegex = Regex("birth|b-?day")
    private val shareRegex = Regex("share|refer|invite|downline")
    private val dailyRegex = Regex("\\bdaily\\b|every day")
    private val signupRegex = Regex("sign\\s?up|register|no deposit|welcome|new register|free credit")

    suspend fun checkSiteStatus(rawDomain: String): DomainHealthRecord = withContext(Dispatchers.IO) {
        val domain = DomainUtil.normalizeDomain(rawDomain)
        val now = System.currentTimeMillis()
        if (!DomainUtil.isPublicDomain(domain)) {
            return@withContext DomainHealthRecord(domain, SiteStatus.DEAD, 0L, now)
        }
        val started = System.currentTimeMillis()
        try {
            val request = Request.Builder()
                .url("https://$domain/")
                .header("User-Agent", userAgent)
                .header("Accept", "text/html")
                .get()
                .build()

            probeClient.newCall(request).execute().use { response ->
                val elapsed = (System.currentTimeMillis() - started).coerceAtLeast(1L)
                val code = response.code
                val status = when {
                    code in 300..399 -> SiteStatus.REDIRECT
                    code in 200..299 -> if (elapsed > 2800L) SiteStatus.SLOW else SiteStatus.LIVE
                    else -> SiteStatus.DEAD
                }
                DomainHealthRecord(domain, status, elapsed, System.currentTimeMillis())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            val elapsed = (System.currentTimeMillis() - started).coerceAtLeast(1L)
            DomainHealthRecord(domain, SiteStatus.DEAD, elapsed, System.currentTimeMillis())
        }
    }

    suspend fun fetchBulkStatuses(): Map<String, SiteStatus>? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://intel.bestpokieslist.online/public/statuses")
                .header("Accept", "application/json")
                .header("User-Agent", userAgent)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string()?.trim() ?: return@withContext null
                val array: JSONArray = when {
                    body.startsWith("[") -> JSONArray(body)
                    body.startsWith("{") -> {
                        val obj = JSONObject(body)
                        obj.optJSONArray("domains") ?: obj.optJSONArray("statuses") ?: return@withContext null
                    }
                    else -> return@withContext null
                }
                val out = HashMap<String, SiteStatus>(array.length())
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val domain = DomainUtil.normalizeDomain(item.optString("domain", ""))
                    if (!DomainUtil.isPublicDomain(domain)) continue
                    val raw = item.optString("status", "").lowercase(Locale.US)
                    out[domain] = when (raw) {
                        "live", "up", "ok" -> SiteStatus.LIVE
                        "slow" -> SiteStatus.SLOW
                        "redirect" -> SiteStatus.REDIRECT
                        "unknown" -> SiteStatus.UNKNOWN
                        else -> SiteStatus.DEAD
                    }
                }
                out
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Streams homepage HTML only until MERCHANTID is matched (max 1.2MB) to minimize bandwidth and latency.
     */
    suspend fun discoverHostTarget(domain: String): HostTarget? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://$domain/")
                .header("User-Agent", userAgent)
                .header("Accept", "text/html")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val finalHost = DomainUtil.normalizeDomain(response.request.url.host)
                val resolvedHost = if (DomainUtil.isPublicDomain(finalHost)) finalHost else domain
                val source = response.body?.charStream() ?: return@withContext null
                val buffer = CharArray(8192)
                val sb = StringBuilder(16384)
                var merchantId = ""

                while (sb.length < 1_200_000) {
                    val read = source.read(buffer)
                    if (read <= 0) break
                    sb.append(buffer, 0, read)
                    val matcher = merchantIdPattern.matcher(sb)
                    if (matcher.find()) {
                        merchantId = matcher.group(1) ?: ""
                        break
                    }
                }
                if (merchantId.isNotEmpty()) {
                    HostTarget(domain = domain, host = resolvedHost, merchantId = merchantId)
                } else {
                    null
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    suspend fun fetchCasinoAccount(
        rawDomain: String,
        phoneRaw: String,
        passwordRaw: String,
        cachedTarget: HostTarget? = null
    ): AccountFetchOutcome = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val domain = DomainUtil.normalizeDomain(rawDomain)
        if (!DomainUtil.isPublicDomain(domain)) {
            return@withContext AccountFetchOutcome(
                AccountResult.Err(domain, "other", "Enter a real site domain", fetchedAt = started)
            )
        }
        val phone = phoneRaw.replace(whitespaceRegex, "")
        if (!phoneRegex.matches(phone)) {
            return@withContext AccountFetchOutcome(
                AccountResult.Err(domain, "other", "Invalid phone number format", fetchedAt = started)
            )
        }
        if (passwordRaw.length < 4 || passwordRaw.length > 64) {
            return@withContext AccountFetchOutcome(
                AccountResult.Err(domain, "other", "Password must be 4–64 characters", fetchedAt = started)
            )
        }

        // Resolve host + merchantId via cache first, falling back to streaming HTML inspection.
        // A blocked/blank homepage never stops direct login attempts (plain mode fallback).
        var target = cachedTarget
        var newlyDiscovered: HostTarget? = null
        if (target == null || target.merchantId.isBlank()) {
            val inspected = discoverHostTarget(domain)
            if (inspected != null) {
                target = inspected
                newlyDiscovered = inspected
            }
        }

        val host = target?.host?.takeIf { DomainUtil.isPublicDomain(it) } ?: domain
        val merchantId = target?.merchantId ?: ""
        val useSelfHeaders = merchantId.isNotEmpty()

        var retriesUsed = 0
        var congested = false

        val loginFields = mapOf(
            "module" to "/users/login",
            "merchantId" to merchantId,
            "accessId" to "",
            "accessToken" to "",
            "walletIsAdmin" to "",
            "mobile" to phone,
            "password" to passwordRaw
        )

        val loginResult = executeWithRetry(
            onRetry = { retriesUsed++ },
            onCongestion = { congested = true }
        ) {
            postApi(host, loginFields, useSelfHeaders)
        }

        val loginJson = loginResult.getOrElse { ex ->
            val elapsed = System.currentTimeMillis() - started
            val err = classifyFailure(domain, ex.message ?: "Could not reach site", elapsed, retriesUsed)
            return@withContext AccountFetchOutcome(err, newlyDiscovered, congested || err.code == "timeout")
        }

        if (loginJson.optString("status") != "SUCCESS") {
            val dataObj = loginJson.optJSONObject("data")
            val msg = dataObj?.optString("message") ?: loginJson.optString("message", "Could not sign in")
            val elapsed = System.currentTimeMillis() - started
            return@withContext AccountFetchOutcome(
                classifyFailure(domain, msg, elapsed, retriesUsed),
                newlyDiscovered,
                congested
            )
        }

        val dataObj = loginJson.optJSONObject("data")
        val accessId = dataObj?.optString("id", "") ?: ""
        val accessToken = dataObj?.optString("token", "") ?: ""
        if (accessId.isEmpty() || accessToken.isEmpty()) {
            val elapsed = System.currentTimeMillis() - started
            return@withContext AccountFetchOutcome(
                AccountResult.Err(domain, "other", "Sign-in did not return a session", elapsed, retriesUsed, System.currentTimeMillis()),
                newlyDiscovered,
                congested
            )
        }

        val syncFields = mapOf(
            "module" to "/users/syncData",
            "merchantId" to merchantId,
            "accessId" to accessId,
            "accessToken" to accessToken,
            "walletIsAdmin" to "",
            "background" to "1"
        )

        val syncResult = executeWithRetry(
            onRetry = { retriesUsed++ },
            onCongestion = { congested = true }
        ) {
            postApi(host, syncFields, useSelfHeaders)
        }

        val syncJson = syncResult.getOrElse { ex ->
            val elapsed = System.currentTimeMillis() - started
            val err = classifyFailure(domain, ex.message ?: "Balances did not load", elapsed, retriesUsed)
            return@withContext AccountFetchOutcome(err, newlyDiscovered, congested || err.code == "timeout")
        }

        if (syncJson.optString("status") != "SUCCESS") {
            val sData = syncJson.optJSONObject("data")
            val msg = sData?.optString("message") ?: "Signed in, but balances did not load"
            val elapsed = System.currentTimeMillis() - started
            return@withContext AccountFetchOutcome(
                classifyFailure(domain, msg, elapsed, retriesUsed),
                newlyDiscovered,
                congested
            )
        }

        val userObj = syncJson.optJSONObject("data") ?: JSONObject()
        // If syncData omitted bonus[], try /merchants/getPublicInfo as fallback
        var fallbackBonuses: JSONArray? = null
        if (userObj.optJSONArray("bonus") == null && merchantId.isNotEmpty()) {
            try {
                val pub = postApi(
                    host,
                    mapOf(
                        "module" to "/merchants/getPublicInfo",
                        "merchantId" to merchantId,
                        "accessId" to "",
                        "accessToken" to "",
                        "walletIsAdmin" to ""
                    ),
                    useSelfHeaders = true
                )
                fallbackBonuses = pub.optJSONObject("data")?.optJSONArray("promotions")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Non-fatal fallback
            }
        }

        val elapsed = System.currentTimeMillis() - started
        val parsed = parseSyncUser(domain, userObj, fallbackBonuses, elapsed, retriesUsed)
        AccountFetchOutcome(parsed, newlyDiscovered, congested)
    }

    suspend fun fetchPublicBonuses(
        rawDomain: String,
        cachedTarget: HostTarget? = null
    ): PublicCatalogueResult = withContext(Dispatchers.IO) {
        val domain = DomainUtil.normalizeDomain(rawDomain)
        if (!DomainUtil.isPublicDomain(domain)) {
            return@withContext PublicCatalogueResult(domain, false, emptyList(), "Enter a valid domain")
        }

        var host = cachedTarget?.host?.takeIf { DomainUtil.isPublicDomain(it) } ?: ""
        var merchantId = cachedTarget?.merchantId ?: ""

        if (merchantId.isBlank()) {
            val discovered = discoverHostTarget(domain)
            if (discovered != null) {
                host = discovered.host
                merchantId = discovered.merchantId
            }
        }

        if (merchantId.isBlank()) {
            return@withContext PublicCatalogueResult(
                domain = domain,
                ok = false,
                bonuses = emptyList(),
                message = "No public bonus list published"
            )
        }

        val resolvedHost = host.ifBlank { domain }
        val result = executeWithRetry(maxRetries = 1) {
            postApi(
                resolvedHost,
                mapOf(
                    "module" to "/merchants/getPublicInfo",
                    "merchantId" to merchantId,
                    "accessId" to "",
                    "accessToken" to "",
                    "walletIsAdmin" to ""
                ),
                useSelfHeaders = true
            )
        }

        val json = result.getOrElse {
            return@withContext PublicCatalogueResult(
                domain = domain,
                ok = false,
                bonuses = emptyList(),
                message = "Could not reach bonus list",
                host = resolvedHost,
                merchantId = merchantId
            )
        }

        if (json.optString("status") != "SUCCESS") {
            return@withContext PublicCatalogueResult(
                domain = domain,
                ok = false,
                bonuses = emptyList(),
                message = "Bonus list did not load",
                host = resolvedHost,
                merchantId = merchantId
            )
        }

        val promos = json.optJSONObject("data")?.optJSONArray("promotions")
        val bonuses = parsePublicPromotions(promos)
        PublicCatalogueResult(
            domain = domain,
            ok = true,
            bonuses = bonuses,
            host = resolvedHost,
            merchantId = merchantId
        )
    }

    private suspend fun <T> executeWithRetry(
        maxRetries: Int = 2,
        onRetry: () -> Unit = {},
        onCongestion: () -> Unit = {},
        block: () -> T
    ): Result<T> {
        var attempt = 0
        while (true) {
            try {
                return Result.success(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: TransientHttpException) {
                onCongestion()
                if (attempt >= maxRetries) return Result.failure(e)
                attempt++
                onRetry()
                val baseMs = (500.0 * 2.0.pow((attempt - 1).toDouble())).toLong()
                val jitterMs = Random.nextLong(80L, 340L)
                delay(baseMs + jitterMs)
            } catch (e: SocketTimeoutException) {
                onCongestion()
                if (attempt >= maxRetries) return Result.failure(IOException("timeout"))
                attempt++
                onRetry()
                val baseMs = (450.0 * 2.0.pow((attempt - 1).toDouble())).toLong()
                val jitterMs = Random.nextLong(80L, 300L)
                delay(baseMs + jitterMs)
            } catch (e: IOException) {
                if (attempt >= maxRetries) return Result.failure(e)
                attempt++
                onRetry()
                delay(400L + Random.nextLong(50L, 250L))
            } catch (e: Exception) {
                return Result.failure(e)
            }
        }
    }

    private fun postApi(
        host: String,
        fields: Map<String, String>,
        useSelfHeaders: Boolean
    ): JSONObject {
        val bodyBuilder = FormBody.Builder()
        fields.forEach { (k, v) -> bodyBuilder.add(k, v) }

        val reqBuilder = Request.Builder()
            .url("https://$host/api/v1/index.php")
            .header("User-Agent", userAgent)
            .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .header("Accept", "application/json,text/plain,*/*")
            .post(bodyBuilder.build())

        if (useSelfHeaders) {
            reqBuilder.header("Origin", "https://$host")
            reqBuilder.header("Referer", "https://$host/")
        }

        client.newCall(reqBuilder.build()).execute().use { res ->
            val code = res.code
            if (code == 429 || code in 500..599) {
                throw TransientHttpException("HTTP $code")
            }
            if (code == 401 || code == 403) {
                throw BlockedException("Page blocked ($code)")
            }
            val str = res.body?.string()?.trim()
            if (str.isNullOrEmpty()) throw BadJsonException("empty-response")
            return try {
                JSONObject(str)
            } catch (_: Exception) {
                throw BadJsonException("bad-json")
            }
        }
    }

    private fun classifyFailure(
        domain: String,
        message: String,
        latencyMs: Long,
        retriesUsed: Int
    ): AccountResult.Err {
        val lower = message.lowercase(Locale.US)
        val now = System.currentTimeMillis()
        return when {
            lower.contains("invalid login") || lower.contains("invalid password") ->
                AccountResult.Err(domain, "invalid_login", "Not your account", latencyMs, retriesUsed, now)
            lower.contains("captcha") ->
                AccountResult.Err(domain, "captcha", "Site asked for a captcha", latencyMs, retriesUsed, now)
            lower.contains("blocked") || lower.contains("403") || lower.contains("401") ->
                AccountResult.Err(domain, "blocked", "Blocked by firewall", latencyMs, retriesUsed, now)
            lower.contains("429") ->
                AccountResult.Err(domain, "rate_limit", "Rate limited", latencyMs, retriesUsed, now)
            lower.contains("bad-json") || lower.contains("empty-") ->
                AccountResult.Err(domain, "unreachable", "No account JSON returned", latencyMs, retriesUsed, now)
            lower.contains("timeout") || lower.contains("timed out") ->
                AccountResult.Err(domain, "timeout", "Timed out", latencyMs, retriesUsed, now)
            else ->
                AccountResult.Err(domain, "other", message.ifBlank { "Could not sign in" }, latencyMs, retriesUsed, now)
        }
    }

    private fun parseSyncUser(
        domain: String,
        user: JSONObject,
        fallbackBonuses: JSONArray?,
        latencyMs: Long,
        retriesUsed: Int
    ): AccountResult.Ok {
        val wallets = mutableListOf<WalletView>()
        val walletArr = user.optJSONArray("wallet") ?: JSONArray()
        for (i in 0 until walletArr.length()) {
            val w = walletArr.optJSONObject(i) ?: continue
            val id = w.optString("id", "")
            val name = cleanName(w.optString("name", "Balance")).ifBlank { "Balance" }
            val balance = w.optDouble("balance", 0.0).takeIf { it.isFinite() } ?: 0.0
            wallets.add(WalletView(id, name, balance))
        }

        var cash = user.optDouble("cash", 0.0).takeIf { it.isFinite() } ?: 0.0
        var cashWalletName = "Balance"
        for (w in wallets) {
            if (w.balance > 0.001) {
                cash = w.balance
                cashWalletName = w.name
            }
        }

        val minWithdraw = optPositiveDouble(user, "minWithdraw")
        val maxWithdraw = optPositiveDouble(user, "maxWithdraw")

        val taken = user.optJSONObject("timebonus") ?: JSONObject()
        val commissionList = mutableListOf<BonusView>()
        val shareList = mutableListOf<BonusView>()
        val referralList = mutableListOf<BonusView>()
        val newList = mutableListOf<BonusView>()
        val rebateList = mutableListOf<BonusView>()
        val availableBonuses = mutableListOf<BonusView>()

        val bonusArr = user.optJSONArray("bonus") ?: fallbackBonuses ?: JSONArray()
        for (i in 0 until bonusArr.length()) {
            val b = bonusArr.optJSONObject(i) ?: continue
            val id = b.optString("id", "")
            val maxRound = b.optInt("maxRound", 0)
            val used = taken.optInt(id, 0)
            if (maxRound > 0 && used >= maxRound) continue

            val claimConfig = parseJsonObj(b.opt("claimConfig"))
            val creditLessThan = claimConfig.optDouble("creditLessThan", -1.0)
            if (creditLessThan > 0.0 && cash >= creditLessThan) continue

            val name = cleanName(b.optString("name", "Bonus")).ifBlank { "Bonus" }
            val custom = claimConfig.optString("customBonus", "")
            val fixed = b.optDouble("bonusFixed", 0.0).takeIf { it.isFinite() } ?: 0.0
            val percent = b.optDouble("bonus", 0.0).takeIf { it.isFinite() } ?: 0.0
            val randomObj = parseJsonObj(b.opt("bonusRandom"))
            val rmin = randomObj.optDouble("min", 0.0).takeIf { it.isFinite() } ?: 0.0
            val rmax = randomObj.optDouble("max", 0.0).takeIf { it.isFinite() } ?: 0.0
            val rollover = optPositiveDouble(b, "rollover")

            val amount: Double
            val amountDisplay: String
            var isPercent = false

            when {
                fixed > 0.0 -> {
                    amount = fixed
                    amountDisplay = FormatUtil.moneyCompact(fixed)
                }
                rmax > 0.0 -> {
                    amount = rmax
                    amountDisplay = "${FormatUtil.moneyCompact(rmin)}–${FormatUtil.moneyCompact(rmax)}"
                }
                percent > 0.0 -> {
                    amount = 0.0
                    val pctStr = if (percent % 1.0 == 0.0) {
                        percent.toInt().toString()
                    } else {
                        String.format(Locale.US, "%.0f", percent)
                    }
                    amountDisplay = "$pctStr% match"
                    isPercent = true
                }
                else -> continue
            }

            val minW = optPositiveDouble(claimConfig, "minWithdraw")
                ?: optPositiveDouble(claimConfig, "minWithdrawal")
                ?: optPositiveDouble(b, "minWithdraw")
            val maxW = optPositiveDouble(claimConfig, "maxWithdraw")
                ?: optPositiveDouble(claimConfig, "maxWithdrawal")
                ?: optPositiveDouble(b, "maxWithdraw")

            val view = BonusView(
                name = name,
                amount = amount,
                amountDisplay = amountDisplay,
                minWithdrawal = minW,
                maxWithdrawal = maxW,
                rollover = rollover,
                isPercent = isPercent
            )
            availableBonuses.add(view)

            when (categoryOf(name, custom)) {
                "commission" -> commissionList.add(view)
                "share" -> shareList.add(view)
                "referral" -> referralList.add(view)
                "new" -> newList.add(view)
                "rebate" -> rebateList.add(view)
            }
        }

        availableBonuses.sortWith(compareByDescending<BonusView> { it.amount }.thenBy { it.name })

        fun pick(list: List<BonusView>): BonusView? {
            if (list.isEmpty()) return null
            val dollars = list.filter { !it.isPercent }
            val pool = dollars.ifEmpty { list }
            return pool.maxByOrNull { it.amount }
        }

        val commission = pick(commissionList)
        val share = pick(shareList)
        val referral = pick(referralList)
        val fresh = pick(newList)
        val rebate = pick(rebateList)

        val hasData = cash > 0.001 ||
            commission != null ||
            share != null ||
            referral != null ||
            fresh != null ||
            rebate != null ||
            availableBonuses.isNotEmpty()

        return AccountResult.Ok(
            domain = domain,
            hasData = hasData,
            cash = cash,
            cashWalletName = cashWalletName,
            cashMinWithdrawal = minWithdraw,
            cashMaxWithdrawal = maxWithdraw,
            wallets = wallets,
            commission = commission,
            share = share,
            referral = referral,
            newMember = fresh,
            rebate = rebate,
            availableBonuses = availableBonuses,
            latencyMs = latencyMs,
            retriesUsed = retriesUsed,
            fetchedAt = System.currentTimeMillis()
        )
    }

    private fun parsePublicPromotions(raw: JSONArray?): List<PublicBonus> {
        if (raw == null) return emptyList()
        val out = ArrayList<PublicBonus>(raw.length())
        for (i in 0 until raw.length()) {
            val row = raw.optJSONObject(i) ?: continue
            val name = cleanName(row.optString("name", ""))
            if (name.isBlank()) continue
            val claim = parseJsonObj(row.opt("claimConfig"))
            val custom = claim.optString("customBonus", "")
            val fixed = row.optDouble("bonusFixed", 0.0).takeIf { it.isFinite() } ?: 0.0
            val percent = row.optDouble("bonus", 0.0).takeIf { it.isFinite() } ?: 0.0
            val rangeObj = parseJsonObj(row.opt("bonusRandom"))
            val rMin = rangeObj.optDouble("min", 0.0).takeIf { it.isFinite() } ?: 0.0
            val rMax = rangeObj.optDouble("max", 0.0).takeIf { it.isFinite() } ?: 0.0

            val kind: PublicBonusKind
            val amount: Double
            val amountDisplay: String
            when {
                fixed > 0.0 -> {
                    kind = PublicBonusKind.FIXED
                    amount = fixed
                    amountDisplay = FormatUtil.moneyCompact(fixed)
                }
                percent > 0.0 -> {
                    kind = PublicBonusKind.PERCENT
                    amount = percent
                    amountDisplay = if (percent % 1.0 == 0.0) {
                        "${percent.toInt()}%"
                    } else {
                        String.format(Locale.US, "%.2f%%", percent)
                    }
                }
                rMin > 0.0 || rMax > 0.0 -> {
                    kind = PublicBonusKind.RANDOM
                    amount = rMax
                    amountDisplay = "${FormatUtil.moneyCompact(rMin)}–${FormatUtil.moneyCompact(rMax)}"
                }
                else -> {
                    kind = PublicBonusKind.UNSET
                    amount = 0.0
                    amountDisplay = "—"
                }
            }

            val minWithdraw = optPositiveDouble(row, "minWithdraw")
            val maxWithdraw = optPositiveDouble(row, "maxWithdraw")
            val rollover = optPositiveDouble(row, "rollover")
            val minDeposit = optPositiveDouble(row, "minTopup")

            out.add(
                PublicBonus(
                    id = row.optString("id", name).ifBlank { name },
                    name = name,
                    slot = publicSlotOf(name, custom, minDeposit, percent),
                    kind = kind,
                    amount = amount,
                    amountDisplay = amountDisplay,
                    minWithdraw = minWithdraw,
                    maxWithdraw = maxWithdraw,
                    rollover = rollover,
                    minDeposit = minDeposit
                )
            )
        }
        return out
    }

    private fun publicSlotOf(
        name: String,
        custom: String,
        minTopup: Double?,
        percent: Double
    ): PublicBonusSlot {
        val text = "$name $custom".lowercase(Locale.US)
        if (birthdayRegex.containsMatchIn(text)) return PublicBonusSlot.BIRTHDAY
        if (shareRegex.containsMatchIn(text)) return PublicBonusSlot.SHARE
        val needsDeposit = minTopup != null && minTopup > 0.0
        if (needsDeposit && percent > 0.0) return PublicBonusSlot.DEPOSIT
        if (dailyRegex.containsMatchIn(text)) return PublicBonusSlot.DAILY
        if (signupRegex.containsMatchIn(text)) {
            return if (needsDeposit) PublicBonusSlot.DEPOSIT else PublicBonusSlot.SIGNUP
        }
        return PublicBonusSlot.OTHER
    }

    private fun optPositiveDouble(obj: JSONObject, key: String): Double? {
        if (!obj.has(key) || obj.isNull(key)) return null
        val v = obj.optDouble(key, -1.0)
        return if (v.isFinite() && v > 0.0) v else null
    }

    private fun parseJsonObj(raw: Any?): JSONObject {
        if (raw is JSONObject) return raw
        if (raw is String && raw.trim().startsWith("{")) {
            return try {
                JSONObject(raw)
            } catch (_: Exception) {
                JSONObject()
            }
        }
        return JSONObject()
    }

    private fun cleanName(str: String): String {
        return str
            .replace(htmlTagRegex, " ")
            .replace(nbspRegex, " ")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
            .replace(whitespaceRegex, " ")
            .trim()
    }

    private fun categoryOf(name: String, custom: String): String? {
        val c = custom.lowercase(Locale.US)
        val n = name.lowercase(Locale.US)
        return when {
            c.contains("share") || n.contains("share") -> "share"
            c.contains("commission") || n.contains("commission") -> "commission"
            c.contains("rebate") || n.contains("rebate") || n.contains("cashback") || n.contains("cash back") -> "rebate"
            c.contains("refer") || c.contains("downline") || c.contains("invite") || n.contains("refer") || n.contains("invite") || n.contains("mate") -> "referral"
            c.contains("welcome") || c.contains("signup") || c.contains("newmember") || n.contains("welcome") || n.contains("sign up") || n.contains("new member") -> "new"
            else -> null
        }
    }

    private class TransientHttpException(message: String) : IOException(message)
    private class BlockedException(message: String) : Exception(message)
    private class BadJsonException(message: String) : Exception(message)
}
