package com.aistudio.pokiesvault.data.network

import com.aistudio.pokiesvault.data.local.HostCacheEntity
import com.aistudio.pokiesvault.data.model.AccountResult
import com.aistudio.pokiesvault.data.model.DomainHealthRecord
import com.aistudio.pokiesvault.data.model.EgressIpInfo
import com.aistudio.pokiesvault.data.model.FetchSpeedPreset
import com.aistudio.pokiesvault.data.model.SavedDomainSession
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
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

data class FetchBatchReport(
    val wasCircuitBroken: Boolean = false,
    val pauseDetail: String = "",
    val trippedDomains: List<String> = emptyList(),
    val sessionsReused: Int = 0,
    val cooldownsCompleted: Int = 0
)

/**
 * Orchestrates bounded concurrent account fetches, public bonus scans, and domain health probes
 * with full-catalog DNS clustering, dead-DNS fast-skip, non-blocking per-origin fair scheduling,
 * dual-lane concurrency (fast saved-session syncs vs protected cold logins), self-healing
 * auto-cooldowns, and a persistent multi-origin WAF circuit breaker.
 */
class ConcurrentFetchEngine(
    private val apiService: CasinoApiService
) {
    private val adaptiveController = AdaptiveConcurrencyController(minLimit = 6, initialLimit = 6, maxLimit = 8)
    private val bonusAdaptiveController = AdaptiveConcurrencyController(minLimit = 8, initialLimit = 8, maxLimit = 12)
    private val statusProbeSemaphore = Semaphore(permits = 6)
    private val dnsResolveSemaphore = Semaphore(permits = 56)

    val currentWorkerLimit: StateFlow<Int> = adaptiveController.currentLimit
    val activeWorkers: StateFlow<Int> = adaptiveController.activeWorkers
    val peakWorkers: StateFlow<Int> = adaptiveController.peakWorkers

    val bonusCurrentWorkerLimit: StateFlow<Int> = bonusAdaptiveController.currentLimit
    val bonusActiveWorkers: StateFlow<Int> = bonusAdaptiveController.activeWorkers
    val bonusPeakWorkers: StateFlow<Int> = bonusAdaptiveController.peakWorkers

    suspend fun lookupEgressInfo(): Result<EgressIpInfo> = apiService.lookupEgressInfo()

    private data class OriginPlan(
        val liveDomains: List<String>,
        val deadDnsDomains: List<String>,
        val originByDomain: Map<String, String>
    )

    /**
     * Resolves DNS origin clusters across the full target list using 56 parallel lookups and a
     * 30-minute in-memory cache so large shared-IP clusters (e.g., 800 sites on 43.106.19.168)
     * are accurately isolated and spaced, while NXDOMAIN hosts fast-fail without burning slots.
     */
    private suspend fun resolveOriginsPlan(domains: List<String>): OriginPlan = coroutineScope {
        if (domains.size <= 3) {
            return@coroutineScope OriginPlan(
                liveDomains = domains,
                deadDnsDomains = emptyList(),
                originByDomain = domains.associateWith { "host:$it" }
            )
        }

        val resolvedMap = ConcurrentHashMap<String, DnsClusterResult>(domains.size)
        val timeoutMs = if (domains.size > 100) 12_000L else 4_500L
        withTimeoutOrNull(timeoutMs) {
            domains.map { d ->
                async(Dispatchers.IO) {
                    dnsResolveSemaphore.withPermit {
                        resolvedMap[d] = apiService.resolveOriginClusterDetailed(d)
                    }
                }
            }.awaitAll()
        }

        val liveDomains = ArrayList<String>(domains.size)
        val deadDomains = ArrayList<String>()
        val originByDomain = HashMap<String, String>(domains.size)

        for (d in domains) {
            val res = resolvedMap[d]
            if (res != null && res.isDeadDns) {
                deadDomains.add(d)
                originByDomain[d] = res.cluster
            } else {
                liveDomains.add(d)
                originByDomain[d] = res?.cluster ?: "host:$d"
            }
        }

        OriginPlan(
            liveDomains = liveDomains,
            deadDnsDomains = deadDomains,
            originByDomain = originByDomain
        )
    }

    /**
     * Fetches multiple accounts concurrently using a dual-lane adaptive worker pool:
     * 1. Dead-DNS fast-skip (instant offline classification, zero wasted worker slots).
     * 2. Lane A — Saved Session Fast Lane (/users/syncData) with higher concurrency & shorter per-IP gap.
     * 3. Lane B — Cold Login Protected Lane (/users/login + winning-phone memory + expired-token fallbacks)
     *    with strict shared-IP pacing tailored for fixed Home Wi-Fi residential IPs.
     */
    suspend fun fetchAccountsBatch(
        domains: List<String>,
        credentials: List<Pair<String, String>>,
        hostCache: Map<String, HostTarget>,
        preset: FetchSpeedPreset = FetchSpeedPreset.SAFE,
        savedSessions: Map<String, SavedDomainSession> = emptyMap(),
        onHostDiscovered: suspend (HostTarget) -> Unit,
        onSessionSaved: suspend (SavedDomainSession) -> Unit = {},
        onSessionExpired: suspend (String) -> Unit = {},
        onSessionReused: () -> Unit = {},
        onCooldownChanged: (remainingSec: Int, reason: String) -> Unit = { _, _ -> },
        onResult: suspend (AccountResult) -> Unit
    ): FetchBatchReport = coroutineScope {
        if (domains.isEmpty()) return@coroutineScope FetchBatchReport()

        val resultChannel = Channel<AccountResult>(capacity = Channel.UNLIMITED)
        val collectorJob = launch(Dispatchers.Default) {
            for (res in resultChannel) {
                onResult(res)
            }
        }

        val breakerTripped = AtomicBoolean(false)
        val reusedCounter = AtomicInteger(0)
        val cooldownsCompletedCounter = AtomicInteger(0)

        // Self-healing cooldown gate shared across both lanes
        val cooldownMutex = Mutex()
        val cooldownUntilMs = AtomicLong(0L)

        // Rolling genuine WAF / rate-limit detector
        val breakerMutex = Mutex()
        var cooldownsTriggered = 0
        var consecutiveWafHits = 0
        val recentWindow = ArrayDeque<Triple<String, String, Boolean>>(8)
        val postCooldownBlockedOrigins = LinkedHashSet<String>()
        var pauseDetail = ""
        val trippedDomains = LinkedHashSet<String>()

        suspend fun awaitActiveCooldown() {
            while (!breakerTripped.get()) {
                val until = cooldownUntilMs.get()
                val now = System.currentTimeMillis()
                if (until <= now) return
                delay(minOf(350L, until - now))
            }
        }

        suspend fun runSelfHealingCooldown(waitSec: Long, reason: String) {
            cooldownMutex.withLock {
                val now = System.currentTimeMillis()
                if (cooldownUntilMs.get() > now) {
                    while (cooldownUntilMs.get() > System.currentTimeMillis() && !breakerTripped.get()) {
                        delay(250L)
                    }
                    return
                }
                val durationSec = waitSec.coerceIn(5L, 60L)
                cooldownUntilMs.set(now + durationSec * 1000L)
                adaptiveController.stepDownForCooldown()
                try {
                    var rem = durationSec.toInt()
                    while (rem > 0 && !breakerTripped.get()) {
                        onCooldownChanged(rem, reason)
                        delay(1000L)
                        rem = ((cooldownUntilMs.get() - System.currentTimeMillis() + 500L) / 1000L).toInt().coerceAtLeast(0)
                    }
                    cooldownsCompletedCounter.incrementAndGet()
                } finally {
                    cooldownUntilMs.set(0L)
                    onCooldownChanged(0, "")
                }
            }
        }

        suspend fun executeSavedSessionOnly(
            domain: String,
            savedSession: SavedDomainSession,
            initialTarget: HostTarget?
        ): SavedLaneOutput {
            val tokenTarget = initialTarget ?: if (savedSession.merchantId.isNotBlank() || savedSession.host.isNotBlank()) {
                HostTarget(
                    domain = domain,
                    host = savedSession.host.ifBlank { domain },
                    merchantId = savedSession.merchantId
                )
            } else null

            val syncOutcome = apiService.syncAccountWithSession(
                rawDomain = domain,
                accessId = savedSession.accessId,
                accessToken = savedSession.accessToken,
                cachedTarget = tokenTarget
            )
            val resolvedTarget = syncOutcome.discoveredTarget ?: initialTarget
            syncOutcome.discoveredTarget?.let { onHostDiscovered(it) }

            val totalRetries = syncOutcome.result.retriesUsed
            val totalAttempts = 1 + totalRetries
            val totalLatencyMs = syncOutcome.result.latencyMs

            return when (val res = syncOutcome.result) {
                is AccountResult.Ok -> {
                    reusedCounter.incrementAndGet()
                    onSessionReused()
                    SavedLaneOutput(
                        expiredToColdLogin = false,
                        output = DomainRunOutput(
                            result = res.copy(
                                latencyMs = totalLatencyMs,
                                retriesUsed = totalRetries,
                                attemptsUsed = totalAttempts
                            ),
                            resolvedTarget = resolvedTarget,
                            sawCongestion = syncOutcome.congested,
                            sawWafSignal = syncOutcome.wafSignal,
                            retryAfterSec = syncOutcome.retryAfterSec
                        )
                    )
                }
                is AccountResult.Err -> {
                    if (syncOutcome.sessionExpired && credentials.isNotEmpty()) {
                        onSessionExpired(domain)
                        SavedLaneOutput(
                            expiredToColdLogin = true,
                            output = DomainRunOutput(
                                result = res,
                                resolvedTarget = resolvedTarget,
                                sawCongestion = syncOutcome.congested,
                                sawWafSignal = syncOutcome.wafSignal,
                                retryAfterSec = syncOutcome.retryAfterSec
                            )
                        )
                    } else {
                        SavedLaneOutput(
                            expiredToColdLogin = false,
                            output = DomainRunOutput(
                                result = res.copy(
                                    latencyMs = totalLatencyMs,
                                    retriesUsed = totalRetries,
                                    attemptsUsed = totalAttempts
                                ),
                                resolvedTarget = resolvedTarget,
                                sawCongestion = syncOutcome.congested,
                                sawWafSignal = syncOutcome.wafSignal,
                                retryAfterSec = syncOutcome.retryAfterSec
                            )
                        )
                    }
                }
            }
        }

        suspend fun executeColdLoginDomain(
            domain: String,
            initialTarget: HostTarget?
        ): DomainRunOutput {
            var currentTarget = initialTarget
            var finalResult: AccountResult = AccountResult.Err(
                domain = domain,
                code = "other",
                message = "No credentials configured",
                fetchedAt = System.currentTimeMillis()
            )
            var sawCongestion = false
            var sawWafSignal = false
            var maxRetryAfterSec: Long? = null
            var totalAttempts = 0
            var totalRetries = 0
            var totalLatencyMs = 0L

            val savedSession = savedSessions[domain]
            if (credentials.isNotEmpty()) {
                val winningPhone = savedSession?.winningPhone?.trim().orEmpty()
                val orderedCreds = if (winningPhone.isNotEmpty()) {
                    val primary = credentials.filter { it.first.trim() == winningPhone }
                    val rest = credentials.filter { it.first.trim() != winningPhone }
                    primary + rest
                } else {
                    credentials
                }

                for ((credIdx, credPair) in orderedCreds.withIndex()) {
                    val (phone, password) = credPair
                    if (credIdx > 0) {
                        // Gentle jitter gap between fallback credential attempts
                        delay(420L + Random.nextLong(0L, 240L))
                    }
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
                    outcome.retryAfterSec?.let { r ->
                        maxRetryAfterSec = maxOf(maxRetryAfterSec ?: 0L, r)
                    }

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
                            outcome.newSession?.let { onSessionSaved(it) }
                            break
                        }
                        is AccountResult.Err -> {
                            finalResult = res.copy(
                                latencyMs = totalLatencyMs,
                                retriesUsed = totalRetries,
                                attemptsUsed = totalAttempts
                            )
                            if (res.code != "invalid_login") break
                        }
                    }
                }
            }

            return DomainRunOutput(
                result = finalResult,
                resolvedTarget = currentTarget,
                sawCongestion = sawCongestion,
                sawWafSignal = sawWafSignal,
                retryAfterSec = maxRetryAfterSec
            )
        }

        suspend fun evaluateWafAndRecord(
            domain: String,
            origin: String,
            runOut: DomainRunOutput,
            scheduler: PerOriginFairScheduler
        ) {
            if (runOut.sawWafSignal) {
                scheduler.penalizeOrigin(origin, 2500L)
            }
            if (runOut.sawCongestion || runOut.sawWafSignal) {
                adaptiveController.recordCongestion()
            } else {
                adaptiveController.recordHealthy()
            }

            breakerMutex.withLock {
                if (runOut.sawWafSignal) {
                    consecutiveWafHits++
                    if (cooldownsTriggered >= 2) {
                        postCooldownBlockedOrigins.add(origin)
                    }
                } else {
                    consecutiveWafHits = 0
                }
                if (recentWindow.size >= 8) {
                    recentWindow.removeFirst()
                }
                recentWindow.addLast(Triple(domain, origin, runOut.sawWafSignal))

                val wafEntries = recentWindow.filter { it.third }
                val distinctBlockedOrigins = wafEntries.map { it.second }.toSet()

                val shouldTrip = cooldownsTriggered >= 2 && (
                    (postCooldownBlockedOrigins.size >= 2 && consecutiveWafHits >= 3) ||
                        (distinctBlockedOrigins.size >= 2 && wafEntries.size >= 5)
                    )

                if (shouldTrip && !breakerTripped.get()) {
                    breakerTripped.set(true)
                    val originCount = maxOf(postCooldownBlockedOrigins.size, distinctBlockedOrigins.size)
                    pauseDetail = "Persistent HTTP 429 / firewall blocks across $originCount servers after auto-cooldown"
                    wafEntries.forEach { trippedDomains.add(it.first) }
                } else if (breakerTripped.get() && runOut.sawWafSignal) {
                    trippedDomains.add(domain)
                }
            }
        }

        suspend fun maybeRunAutoCooldown(runOut: DomainRunOutput): Boolean {
            if (!runOut.sawWafSignal) return false
            var shouldRunCooldown = false
            var cooldownWaitSec = 15L
            var cooldownLabel = "Rate limit detected"
            val isExplicit429 = (runOut.result as? AccountResult.Err)?.code == "rate_limit"

            breakerMutex.withLock {
                consecutiveWafHits++
                if (cooldownsTriggered < 2 && (isExplicit429 || consecutiveWafHits >= 2)) {
                    cooldownsTriggered++
                    val baseSec = if (cooldownsTriggered == 1) 15L else 30L
                    cooldownWaitSec = maxOf(baseSec, runOut.retryAfterSec ?: 0L).coerceAtMost(60L)
                    cooldownLabel = if (isExplicit429) "HTTP 429 cooldown" else "Firewall cooldown"
                    shouldRunCooldown = true
                    consecutiveWafHits = 0
                }
            }

            if (shouldRunCooldown) {
                runSelfHealingCooldown(cooldownWaitSec, cooldownLabel)
                return !breakerTripped.get()
            }
            return false
        }

        try {
            val originPlan = resolveOriginsPlan(domains)

            // Fast-skip dead-DNS hosts immediately without consuming worker slots
            if (originPlan.deadDnsDomains.isNotEmpty()) {
                val now = System.currentTimeMillis()
                for (deadDomain in originPlan.deadDnsDomains) {
                    resultChannel.send(
                        AccountResult.Err(
                            domain = deadDomain,
                            code = "unreachable",
                            message = "Site offline (DNS)",
                            latencyMs = 1L,
                            retriesUsed = 0,
                            attemptsUsed = 1,
                            fetchedAt = now
                        )
                    )
                }
            }

            val savedSessionDomains = ArrayList<String>()
            val coldLoginDomains = ArrayList<String>()
            for (d in originPlan.liveDomains) {
                if (savedSessions[d]?.hasToken == true) {
                    savedSessionDomains.add(d)
                } else {
                    coldLoginDomains.add(d)
                }
            }

            val expiredFallbackDomains = Collections.synchronizedList(ArrayList<String>())
            var ranSavedLane = false

            // --- LANE A: Saved Session Fast Sweep (/users/syncData) ---
            if (savedSessionDomains.isNotEmpty() && !breakerTripped.get()) {
                ranSavedLane = true
                val savedProfile = preset.savedSessionProfile
                adaptiveController.reset(
                    min = savedProfile.minWorkers,
                    initial = savedProfile.initialWorkers,
                    max = savedProfile.maxWorkers,
                    preservePeak = false
                )
                val scheduler = PerOriginFairScheduler(
                    domains = savedSessionDomains,
                    originByDomain = originPlan.originByDomain,
                    profile = savedProfile
                )
                val workerCount = savedProfile.maxWorkers.coerceIn(1, savedSessionDomains.size)
                val workers = List(workerCount) {
                    launch(Dispatchers.IO) {
                        while (!breakerTripped.get()) {
                            var finishedAll = false
                            adaptiveController.withPermit {
                                val scheduled = scheduler.acquireNext(
                                    shouldStop = { breakerTripped.get() },
                                    awaitCooldown = { awaitActiveCooldown() }
                                )
                                if (scheduled == null) {
                                    finishedAll = true
                                    return@withPermit
                                }
                                try {
                                    val domain = scheduled.domain
                                    val origin = scheduled.origin
                                    val session = savedSessions[domain]
                                    if (session == null || !session.hasToken) {
                                        expiredFallbackDomains.add(domain)
                                        return@withPermit
                                    }

                                    var laneOut = executeSavedSessionOnly(domain, session, hostCache[domain])
                                    if (!laneOut.expiredToColdLogin && maybeRunAutoCooldown(laneOut.output)) {
                                        laneOut = executeSavedSessionOnly(
                                            domain,
                                            session,
                                            laneOut.output.resolvedTarget ?: hostCache[domain]
                                        )
                                    }

                                    if (laneOut.expiredToColdLogin) {
                                        expiredFallbackDomains.add(domain)
                                    } else {
                                        evaluateWafAndRecord(domain, origin, laneOut.output, scheduler)
                                        resultChannel.send(laneOut.output.result)
                                    }
                                } finally {
                                    scheduler.release(scheduled.origin)
                                }
                            }
                            if (finishedAll) break
                        }
                    }
                }
                workers.forEach { it.join() }
            }

            // --- LANE B: Cold Login Protected Sweep (/users/login + expired-token fallbacks) ---
            val phase2Domains = if (expiredFallbackDomains.isEmpty()) {
                coldLoginDomains
            } else {
                coldLoginDomains + expiredFallbackDomains
            }

            if (phase2Domains.isNotEmpty() && !breakerTripped.get()) {
                val coldProfile = preset.coldLoginProfile
                adaptiveController.reset(
                    min = coldProfile.minWorkers,
                    initial = coldProfile.initialWorkers,
                    max = coldProfile.maxWorkers,
                    preservePeak = ranSavedLane
                )
                val scheduler = PerOriginFairScheduler(
                    domains = phase2Domains,
                    originByDomain = originPlan.originByDomain,
                    profile = coldProfile
                )
                val workerCount = coldProfile.maxWorkers.coerceIn(1, phase2Domains.size)
                val workers = List(workerCount) {
                    launch(Dispatchers.IO) {
                        while (!breakerTripped.get()) {
                            var finishedAll = false
                            adaptiveController.withPermit {
                                val scheduled = scheduler.acquireNext(
                                    shouldStop = { breakerTripped.get() },
                                    awaitCooldown = { awaitActiveCooldown() }
                                )
                                if (scheduled == null) {
                                    finishedAll = true
                                    return@withPermit
                                }
                                try {
                                    val domain = scheduled.domain
                                    val origin = scheduled.origin
                                    var runOut = executeColdLoginDomain(domain, hostCache[domain])
                                    if (maybeRunAutoCooldown(runOut)) {
                                        runOut = executeColdLoginDomain(
                                            domain,
                                            runOut.resolvedTarget ?: hostCache[domain]
                                        )
                                    }
                                    evaluateWafAndRecord(domain, origin, runOut, scheduler)
                                    resultChannel.send(runOut.result)
                                } finally {
                                    scheduler.release(scheduled.origin)
                                }
                            }
                            if (finishedAll) break
                        }
                    }
                }
                workers.forEach { it.join() }
            }
        } catch (e: CancellationException) {
            throw e
        } finally {
            onCooldownChanged(0, "")
            resultChannel.close()
            collectorJob.join()
        }

        FetchBatchReport(
            wasCircuitBroken = breakerTripped.get(),
            pauseDetail = pauseDetail,
            trippedDomains = trippedDomains.toList(),
            sessionsReused = reusedCounter.get(),
            cooldownsCompleted = cooldownsCompletedCounter.get()
        )
    }

    /**
     * Scans public bonus catalogues using a dedicated Direct-API adaptive worker pool configured by
     * [preset.bonusScanProfile], with full DNS clustering, dead-DNS fast-skip, non-blocking
     * per-origin fair scheduling, self-healing auto-cooldowns, and a persistent WAF circuit breaker.
     */
    suspend fun scanPublicBonusesBatch(
        domains: List<String>,
        hostCache: Map<String, HostTarget>,
        preset: FetchSpeedPreset = FetchSpeedPreset.SAFE,
        onHostDiscovered: suspend (HostTarget) -> Unit,
        onCooldownChanged: (remainingSec: Int, reason: String) -> Unit = { _, _ -> },
        onResult: suspend (PublicCatalogueResult) -> Unit
    ): FetchBatchReport = coroutineScope {
        val bonusProfile = preset.bonusScanProfile
        bonusAdaptiveController.reset(
            min = bonusProfile.minWorkers,
            initial = bonusProfile.initialWorkers,
            max = bonusProfile.maxWorkers,
            preservePeak = false
        )
        if (domains.isEmpty()) return@coroutineScope FetchBatchReport()

        val resultChannel = Channel<PublicCatalogueResult>(capacity = Channel.UNLIMITED)
        val collectorJob = launch(Dispatchers.Default) {
            for (res in resultChannel) {
                onResult(res)
            }
        }

        val breakerTripped = AtomicBoolean(false)
        val cooldownsCompletedCounter = AtomicInteger(0)

        val cooldownMutex = Mutex()
        val cooldownUntilMs = AtomicLong(0L)

        val breakerMutex = Mutex()
        var cooldownsTriggered = 0
        var consecutiveWafHits = 0
        val recentWindow = ArrayDeque<Triple<String, String, Boolean>>(8)
        val postCooldownBlockedOrigins = LinkedHashSet<String>()
        var pauseDetail = ""
        val trippedDomains = LinkedHashSet<String>()

        suspend fun awaitActiveCooldown() {
            while (!breakerTripped.get()) {
                val until = cooldownUntilMs.get()
                val now = System.currentTimeMillis()
                if (until <= now) return
                delay(minOf(350L, until - now))
            }
        }

        suspend fun runSelfHealingCooldown(waitSec: Long, reason: String) {
            cooldownMutex.withLock {
                val now = System.currentTimeMillis()
                if (cooldownUntilMs.get() > now) {
                    while (cooldownUntilMs.get() > System.currentTimeMillis() && !breakerTripped.get()) {
                        delay(250L)
                    }
                    return
                }
                val durationSec = waitSec.coerceIn(5L, 60L)
                cooldownUntilMs.set(now + durationSec * 1000L)
                bonusAdaptiveController.stepDownForCooldown()
                try {
                    var rem = durationSec.toInt()
                    while (rem > 0 && !breakerTripped.get()) {
                        onCooldownChanged(rem, reason)
                        delay(1000L)
                        rem = ((cooldownUntilMs.get() - System.currentTimeMillis() + 500L) / 1000L).toInt().coerceAtLeast(0)
                    }
                    cooldownsCompletedCounter.incrementAndGet()
                } finally {
                    cooldownUntilMs.set(0L)
                    onCooldownChanged(0, "")
                }
            }
        }

        try {
            val originPlan = resolveOriginsPlan(domains)

            // Fast-skip dead-DNS hosts immediately without burning worker slots
            if (originPlan.deadDnsDomains.isNotEmpty()) {
                for (deadDomain in originPlan.deadDnsDomains) {
                    resultChannel.send(
                        PublicCatalogueResult(
                            domain = deadDomain,
                            ok = false,
                            bonuses = emptyList(),
                            message = "Site offline (DNS)",
                            latencyMs = 1L,
                            retriesUsed = 0,
                            attemptsUsed = 1,
                            errorCode = "unreachable"
                        )
                    )
                }
            }

            if (originPlan.liveDomains.isNotEmpty()) {
                val scheduler = PerOriginFairScheduler(
                    domains = originPlan.liveDomains,
                    originByDomain = originPlan.originByDomain,
                    profile = bonusProfile
                )
                val workerCount = bonusProfile.maxWorkers.coerceIn(1, originPlan.liveDomains.size)
                val workers = List(workerCount) {
                    launch(Dispatchers.IO) {
                        while (!breakerTripped.get()) {
                            var finishedAll = false
                            bonusAdaptiveController.withPermit {
                                val scheduled = scheduler.acquireNext(
                                    shouldStop = { breakerTripped.get() },
                                    awaitCooldown = { awaitActiveCooldown() }
                                )
                                if (scheduled == null) {
                                    finishedAll = true
                                    return@withPermit
                                }
                                try {
                                    val domain = scheduled.domain
                                    val origin = scheduled.origin
                                    var outcome = apiService.fetchPublicBonuses(domain, hostCache[domain])

                                    if (outcome.wafSignal) {
                                        scheduler.penalizeOrigin(origin, 2500L)
                                        var shouldRunCooldown = false
                                        var cooldownWaitSec = 15L
                                        var cooldownLabel = "Bonus scan cooldown"
                                        val isExplicit429 = outcome.errorCode == "rate_limit"
                                        breakerMutex.withLock {
                                            consecutiveWafHits++
                                            if (cooldownsTriggered < 2 && (isExplicit429 || consecutiveWafHits >= 2)) {
                                                cooldownsTriggered++
                                                val baseSec = if (cooldownsTriggered == 1) 15L else 30L
                                                cooldownWaitSec = maxOf(baseSec, outcome.retryAfterSec ?: 0L).coerceAtMost(60L)
                                                cooldownLabel = if (isExplicit429) "HTTP 429 cooldown" else "Firewall cooldown"
                                                shouldRunCooldown = true
                                                consecutiveWafHits = 0
                                            }
                                        }
                                        if (shouldRunCooldown) {
                                            runSelfHealingCooldown(cooldownWaitSec, cooldownLabel)
                                            if (!breakerTripped.get()) {
                                                outcome = apiService.fetchPublicBonuses(domain, hostCache[domain])
                                            }
                                        }
                                    }

                                    val host = outcome.host
                                    val merchantId = outcome.merchantId
                                    if (!host.isNullOrBlank() &&
                                        !merchantId.isNullOrBlank() &&
                                        hostCache[domain]?.merchantId != merchantId
                                    ) {
                                        onHostDiscovered(HostTarget(domain, host, merchantId))
                                    }

                                    if (outcome.congested || outcome.wafSignal) {
                                        bonusAdaptiveController.recordCongestion()
                                    } else {
                                        bonusAdaptiveController.recordHealthy()
                                    }

                                    breakerMutex.withLock {
                                        if (outcome.wafSignal) {
                                            consecutiveWafHits++
                                            if (cooldownsTriggered >= 2) {
                                                postCooldownBlockedOrigins.add(origin)
                                            }
                                        } else {
                                            consecutiveWafHits = 0
                                        }
                                        if (recentWindow.size >= 8) {
                                            recentWindow.removeFirst()
                                        }
                                        recentWindow.addLast(Triple(domain, origin, outcome.wafSignal))

                                        val wafEntries = recentWindow.filter { it.third }
                                        val distinctBlockedOrigins = wafEntries.map { it.second }.toSet()

                                        val shouldTrip = cooldownsTriggered >= 2 && (
                                            (postCooldownBlockedOrigins.size >= 2 && consecutiveWafHits >= 3) ||
                                                (distinctBlockedOrigins.size >= 2 && wafEntries.size >= 5)
                                            )

                                        if (shouldTrip && !breakerTripped.get()) {
                                            breakerTripped.set(true)
                                            val originCount = maxOf(postCooldownBlockedOrigins.size, distinctBlockedOrigins.size)
                                            pauseDetail = "Persistent HTTP 429 / firewall blocks across $originCount servers after auto-cooldown"
                                            wafEntries.forEach { trippedDomains.add(it.first) }
                                        } else if (breakerTripped.get() && outcome.wafSignal) {
                                            trippedDomains.add(domain)
                                        }
                                    }

                                    resultChannel.send(outcome)
                                } finally {
                                    scheduler.release(scheduled.origin)
                                }
                            }
                            if (finishedAll) break
                        }
                    }
                }
                workers.forEach { it.join() }
            }
        } catch (e: CancellationException) {
            throw e
        } finally {
            onCooldownChanged(0, "")
            resultChannel.close()
            collectorJob.join()
        }

        FetchBatchReport(
            wasCircuitBroken = breakerTripped.get(),
            pauseDetail = pauseDetail,
            trippedDomains = trippedDomains.toList(),
            cooldownsCompleted = cooldownsCompletedCounter.get()
        )
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

    suspend fun fetchLatestDomainsList(): Result<List<String>> = apiService.fetchLatestDomainsList()

    private data class DomainRunOutput(
        val result: AccountResult,
        val resolvedTarget: HostTarget?,
        val sawCongestion: Boolean,
        val sawWafSignal: Boolean,
        val retryAfterSec: Long?
    )

    private data class SavedLaneOutput(
        val expiredToColdLogin: Boolean,
        val output: DomainRunOutput
    )

    companion object {
        fun HostCacheEntity.toHostTarget(): HostTarget =
            HostTarget(domain = domain, host = resolvedHost, merchantId = merchantId)
    }
}
