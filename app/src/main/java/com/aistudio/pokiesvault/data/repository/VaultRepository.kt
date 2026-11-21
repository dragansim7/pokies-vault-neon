package com.aistudio.pokiesvault.data.repository

import android.content.Context
import com.aistudio.pokiesvault.data.catalog.CatalogData
import com.aistudio.pokiesvault.data.local.AccountSnapshotEntity
import com.aistudio.pokiesvault.data.local.DomainHealthEntity
import com.aistudio.pokiesvault.data.local.FetchRunEntity
import com.aistudio.pokiesvault.data.local.HostCacheEntity
import com.aistudio.pokiesvault.data.local.PublicBonusCacheEntity
import com.aistudio.pokiesvault.data.local.SiteEntity
import com.aistudio.pokiesvault.data.local.VaultDatabase
import com.aistudio.pokiesvault.data.local.VaultPreferencesStore
import com.aistudio.pokiesvault.data.model.AccountResult
import com.aistudio.pokiesvault.data.model.BonusMode
import com.aistudio.pokiesvault.data.model.CatalogSite
import com.aistudio.pokiesvault.data.model.DomainHealthRecord
import com.aistudio.pokiesvault.data.model.FallbackCred
import com.aistudio.pokiesvault.data.model.FetchCheckpoint
import com.aistudio.pokiesvault.data.model.FetchRunSummary
import com.aistudio.pokiesvault.data.model.FetchSpeedPreset
import com.aistudio.pokiesvault.data.model.PublicBonus
import com.aistudio.pokiesvault.data.model.PublicBonusSiteCache
import com.aistudio.pokiesvault.data.model.SiteGroup
import com.aistudio.pokiesvault.data.model.SiteRecord
import com.aistudio.pokiesvault.data.model.SiteStatus
import com.aistudio.pokiesvault.data.model.SortMode
import com.aistudio.pokiesvault.data.model.VaultBackupPayload
import com.aistudio.pokiesvault.data.model.VaultCredentials
import com.aistudio.pokiesvault.data.model.VaultPreferences
import com.aistudio.pokiesvault.data.model.VaultThemeId
import com.aistudio.pokiesvault.data.model.ViewMode
import com.aistudio.pokiesvault.data.network.CasinoApiService
import com.aistudio.pokiesvault.data.network.ConcurrentFetchEngine
import com.aistudio.pokiesvault.data.network.ConcurrentFetchEngine.Companion.toHostTarget
import com.aistudio.pokiesvault.data.network.HostTarget
import com.aistudio.pokiesvault.data.network.PublicCatalogueResult
import com.aistudio.pokiesvault.util.DomainUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import java.time.Instant

class VaultRepository(private val context: Context) {
    private val db = VaultDatabase.getInstance(context)
    private val dao = db.vaultDao()
    private val prefsStore = VaultPreferencesStore(context)
    private val apiService = CasinoApiService()
    val engine = ConcurrentFetchEngine(apiService)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    val preferencesFlow: Flow<VaultPreferences> = prefsStore.preferencesFlow
    val credentialsFlow: Flow<VaultCredentials> = prefsStore.credentialsFlow
    val checkpointFlow: Flow<FetchCheckpoint?> = prefsStore.checkpointFlow
    val hiddenCountFlow: Flow<Int> = dao.observeHiddenCount()

    val visibleSitesFlow: Flow<List<SiteRecord>> = dao.observeVisibleSites().map { entities ->
        entities.map { entity ->
            val group = try {
                SiteGroup.valueOf(entity.siteGroup)
            } catch (_: Exception) {
                SiteGroup.LATEST
            }
            SiteRecord(
                domain = entity.domain,
                group = group,
                addedAt = entity.addedAt,
                isMine = entity.isMine,
                isVisited = entity.isVisited,
                isHidden = entity.isHidden
            )
        }
    }

