package com.aistudio.pokiesvault.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sites")
data class SiteEntity(
    @PrimaryKey val domain: String,
    val siteGroup: String,
    val addedAt: String,
    val isCustom: Boolean = false,
    val isMine: Boolean = false,
    val isVisited: Boolean = false,
    val isHidden: Boolean = false,
    val sortOrder: Int = 0
)

@Entity(tableName = "host_cache")
data class HostCacheEntity(
    @PrimaryKey val domain: String,
    val resolvedHost: String,
    val merchantId: String,
    val updatedAt: Long
)

@Entity(tableName = "fetch_runs")
data class FetchRunEntity(
    @PrimaryKey(autoGenerate = true) val runId: Long = 0L,
    val startedAt: Long,
    val completedAt: Long,
    val targetCount: Int,
    val okCount: Int,
    val withDataCount: Int,
    val errCount: Int,
    val totalCash: Double
)

@Entity(
    tableName = "account_snapshots",
    primaryKeys = ["runId", "domain"],
    indices = [Index(value = ["domain"]), Index(value = ["runId"])]
)
data class AccountSnapshotEntity(
    val runId: Long,
    val domain: String,
    val isOk: Boolean,
    val hasData: Boolean,
    val cash: Double,
    val cashWalletName: String,
    val errorCode: String?,
    val errorMessage: String?,
    val latencyMs: Long,
    val retriesUsed: Int,
    val fetchedAt: Long,
    val payloadJson: String
)

@Entity(tableName = "public_bonus_cache")
data class PublicBonusCacheEntity(
    @PrimaryKey val domain: String,
    val ok: Boolean,
    val bonusesJson: String,
    val errorMessage: String?,
    val fetchedAt: Long
)

@Entity(tableName = "domain_health")
data class DomainHealthEntity(
    @PrimaryKey val domain: String,
    val status: String,
    val latencyMs: Long,
    val checkedAt: Long
)
