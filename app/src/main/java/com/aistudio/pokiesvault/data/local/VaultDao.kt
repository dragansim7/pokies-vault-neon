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

    @Query("UPDATE sites SET isVisited = :isVisited WHERE domain = :domain")
    suspend fun updateVisited(domain: String, isVisited: Boolean)

    @Query("UPDATE sites SET isHidden = :isHidden WHERE domain IN (:domains)")
    suspend fun updateHidden(domains: List<String>, isHidden: Boolean)

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHostCache(entry: HostCacheEntity)

    @Query("DELETE FROM host_cache WHERE domain = :domain")
    suspend fun deleteHostCache(domain: String)

    @Query("DELETE FROM host_cache")
    suspend fun clearHostCache()

    // --- Fetch Runs & Snapshots ---
    @Query("SELECT * FROM fetch_runs ORDER BY completedAt DESC LIMIT 20")
    fun observeFetchRuns(): Flow<List<FetchRunEntity>>

    @Query("SELECT * FROM fetch_runs ORDER BY completedAt DESC LIMIT 1")
    suspend fun getLatestRun(): FetchRunEntity?

    @Query("SELECT * FROM account_snapshots WHERE runId = :runId ORDER BY isOk DESC, hasData DESC, cash DESC, domain ASC")
    suspend fun getSnapshotsForRun(runId: Long): List<AccountSnapshotEntity>

    @Query(
        """
        SELECT a.* FROM account_snapshots a
        INNER JOIN (
            SELECT domain, MAX(fetchedAt) AS maxTime
            FROM account_snapshots
            WHERE isOk = 1
            GROUP BY domain
        ) latest ON a.domain = latest.domain AND a.fetchedAt = latest.maxTime
        WHERE a.isOk = 1
        """
    )
    fun observeLatestOkSnapshots(): Flow<List<AccountSnapshotEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFetchRun(run: FetchRunEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshots(snapshots: List<AccountSnapshotEntity>)

    @Transaction
    suspend fun saveCompletedRun(
        run: FetchRunEntity,
        snapshots: List<AccountSnapshotEntity>
    ): Long {
        val runId = insertFetchRun(run)
        val linked = snapshots.map { it.copy(runId = runId) }
        insertSnapshots(linked)
        val okDomains = snapshots.filter { it.isOk }.map { it.domain }
        if (okDomains.isNotEmpty()) {
            markAllMine(okDomains)
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