    val domainHealthFlow: Flow<Map<String, DomainHealthRecord>> =
        dao.observeDomainHealth().map { list ->
            list.associate { entity ->
                val status = try {
                    SiteStatus.valueOf(entity.status)
                } catch (_: Exception) {
                    SiteStatus.UNKNOWN
                }
                entity.domain to DomainHealthRecord(
                    domain = entity.domain,
                    status = status,
                    latencyMs = entity.latencyMs,
                    checkedAt = entity.checkedAt
                )
            }
        }

    val latestAccountsByDomainFlow: Flow<Map<String, AccountResult.Ok>> =
        dao.observeLatestOkSnapshots().map { snapshots ->
            val out = HashMap<String, AccountResult.Ok>(snapshots.size)
            for (snap in snapshots) {
                val decoded = decodeAccountResult(snap.payloadJson) as? AccountResult.Ok
                if (decoded != null) {
                    out[snap.domain] = decoded
                }
            }
            out
        }

    val fetchRunsFlow: Flow<List<FetchRunSummary>> = dao.observeFetchRuns().map { runs ->
        runs.map {
            FetchRunSummary(
                runId = it.runId,
                startedAt = it.startedAt,
                completedAt = it.completedAt,
                targetCount = it.targetCount,
                okCount = it.okCount,
                withDataCount = it.withDataCount,
                errCount = it.errCount,
                totalCash = it.totalCash
            )
        }
    }

    val publicBonusesFlow: Flow<Map<String, PublicBonusSiteCache>> =
        dao.observePublicBonuses().map { entries ->
            entries.associate { entity ->
                val bonuses = try {
                    json.decodeFromString<List<PublicBonus>>(entity.bonusesJson)
                } catch (_: Exception) {
                    emptyList()
                }
                entity.domain to PublicBonusSiteCache(
                    domain = entity.domain,
                    ok = entity.ok,
                    bonuses = bonuses,
                    errorMessage = entity.errorMessage,
                    fetchedAt = entity.fetchedAt
                )
            }
        }

    /**
     * Seeds built-in catalog into Room on first run and migrates any legacy v1 SharedPreferences
     * bookmarks, custom domains, and credentials transparently off the main thread.
     */
    suspend fun ensureInitialized() = withContext(Dispatchers.IO) {
        if (dao.countSites() == 0) {
            val builtIn = CatalogData.SITES.mapIndexed { idx, site ->
                SiteEntity(
                    domain = DomainUtil.normalizeDomain(site.domain),
                    siteGroup = site.group.name,
                    addedAt = site.addedAt,
                    isCustom = false,
                    sortOrder = idx
                )
            }
            dao.insertSitesIgnore(builtIn)
        }

        // One-time migration from legacy SharedPreferences if present
        val legacyPrefs = context.getSharedPreferences("pokies_vault_prefs", Context.MODE_PRIVATE)
        if (!legacyPrefs.getBoolean("migrated_to_room_v2", false)) {
            val legacyMine = legacyPrefs.getStringSet("mine", emptySet()) ?: emptySet()
            val legacyVisited = legacyPrefs.getStringSet("visited", emptySet()) ?: emptySet()
            val legacyPhone = legacyPrefs.getString("phone", "") ?: ""
            val legacyPass = legacyPrefs.getString("password", "") ?: ""
            val legacyCustomJson = legacyPrefs.getString("custom", "[]") ?: "[]"
            val legacyFallbacksJson = legacyPrefs.getString("fallbacks", "[]") ?: "[]"

            try {
                val arr = JSONArray(legacyCustomJson)
                val customEntities = mutableListOf<SiteEntity>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val d = DomainUtil.normalizeDomain(obj.optString("domain", ""))
                    if (DomainUtil.isPublicDomain(d)) {
                        customEntities.add(
                            SiteEntity(
                                domain = d,
                                siteGroup = SiteGroup.CUSTOM.name,
                                addedAt = obj.optString("addedAt", Instant.now().toString()),
                                isCustom = true,
                                isMine = true
                            )
                        )
                    }
                }
                if (customEntities.isNotEmpty()) dao.insertSitesIgnore(customEntities)
            } catch (_: Exception) {
            }

            if (legacyMine.isNotEmpty()) {
                dao.markAllMine(legacyMine.map { DomainUtil.normalizeDomain(it) })
            }
            for (v in legacyVisited) {
                dao.updateVisited(DomainUtil.normalizeDomain(v), true)
            }

            if (legacyPhone.isNotBlank() || legacyPass.isNotBlank()) {
                val fallbacks = mutableListOf<FallbackCred>()
                try {
                    val arr = JSONArray(legacyFallbacksJson)
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        fallbacks.add(
                            FallbackCred(
                                phone = obj.optString("phone", ""),
                                password = obj.optString("password", "")
                            )
                        )
                    }
                } catch (_: Exception) {
                }
                prefsStore.saveCredentials(VaultCredentials(legacyPhone, legacyPass, fallbacks))
            }

