package com.aistudio.pokiesvault.data.network

import com.aistudio.pokiesvault.data.model.AccountResult
import com.aistudio.pokiesvault.data.model.BonusView
import com.aistudio.pokiesvault.data.model.DomainHealthRecord
import com.aistudio.pokiesvault.data.model.EgressIpInfo
import com.aistudio.pokiesvault.data.model.PublicBonus
import com.aistudio.pokiesvault.data.model.PublicBonusKind
import com.aistudio.pokiesvault.data.model.PublicBonusSlot
import com.aistudio.pokiesvault.data.model.SavedDomainSession
import com.aistudio.pokiesvault.data.model.SiteStatus
import com.aistudio.pokiesvault.data.model.WalletView
import com.aistudio.pokiesvault.util.DomainUtil
import com.aistudio.pokiesvault.util.FormatUtil
import com.aistudio.pokiesvault.util.SiteOrderUtil
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
import java.net.Inet4Address
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
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
    val merchantId: String? = null,
    val latencyMs: Long = 0L,
    val retriesUsed: Int = 0,
    val attemptsUsed: Int = 1,
    val errorCode: String? = null,
    val congested: Boolean = false,
    val wafSignal: Boolean = false,
    val retryAfterSec: Long? = null
)

data class AccountFetchOutcome(
    val result: AccountResult,
    val discoveredTarget: HostTarget? = null,
    val congested: Boolean = false,
    val wafSignal: Boolean = false,
    val retryAfterSec: Long? = null,
    val newSession: SavedDomainSession? = null,
    val sessionExpired: Boolean = false,
    val reusedSession: Boolean = false
)

data class DnsClusterResult(
    val cluster: String,
    val isDeadDns: Boolean = false
)

