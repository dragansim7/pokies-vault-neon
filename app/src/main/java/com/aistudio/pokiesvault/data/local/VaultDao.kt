package com.aistudio.pokiesvault.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    // --- Sites ---
    @Query("SELECT * FROM sites WHERE isHidden = 0 ORDER BY isCustom DESC, sortOrder ASC, domain ASC")
    fun observeVisibleSites(): Flow<List<SiteEntity>>

    @Query("SELECT * FROM sites ORDER BY sortOrder ASC, domain ASC")
    suspend fun getAllSitesSnapshot(): List<SiteEntity>

    @Query("SELECT COUNT(*) FROM sites")
    suspend fun countSites(): Int

    @Query("SELECT COUNT(*) FROM sites WHERE isHidden = 1")
    fun observeHiddenCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSitesIgnore(sites: List<SiteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSite(site: SiteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSites(sites: List<SiteEntity>)

    @Query("UPDATE sites SET isMine = :isMine WHERE domain = :domain")
    suspend fun updateMine(domain: String, isMine: Boolean)

    @Query("UPDATE sites SET isMine = 1 WHERE domain IN (:domains)")
    suspend fun markAllMine(domains: List<String>)

    @Transaction
    suspend fun markAllMineChunked(domains: List<String>) {
        if (domains.isEmpty()) return
        domains.distinct().chunked(400).forEach { chunk ->
            markAllMine(chunk)
        }
    }

    @Query("UPDATE sites SET isVisited = :isVisited WHERE domain = :domain")
    suspend fun updateVisited(domain: String, isVisited: Boolean)

    @Query("UPDATE sites SET isHidden = :isHidden WHERE domain IN (:domains)")
    suspend fun updateHidden(domains: List<String>, isHidden: Boolean)

    @Transaction
    suspend fun updateHiddenChunked(domains: List<String>, isHidden: Boolean) {
        if (domains.isEmpty()) return
        domains.distinct().chunked(400).forEach { chunk ->
            updateHidden(chunk, isHidden)
        }
    }

    @Query("UPDATE sites SET isHidden = 0")
    suspend fun restoreAllHidden()

    @Query("UPDATE sites SET isMine = 0, isVisited = 0, isHidden = 0 WHERE isCustom = 0")
    suspend fun resetBuiltInFlags()

    @Query("DELETE FROM sites WHERE isCustom = 1")
    suspend fun deleteCustomSites()

    // --- Host & Merchant Cache ---
    @Query("SELECT * FROM host_cache WHERE domain = :domain LIMIT 1")
    suspend fun getHostCache(domain: String): HostCacheEntity?

    @Query("SELECT * FROM host_cache")
    suspend fun getAllHostCaches(): List<HostCacheEntity>

    @Query("SELECT * FROM host_cache")
    fun observeHostCaches(): Flow<List<HostCacheEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHostCache(entry: HostCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHostCaches(entries: List<HostCacheEntity>)

    @Query("DELETE FROM host_cache WHERE domain = :domain")
    suspend fun deleteHostCache(domain: String)

    @Query("DELETE FROM host_cache")
    suspend fun clearHostCache()

    // --- Fetch Runs & Snapshots ---
    @Query("SELECT * FROM fetch_runs ORDER BY completedAt DESC LIMIT 20")
    fun observeFetchRuns(): Flow<List<FetchRunEntity>>

    @Query("SELECT * FROM fetch_runs ORDER BY completedAt DESC LIMIT 1")
    suspend fun getLatestRun(): FetchRunEntity?

    @Query("SELECT * FROM fetch_runs WHERE runId = :runId LIMIT 1")
    suspend fun getFetchRunById(runId: Long): FetchRunEntity?

    @Query("SELECT * FROM account_snapshots WHERE runId = :runId ORDER BY isOk DESC, hasData DESC, cash DESC, domain ASC")
    suspend fun getSnapshotsForRun(runId: Long): List<AccountSnapshotEntity>

    @Query(
        """
        SELECT * FROM account_snapshots
        WHERE runId = :runId
        ORDER BY isOk DESC, hasData DESC, cash DESC, domain ASC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getSnapshotsForRunPaged(runId: Long, limit: Int, offset: Int): List<AccountSnapshotEntity>

    @Query(
        """
        SELECT a.* FROM account_snapshots a
        INNER JOIN (
            SELECT a1.domain, MAX(a1.runId) AS maxRunId
            FROM account_snapshots a1
            INNER JOIN (
                SELECT domain, MAX(fetchedAt) AS maxTime
                FROM account_snapshots
                GROUP BY domain
            ) t ON a1.domain = t.domain AND a1.fetchedAt = t.maxTime
            GROUP BY a1.domain
        ) latest ON a.domain = latest.domain AND a.runId = latest.maxRunId
        ORDER BY a.isOk DESC, a.hasData DESC, a.cash DESC, a.domain ASC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getLatestSnapshotsPaged(limit: Int, offset: Int): List<AccountSnapshotEntity>

    @Query(
        """
        SELECT a.* FROM account_snapshots a
        INNER JOIN (
            SELECT a1.domain, MAX(a1.runId) AS maxRunId
            FROM account_snapshots a1
            INNER JOIN (
                SELECT domain, MAX(fetchedAt) AS maxTime
                FROM account_snapshots
                WHERE isOk = 1
                GROUP BY domain
            ) t ON a1.domain = t.domain AND a1.fetchedAt = t.maxTime
            WHERE a1.isOk = 1
            GROUP BY a1.domain
        ) latest ON a.domain = latest.domain AND a.runId = latest.maxRunId
        WHERE a.isOk = 1
        """
    )
    fun observeLatestOkSnapshots(): Flow<List<AccountSnapshotEntity>>

    @Query(
        """
        SELECT a.* FROM account_snapshots a
        INNER JOIN (
            SELECT a1.domain, MAX(a1.runId) AS maxRunId
            FROM account_snapshots a1
            INNER JOIN (
                SELECT domain, MAX(fetchedAt) AS maxTime
                FROM account_snapshots
                GROUP BY domain
            ) t ON a1.domain = t.domain AND a1.fetchedAt = t.maxTime
            GROUP BY a1.domain
        ) latest ON a.domain = latest.domain AND a.runId = latest.maxRunId
        """
    )
    fun observeLatestSnapshots(): Flow<List<AccountSnapshotEntity>>

    @Query("SELECT COUNT(*) FROM account_snapshots")
    suspend fun countSnapshots(): Int

    @Query("SELECT COUNT(DISTINCT domain) FROM account_snapshots")
    suspend fun countDistinctSnapshotDomains(): Int

    @Query(
        """
        DELETE FROM account_snapshots
        WHERE rowid NOT IN (
            SELECT a.rowid FROM account_snapshots a
            INNER JOIN (
                SELECT a1.domain, MAX(a1.runId) AS maxRunId
                FROM account_snapshots a1
                INNER JOIN (
                    SELECT domain, MAX(fetchedAt) AS maxTime
                    FROM account_snapshots
                    GROUP BY domain
                ) t ON a1.domain = t.domain AND a1.fetchedAt = t.maxTime
                GROUP BY a1.domain
            ) keep_any ON a.domain = keep_any.domain AND a.runId = keep_any.maxRunId
            UNION
            SELECT a.rowid FROM account_snapshots a
            INNER JOIN (
                SELECT a2.domain, MAX(a2.runId) AS maxRunId
                FROM account_snapshots a2
                INNER JOIN (
                    SELECT domain, MAX(fetchedAt) AS maxTime
                    FROM account_snapshots
                    WHERE isOk = 1
                    GROUP BY domain
                ) t2 ON a2.domain = t2.domain AND a2.fetchedAt = t2.maxTime
                WHERE a2.isOk = 1
                GROUP BY a2.domain
            ) keep_ok ON a.domain = keep_ok.domain AND a.runId = keep_ok.maxRunId
        )
        """
    )
    suspend fun deleteDuplicateSnapshots(): Int

    @Query(
        """
        DELETE FROM fetch_runs
        WHERE runId NOT IN (SELECT DISTINCT runId FROM account_snapshots)
          AND (:preserveRunId IS NULL OR runId != :preserveRunId)
        """
    )
    suspend fun deleteOrphanedFetchRuns(preserveRunId: Long?): Int

    @Transaction
    suspend fun cleanupDuplicateSnapshots(preserveRunId: Long?): Int {
        val deleted = deleteDuplicateSnapshots()
        deleteOrphanedFetchRuns(preserveRunId)
        return deleted
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFetchRun(run: FetchRunEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshots(snapshots: List<AccountSnapshotEntity>)

    @Transaction
    suspend fun upsertRunProgress(
        run: FetchRunEntity,
        deltaSnapshots: List<AccountSnapshotEntity>
    ): Long {
        val runId = insertFetchRun(run)
        if (deltaSnapshots.isNotEmpty()) {
            deltaSnapshots.chunked(250).forEach { chunk ->
                insertSnapshots(chunk.map { it.copy(runId = runId) })
            }
            val okDomains = deltaSnapshots.filter { it.isOk }.map { it.domain }
            if (okDomains.isNotEmpty()) {
                markAllMineChunked(okDomains)
            }
        }
        return runId
    }

    @Transaction
    suspend fun saveCompletedRun(
        run: FetchRunEntity,
        snapshots: List<AccountSnapshotEntity>
    ): Long {
        val runId = insertFetchRun(run)
        if (snapshots.isNotEmpty()) {
            snapshots.chunked(250).forEach { chunk ->
                insertSnapshots(chunk.map { it.copy(runId = runId) })
            }
            val okDomains = snapshots.filter { it.isOk }.map { it.domain }
            if (okDomains.isNotEmpty()) {
                markAllMineChunked(okDomains)
            }
        }
        return runId
    }

    @Query("DELETE FROM account_snapshots")
    suspend fun clearSnapshots()

    @Query("DELETE FROM fetch_runs")
    suspend fun clearFetchRuns()

    // --- Public Bonus Cache ---
    @Query("SELECT * FROM public_bonus_cache")
    fun observePublicBonuses(): Flow<List<PublicBonusCacheEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPublicBonus(entry: PublicBonusCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPublicBonuses(entries: List<PublicBonusCacheEntity>)

    @Query("DELETE FROM public_bonus_cache")
    suspend fun clearPublicBonuses()

    // --- Domain Health Telemetry ---
    @Query("SELECT * FROM domain_health")
    fun observeDomainHealth(): Flow<List<DomainHealthEntity>>

    @Query("SELECT * FROM domain_health WHERE domain = :domain LIMIT 1")
    suspend fun getDomainHealth(domain: String): DomainHealthEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDomainHealth(entry: DomainHealthEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDomainHealthBatch(entries: List<DomainHealthEntity>)

    @Query("DELETE FROM domain_health")
    suspend fun clearDomainHealth()
}