            legacyPrefs.edit()
                .remove("password")
                .remove("fallbacks")
                .putBoolean("migrated_to_room_v2", true)
                .apply()
        }
    }

    // --- Preferences & Credentials ---
    suspend fun setTheme(themeId: VaultThemeId) = prefsStore.setTheme(themeId)
    suspend fun setViewMode(mode: ViewMode) = prefsStore.setViewMode(mode)
    suspend fun setSortMode(mode: SortMode) = prefsStore.setSortMode(mode)
    suspend fun setBonusMode(mode: BonusMode) = prefsStore.setBonusMode(mode)
    suspend fun setNotVisited(enabled: Boolean) = prefsStore.setNotVisited(enabled)
    suspend fun setWallets(enabled: Boolean) = prefsStore.setWallets(enabled)
    suspend fun setMineOnly(enabled: Boolean) = prefsStore.setMineOnly(enabled)
    suspend fun setFetchSpeedPreset(preset: FetchSpeedPreset) = prefsStore.setFetchSpeedPreset(preset)
    suspend fun saveCheckpoint(checkpoint: FetchCheckpoint) = prefsStore.saveCheckpoint(checkpoint)
    suspend fun clearCheckpoint() = prefsStore.clearCheckpoint()
    suspend fun saveCredentials(credentials: VaultCredentials) = prefsStore.saveCredentials(credentials)

    // --- Unified Domain Management ---
    suspend fun toggleMine(domain: String, currentlyMine: Boolean) = withContext(Dispatchers.IO) {
        dao.updateMine(domain, !currentlyMine)
    }

    suspend fun markVisited(domain: String) = withContext(Dispatchers.IO) {
        dao.updateVisited(domain, true)
    }

    suspend fun addDomains(rawDomains: List<String>, markAsMine: Boolean = false): Int = withContext(Dispatchers.IO) {
        val existing = dao.getAllSitesSnapshot().associateBy { it.domain }
        val nowIso = Instant.now().toString()
        val toUpsert = mutableListOf<SiteEntity>()
        var addedCount = 0

        for (raw in rawDomains) {
            val domain = DomainUtil.normalizeDomain(raw)
            if (!DomainUtil.isPublicDomain(domain)) continue
            val prev = existing[domain]
            if (prev == null) {
                toUpsert.add(
                    SiteEntity(
                        domain = domain,
                        siteGroup = SiteGroup.CUSTOM.name,
                        addedAt = nowIso,
                        isCustom = true,
                        isMine = markAsMine,
                        isVisited = false,
                        isHidden = false,
                        sortOrder = -1
                    )
                )
                addedCount++
            } else if (prev.isHidden || (markAsMine && !prev.isMine)) {
                toUpsert.add(
                    prev.copy(
                        isHidden = false,
                        isMine = prev.isMine || markAsMine
                    )
                )
                if (prev.isHidden) addedCount++
            }
        }
        if (toUpsert.isNotEmpty()) {
            dao.upsertSites(toUpsert)
        }
        addedCount
    }

    suspend fun hideDomains(domains: List<String>) = withContext(Dispatchers.IO) {
        if (domains.isNotEmpty()) {
            dao.updateHidden(domains, true)
        }
    }

    suspend fun restoreHiddenDomains() = withContext(Dispatchers.IO) {
        dao.restoreAllHidden()
    }

    // --- Host Cache ---
    suspend fun loadHostCacheMap(): Map<String, HostTarget> = withContext(Dispatchers.IO) {
        dao.getAllHostCaches().associate { it.domain to it.toHostTarget() }
    }

    suspend fun saveHostTarget(target: HostTarget) = withContext(Dispatchers.IO) {
        dao.upsertHostCache(
            HostCacheEntity(
                domain = target.domain,
                resolvedHost = target.host,
                merchantId = target.merchantId,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    // --- Fetch Runs & Snapshots ---
    suspend fun loadRunResults(runId: Long): List<AccountResult> = withContext(Dispatchers.IO) {
        dao.getSnapshotsForRun(runId).mapNotNull { decodeAccountResult(it.payloadJson) }
    }

    suspend fun loadLatestRunResults(): List<AccountResult> = withContext(Dispatchers.IO) {
        val latest = dao.getLatestRun() ?: return@withContext emptyList()
        loadRunResults(latest.runId)
    }

    suspend fun persistFetchRun(startedAt: Long, targetCount: Int, rows: List<AccountResult>): Long =
        withContext(Dispatchers.IO) {
            val okRows = rows.filterIsInstance<AccountResult.Ok>()
            val errCount = rows.count { it is AccountResult.Err }
            val withData = okRows.count { it.hasData }
            val totalCash = okRows.sumOf { it.cash }

            val run = FetchRunEntity(
                startedAt = startedAt,
                completedAt = System.currentTimeMillis(),
                targetCount = targetCount,
                okCount = okRows.size,
                withDataCount = withData,
                errCount = errCount,
                totalCash = totalCash
            )

            val snapshots = rows.map { row ->
                when (row) {
                    is AccountResult.Ok -> AccountSnapshotEntity(
                        runId = 0L,
                        domain = row.domain,
                        isOk = true,
                        hasData = row.hasData,
                        cash = row.cash,
                        cashWalletName = row.cashWalletName,
                        errorCode = null,
                        errorMessage = null,
                        latencyMs = row.latencyMs,
                        retriesUsed = row.retriesUsed,
                        fetchedAt = row.fetchedAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
                        payloadJson = json.encodeToString<AccountResult>(row)
                    )
                    is AccountResult.Err -> AccountSnapshotEntity(
                        runId = 0L,
                        domain = row.domain,
                        isOk = false,
                        hasData = false,
                        cash = 0.0,
                        cashWalletName = "",
                        errorCode = row.code,
                        errorMessage = row.message,
                        latencyMs = row.latencyMs,
                        retriesUsed = row.retriesUsed,
                        fetchedAt = row.fetchedAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
                        payloadJson = json.encodeToString<AccountResult>(row)
                    )
                }
            }

            dao.saveCompletedRun(run, snapshots)
        }

    // --- Public Bonuses ---
    suspend fun savePublicBonusResult(result: PublicCatalogueResult) = withContext(Dispatchers.IO) {
        dao.upsertPublicBonus(
            PublicBonusCacheEntity(
                domain = result.domain,
                ok = result.ok,
                bonusesJson = json.encodeToString(result.bonuses),
                errorMessage = result.message,
                fetchedAt = System.currentTimeMillis()
            )
        )
    }

    // --- Domain Health ---
    suspend fun saveDomainHealth(record: DomainHealthRecord) = withContext(Dispatchers.IO) {
        dao.upsertDomainHealth(
            DomainHealthEntity(
                domain = record.domain,
                status = record.status.name,
                latencyMs = record.latencyMs,
                checkedAt = record.checkedAt
            )
        )
    }

    suspend fun saveDomainHealthBatch(records: List<DomainHealthRecord>) = withContext(Dispatchers.IO) {
        if (records.isEmpty()) return@withContext
        dao.upsertDomainHealthBatch(
            records.map {
                DomainHealthEntity(
                    domain = it.domain,
                    status = it.status.name,
                    latencyMs = it.latencyMs,
                    checkedAt = it.checkedAt
                )
            }
        )
    }

    // --- Cache Maintenance & Reset ---
    suspend fun clearVolatileCaches() = withContext(Dispatchers.IO) {
        dao.clearHostCache()
        dao.clearDomainHealth()
        dao.clearPublicBonuses()
    }

    suspend fun clearAllDeviceData() = withContext(Dispatchers.IO) {
        dao.clearSnapshots()
        dao.clearFetchRuns()
        dao.clearHostCache()
        dao.clearDomainHealth()
        dao.clearPublicBonuses()
        dao.deleteCustomSites()
        dao.resetBuiltInFlags()
        prefsStore.clearAll()
    }

    // --- SAF JSON Backup Export / Import ---
    suspend fun exportVaultJson(includeCredentials: Boolean): String = withContext(Dispatchers.IO) {
        val sites = dao.getAllSitesSnapshot()
        val prefs = prefsStore.preferencesFlow.first()
        val creds = if (includeCredentials) prefsStore.credentialsFlow.first() else null
        val latestOk = latestAccountsByDomainFlow.first().values.toList()

        val payload = VaultBackupPayload(
            version = 2,
            exportedAt = System.currentTimeMillis(),
            themeId = prefs.themeId,
            customSites = sites.filter { it.isCustom }.map {
                CatalogSite(it.domain, SiteGroup.CUSTOM, it.addedAt)
            },
            mineDomains = sites.filter { it.isMine }.map { it.domain },
            visitedDomains = sites.filter { it.isVisited }.map { it.domain },
            hiddenDomains = sites.filter { it.isHidden }.map { it.domain },
            latestAccounts = latestOk,
            credentials = creds
        )
        json.encodeToString(payload)
    }

    suspend fun importVaultJson(rawJson: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val payload = json.decodeFromString<VaultBackupPayload>(rawJson)
            if (payload.customSites.isNotEmpty()) {
                addDomains(payload.customSites.map { it.domain }, markAsMine = false)
            }
            if (payload.mineDomains.isNotEmpty()) {
                dao.markAllMine(payload.mineDomains.map { DomainUtil.normalizeDomain(it) })
            }
            for (visited in payload.visitedDomains) {
                dao.updateVisited(DomainUtil.normalizeDomain(visited), true)
            }
            if (payload.hiddenDomains.isNotEmpty()) {
                dao.updateHidden(payload.hiddenDomains.map { DomainUtil.normalizeDomain(it) }, true)
            }
            prefsStore.setTheme(payload.themeId)
            payload.credentials?.let { creds ->
                if (creds.phone.isNotBlank() || creds.password.isNotBlank()) {
                    prefsStore.saveCredentials(creds)
                }
            }
            if (payload.latestAccounts.isNotEmpty()) {
                persistFetchRun(
                    startedAt = payload.exportedAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
                    targetCount = payload.latestAccounts.size,
                    rows = payload.latestAccounts
                )
            }
            Result.success(
                "Restored ${payload.mineDomains.size} saved accounts, ${payload.customSites.size} custom sites, and ${payload.latestAccounts.size} balance snapshots."
            )
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("Invalid Pokies Vault backup JSON: ${e.message ?: "parse error"}"))
        }
    }

    private fun decodeAccountResult(raw: String): AccountResult? {
        return try {
            json.decodeFromString<AccountResult>(raw)
        } catch (_: Exception) {
            null
        }
    }
}