class CasinoApiService {
    private val sharedPool = ConnectionPool(32, 2, TimeUnit.MINUTES)
    private val sharedDispatcher = Dispatcher().apply {
        maxRequests = 96
        maxRequestsPerHost = 12
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(sharedPool)
        .dispatcher(sharedDispatcher)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(13, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    private data class CachedDnsEntry(
        val result: DnsClusterResult,
        val resolvedAtMs: Long
    )

    private val originClusterCache = ConcurrentHashMap<String, CachedDnsEntry>()
    private val dnsCacheTtlMs = 30L * 60L * 1000L

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

    /**
     * Resolves the device's current public outbound IP, ISP, and location using ipwho.is,
     * with automatic fallback to ipapi.co and api.ipify.org for high availability.
     */
    suspend fun lookupEgressInfo(): Result<EgressIpInfo> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        // Primary: ipwho.is
        try {
            val req = Request.Builder()
                .url("https://ipwho.is/")
                .header("Accept", "application/json")
                .header("User-Agent", userAgent)
                .get()
                .build()
            probeClient.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val body = res.body?.string()
                    if (!body.isNullOrBlank()) {
                        val obj = JSONObject(body)
                        val ip = obj.optString("ip", "").trim()
                        if (obj.optBoolean("success", true) && ip.isNotEmpty()) {
                            val conn = obj.optJSONObject("connection")
                            val isp = conn?.optString("isp", "")?.takeIf { it.isNotBlank() }
                                ?: conn?.optString("org", "")?.takeIf { it.isNotBlank() }
                                ?: "Unknown network"
                            val city = obj.optString("city", "").trim()
                            val cc = obj.optString("country_code", "").trim()
                            val loc = listOf(city, cc).filter { it.isNotBlank() }.joinToString(", ")
                            return@withContext Result.success(EgressIpInfo(ip, isp, loc, now))
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Fall through to secondary provider
        }

        // Secondary: ipapi.co
        try {
            val req = Request.Builder()
                .url("https://ipapi.co/json/")
                .header("Accept", "application/json")
                .header("User-Agent", userAgent)
                .get()
                .build()
            probeClient.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val body = res.body?.string()
                    if (!body.isNullOrBlank()) {
                        val obj = JSONObject(body)
                        val ip = obj.optString("ip", "").trim()
                        if (ip.isNotEmpty()) {
                            val isp = obj.optString("org", "").ifBlank { "Unknown network" }
                            val city = obj.optString("city", "").trim()
                            val cc = obj.optString("country_code", "").trim()
                            val loc = listOf(city, cc).filter { it.isNotBlank() }.joinToString(", ")
                            return@withContext Result.success(EgressIpInfo(ip, isp, loc, now))
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Fall through to tertiary IP-only provider
        }

        // Tertiary: api.ipify.org
        try {
            val req = Request.Builder()
                .url("https://api.ipify.org?format=json")
                .header("Accept", "application/json")
                .header("User-Agent", userAgent)
                .get()
                .build()
            probeClient.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val body = res.body?.string()
                    if (!body.isNullOrBlank()) {
                        val ip = JSONObject(body).optString("ip", "").trim()
                        if (ip.isNotEmpty()) {
                            return@withContext Result.success(
                                EgressIpInfo(ip, "Local network", "", now)
                            )
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }

        Result.failure(IOException("Could not determine outgoing IP"))
    }

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
     * Fetches the latest domain list JSON from https://intel.bestpokieslist.online/domains
     * and extracts each entry's "domain" string (ignoring status and all other fields).
     */
    suspend fun fetchLatestDomainsList(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://intel.bestpokieslist.online/domains")
                .header("Accept", "application/json")
                .header("User-Agent", userAgent)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("Server returned HTTP ${response.code}")
                    )
                }
                val body = response.body?.string()?.trim()
                if (body.isNullOrEmpty()) {
                    return@withContext Result.failure(IOException("Empty response from server"))
                }
                val root = try {
                    JSONObject(body)
                } catch (_: Exception) {
                    return@withContext Result.failure(IllegalStateException("Invalid JSON response"))
                }
                val array = root.optJSONArray("domains")
                    ?: return@withContext Result.failure(
                        IllegalStateException("Response missing domains list")
                    )

                val extracted = LinkedHashSet<String>(array.length())
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val rawDomain = item.optString("domain", "").trim()
                    if (rawDomain.isNotEmpty()) {
                        extracted.add(rawDomain)
                    }
                }

                if (extracted.isEmpty()) {
                    return@withContext Result.failure(
                        IllegalStateException("Latest list returned no domains")
                    )
                }
                Result.success(extracted.toList())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: SocketTimeoutException) {
            Result.failure(IOException("Request timed out"))
        } catch (e: IOException) {
            Result.failure(IOException(e.message?.takeIf { it.isNotBlank() } ?: "Network error"))
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.message?.takeIf { it.isNotBlank() } ?: "Unexpected error"))
        }
    }

    /**
     * Streams homepage HTML only until MERCHANTID is matched (max 1.2MB) using linear
     * sliding-window scanning to avoid quadratic regex scans. Surfaces HTTP 429/403/WAF
     * challenges so callers never silently swallow rate limits.
     */
    suspend fun discoverHostTargetChecked(domain: String): HostTarget? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://$domain/")
            .header("User-Agent", userAgent)
            .header("Accept", "text/html")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val code = response.code
            if (code == 429) {
                val retryAfter = response.header("Retry-After")?.trim()?.toLongOrNull()?.coerceIn(1L, 120L)
                throw RateLimitException("Rate limited (429)", retryAfter)
            }
            if (code == 401 || code == 403) {
                throw BlockedException("Blocked by firewall ($code)")
            }
            val finalHost = DomainUtil.normalizeDomain(response.request.url.host)
            val resolvedHost = if (DomainUtil.isPublicDomain(finalHost)) finalHost else domain
            val source = response.body?.charStream() ?: return@withContext null
            val buffer = CharArray(8192)
            var tail = ""
            var totalRead = 0
            var inspectedHeader = false
            var merchantId = ""

            while (totalRead < 1_200_000) {
                val read = source.read(buffer)
                if (read <= 0) break
                totalRead += read
                val chunk = String(buffer, 0, read)
                val window = if (tail.isEmpty()) chunk else tail + chunk
                if (!inspectedHeader) {
                    inspectedHeader = true
                    if (isWafChallengeBody(window)) {
                        throw BlockedException("Blocked by firewall (WAF challenge)")
                    }
                }
                val matcher = merchantIdPattern.matcher(window)
                if (matcher.find()) {
                    merchantId = matcher.group(1) ?: ""
                    break
                }
                tail = if (window.length > 64) window.substring(window.length - 64) else window
            }
            if (merchantId.isNotEmpty()) {
                HostTarget(domain = domain, host = resolvedHost, merchantId = merchantId)
            } else {
                null
            }
        }
    }

    /**
     * Streams homepage HTML only until MERCHANTID is matched (max 1.2MB) to minimize bandwidth and latency.
     */
    suspend fun discoverHostTarget(domain: String): HostTarget? = withContext(Dispatchers.IO) {
        try {
            discoverHostTargetChecked(domain)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Resolves and caches the DNS origin cluster for a domain (30 min TTL) so shared backend
     * IP pools (e.g., 800 sites on 43.106.19.168) are interleaved and spaced accurately, while
     * NXDOMAIN / dead-DNS domains are flagged for immediate fast-skip.
     */
    suspend fun resolveOriginClusterDetailed(rawDomain: String): DnsClusterResult = withContext(Dispatchers.IO) {
        val domain = DomainUtil.normalizeDomain(rawDomain)
        if (!DomainUtil.isPublicDomain(domain)) {
            return@withContext DnsClusterResult("host:$domain", isDeadDns = false)
        }
        val now = System.currentTimeMillis()
        originClusterCache[domain]?.let { cached ->
            if ((now - cached.resolvedAtMs) < dnsCacheTtlMs) {
                return@withContext cached.result
            }
        }
        val resolved = try {
            val addresses = InetAddress.getAllByName(domain)
            val ipv4s = addresses
                .filterIsInstance<Inet4Address>()
                .mapNotNull { it.hostAddress }
                .sorted()
            val primary = ipv4s.firstOrNull()
            when {
                primary == null -> DnsClusterResult("host:$domain", isDeadDns = false)
                isCloudflareIpv4(primary) -> DnsClusterResult("cf:$domain", isDeadDns = false)
                else -> DnsClusterResult("ip:$primary", isDeadDns = false)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: UnknownHostException) {
            DnsClusterResult("dead:$domain", isDeadDns = true)
        } catch (_: Exception) {
            DnsClusterResult("host:$domain", isDeadDns = false)
        }
        originClusterCache[domain] = CachedDnsEntry(resolved, now)
        resolved
    }

    suspend fun resolveOriginCluster(rawDomain: String): String =
        resolveOriginClusterDetailed(rawDomain).cluster

    private fun isCloudflareIpv4(ip: String): Boolean {
        val parts = ip.split('.')
        if (parts.size != 4) return false
        val a = parts[0].toIntOrNull() ?: return false
        val b = parts[1].toIntOrNull() ?: return false
        return (a == 104 && b in 16..31) ||
            (a == 172 && b in 64..71) ||
            (a == 188 && b == 114) ||
            (a == 162 && b == 159) ||
            (a == 173 && b == 245) ||
            (a == 198 && b == 41)
    }

    /**
     * Fast-path account sync using a saved (accessId, accessToken) session without calling /users/login.
     * Works for both normal accounts and captcha-gated accounts after a one-time in-app sign-in.
     */
    suspend fun syncAccountWithSession(
        rawDomain: String,
        accessId: String,
        accessToken: String,
        cachedTarget: HostTarget? = null
    ): AccountFetchOutcome = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val domain = DomainUtil.normalizeDomain(rawDomain)
        if (!DomainUtil.isPublicDomain(domain) || accessId.isBlank() || accessToken.isBlank()) {
            return@withContext AccountFetchOutcome(
                result = AccountResult.Err(domain, "other", "Invalid saved session", fetchedAt = started),
                sessionExpired = true
            )
        }

        var host = cachedTarget?.host?.takeIf { DomainUtil.isPublicDomain(it) } ?: domain
        var merchantId = cachedTarget?.merchantId ?: ""
        var useSelfHeaders = merchantId.isNotEmpty()
        var newlyDiscovered: HostTarget? = null
        var retriesUsed = 0
        var congested = false

        fun buildSyncFields(mid: String): Map<String, String> = mapOf(
            "module" to "/users/syncData",
            "merchantId" to mid,
            "accessId" to accessId,
            "accessToken" to accessToken,
            "walletIsAdmin" to "",
            "background" to "1"
        )

        var syncResult = executeWithRetry(
            maxRetries = 1,
            onRetry = { retriesUsed++ },
            onCongestion = { congested = true }
        ) {
            postApi(host, buildSyncFields(merchantId), useSelfHeaders)
        }

        var syncJson = syncResult.getOrElse { ex ->
            val elapsed = System.currentTimeMillis() - started
            val err = classifyFailure(domain, ex.message ?: "Balances did not load", elapsed, retriesUsed)
            val isWaf = isWafErrorCode(err.code)
            val retryAfter = (ex as? RateLimitException)?.retryAfterSec
            return@withContext AccountFetchOutcome(
                result = err,
                discoveredTarget = newlyDiscovered,
                congested = congested || isWaf || err.code == "timeout",
                wafSignal = isWaf,
                retryAfterSec = retryAfter
            )
        }

        if (syncJson.optString("status") != "SUCCESS" && merchantId.isEmpty()) {
            val firstMsg = (syncJson.optJSONObject("data")?.optString("message")
                ?: syncJson.optString("message", "")).lowercase(Locale.US)
            if (firstMsg.contains("merchant") || firstMsg.contains("missing")) {
                val inspected = discoverHostTarget(domain)
                if (inspected != null && inspected.merchantId.isNotBlank()) {
                    newlyDiscovered = inspected
                    host = inspected.host.takeIf { DomainUtil.isPublicDomain(it) } ?: domain
                    merchantId = inspected.merchantId
                    useSelfHeaders = true
                    syncResult = executeWithRetry(
                        maxRetries = 1,
                        onRetry = { retriesUsed++ },
                        onCongestion = { congested = true }
                    ) {
                        postApi(host, buildSyncFields(merchantId), useSelfHeaders)
                    }
                    syncJson = syncResult.getOrElse { ex ->
                        val elapsed = System.currentTimeMillis() - started
                        val err = classifyFailure(domain, ex.message ?: "Balances did not load", elapsed, retriesUsed)
                        val isWaf = isWafErrorCode(err.code)
                        val retryAfter = (ex as? RateLimitException)?.retryAfterSec
                        return@withContext AccountFetchOutcome(
                            result = err,
                            discoveredTarget = newlyDiscovered,
                            congested = congested || isWaf || err.code == "timeout",
                            wafSignal = isWaf,
                            retryAfterSec = retryAfter
                        )
                    }
                }
            }
        }

        if (syncJson.optString("status") != "SUCCESS") {
            val sData = syncJson.optJSONObject("data")
            val msg = sData?.optString("message")
                ?: syncJson.optString("message", "Saved session expired")
            val elapsed = System.currentTimeMillis() - started
            val err = classifyFailure(domain, msg, elapsed, retriesUsed)
            val isWaf = isWafErrorCode(err.code)
            val expired = !isWaf && (isExpiredSessionMessage(msg) || err.code == "invalid_login" || err.code == "other")
            return@withContext AccountFetchOutcome(
                result = err,
                discoveredTarget = newlyDiscovered,
                congested = congested || isWaf,
                wafSignal = isWaf,
                sessionExpired = expired
            )
        }

        val userObj = syncJson.optJSONObject("data") ?: JSONObject()
        val hasInlineBonuses = userObj.optJSONArray("claimableBonuses") != null ||
            userObj.optJSONArray("availableBonuses") != null ||
            userObj.optJSONArray("bonus") != null
        var fallbackBonuses: JSONArray? = null
        if (!hasInlineBonuses && merchantId.isNotEmpty()) {
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
        AccountFetchOutcome(
            result = parsed,
            discoveredTarget = newlyDiscovered,
            congested = congested,
            reusedSession = true
        )
    }

    private fun isExpiredSessionMessage(raw: String): Boolean {
        val l = raw.lowercase(Locale.US)
        return l.contains("token") ||
            l.contains("session") ||
            l.contains("login") ||
            l.contains("unauthorized") ||
            l.contains("expired") ||
            l.contains("access") ||
            l.contains("auth") ||
            l.contains("user")
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

        // Direct API-first login:
        // 1. If Room host cache already has a valid merchantId, use it immediately with Origin/Referer.
        // 2. Otherwise, attempt direct blank-mode login on /api/v1/index.php WITHOUT scraping the homepage HTML.
        // 3. Only fall back to a single discoverHostTarget(domain) HTML scrape if the API explicitly asks for merchantId.
        var host = cachedTarget?.host?.takeIf { DomainUtil.isPublicDomain(it) } ?: domain
        var merchantId = cachedTarget?.merchantId ?: ""
        var useSelfHeaders = merchantId.isNotEmpty()
        var newlyDiscovered: HostTarget? = null

        var retriesUsed = 0
        var congested = false

        fun buildLoginFields(mid: String): Map<String, String> = mapOf(
            "module" to "/users/login",
            "merchantId" to mid,
            "accessId" to "",
            "accessToken" to "",
            "walletIsAdmin" to "",
            "mobile" to phone,
            "password" to passwordRaw
        )

        var loginResult = executeWithRetry(
            maxRetries = 1,
            onRetry = { retriesUsed++ },
            onCongestion = { congested = true }
        ) {
            postApi(host, buildLoginFields(merchantId), useSelfHeaders)
        }

        var loginJson = loginResult.getOrElse { ex ->
            val elapsed = System.currentTimeMillis() - started
            val err = classifyFailure(domain, ex.message ?: "Could not reach site", elapsed, retriesUsed)
            val isWaf = isWafErrorCode(err.code)
            val retryAfter = (ex as? RateLimitException)?.retryAfterSec
            return@withContext AccountFetchOutcome(
                result = err,
                discoveredTarget = newlyDiscovered,
                congested = congested || isWaf || err.code == "timeout",
                wafSignal = isWaf,
                retryAfterSec = retryAfter
            )
        }

        // Lazy homepage discovery fallback ONLY if blank-mode login explicitly complains about merchantId
        if (loginJson.optString("status") != "SUCCESS" && merchantId.isEmpty()) {
            val firstMsg = (loginJson.optJSONObject("data")?.optString("message")
                ?: loginJson.optString("message", "")).lowercase(Locale.US)
            val needsMerchantDiscovery = (firstMsg.contains("merchant") || firstMsg.contains("missing")) &&
                !firstMsg.contains("invalid login") &&
                !firstMsg.contains("invalid password") &&
                !firstMsg.contains("captcha") &&
                !firstMsg.contains("blocked")

            if (needsMerchantDiscovery) {
                val inspected = discoverHostTarget(domain)
                if (inspected != null && inspected.merchantId.isNotBlank()) {
                    newlyDiscovered = inspected
                    host = inspected.host.takeIf { DomainUtil.isPublicDomain(it) } ?: domain
                    merchantId = inspected.merchantId
                    useSelfHeaders = true

                    loginResult = executeWithRetry(
                        maxRetries = 1,
                        onRetry = { retriesUsed++ },
                        onCongestion = { congested = true }
                    ) {
                        postApi(host, buildLoginFields(merchantId), useSelfHeaders)
                    }
                    loginJson = loginResult.getOrElse { ex ->
                        val elapsed = System.currentTimeMillis() - started
                        val err = classifyFailure(domain, ex.message ?: "Could not reach site", elapsed, retriesUsed)
                        val isWaf = isWafErrorCode(err.code)
                        val retryAfter = (ex as? RateLimitException)?.retryAfterSec
                        return@withContext AccountFetchOutcome(
                            result = err,
                            discoveredTarget = newlyDiscovered,
                            congested = congested || isWaf || err.code == "timeout",
                            wafSignal = isWaf,
                            retryAfterSec = retryAfter
                        )
                    }
                }
            }
        }

        if (loginJson.optString("status") != "SUCCESS") {
            val dataObj = loginJson.optJSONObject("data")
            val msg = dataObj?.optString("message") ?: loginJson.optString("message", "Could not sign in")
            val elapsed = System.currentTimeMillis() - started
            val err = classifyFailure(domain, msg, elapsed, retriesUsed)
            val isWaf = isWafErrorCode(err.code)
            return@withContext AccountFetchOutcome(
                result = err,
                discoveredTarget = newlyDiscovered,
                congested = congested || isWaf,
                wafSignal = isWaf
            )
        }

        val dataObj = loginJson.optJSONObject("data")
        val accessId = dataObj?.optString("id", "") ?: ""
        val accessToken = dataObj?.optString("token", "") ?: ""
        if (accessId.isEmpty() || accessToken.isEmpty()) {
            val elapsed = System.currentTimeMillis() - started
            return@withContext AccountFetchOutcome(
                result = AccountResult.Err(domain, "other", "Sign-in did not return a session", elapsed, retriesUsed, System.currentTimeMillis()),
                discoveredTarget = newlyDiscovered,
                congested = congested,
                wafSignal = false
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
            maxRetries = 1,
            onRetry = { retriesUsed++ },
            onCongestion = { congested = true }
        ) {
            postApi(host, syncFields, useSelfHeaders)
        }

        val syncJson = syncResult.getOrElse { ex ->
            val elapsed = System.currentTimeMillis() - started
            val err = classifyFailure(domain, ex.message ?: "Balances did not load", elapsed, retriesUsed)
            val isWaf = isWafErrorCode(err.code)
            val retryAfter = (ex as? RateLimitException)?.retryAfterSec
            return@withContext AccountFetchOutcome(
                result = err,
                discoveredTarget = newlyDiscovered,
                congested = congested || isWaf || err.code == "timeout",
                wafSignal = isWaf,
                retryAfterSec = retryAfter
            )
        }

        if (syncJson.optString("status") != "SUCCESS") {
            val sData = syncJson.optJSONObject("data")
            val msg = sData?.optString("message") ?: "Signed in, but balances did not load"
            val elapsed = System.currentTimeMillis() - started
            val err = classifyFailure(domain, msg, elapsed, retriesUsed)
            val isWaf = isWafErrorCode(err.code)
            return@withContext AccountFetchOutcome(
                result = err,
                discoveredTarget = newlyDiscovered,
                congested = congested || isWaf,
                wafSignal = isWaf
            )
        }

        val userObj = syncJson.optJSONObject("data") ?: JSONObject()
        // If syncData omitted bonus[], try /merchants/getPublicInfo as fallback
        val hasInlineBonuses = userObj.optJSONArray("claimableBonuses") != null ||
            userObj.optJSONArray("availableBonuses") != null ||
            userObj.optJSONArray("bonus") != null
        var fallbackBonuses: JSONArray? = null
        if (!hasInlineBonuses && merchantId.isNotEmpty()) {
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
        val savedSession = SavedDomainSession(
            domain = domain,
            host = host,
            merchantId = merchantId,
            accessId = accessId,
            accessToken = accessToken,
            winningPhone = phone,
            updatedAt = System.currentTimeMillis()
        )
        AccountFetchOutcome(
            result = parsed,
            discoveredTarget = newlyDiscovered,
            congested = congested,
            newSession = savedSession
        )
    }

    suspend fun fetchPublicBonuses(
        rawDomain: String,
        cachedTarget: HostTarget? = null
    ): PublicCatalogueResult = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val domain = DomainUtil.normalizeDomain(rawDomain)
        if (!DomainUtil.isPublicDomain(domain)) {
            return@withContext PublicCatalogueResult(
                domain = domain,
                ok = false,
                bonuses = emptyList(),
                message = "Enter a valid domain",
                latencyMs = (System.currentTimeMillis() - started).coerceAtLeast(1L),
                errorCode = "other"
            )
        }

        var resolvedHost = cachedTarget?.host?.takeIf { DomainUtil.isPublicDomain(it) } ?: domain
        var merchantId = cachedTarget?.merchantId ?: ""
        var retriesUsed = 0
        var attemptsUsed = 0
        var congested = false

        fun buildPublicFields(mid: String): Map<String, String> = mapOf(
            "module" to "/merchants/getPublicInfo",
            "merchantId" to mid,
            "accessId" to "",
            "accessToken" to "",
            "walletIsAdmin" to ""
        )

        // Direct API-first path: call /merchants/getPublicInfo directly (blank merchantId works on
        // the vast majority of sites, avoiding a 750KB–1.2MB homepage GET and homepage-only 403s).
        attemptsUsed++
        var result = executeWithRetry(
            maxRetries = 1,
            onRetry = { retriesUsed++ },
            onCongestion = { congested = true }
        ) {
            postApi(resolvedHost, buildPublicFields(merchantId), useSelfHeaders = true)
        }

        // Lazy homepage discovery fallback ONLY if merchantId was blank and the direct API call
        // explicitly failed with a missing-merchant response or non-JSON/404 redirect.
        val needsHtmlDiscovery = merchantId.isBlank() && result.fold(
            onSuccess = { json ->
                if (json.optString("status") == "SUCCESS") {
                    false
                } else {
                    val msg = (json.optJSONObject("data")?.optString("message")
                        ?: json.optString("message", "")).lowercase(Locale.US)
                    msg.contains("merchant") || msg.contains("missing") || msg.isBlank()
                }
            },
            onFailure = { ex ->
                ex is BadJsonException
            }
        )

        if (needsHtmlDiscovery) {
            val discovered = try {
                discoverHostTargetChecked(domain)
            } catch (e: CancellationException) {
                throw e
            } catch (e: RateLimitException) {
                val elapsed = (System.currentTimeMillis() - started).coerceAtLeast(1L)
                return@withContext PublicCatalogueResult(
                    domain = domain,
                    ok = false,
                    bonuses = emptyList(),
                    message = "Rate limited (429)",
                    host = resolvedHost,
                    latencyMs = elapsed,
                    retriesUsed = retriesUsed,
                    attemptsUsed = attemptsUsed + retriesUsed,
                    errorCode = "rate_limit",
                    congested = true,
                    wafSignal = true,
                    retryAfterSec = e.retryAfterSec
                )
            } catch (e: BlockedException) {
                val elapsed = (System.currentTimeMillis() - started).coerceAtLeast(1L)
                return@withContext PublicCatalogueResult(
                    domain = domain,
                    ok = false,
                    bonuses = emptyList(),
                    message = e.message ?: "Blocked by firewall",
                    host = resolvedHost,
                    latencyMs = elapsed,
                    retriesUsed = retriesUsed,
                    attemptsUsed = attemptsUsed + retriesUsed,
                    errorCode = "blocked",
                    congested = true,
                    wafSignal = true
                )
            } catch (_: Exception) {
                null
            }

            if (discovered != null && discovered.merchantId.isNotBlank()) {
                resolvedHost = discovered.host.takeIf { DomainUtil.isPublicDomain(it) } ?: domain
                merchantId = discovered.merchantId
                attemptsUsed++
                result = executeWithRetry(
                    maxRetries = 1,
                    onRetry = { retriesUsed++ },
                    onCongestion = { congested = true }
                ) {
                    postApi(resolvedHost, buildPublicFields(merchantId), useSelfHeaders = true)
                }
            }
        }

        val json = result.getOrElse { ex ->
            val elapsed = (System.currentTimeMillis() - started).coerceAtLeast(1L)
            val err = classifyFailure(domain, ex.message ?: "Could not reach bonus list", elapsed, retriesUsed)
            val isWaf = isWafErrorCode(err.code)
            val retryAfter = (ex as? RateLimitException)?.retryAfterSec
            return@withContext PublicCatalogueResult(
                domain = domain,
                ok = false,
                bonuses = emptyList(),
                message = err.message.ifBlank { "Could not reach bonus list" },
                host = resolvedHost,
                merchantId = merchantId.ifBlank { null },
                latencyMs = elapsed,
                retriesUsed = retriesUsed,
                attemptsUsed = attemptsUsed + retriesUsed,
                errorCode = err.code,
                congested = congested || isWaf || err.code == "timeout",
                wafSignal = isWaf,
                retryAfterSec = retryAfter
            )
        }

        val elapsed = (System.currentTimeMillis() - started).coerceAtLeast(1L)
        if (json.optString("status") != "SUCCESS") {
            val rawMsg = json.optJSONObject("data")?.optString("message")
                ?: json.optString("message", "Bonus list did not load")
            val err = classifyFailure(domain, rawMsg, elapsed, retriesUsed)
            val isWaf = isWafErrorCode(err.code)
            return@withContext PublicCatalogueResult(
                domain = domain,
                ok = false,
                bonuses = emptyList(),
                message = rawMsg.ifBlank { "Bonus list did not load" },
                host = resolvedHost,
                merchantId = merchantId.ifBlank { null },
                latencyMs = elapsed,
                retriesUsed = retriesUsed,
                attemptsUsed = attemptsUsed + retriesUsed,
                errorCode = err.code,
                congested = congested || isWaf,
                wafSignal = isWaf
            )
        }

        val dataObj = json.optJSONObject("data")
        if (merchantId.isBlank() && dataObj != null) {
            val extractedMid = dataObj.optJSONObject("merchant")?.optString("id", "")
                ?.takeIf { it.isNotBlank() && it != "null" }
                ?: dataObj.optString("merchantId", "").takeIf { it.isNotBlank() && it != "null" }
            if (!extractedMid.isNullOrBlank()) {
                merchantId = extractedMid
            }
        }

        val promos = dataObj?.optJSONArray("promotions")
        val bonuses = parsePublicPromotions(promos)
        PublicCatalogueResult(
            domain = domain,
            ok = true,
            bonuses = bonuses,
            host = resolvedHost,
            merchantId = merchantId.ifBlank { null },
            latencyMs = elapsed,
            retriesUsed = retriesUsed,
            attemptsUsed = attemptsUsed + retriesUsed,
            congested = congested,
            wafSignal = false
        )
    }

    private suspend fun <T> executeWithRetry(
        maxRetries: Int = 1,
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
            } catch (e: BlockedException) {
                // Immediately signal congestion and stop — never blindly retry a firewall block
                onCongestion()
                return Result.failure(e)
            } catch (e: RateLimitException) {
                // Immediately signal congestion and stop — never hammer a 429 rate limit
                onCongestion()
                return Result.failure(e)
            } catch (e: UnknownHostException) {
                // Dead DNS domain — fast-fail immediately without retrying or triggering congestion
                return Result.failure(e)
            } catch (e: TransientHttpException) {
                onCongestion()
                if (attempt >= maxRetries) return Result.failure(e)
                attempt++
                onRetry()
                val baseMs = (600.0 * 2.0.pow((attempt - 1).toDouble())).toLong()
                val jitterMs = Random.nextLong(120L, 380L)
                delay(baseMs + jitterMs)
            } catch (e: SocketTimeoutException) {
                onCongestion()
                if (attempt >= maxRetries) return Result.failure(IOException("timeout"))
                attempt++
                onRetry()
                val baseMs = (500.0 * 2.0.pow((attempt - 1).toDouble())).toLong()
                val jitterMs = Random.nextLong(100L, 320L)
                delay(baseMs + jitterMs)
            } catch (e: IOException) {
                if (attempt >= maxRetries) return Result.failure(e)
                attempt++
                onRetry()
                delay(450L + Random.nextLong(80L, 260L))
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
            if (code == 429) {
                val retryAfter = res.header("Retry-After")?.trim()?.toLongOrNull()?.coerceIn(1L, 120L)
                throw RateLimitException("Rate limited (429)", retryAfter)
            }
            if (code == 401 || code == 403) {
                throw BlockedException("Blocked by firewall ($code)")
            }
            if (code in 500..599) {
                throw TransientHttpException("HTTP $code")
            }
            val str = res.body?.string()?.trim()
            if (str.isNullOrEmpty()) throw BadJsonException("empty-response")

            // Detect Cloudflare / WAF HTML challenge pages returned even on 200/503 responses
            if (isWafChallengeBody(str)) {
                throw BlockedException("Blocked by firewall (WAF challenge)")
            }

            return try {
                JSONObject(str)
            } catch (_: Exception) {
                throw BadJsonException("bad-json")
            }
        }
    }

    private fun isWafChallengeBody(body: String): Boolean {
        if (!body.startsWith("<") && !body.startsWith("<!")) return false
        val sample = body.take(2048).lowercase(Locale.US)
        return sample.contains("just a moment") ||
            sample.contains("cf-browser-verification") ||
            sample.contains("challenge-platform") ||
            sample.contains("attention required") ||
            sample.contains("access denied") ||
            sample.contains("cloudflare") ||
            sample.contains("ddos-guard") ||
            sample.contains("enable javascript and cookies")
    }

    private fun isWafErrorCode(code: String): Boolean =
        code == "blocked" || code == "rate_limit"

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
                AccountResult.Err(domain, "captcha", "Site requires one-time captcha sign-in", latencyMs, retriesUsed, now)
            lower.contains("429") || lower.contains("rate limit") || lower.contains("too many") ->
                AccountResult.Err(domain, "rate_limit", "Rate limited (429)", latencyMs, retriesUsed, now)
            lower.contains("blocked") || lower.contains("waf") || lower.contains("cloudflare") ||
                lower.contains("403") || lower.contains("401") || lower.contains("forbidden") ->
                AccountResult.Err(domain, "blocked", "Blocked by firewall", latencyMs, retriesUsed, now)
            lower.contains("unable to resolve host") || lower.contains("unknown host") ||
                lower.contains("no address associated") ->
                AccountResult.Err(domain, "unreachable", "Site offline (DNS)", latencyMs, retriesUsed, now)
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

        val commissionList = mutableListOf<BonusView>()
        val shareList = mutableListOf<BonusView>()
        val referralList = mutableListOf<BonusView>()
        val newList = mutableListOf<BonusView>()
        val rebateList = mutableListOf<BonusView>()
        val availableBonuses = mutableListOf<BonusView>()

        val accountPackets = user.optJSONArray("bonus")
            ?: user.optJSONArray("claimableBonuses")
            ?: user.optJSONArray("availableBonuses")
        val accountBound = accountPackets != null
        val bonusArr = accountPackets ?: fallbackBonuses ?: JSONArray()

        val seen = HashSet<String>(bonusArr.length())
        val nowMs = System.currentTimeMillis()

        for (i in 0 until bonusArr.length()) {
            val b = bonusArr.optJSONObject(i) ?: continue
            val packetId = optLongId(b, "id")
            val transactionId = optLongId(b, "transactionId")
            val angpaoId = optLongId(b, "angpaoId")
            val luckyId = optLongId(b, "luckyId")

            val rawHeading = b.optString("name", "")
                .ifBlank { b.optString("title", "") }
                .ifBlank { b.optString("description", "") }
            val name = cleanName(rawHeading).ifBlank { "Bonus" }

            val dedupeKey = if (packetId != 0L || transactionId != 0L || angpaoId != 0L || luckyId != 0L) {
                "$packetId:$transactionId:$angpaoId:$luckyId"
            } else {
                "idx:$i:${name.lowercase(Locale.US)}"
            }
            if (!seen.add(dedupeKey)) continue

            val claimConfig = parseJsonObj(b.opt("claimConfig"))
            val txType = b.optString("transactionType", "").uppercase(Locale.US)
            if (!isVisiblePacket(b, claimConfig, txType, name)) continue

            // Authoritative per-account eligibility check matching vefrop app.js & parse-account.ts:
            // Only packets from the signed-in user's bonus[] array that carry an explicit `amount`
            // (including 0 / "0" for mystery/random bonuses like DAILY FREE) and are inside their
            // active start/end window are lit/claimable right now.
            val lit = accountBound && holdsAmount(b.opt("amount")) && isWithinWindow(b, nowMs)

            val displayAmountFlag = optIntOrNull(b, "displayAmount")
            val liveAmount = optFiniteDouble(b, "amount") ?: 0.0
            val randomObj = parseJsonObj(b.opt("bonusRandom"))
            val rMin = optFiniteDouble(randomObj, "min")
            val rMax = optFiniteDouble(randomObj, "max")
            val hasRandomRange = rMax != null && rMax > 0.0

            val resolvedAmount: Double
            val amountDisplay: String
            if (lit) {
                if (displayAmountFlag == 0) {
                    if (hasRandomRange) {
                        resolvedAmount = if (liveAmount > 0.0) liveAmount else 0.0
                        amountDisplay = formatRandomRange(rMin ?: 0.0, rMax!!)
                    } else {
                        resolvedAmount = liveAmount
                        amountDisplay = "?"
                    }
                } else if (liveAmount > 0.0) {
                    resolvedAmount = liveAmount
                    amountDisplay = FormatUtil.moneyCompact(liveAmount)
                } else if (hasRandomRange) {
                    resolvedAmount = 0.0
                    amountDisplay = formatRandomRange(rMin ?: 0.0, rMax!!)
                } else {
                    resolvedAmount = 0.0
                    amountDisplay = "?"
                }
            } else {
                resolvedAmount = 0.0
                amountDisplay = ""
            }

            val hideMin = optIntFlag(claimConfig, "minWithdrawTimeAmount") != 0
            val rawMinW = optPositiveDouble(b, "minWithdraw")
                ?: optPositiveDouble(b, "minWithdrawal")
                ?: optPositiveDouble(claimConfig, "minWithdraw")
                ?: optPositiveDouble(claimConfig, "minWithdrawal")
            val minW = if (!hideMin && rawMinW != null && rawMinW >= 0.01) rawMinW else null

            val rawMaxW = optPositiveDouble(b, "maxWithdraw")
                ?: optPositiveDouble(b, "maxWithdrawal")
                ?: optPositiveDouble(claimConfig, "maxWithdraw")
                ?: optPositiveDouble(claimConfig, "maxWithdrawal")
            val maxW = if (rawMaxW != null && rawMaxW >= 0.01) rawMaxW else null
            val isAngpaoPercentMax = txType == "ANGPAO" && maxW != null && maxW < 1.0
            val maxDisplay = maxW?.let {
                if (isAngpaoPercentMax) {
                    "${kotlin.math.round(it * 100.0).toInt()}%"
                } else {
                    FormatUtil.moneyCompact(it)
                }
            }

            val minBet = optPositiveDouble(b, "minBet")?.takeIf { it >= 0.01 }
            val mobileAppOnly = optIntFlag(claimConfig, "mobileAppOnly") == 1 ||
                claimConfig.optBoolean("mobileAppOnly", false)
            val detail = buildBonusDetail(b, claimConfig, txType).ifBlank { null }

            val view = BonusView(
                name = name,
                amount = resolvedAmount,
                amountDisplay = amountDisplay,
                minWithdrawal = minW,
                maxWithdrawal = maxW,
                rollover = minBet,
                isPercent = false,
                maxDisplay = maxDisplay,
                detail = detail,
                lit = lit,
                mobileAppOnly = mobileAppOnly,
                packetId = packetId,
                transactionId = transactionId,
                angpaoId = angpaoId,
                luckyId = luckyId
            )
            availableBonuses.add(view)

            if (!lit) continue
            val custom = claimConfig.optString("customBonus", "")
            when (SiteOrderUtil.bonusCategoryKey(name, custom, txType)) {
                "commission" -> commissionList.add(view)
                "share" -> shareList.add(view)
                "referral" -> referralList.add(view)
                "new" -> newList.add(view)
                "rebate" -> rebateList.add(view)
            }
        }

        availableBonuses.sortWith(
            compareByDescending<BonusView> { it.lit }
                .thenBy { SiteOrderUtil.bonusTurnover(it) ?: Double.POSITIVE_INFINITY }
                .thenByDescending { it.amount }
                .thenBy { it.name.lowercase(Locale.US) }
        )

        fun pick(list: List<BonusView>): BonusView? {
            val litOnly = list.filter { it.lit }
            if (litOnly.isEmpty()) return null
            return litOnly.maxByOrNull { it.amount }
        }

        val commission = pick(commissionList)
        val share = pick(shareList)
        val referral = pick(referralList)
        val fresh = pick(newList)
        val rebate = pick(rebateList)

        val hasLitUnclaimed = availableBonuses.any { it.lit }
        val hasData = wallets.any { it.balance > 0.001 } || cash > 0.001 || hasLitUnclaimed

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

    private fun holdsAmount(raw: Any?): Boolean {
        if (raw == null || raw === JSONObject.NULL) return false
        return when (raw) {
            is Number -> raw.toDouble().isFinite()
            is String -> {
                val trimmed = raw.trim()
                trimmed.isNotEmpty() && trimmed.toDoubleOrNull()?.isFinite() == true
            }
            else -> false
        }
    }

    private fun isVisiblePacket(
        bonus: JSONObject,
        cfg: JSONObject,
        txType: String,
        cleanBonusName: String
    ): Boolean {
        if (txType == "REBATE" || txType == "COMMISSION" || txType == "ANGPAO" ||
            cleanBonusName.equals("Lucky Draw", ignoreCase = true)
        ) {
            return true
        }
        val showIf = optIntFlag(cfg, "showIfAmount")
        if (showIf == 1 && !holdsAmount(bonus.opt("amount"))) {
            return false
        }
        return true
    }

    private fun isWithinWindow(bonus: JSONObject, nowMs: Long): Boolean {
        val start = parseWindowTimestamp(bonus.optString("startDateTime", ""))
        val end = parseWindowTimestamp(bonus.optString("endDateTime", ""))
        if (start != null && nowMs < start) return false
        if (end != null && nowMs > end) return false
        return true
    }

    private fun parseWindowTimestamp(raw: String): Long? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.equals("null", ignoreCase = true)) return null
        val isoEpoch = FormatUtil.parseIsoEpochMs(trimmed)
        if (isoEpoch > 0L) return isoEpoch
        val formats = arrayOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd"
        )
        for (pattern in formats) {
            try {
                val sdf = java.text.SimpleDateFormat(pattern, Locale.US)
                sdf.isLenient = false
                val parsed = sdf.parse(trimmed)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
                // Try next format
            }
        }
        return null
    }

    private fun formatRandomRange(minVal: Double, maxVal: Double): String {
        return if (minVal > 0.0 && maxVal > minVal) {
            "${FormatUtil.moneyCompact(minVal)}–${FormatUtil.moneyCompact(maxVal)}"
        } else {
            FormatUtil.moneyCompact(maxVal)
        }
    }

    private fun buildBonusDetail(
        bonus: JSONObject,
        cfg: JSONObject,
        txType: String
    ): String {
        if (optIntFlag(cfg, "displayDescriptionOnly") != 0) return ""
        val parts = mutableListOf<String>()
        val turnover = optPositiveDouble(bonus, "minBet")
        if (turnover != null && turnover >= 0.01) {
            parts.add("Turnover ${FormatUtil.moneyCompact(turnover)}")
        }
        val hideMin = optIntFlag(cfg, "minWithdrawTimeAmount") != 0
        val minW = optPositiveDouble(bonus, "minWithdraw")
        if (!hideMin && minW != null && minW >= 0.01) {
            parts.add("Min ${FormatUtil.moneyCompact(minW)}")
        }
        val maxW = optPositiveDouble(bonus, "maxWithdraw")
        if (maxW != null && maxW >= 0.01) {
            val isAngpaoPct = txType == "ANGPAO" && maxW < 1.0
            val formattedMax = if (isAngpaoPct) {
                "${kotlin.math.round(maxW * 100.0).toInt()}%"
            } else {
                FormatUtil.moneyCompact(maxW)
            }
            parts.add("Max $formattedMax")
        }
        val minTop = optPositiveDouble(bonus, "minTopup")
        val maxTop = optPositiveDouble(bonus, "maxTopup")
        when {
            minTop != null && maxTop != null ->
                parts.add("Deposit ${FormatUtil.moneyCompact(minTop)}–${FormatUtil.moneyCompact(maxTop)}")
            minTop != null -> parts.add("Min deposit ${FormatUtil.moneyCompact(minTop)}")
            maxTop != null -> parts.add("Max deposit ${FormatUtil.moneyCompact(maxTop)}")
        }
        val minRound = optPositiveDouble(bonus, "minRound")
        if (minRound != null && minRound > 0.0) {
            parts.add("Deposits ${minRound.toInt()}")
        }
        val maxRound = optPositiveDouble(bonus, "maxRound")
        val reset = bonus.optString("reset", "").trim().uppercase(Locale.US)
        if (maxRound != null && maxRound > 0.0 && reset.isNotEmpty()) {
            val period = when (reset) {
                "DAILY" -> "day"
                "WEEKLY" -> "week"
                "MONTHLY" -> "month"
                else -> "hour"
            }
            parts.add("Limited to ${maxRound.toInt()} claim per $period")
        }
        return parts.joinToString(" · ")
    }

    private fun optLongId(obj: JSONObject, key: String): Long {
        if (!obj.has(key) || obj.isNull(key)) return 0L
        return when (val raw = obj.opt(key)) {
            is Number -> raw.toLong()
            is String -> raw.trim().toLongOrNull() ?: 0L
            else -> 0L
        }
    }

    private fun optIntFlag(obj: JSONObject, key: String): Int {
        return optIntOrNull(obj, key) ?: 0
    }

    private fun optIntOrNull(obj: JSONObject, key: String): Int? {
        if (!obj.has(key) || obj.isNull(key)) return null
        return when (val raw = obj.opt(key)) {
            is Number -> raw.toInt()
            is Boolean -> if (raw) 1 else 0
            is String -> raw.trim().toIntOrNull()
            else -> null
        }
    }

    private fun optFiniteDouble(obj: JSONObject, key: String): Double? {
        if (!obj.has(key) || obj.isNull(key)) return null
        val v = when (val raw = obj.opt(key)) {
            is Number -> raw.toDouble()
            is String -> raw.trim().toDoubleOrNull()
            else -> null
        }
        return if (v != null && v.isFinite()) v else null
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
    private class RateLimitException(message: String, val retryAfterSec: Long? = null) : Exception(message)
    private class BadJsonException(message: String) : Exception(message)
}
