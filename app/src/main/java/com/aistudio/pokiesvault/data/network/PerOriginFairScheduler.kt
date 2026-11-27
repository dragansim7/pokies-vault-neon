package com.aistudio.pokiesvault.data.network

import com.aistudio.pokiesvault.data.model.WorkerLaneProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/**
 * Non-blocking per-origin fair scheduler for multi-thousand-domain scans.
 *
 * Unlike a global mutex that stalls every worker while a single shared IP cools down,
 * this scheduler holds its lock only for microsecond bookkeeping and immediately dispatches
 * ready domains from independent IPs or Cloudflare while enforcing strict per-IP concurrency
 * (`perIpMaxInFlight`) and minimum launch spacing (`perIpMinGapMs`) on shared direct-IP clusters
 * (such as `43.106.19.168`).
 *
 * Largest shared clusters are checked first whenever their per-IP timer is ready so they never
 * accumulate into an avoidable end-of-run tail.
 */
class PerOriginFairScheduler(
    domains: List<String>,
    originByDomain: Map<String, String>,
    private val profile: WorkerLaneProfile,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val jitterProvider: (Long) -> Long = { bound ->
        if (bound > 0L) Random.nextLong(0L, bound) else 0L
    }
) {
    data class ScheduledDomain(
        val domain: String,
        val origin: String
    )

    private class OriginBucket(
        val origin: String,
        val isSharedDirectIp: Boolean,
        val queue: ArrayDeque<String> = ArrayDeque(),
        var inFlight: Int = 0,
        var nextReadyAtMs: Long = 0L
    )

    private val mutex = Mutex()
    private val bucketsByOrigin = LinkedHashMap<String, OriginBucket>()
    private val activeBuckets = ArrayList<OriginBucket>()
    private var rrCursor: Int = 0
    private var nextGlobalLaunchAtMs: Long = 0L

    init {
        for (domain in domains) {
            val origin = originByDomain[domain] ?: "host:$domain"
            val bucket = bucketsByOrigin.getOrPut(origin) {
                OriginBucket(
                    origin = origin,
                    isSharedDirectIp = origin.startsWith("ip:")
                )
            }
            bucket.queue.addLast(domain)
        }
        // Sort largest clusters first so dominant shared IPs get every eligible slot early.
        val sorted = bucketsByOrigin.values
            .filter { it.queue.isNotEmpty() }
            .sortedByDescending { it.queue.size }
        activeBuckets.addAll(sorted)
    }

    /**
     * Dynamically appends domains (for example, expired saved sessions falling back to cold login).
     */
    suspend fun enqueueAll(domains: List<String>, originByDomain: Map<String, String>) {
        if (domains.isEmpty()) return
        mutex.withLock {
            for (domain in domains) {
                val origin = originByDomain[domain] ?: "host:$domain"
                val bucket = bucketsByOrigin.getOrPut(origin) {
                    OriginBucket(
                        origin = origin,
                        isSharedDirectIp = origin.startsWith("ip:")
                    )
                }
                val wasEmpty = bucket.queue.isEmpty()
                bucket.queue.addLast(domain)
                if (wasEmpty && bucket !in activeBuckets) {
                    activeBuckets.add(bucket)
                }
            }
        }
    }

    /**
     * Claims the next ready domain without ever holding the lock across a delay.
     * Returns null when all queued domains have been claimed or [shouldStop] becomes true.
     */
    suspend fun acquireNext(
        shouldStop: () -> Boolean,
        awaitCooldown: suspend () -> Unit = {}
    ): ScheduledDomain? {
        while (!shouldStop()) {
            awaitCooldown()
            if (shouldStop()) return null

            var claimed: ScheduledDomain? = null
            var sleepMs = 0L
            var exhausted = false

            mutex.withLock {
                if (shouldStop()) {
                    exhausted = true
                    return@withLock
                }
                if (activeBuckets.isEmpty()) {
                    exhausted = true
                    return@withLock
                }

                val now = clockMs()
                if (now < nextGlobalLaunchAtMs) {
                    sleepMs = (nextGlobalLaunchAtMs - now).coerceIn(10L, 150L)
                    return@withLock
                }

                var selectedIdx = -1
                var earliestWaitMs = Long.MAX_VALUE

                // Pass 1: Prioritize the largest shared cluster(s) (>8 remaining) whenever ready
                // so a 800-site server IP never loses ticks to tiny single-site buckets.
                var bestHeavyIdx = -1
                var bestHeavySize = 8
                for (i in activeBuckets.indices) {
                    val b = activeBuckets[i]
                    if (b.isSharedDirectIp && b.queue.size > bestHeavySize) {
                        val ready = b.inFlight < profile.perIpMaxInFlight && now >= b.nextReadyAtMs
                        if (ready) {
                            bestHeavySize = b.queue.size
                            bestHeavyIdx = i
                        }
                    }
                }

                if (bestHeavyIdx >= 0) {
                    selectedIdx = bestHeavyIdx
                } else {
                    // Pass 2: Fair round-robin scan across all remaining active buckets.
                    val count = activeBuckets.size
                    val start = if (count > 0) rrCursor % count else 0
                    for (offset in 0 until count) {
                        val idx = (start + offset) % count
                        val b = activeBuckets[idx]
                        if (!b.isSharedDirectIp) {
                            selectedIdx = idx
                            rrCursor = (idx + 1) % count
                            break
                        }
                        if (b.inFlight < profile.perIpMaxInFlight) {
                            val wait = b.nextReadyAtMs - now
                            if (wait <= 0L) {
                                selectedIdx = idx
                                rrCursor = (idx + 1) % count
                                break
                            } else if (wait < earliestWaitMs) {
                                earliestWaitMs = wait
                            }
                        } else {
                            // Waiting for an in-flight request on this IP to finish
                            if (earliestWaitMs > 40L) earliestWaitMs = 40L
                        }
                    }
                }

                if (selectedIdx >= 0) {
                    val bucket = activeBuckets[selectedIdx]
                    val domain = bucket.queue.removeFirst()
                    bucket.inFlight++
                    if (bucket.isSharedDirectIp) {
                        bucket.nextReadyAtMs = now + profile.perIpMinGapMs
                    }
                    val jitter = jitterProvider(profile.globalJitterMs)
                    nextGlobalLaunchAtMs = now + profile.globalStaggerMs + jitter

                    if (bucket.queue.isEmpty()) {
                        activeBuckets.removeAt(selectedIdx)
                        if (activeBuckets.isNotEmpty()) {
                            rrCursor = rrCursor.coerceIn(0, activeBuckets.size - 1)
                        } else {
                            rrCursor = 0
                        }
                    }
                    claimed = ScheduledDomain(domain = domain, origin = bucket.origin)
                } else {
                    sleepMs = if (earliestWaitMs == Long.MAX_VALUE) {
                        40L
                    } else {
                        earliestWaitMs.coerceIn(15L, 150L)
                    }
                }
            }

            if (claimed != null) return claimed
            if (exhausted) return null
            if (sleepMs > 0L) {
                delay(sleepMs)
            }
        }
        return null
    }

    /**
     * Releases an in-flight slot for [origin]. Must be called in a `finally` block for every
     * domain returned by [acquireNext].
     */
    suspend fun release(origin: String) {
        mutex.withLock {
            val bucket = bucketsByOrigin[origin] ?: return@withLock
            bucket.inFlight = (bucket.inFlight - 1).coerceAtLeast(0)
        }
    }

    /**
     * Pushes back the next eligible launch time for a specific origin after a rate-limit/WAF hit.
     */
    suspend fun penalizeOrigin(origin: String, extraDelayMs: Long) {
        if (extraDelayMs <= 0L) return
        mutex.withLock {
            val bucket = bucketsByOrigin[origin] ?: return@withLock
            val target = clockMs() + extraDelayMs
            if (target > bucket.nextReadyAtMs) {
                bucket.nextReadyAtMs = target
            }
        }
    }
}
