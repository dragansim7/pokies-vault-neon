package com.aistudio.pokiesvault.util

import com.aistudio.pokiesvault.data.model.DomainHealthRecord
import com.aistudio.pokiesvault.data.model.SiteStatus
import java.util.Locale
import kotlin.math.roundToLong

data class ScanRateSnapshot(
    val sitesPerSec: Double = 0.0,
    val etaSeconds: Long? = null
) {
    val rateLabel: String
        get() = if (sitesPerSec >= 0.05) {
            String.format(Locale.US, "%.1f/s", sitesPerSec)
        } else {
            "—"
        }

    val etaLabel: String
        get() {
            val sec = etaSeconds ?: return "—"
            return formatDurationShort(sec)
        }

    companion object {
        fun formatDurationShort(totalSeconds: Long): String {
            val s = totalSeconds.coerceAtLeast(0L)
            val hrs = s / 3600L
            val mins = (s % 3600L) / 60L
            val remSec = s % 60L
            return when {
                hrs > 0L -> "${hrs}h ${mins}m"
                mins > 0L -> "${mins}m ${remSec}s"
                else -> "${remSec}s"
            }
        }
    }
}

object ScanTelemetryUtil {

    /**
     * Computes live throughput (sites/sec) and estimated time remaining (seconds) for an active run.
     * Requires at least 3 completed items and 1.2s elapsed to avoid noisy initial spikes.
     */
    fun computeThroughputAndEta(
        completedInSegment: Int,
        remainingCount: Int,
        segmentElapsedMs: Long
    ): ScanRateSnapshot {
        if (completedInSegment <= 0 || segmentElapsedMs < 1200L) {
            return ScanRateSnapshot()
        }
        val rate = completedInSegment.toDouble() / (segmentElapsedMs.toDouble() / 1000.0)
        if (!rate.isFinite() || rate <= 0.01) {
            return ScanRateSnapshot()
        }
        val eta = if (remainingCount <= 0) {
            0L
        } else if (completedInSegment < 3) {
            null
        } else {
            (remainingCount.toDouble() / rate).roundToLong().coerceIn(1L, 86_400L)
        }
        return ScanRateSnapshot(sitesPerSec = rate, etaSeconds = eta)
    }

    /**
     * Filters out domains marked [SiteStatus.DEAD] in Domain Intel when [skipKnownDead] is enabled.
     * Always preserves single-domain explicit checks.
     */
    fun filterKnownDeadDomains(
        domains: List<String>,
        healthMap: Map<String, DomainHealthRecord>,
        skipKnownDead: Boolean
    ): Pair<List<String>, Int> {
        if (!skipKnownDead || domains.size <= 1 || healthMap.isEmpty()) {
            return domains to 0
        }
        val kept = ArrayList<String>(domains.size)
        var skipped = 0
        for (domain in domains) {
            val key = DomainUtil.normalizeDomain(domain)
            if (healthMap[key]?.status == SiteStatus.DEAD) {
                skipped++
            } else {
                kept.add(domain)
            }
        }
        return kept to skipped
    }
}
