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
import com.aistudio.pokiesvault.data.model.CardColor
import com.aistudio.pokiesvault.data.model.CatalogSite
import com.aistudio.pokiesvault.data.model.DomainHealthRecord
import com.aistudio.pokiesvault.data.model.FallbackCred
import com.aistudio.pokiesvault.data.model.FetchCheckpoint
import com.aistudio.pokiesvault.data.model.FetchRunSummary
import com.aistudio.pokiesvault.data.model.FetchSpeedPreset
import com.aistudio.pokiesvault.data.model.PublicBonus
import com.aistudio.pokiesvault.data.model.PublicBonusSiteCache
import com.aistudio.pokiesvault.data.model.SavedDomainSession
import com.aistudio.pokiesvault.data.model.SiteGroup
import com.aistudio.pokiesvault.data.model.SiteRecord
import com.aistudio.pokiesvault.data.model.SiteStatus
import com.aistudio.pokiesvault.data.model.SortMode
import com.aistudio.pokiesvault.data.model.VaultBackupPayload
import com.aistudio.pokiesvault.data.model.VaultCredentials
import com.aistudio.pokiesvault.data.model.VaultPreferences
import com.aistudio.pokiesvault.data.model.VaultThemeId
import com.aistudio.pokiesvault.data.model.ViewMode
import com.aistudio.pokiesvault.data.network.AccountFetchOutcome
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
        prettyPrint = false
    }

    private val prettyJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    val preferencesFlow: Flow<VaultPreferences> = prefsStore.preferencesFlow
    val credentialsFlow: Flow<VaultCredentials> = prefsStore.credentialsFlow
    val checkpointFlow: Flow<FetchCheckpoint?> = prefsStore.checkpointFlow
    val bonusCheckpointFlow: Flow<FetchCheckpoint?> = prefsStore.bonusCheckpointFlow
    val domainSessionsFlow: Flow<Map<String, SavedDomainSession>> = prefsStore.domainSessionsFlow
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

    val latestResultsByDomainFlow: Flow<Map<String, AccountResult>> =
        dao.observeLatestSnapshots().map { snapshots ->
            val out = HashMap<String, AccountResult>(snapshots.size)
            for (snap in snapshots) {
                val decoded = decodeAccountResult(snap.payloadJson)
                if (decoded != null) {
                    val existing = out[snap.domain]
                    // Prefer OK over Err if two snapshots share the exact same millisecond timestamp
                    if (existing == null || (existing is AccountResult.Err && decoded is AccountResult.Ok)) {
                        out[snap.domain] = decoded
                    }
                }
            }
            out
        }

    val sameCasinoCountsFlow: Flow<Map<String, Int>> =
        dao.observeHostCaches().map { caches ->
            val byCluster = HashMap<String, MutableSet<String>>()
            val domainToCluster = HashMap<String, String>(caches.size)
            for (entry in caches) {
                val domain = DomainUtil.normalizeDomain(entry.domain)
                if (domain.isBlank()) continue
                val key = when {
                    entry.merchantId.isNotBlank() -> "mid:${entry.merchantId.trim()}"
                    entry.resolvedHost.isNotBlank() && DomainUtil.normalizeDomain(entry.resolvedHost) != domain ->
                        "host:${DomainUtil.normalizeDomain(entry.resolvedHost)}"
                    else -> continue
                }
                domainToCluster[domain] = key
                byCluster.getOrPut(key) { LinkedHashSet() }.add(domain)
            }
            val counts = HashMap<String, Int>(domainToCluster.size)
            for ((domain, key) in domainToCluster) {
                val clusterSize = byCluster[key]?.size ?: 1
                if (clusterSize > 1) {
                    counts[domain] = clusterSize - 1
                }
            }
            counts
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
                dao.markAllMineChunked(legacyMine.map { DomainUtil.normalizeDomain(it) })
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

        cleanupDuplicateSnapshotsIfNeeded()
    }

    /**
     * Removes redundant duplicate partial-scan snapshot rows from earlier large runs while
     * strictly preserving the latest saved snapshot and latest OK balance per site.
     */
    suspend fun cleanupDuplicateSnapshotsIfNeeded(): Int = withContext(Dispatchers.IO) {
        try {
            val totalRows = dao.countSnapshots()
            if (totalRows <= 50) return@withContext 0
            val distinctDomains = dao.countDistinctSnapshotDomains().coerceAtLeast(1)
            // Only compact when duplicates exist beyond the 2 retained rows per domain (latest + latest OK)
            if (totalRows > distinctDomains * 2) {
                val checkpointRunId = try {
                    prefsStore.checkpointFlow.first()?.partialRunId
                } catch (_: Exception) {
                    null
                }
                dao.cleanupDuplicateSnapshots(preserveRunId = checkpointRunId)
            } else {
                0
            }
        } catch (_: Throwable) {
            0
        }
    }

    // --- Preferences & Credentials ---
    suspend fun setTheme(themeId: VaultThemeId) = prefsStore.setTheme(themeId)
    suspend fun setViewMode(mode: ViewMode) = prefsStore.setViewMode(mode)
    suspend fun setSortMode(mode: SortMode) = prefsStore.setSortMode(mode)
    suspend fun setBonusMode(mode: BonusMode) = prefsStore.setBonusMode(mode)
    suspend fun setCardColor(color: CardColor) = prefsStore.setCardColor(color)
    suspend fun setNotVisited(enabled: Boolean) = prefsStore.setNotVisited(enabled)
    suspend fun setSignupOnly(enabled: Boolean) = prefsStore.setSignupOnly(enabled)
    suspend fun setWallets(enabled: Boolean) = prefsStore.setWallets(enabled)
    suspend fun setMineOnly(enabled: Boolean) = prefsStore.setMineOnly(enabled)
    suspend fun applySortFilter(
        sortMode: SortMode,
        bonusMode: BonusMode,
        notVisited: Boolean,
        signupOnly: Boolean,
        mineOnly: Boolean
    ) = prefsStore.applySortFilter(sortMode, bonusMode, notVisited, signupOnly, mineOnly)
    suspend fun resetSortFilter() = prefsStore.resetSortFilter()
    suspend fun setFetchSpeedPreset(preset: FetchSpeedPreset) = prefsStore.setFetchSpeedPreset(preset)
    suspend fun setBonusSpeedPreset(preset: FetchSpeedPreset) = prefsStore.setBonusSpeedPreset(preset)
    suspend fun setAllowBackgroundRuns(enabled: Boolean) = prefsStore.setAllowBackgroundRuns(enabled)
    suspend fun saveCheckpoint(checkpoint: FetchCheckpoint) = prefsStore.saveCheckpoint(checkpoint)
    suspend fun clearCheckpoint() = prefsStore.clearCheckpoint()
    suspend fun saveBonusCheckpoint(checkpoint: FetchCheckpoint) = prefsStore.saveBonusCheckpoint(checkpoint)
    suspend fun clearBonusCheckpoint() = prefsStore.clearBonusCheckpoint()
    suspend fun saveCredentials(credentials: VaultCredentials) = prefsStore.saveCredentials(credentials)
    suspend fun loadDomainSessionsMap(): Map<String, SavedDomainSession> = prefsStore.domainSessionsFlow.first()
    suspend fun saveDomainSession(session: SavedDomainSession) = prefsStore.saveDomainSession(session)
    suspend fun saveDomainSessionsBatch(updates: Map<String, SavedDomainSession>) =
        prefsStore.saveDomainSessionsBatch(updates)
    suspend fun clearDomainToken(domain: String) = prefsStore.clearDomainToken(domain)

    suspend fun syncWithCapturedSession(
        rawDomain: String,
        accessId: String,
        accessToken: String,
        host: String = "",
        merchantId: String = ""
    ): AccountFetchOutcome {
        val domain = DomainUtil.normalizeDomain(rawDomain)
        val cachedMap = loadHostCacheMap()
        val existingTarget = cachedMap[domain]
        val effectiveHost = host.takeIf { DomainUtil.isPublicDomain(it) }
            ?: existingTarget?.host?.takeIf { DomainUtil.isPublicDomain(it) }
            ?: domain
        val effectiveMerchant = merchantId.ifBlank { existingTarget?.merchantId.orEmpty() }
        val target = if (effectiveMerchant.isNotBlank() || effectiveHost != domain) {
            HostTarget(domain = domain, host = effectiveHost, merchantId = effectiveMerchant)
        } else {
            existingTarget
        }
        val outcome = apiService.syncAccountWithSession(
            rawDomain = domain,
            accessId = accessId,
            accessToken = accessToken,
            cachedTarget = target
        )
        val resolvedTarget = outcome.discoveredTarget ?: target
        if (resolvedTarget != null && resolvedTarget.merchantId.isNotBlank()) {
            saveHostTarget(resolvedTarget)
        }
        if (outcome.result is AccountResult.Ok) {
            saveDomainSession(
                SavedDomainSession(
                    domain = domain,
                    host = resolvedTarget?.host ?: effectiveHost,
                    merchantId = resolvedTarget?.merchantId ?: effectiveMerchant,
                    accessId = accessId,
                    accessToken = accessToken,
                    updatedAt = System.currentTimeMillis()
                )
            )
            dao.updateMine(domain, true)
        }
        return outcome
    }

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
            dao.updateHiddenChunked(domains, true)
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

    suspend fun saveHostTargetsBatch(targets: Collection<HostTarget>) = withContext(Dispatchers.IO) {
        if (targets.isEmpty()) return@withContext
        val now = System.currentTimeMillis()
        targets.chunked(250).forEach { chunk ->
            dao.upsertHostCaches(
                chunk.map { target ->
                    HostCacheEntity(
                        domain = target.domain,
                        resolvedHost = target.host,
                        merchantId = target.merchantId,
                        updatedAt = now
                    )
                }
            )
        }
    }

    // --- Fetch Runs & Snapshots ---
    suspend fun loadRunResults(runId: Long): List<AccountResult> = withContext(Dispatchers.IO) {
        val out = ArrayList<AccountResult>()
        val pageSize = 250
        var offset = 0
        while (true) {
            val page = try {
                dao.getSnapshotsForRunPaged(runId, pageSize, offset)
            } catch (_: Throwable) {
                break
            }
            if (page.isEmpty()) break
            for (entity in page) {
                decodeAccountResult(entity.payloadJson)?.let { out.add(it) }
            }
            if (page.size < pageSize) break
            offset += pageSize
        }
        out
    }

    suspend fun loadLatestSnapshotsMap(): Map<String, AccountResult> = withContext(Dispatchers.IO) {
        val out = LinkedHashMap<String, AccountResult>()
        val pageSize = 250
        var offset = 0
        while (true) {
            val page = try {
                dao.getLatestSnapshotsPaged(pageSize, offset)
            } catch (_: Throwable) {
                break
            }
            if (page.isEmpty()) break
            for (entity in page) {
                decodeAccountResult(entity.payloadJson)?.let { decoded ->
                    out[decoded.domain] = decoded
                }
            }
            if (page.size < pageSize) break
            offset += pageSize
        }
        out
    }

    suspend fun loadLatestRunResults(): List<AccountResult> = withContext(Dispatchers.IO) {
        val latest = dao.getLatestRun() ?: return@withContext emptyList()
        loadRunResults(latest.runId)
    }

    /**
     * Updates a single in-progress FetchRunEntity row in place and inserts only [deltaRows]
     * (newly processed domains since the previous flush) so large multi-thousand-domain scans
     * never multiply snapshot rows in SQLite.
     */
    suspend fun persistRunProgress(
        existingRunId: Long?,
        startedAt: Long,
        targetCount: Int,
        allRows: Collection<AccountResult>,
        deltaRows: List<AccountResult>
    ): Long = withContext(Dispatchers.IO) {
        val resolvedRunId = existingRunId?.takeIf { it > 0L && dao.getFetchRunById(it) != null } ?: 0L

        var okCount = 0
        var errCount = 0
        var withData = 0
        var totalCash = 0.0
        for (row in allRows) {
            when (row) {
                is AccountResult.Ok -> {
                    okCount++
                    if (row.hasData) withData++
                    totalCash += row.cash
                }
                is AccountResult.Err -> errCount++
            }
        }

        val run = FetchRunEntity(
            runId = resolvedRunId,
            startedAt = startedAt,
            completedAt = System.currentTimeMillis(),
            targetCount = targetCount,
            okCount = okCount,
            withDataCount = withData,
            errCount = errCount,
            totalCash = totalCash
        )

        val deltaSnapshots = deltaRows.map { toSnapshotEntity(it) }
        dao.upsertRunProgress(run, deltaSnapshots)
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

            val snapshots = rows.map { toSnapshotEntity(it) }
            dao.saveCompletedRun(run, snapshots)
        }

    private fun toSnapshotEntity(row: AccountResult): AccountSnapshotEntity = when (row) {
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

    suspend fun savePublicBonusResultsBatch(results: Collection<PublicCatalogueResult>) = withContext(Dispatchers.IO) {
        if (results.isEmpty()) return@withContext
        val now = System.currentTimeMillis()
        results.chunked(100).forEach { chunk ->
            dao.upsertPublicBonuses(
                chunk.map { result ->
                    PublicBonusCacheEntity(
                        domain = result.domain,
                        ok = result.ok,
                        bonusesJson = json.encodeToString(result.bonuses),
                        errorMessage = result.message,
                        fetchedAt = now
                    )
                }
            )
        }
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
        prettyJson.encodeToString(payload)
    }

    suspend fun importVaultJson(rawJson: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val payload = json.decodeFromString<VaultBackupPayload>(rawJson)
            if (payload.customSites.isNotEmpty()) {
                addDomains(payload.customSites.map { it.domain }, markAsMine = false)
            }
            if (payload.mineDomains.isNotEmpty()) {
                dao.markAllMineChunked(payload.mineDomains.map { DomainUtil.normalizeDomain(it) })
            }
            for (visited in payload.visitedDomains) {
                dao.updateVisited(DomainUtil.normalizeDomain(visited), true)
            }
            if (payload.hiddenDomains.isNotEmpty()) {
                dao.updateHiddenChunked(payload.hiddenDomains.map { DomainUtil.normalizeDomain(it) }, true)
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
