package com.aistudio.pokiesvault.data.network

import com.aistudio.pokiesvault.data.local.HostCacheEntity
import com.aistudio.pokiesvault.data.model.AccountResult
import com.aistudio.pokiesvault.data.model.DomainHealthRecord
import com.aistudio.pokiesvault.data.model.EgressIpInfo
import com.aistudio.pokiesvault.data.model.FetchSpeedPreset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

data class FetchBatchReport(
    val wasCircuitBroken: Boolean = false,
    val pauseDetail: String = "",
    val trippedDomains: List<String> = emptyList()
)

/**
 * Orchestrates bounded concurrent account fetches, public bonus scans, and domain health probes
 * with adaptive concurrency, serialized launch pacing, rolling WAF circuit breaker, host caching,
 * and partial-failure isolation.
 */
class ConcurrentFetchEngine(
    private val apiService: CasinoApiService
) {
    private val adaptiveController = AdaptiveConcurrencyController(minLimit = 1, initialLimit = 2, maxLimit = 3)
    private val bonusSemaphore = Semaphore(permits = 4)
    private val statusProbeSemaphore = Semaphore(permits = 6)

    val currentWorkerLimit: StateFlow<Int> = adaptiveController.currentLimit
    val activeWorkers: StateFlow<Int> = adaptiveController.activeWorkers
    val peakWorkers: StateFlow<Int> = adaptiveController.peakWorkers

    suspend fun lookupEgressInfo(): Result<EgressIpInfo> = apiService.lookupEgressInfo()

    /**
     * Fetches multiple accounts concurrently using a bounded worker pool configured by [preset].
     * Enforces a global launch gap between domain starts and automatically trips a circuit breaker
     * if a burst of firewall (403/WAF) or rate-limit (429) responses is detected.
     */
    suspend fun fetchAccountsBatch(
        domains: List<String>,
        credentials: List<Pair<String, String>>,
        hostCache: Map<String, HostTarget>,
        preset: FetchSpeedPreset = FetchSpeedPreset.SAFE,
        onHostDiscovered: suspend (HostTarget) -> Unit,
        onResult: suspend (AccountResult) -> Unit
    ): FetchBatchReport = coroutineScope {
        adaptiveController.reset(
            min = preset.minWorkers,
            initial = preset.initialWorkers,
            max = preset.maxWorkers
        )
        if (domains.isEmpty()) return@coroutineScope FetchBatchReport()

        val resultChannel = Channel<AccountResult>(capacity = Channel.UNLIMITED)
        val collectorJob = launch(Dispatchers.Default) {
            for (res in resultChannel) {
                onResult(res)
            }
        }

        val nextIndex = AtomicInteger(0)
        val breakerTripped = AtomicBoolean(false)

        // Global launch pacer: serializes domain start times across all workers
        val launchMutex = Mutex()
        var lastLaunchAtMs = 0L

        // Rolling WAF / rate-limit detector
        val breakerMutex = Mutex()
        var consecutiveWafHits = 0
        val recentWindow = ArrayDeque<Pair<String, Boolean>>(8)
        var pauseDetail = ""
        val trippedDomains = LinkedHashSet<String>()

        suspend fun takeNextPacedDomain(): String? {
            return launchMutex.withLock {
                if (breakerTripped.get()) return@withLock null
                val peekIdx = nextIndex.get()
                if (peekIdx >= domains.size) return@withLock null

                val now = System.currentTimeMillis()
                if (lastLaunchAtMs > 0L) {
                    val jitter = if (preset.jitterMs > 0L) Random.nextLong(0L, preset.jitterMs) else 0L
                    val waitMs = (lastLaunchAtMs + preset.startGapMs + jitter) - now
                    if (waitMs > 0L) {
                        delay(waitMs)
                    }
                }
                if (breakerTripped.get()) return@withLock null
                val claimedIdx = nextIndex.getAndIncrement()
                if (claimedIdx >= domains.size) return@withLock null
                lastLaunchAtMs = System.currentTimeMillis()
                domains[claimedIdx]
            }
        }

        val workerCount = preset.maxWorkers.coerceIn(1, domains.size)

        try {
            val workers = List(workerCount) {
                launch(Dispatchers.IO) {
                    while (!breakerTripped.get()) {
                        var finishedAll = false
                        adaptiveController.withPermit {
                            val domain = takeNextPacedDomain()
                            if (domain == null) {
                                finishedAll = true
                                return@withPermit
                            }

                            var currentTarget = hostCache[domain]
                            var finalResult: AccountResult = AccountResult.Err(
                                domain = domain,
                                code = "other",
                                message = "No credentials configured",
                                fetchedAt = System.currentTimeMillis()
                            )
                            var sawCongestion = false
                            var sawWafSignal = false
                            var totalAttempts = 0
                            var totalRetries = 0
                            var totalLatencyMs = 0L

                            for ((phone, password) in credentials) {
                                val outcome = apiService.fetchCasinoAccount(
                                    rawDomain = domain,
                                    phoneRaw = phone,
                                    passwordRaw = password,
                                    cachedTarget = currentTarget
                                )
                                outcome.discoveredTarget?.let { discovered ->
                                    currentTarget = discovered
                                    onHostDiscovered(discovered)
                                }
                                if (outcome.congested) sawCongestion = true
                                if (outcome.wafSignal) sawWafSignal = true

                                totalRetries += outcome.result.retriesUsed
                                totalAttempts += (1 + outcome.result.retriesUsed)
                                totalLatencyMs += outcome.result.latencyMs

                                when (val res = outcome.result) {
                                    is AccountResult.Ok -> {
                                        finalResult = res.copy(
                                            latencyMs = totalLatencyMs,
                                            retriesUsed = totalRetries,
                                            attemptsUsed = totalAttempts
                                        )
                                        break
                                    }
                                    is AccountResult.Err -> {
                                        finalResult = res.copy(
                                            latencyMs = totalLatencyMs,
                                            retriesUsed = totalRetries,
                                            attemptsUsed = totalAttempts
                                        )
                                        // Rotate to fallback credential only on invalid_login
                                        if (res.code != "invalid_login") break
                                    }
                                }
                            }

                            if (sawCongestion || sawWafSignal) {
                                adaptiveController.recordCongestion()
                            } else {
                                adaptiveController.recordHealthy()
                            }

                            breakerMutex.withLock {
                                if (sawWafSignal) {
                                    consecutiveWafHits++
                                } else {
                                    consecutiveWafHits = 0
                                }
                                if (recentWindow.size >= 8) {
                                    recentWindow.removeFirst()
                                }
                                recentWindow.addLast(domain to sawWafSignal)

                                val wafInWindow = recentWindow.count { it.second }
                                val shouldTrip = consecutiveWafHits >= 4 ||
                                    (recentWindow.size >= 6 && wafInWindow >= 5)

                                if (shouldTrip && !breakerTripped.get()) {
                                    breakerTripped.set(true)
                                    pauseDetail = if (consecutiveWafHits >= 4) {
                                        "$consecutiveWafHits consecutive firewall or rate-limit blocks"
                                    } else {
                                        "$wafInWindow of the last ${recentWindow.size} sites returned firewall / rate-limit blocks"
                                    }
                                    recentWindow.filter { it.second }.forEach { trippedDomains.add(it.first) }
                                } else if (breakerTripped.get() && sawWafSignal) {
                                    trippedDomains.add(domain)
                                }
                            }

                            resultChannel.send(finalResult)
                        }
                        if (finishedAll) break
                    }
                }
            }
            workers.forEach { it.join() }
        } catch (e: CancellationException) {
            throw e
        } finally {
            resultChannel.close()
            collectorJob.join()
        }

        FetchBatchReport(
            wasCircuitBroken = breakerTripped.get(),
            pauseDetail = pauseDetail,
            trippedDomains = trippedDomains.toList()
        )
    }

    /**
     * Scans public bonus catalogues concurrently (6 workers) without sending user credentials.
     */
    suspend fun scanPublicBonusesBatch(
        domains: List<String>,
        hostCache: Map<String, HostTarget>,
        onHostDiscovered: suspend (HostTarget) -> Unit,
        onResult: suspend (PublicCatalogueResult) -> Unit
    ) = coroutineScope {
        val jobs = domains.map { domain ->
            async(Dispatchers.IO) {
                bonusSemaphore.withPermit {
                    val outcome = apiService.fetchPublicBonuses(domain, hostCache[domain])
                    val host = outcome.host
                    val merchantId = outcome.merchantId
                    if (!host.isNullOrBlank() && !merchantId.isNullOrBlank() && hostCache[domain]?.merchantId != merchantId) {
                        onHostDiscovered(HostTarget(domain, host, merchantId))
                    }
                    onResult(outcome)
                }
            }
        }
        jobs.awaitAll()
    }

    /**
     * Probes domain statuses directly with bounded concurrency (max 6).
     */
    suspend fun probeDomainsBatch(
        domains: List<String>,
        onRecord: suspend (DomainHealthRecord) -> Unit
    ) = coroutineScope {
        val jobs = domains.map { domain ->
            async(Dispatchers.IO) {
                statusProbeSemaphore.withPermit {
                    val record = apiService.checkSiteStatus(domain)
                    onRecord(record)
                }
            }
        }
        jobs.awaitAll()
    }

    /**
     * Single bounded TTL-aware probe used by Directory cards.
     */
    suspend fun probeSingleBounded(domain: String): DomainHealthRecord {
        return statusProbeSemaphore.withPermit {
            apiService.checkSiteStatus(domain)
        }
    }

    suspend fun fetchBulkFeed() = apiService.fetchBulkStatuses()

    companion object {
        fun HostCacheEntity.toHostTarget(): HostTarget =
            HostTarget(domain = domain, host = resolvedHost, merchantId = merchantId)
    }
}
