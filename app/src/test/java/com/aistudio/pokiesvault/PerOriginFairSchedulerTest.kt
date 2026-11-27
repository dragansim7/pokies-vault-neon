package com.aistudio.pokiesvault

import com.aistudio.pokiesvault.data.model.FetchSpeedPreset
import com.aistudio.pokiesvault.data.model.WorkerLaneProfile
import com.aistudio.pokiesvault.data.network.PerOriginFairScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

class PerOriginFairSchedulerTest {

    @Test
    fun enforcesPerIpInFlightAndGapWithoutStallingOtherOrigins() = runBlocking {
        val sharedIp = "ip:43.106.19.168"
        val domains = buildList {
            repeat(5) { add("mega$it.example.com") }
            repeat(8) { add("cf$it.example.com") }
        }
        val origins = domains.associateWith { d ->
            if (d.startsWith("mega")) sharedIp else "cf:$d"
        }
        val profile = WorkerLaneProfile(
            minWorkers = 6,
            initialWorkers = 6,
            maxWorkers = 8,
            globalStaggerMs = 10L,
            globalJitterMs = 0L,
            perIpMaxInFlight = 1,
            perIpMinGapMs = 120L
        )
        val scheduler = PerOriginFairScheduler(
            domains = domains,
            originByDomain = origins,
            profile = profile,
            jitterProvider = { 0L }
        )

        val sharedInFlight = AtomicInteger(0)
        val maxSharedInFlight = AtomicInteger(0)
        val totalInFlight = AtomicInteger(0)
        val maxTotalInFlight = AtomicInteger(0)
        val sharedLaunchTimes = Collections.synchronizedList(ArrayList<Long>())
        val completed = Collections.synchronizedSet(HashSet<String>())

        val workers = List(8) {
            launch {
                while (true) {
                    val item = scheduler.acquireNext(shouldStop = { false }) ?: break
                    try {
                        val curTotal = totalInFlight.incrementAndGet()
                        maxTotalInFlight.accumulateAndGet(curTotal, ::maxOf)
                        if (item.origin == sharedIp) {
                            sharedLaunchTimes.add(System.currentTimeMillis())
                            val curShared = sharedInFlight.incrementAndGet()
                            maxSharedInFlight.accumulateAndGet(curShared, ::maxOf)
                        }
                        delay(45L)
                        completed.add(item.domain)
                    } finally {
                        if (item.origin == sharedIp) {
                            sharedInFlight.decrementAndGet()
                        }
                        totalInFlight.decrementAndGet()
                        scheduler.release(item.origin)
                    }
                }
            }
        }
        workers.forEach { it.join() }

        assertEquals(domains.size, completed.size)
        assertEquals("Shared direct IP must never exceed perIpMaxInFlight", 1, maxSharedInFlight.get())
        assertTrue("Other origins must run concurrently while shared IP waits", maxTotalInFlight.get() >= 3)

        val sortedTimes = sharedLaunchTimes.sorted()
        assertEquals(5, sortedTimes.size)
        for (i in 1 until sortedTimes.size) {
            val delta = sortedTimes[i] - sortedTimes[i - 1]
            assertTrue("Expected >=100ms per-IP spacing, got ${delta}ms", delta >= 100L)
        }
    }

    @Test
    fun presetLaneProfilesAreValidAndSavedLaneIsFasterThanColdLogin() {
        for (preset in FetchSpeedPreset.entries) {
            for (p in listOf(preset.coldLoginProfile, preset.savedSessionProfile, preset.bonusScanProfile)) {
                assertTrue(p.minWorkers in 1..p.initialWorkers)
                assertTrue(p.initialWorkers <= p.maxWorkers)
                assertTrue(p.perIpMaxInFlight >= 1)
                assertTrue(p.perIpMinGapMs >= 200L)
            }
            assertTrue(preset.savedSessionProfile.maxWorkers > preset.coldLoginProfile.maxWorkers)
            assertTrue(preset.savedSessionProfile.perIpMinGapMs < preset.coldLoginProfile.perIpMinGapMs)
        }
    }
}
