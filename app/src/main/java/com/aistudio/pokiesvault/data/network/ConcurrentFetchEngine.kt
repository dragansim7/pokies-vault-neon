package com.aistudio.pokiesvault.data.network

import com.aistudio.pokiesvault.data.local.HostCacheEntity
import com.aistudio.pokiesvault.data.model.AccountResult
import com.aistudio.pokiesvault.data.model.DomainHealthRecord
import com.aistudio.pokiesvault.data.model.EgressIpInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Orchestrates bounded concurrent account fetches, public bonus scans, and domain health probes
 * with adaptive concurrency, host caching, and partial-failure isolation.
 */
class ConcurrentFetchEngine(
    private val apiService: CasinoApiService
) {
    private val adaptiveController = AdaptiveConcurrencyController(minLimit = 3, initialLimit = 6, maxLimit = 8)
    private val bonusSemaphore = Semaphore(permits = 6)
    private val statusProbeSemaphore = Semaphore(permits = 6)

    val currentWorkerLimit: StateFlow<Int> = adaptiveController.currentLimit
    val activeWorkers: StateFlow<Int> = adaptiveController.activeWorkers
    val peakWorkers: StateFlow<Int> = adaptiveController.peakWorkers

    suspend fun lookupEgressInfo(): Result<EgressIpInfo> = apiService.lookupEgressInfo()

    /**
     * Fetches multiple casino accounts concurrently using the adaptive 6-8 worker pool.
     * Tracks total retries and attempts across fallback credential rotations per domain.
     */
    suspend fun fetchAccountsBatch(
        domains: List<String>,
        credentials: List<Pair<String, String>>,
        hostCache: Map<String, HostTarget>,
        onHostDiscovered: suspend (HostTarget) -> Unit,
        onResult: suspend (AccountResult) -> Unit
    ) = coroutineScope {
        adaptiveController.reset(6)
        val resultChannel = Channel<AccountResult>(capacity = Channel.UNLIMITED)

        val collectorJob = launch(Dispatchers.Default) {
            for (res in resultChannel) {
                onResult(res)
            }
        }

        try {
            val jobs = domains.map { domain ->
                async(Dispatchers.IO) {
                    adaptiveController.withPermit {
                        var currentTarget = hostCache[domain]
                        var finalResult: AccountResult = AccountResult.Err(
                            domain = domain,
                            code = "other",
                            message = "No credentials configured",
                            fetchedAt = System.currentTimeMillis()
                        )
                        var sawCongestion = false
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

                        if (sawCongestion) {
                            adaptiveController.recordCongestion()
                        } else {
                            adaptiveController.recordHealthy()
                        }
                        resultChannel.send(finalResult)
                    }
                }
            }
            jobs.awaitAll()
        } catch (e: CancellationException) {
            throw e
        } finally {
            resultChannel.close()
            collectorJob.join()
        }
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
