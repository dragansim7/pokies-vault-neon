package com.aistudio.pokiesvault

import com.aistudio.pokiesvault.data.model.DomainHealthRecord
import com.aistudio.pokiesvault.data.model.SiteStatus
import com.aistudio.pokiesvault.util.ScanRateSnapshot
import com.aistudio.pokiesvault.util.ScanTelemetryUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanTelemetryUtilTest {

    @Test
    fun throughputAndEta_respectsWarmupGuardAndCalculatesAccurateEta() {
        // Under 1.2s elapsed should return empty snapshot to avoid spikes
        val tooEarly = ScanTelemetryUtil.computeThroughputAndEta(
            completedInSegment = 5,
            remainingCount = 100,
            segmentElapsedMs = 900L
        )
        assertEquals(0.0, tooEarly.sitesPerSec, 0.001)
        assertNull(tooEarly.etaSeconds)
        assertEquals("—", tooEarly.rateLabel)

        // Under 3 completions shows rate but suppresses noisy ETA
        val warmingUp = ScanTelemetryUtil.computeThroughputAndEta(
            completedInSegment = 2,
            remainingCount = 50,
            segmentElapsedMs = 2_000L
        )
        assertEquals(1.0, warmingUp.sitesPerSec, 0.01)
        assertNull(warmingUp.etaSeconds)
        assertEquals("1.0/s", warmingUp.rateLabel)

        // Steady state: 25 completed in 5s = 5.0/s; 250 remaining -> 50s ETA
        val steady = ScanTelemetryUtil.computeThroughputAndEta(
            completedInSegment = 25,
            remainingCount = 250,
            segmentElapsedMs = 5_000L
        )
        assertEquals(5.0, steady.sitesPerSec, 0.01)
        assertEquals(50L, steady.etaSeconds)
        assertEquals("5.0/s", steady.rateLabel)
        assertEquals("50s", steady.etaLabel)

        // Zero remaining -> 0s ETA
        val done = ScanTelemetryUtil.computeThroughputAndEta(
            completedInSegment = 40,
            remainingCount = 0,
            segmentElapsedMs = 10_000L
        )
        assertEquals(0L, done.etaSeconds)
        assertEquals("0s", done.etaLabel)
    }

    @Test
    fun formatDurationShort_formatsSecondsMinutesAndHoursCleanly() {
        assertEquals("0s", ScanRateSnapshot.formatDurationShort(0L))
        assertEquals("45s", ScanRateSnapshot.formatDurationShort(45L))
        assertEquals("1m 42s", ScanRateSnapshot.formatDurationShort(102L))
        assertEquals("1h 15m", ScanRateSnapshot.formatDurationShort(4_500L))
    }

    @Test
    fun filterKnownDeadDomains_skipsOnlyDeadOnMultiDomainScans() {
        val health = mapOf(
            "live.com" to DomainHealthRecord("live.com", SiteStatus.LIVE, 120L, 1L),
            "slow.com" to DomainHealthRecord("slow.com", SiteStatus.SLOW, 900L, 1L),
            "dead1.com" to DomainHealthRecord("dead1.com", SiteStatus.DEAD, 0L, 1L),
            "dead2.com" to DomainHealthRecord("dead2.com", SiteStatus.DEAD, 0L, 1L)
        )
        val input = listOf("live.com", "dead1.com", "slow.com", "unknown.com", "dead2.com")

        // When toggle is OFF, nothing is skipped
        val (keptOff, skippedOff) = ScanTelemetryUtil.filterKnownDeadDomains(input, health, skipKnownDead = false)
        assertEquals(input, keptOff)
        assertEquals(0, skippedOff)

        // When toggle is ON, DEAD domains are filtered while LIVE/SLOW/UNKNOWN remain
        val (keptOn, skippedOn) = ScanTelemetryUtil.filterKnownDeadDomains(input, health, skipKnownDead = true)
        assertEquals(listOf("live.com", "slow.com", "unknown.com"), keptOn)
        assertEquals(2, skippedOn)

        // Single-domain explicit checks are never skipped even if previously marked DEAD
        val (keptSingle, skippedSingle) = ScanTelemetryUtil.filterKnownDeadDomains(
            listOf("dead1.com"),
            health,
            skipKnownDead = true
        )
        assertEquals(listOf("dead1.com"), keptSingle)
        assertEquals(0, skippedSingle)
    }
}
