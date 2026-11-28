package com.aistudio.pokiesvault.data.coordinator

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.aistudio.pokiesvault.data.model.AccountResult
import com.aistudio.pokiesvault.data.model.CheckpointPauseReason
import com.aistudio.pokiesvault.data.model.EgressIpInfo
import com.aistudio.pokiesvault.data.model.FetchActiveLane
import com.aistudio.pokiesvault.data.model.FetchCheckpoint
import com.aistudio.pokiesvault.data.model.FetchRunSummary
import com.aistudio.pokiesvault.data.model.FetchSpeedPreset
import com.aistudio.pokiesvault.data.model.SavedDomainSession
import com.aistudio.pokiesvault.data.model.VaultCredentials
import com.aistudio.pokiesvault.data.model.VaultPreferences
import com.aistudio.pokiesvault.data.network.HostTarget
import com.aistudio.pokiesvault.data.network.PublicCatalogueResult
import com.aistudio.pokiesvault.data.repository.VaultRepository
import com.aistudio.pokiesvault.service.VaultBackgroundRunService
import com.aistudio.pokiesvault.util.DomainUtil
import com.aistudio.pokiesvault.util.FormatUtil
import com.aistudio.pokiesvault.util.ScanRateSnapshot
import com.aistudio.pokiesvault.util.ScanTelemetryUtil
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

data class CoordinatorEgressState(
    val isLoading: Boolean = true,
    val info: EgressIpInfo? = null,
    val error: String? = null
)

data class BackgroundRunNotificationState(
    val isRunning: Boolean = false,
    val allowBackground: Boolean = false,
    val title: String = "Pokies Vault",
    val subtitle: String = "Background scan active",
    val progressDone: Int = 0,
    val progressTotal: Int = 0
)

/**
 * Application-scoped run coordinator that owns Account Fetcher and Bonus Finder execution,
 * live telemetry StateFlows, durable checkpoints, and synchronization with [VaultBackgroundRunService].
 *
 * Keeping active scan jobs here allows runs to continue seamlessly when the user enables
 * Background Runs and minimizes or closes the app, and allows [VaultViewModel] to re-attach
 * directly to the live progress stream upon reopening.
 */
class VaultRunCoordinator(
    private val appContext: Context,
    val repository: VaultRepository
) {
    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 12)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable is CancellationException) return@CoroutineExceptionHandler
        val detail = throwable.message?.take(120) ?: throwable.javaClass.simpleName
        _snackbarEvents.tryEmit("Scan error recovered: $detail")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)

    @Volatile
    private var allowBackgroundState: Boolean = false

    val preferencesFlow: StateFlow<VaultPreferences> = repository.preferencesFlow
        .stateIn(scope, SharingStarted.Eagerly, VaultPreferences())

    val credentialsFlow: StateFlow<VaultCredentials> = repository.credentialsFlow
        .stateIn(scope, SharingStarted.Eagerly, VaultCredentials())

    val checkpointFlow: StateFlow<FetchCheckpoint?> = repository.checkpointFlow
        .stateIn(scope, SharingStarted.Eagerly, null)

    val bonusCheckpointFlow: StateFlow<FetchCheckpoint?> = repository.bonusCheckpointFlow
        .stateIn(scope, SharingStarted.Eagerly, null)

    private val latestAccountsFlow: StateFlow<Map<String, AccountResult.Ok>> =
        repository.latestAccountsByDomainFlow
            .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    // --- Outgoing Egress IP ---
    private val _egressState = MutableStateFlow(CoordinatorEgressState())
    val egressState: StateFlow<CoordinatorEgressState> = _egressState.asStateFlow()

    // --- Account Fetcher Live State ---
    private val _fetcherRows = MutableStateFlow<List<AccountResult>>(emptyList())
    val fetcherRows: StateFlow<List<AccountResult>> = _fetcherRows.asStateFlow()

    private val _fetcherRunning = MutableStateFlow(false)
    val fetcherRunning: StateFlow<Boolean> = _fetcherRunning.asStateFlow()

    private val _fetcherProgress = MutableStateFlow(0 to 0)
    val fetcherProgress: StateFlow<Pair<Int, Int>> = _fetcherProgress.asStateFlow()

    private val _fetcherStatus = MutableStateFlow(
        "Enter your phone and password to fetch wallets and bonuses safely."
    )
    val fetcherStatus: StateFlow<String> = _fetcherStatus.asStateFlow()

    private val _liveStatusLabel = MutableStateFlow("Idle")
    val liveStatusLabel: StateFlow<String> = _liveStatusLabel.asStateFlow()

    private val _wallElapsedSec = MutableStateFlow(0L)
    val wallElapsedSec: StateFlow<Long> = _wallElapsedSec.asStateFlow()

    private val _fetcherDiffs = MutableStateFlow<List<String>>(emptyList())
    val fetcherDiffs: StateFlow<List<String>> = _fetcherDiffs.asStateFlow()

    private val _showRejected = MutableStateFlow(false)
    val showRejected: StateFlow<Boolean> = _showRejected.asStateFlow()

    private val _viewingRunId = MutableStateFlow<Long?>(null)
    val viewingRunId: StateFlow<Long?> = _viewingRunId.asStateFlow()

    private val _cooldownSeconds = MutableStateFlow(0)
    val cooldownSeconds: StateFlow<Int> = _cooldownSeconds.asStateFlow()

    private val _cooldownReason = MutableStateFlow("")
    val cooldownReason: StateFlow<String> = _cooldownReason.asStateFlow()

    private val _sessionReusedCount = MutableStateFlow(0)
    val sessionReusedCount: StateFlow<Int> = _sessionReusedCount.asStateFlow()

    private val _activeFetchLane = MutableStateFlow(FetchActiveLane.IDLE)
    val activeFetchLane: StateFlow<FetchActiveLane> = _activeFetchLane.asStateFlow()

    private val _fetcherRateSnapshot = MutableStateFlow(ScanRateSnapshot())
    val fetcherRateSnapshot: StateFlow<ScanRateSnapshot> = _fetcherRateSnapshot.asStateFlow()

    // --- Bonus Finder Live State ---
    private val _bonusRunning = MutableStateFlow(false)
    val bonusRunning: StateFlow<Boolean> = _bonusRunning.asStateFlow()

    private val _bonusProgress = MutableStateFlow(0 to 0)
    val bonusProgress: StateFlow<Pair<Int, Int>> = _bonusProgress.asStateFlow()

    private val _bonusStatus = MutableStateFlow(
        "Scan public promotions across your domain list without signing in."
    )
    val bonusStatus: StateFlow<String> = _bonusStatus.asStateFlow()

    private val _bonusLiveStatusLabel = MutableStateFlow("Idle")
    val bonusLiveStatusLabel: StateFlow<String> = _bonusLiveStatusLabel.asStateFlow()

    private val _bonusWallElapsedSec = MutableStateFlow(0L)
    val bonusWallElapsedSec: StateFlow<Long> = _bonusWallElapsedSec.asStateFlow()

    private val _bonusScanLatencyMs = MutableStateFlow(0L)
    val bonusScanLatencyMs: StateFlow<Long> = _bonusScanLatencyMs.asStateFlow()

    private val _bonusRetries = MutableStateFlow(0)
    val bonusRetries: StateFlow<Int> = _bonusRetries.asStateFlow()

    private val _bonusAttempts = MutableStateFlow(0)
    val bonusAttempts: StateFlow<Int> = _bonusAttempts.asStateFlow()

    private val _bonusRunLoaded = MutableStateFlow(0)
    val bonusRunLoaded: StateFlow<Int> = _bonusRunLoaded.asStateFlow()

    private val _bonusRunFailed = MutableStateFlow(0)
    val bonusRunFailed: StateFlow<Int> = _bonusRunFailed.asStateFlow()

    private val _bonusCooldownSeconds = MutableStateFlow(0)
    val bonusCooldownSeconds: StateFlow<Int> = _bonusCooldownSeconds.asStateFlow()

    private val _bonusRateSnapshot = MutableStateFlow(ScanRateSnapshot())
    val bonusRateSnapshot: StateFlow<ScanRateSnapshot> = _bonusRateSnapshot.asStateFlow()

    val hasActiveRun: StateFlow<Boolean> = combine(
        _fetcherRunning,
        _bonusRunning
    ) { fetchOn, bonusOn -> fetchOn || bonusOn }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val notificationState: StateFlow<BackgroundRunNotificationState> = combine(
        preferencesFlow,
        _fetcherRunning,
        _fetcherProgress,
        _bonusRunning,
        _bonusProgress,
        _bonusRunLoaded,
        _bonusRunFailed,
        repository.engine.activeWorkers,
        repository.engine.bonusActiveWorkers,
        _activeFetchLane,
        _fetcherRateSnapshot,
        _bonusRateSnapshot
    ) { arr ->
        val prefs = arr[0] as VaultPreferences
        val fetchOn = arr[1] as Boolean
        @Suppress("UNCHECKED_CAST")
        val fetchProg = arr[2] as Pair<Int, Int>
        val bonusOn = arr[3] as Boolean
        @Suppress("UNCHECKED_CAST")
        val bonusProg = arr[4] as Pair<Int, Int>
        val bLoaded = arr[5] as Int
        val bFailed = arr[6] as Int
        val fWorkers = arr[7] as Int
        val bWorkers = arr[8] as Int
        val lane = arr[9] as FetchActiveLane
        val fetchRate = arr[10] as ScanRateSnapshot
        val bonusRate = arr[11] as ScanRateSnapshot

        when {
            fetchOn && bonusOn -> {
                val done = fetchProg.first + bonusProg.first
                val total = fetchProg.second + bonusProg.second
                BackgroundRunNotificationState(
                    isRunning = true,
                    allowBackground = prefs.allowBackgroundRuns,
                    title = "Pokies Vault · 2 active runs",
                    subtitle = "Accounts ${fetchProg.first}/${fetchProg.second} · Bonuses ${bonusProg.first}/${bonusProg.second}",
                    progressDone = done,
                    progressTotal = total
                )
            }
            fetchOn -> {
                val pct = if (fetchProg.second > 0) (fetchProg.first * 100) / fetchProg.second else 0
                val lanePart = if (lane != FetchActiveLane.IDLE) "${lane.label} · " else ""
                val ratePart = if (fetchRate.sitesPerSec >= 0.05) {
                    " · ${fetchRate.rateLabel}" + (fetchRate.etaSeconds?.let { " · ETA ${fetchRate.etaLabel}" } ?: "")
                } else ""
                BackgroundRunNotificationState(
                    isRunning = true,
                    allowBackground = prefs.allowBackgroundRuns,
                    title = "Account Fetcher · ${fetchProg.first}/${fetchProg.second} ($pct%)",
                    subtitle = "${lanePart}${fWorkers}w${ratePart}",
                    progressDone = fetchProg.first,
                    progressTotal = fetchProg.second
                )
            }
            bonusOn -> {
                val pct = if (bonusProg.second > 0) (bonusProg.first * 100) / bonusProg.second else 0
                val ratePart = if (bonusRate.sitesPerSec >= 0.05) {
                    " · ${bonusRate.rateLabel}" + (bonusRate.etaSeconds?.let { " · ETA ${bonusRate.etaLabel}" } ?: "")
                } else ""
                BackgroundRunNotificationState(
                    isRunning = true,
                    allowBackground = prefs.allowBackgroundRuns,
                    title = "Bonus Finder · ${bonusProg.first}/${bonusProg.second} ($pct%)",
                    subtitle = "$bLoaded loaded · ${bWorkers}w$ratePart",
                    progressDone = bonusProg.first,
                    progressTotal = bonusProg.second
                )
            }
            else -> BackgroundRunNotificationState(
                isRunning = false,
                allowBackground = prefs.allowBackgroundRuns
            )
        }
    }.stateIn(scope, SharingStarted.Eagerly, BackgroundRunNotificationState())

    private var fetchJob: Job? = null
    private var fetchWallTimerJob: Job? = null
    private var bonusScanJob: Job? = null
    private var bonusWallTimerJob: Job? = null
    private var egressJob: Job? = null

    init {
        refreshOutgoingIp()
        scope.launch {
            repository.preferencesFlow.collect { prefs ->
                allowBackgroundState = prefs.allowBackgroundRuns
            }
        }
        scope.launch {
            try {
                repository.ensureInitialized()

                // Hydrate Account Fetcher saved checkpoint or latest run
                val savedCheckpoint = repository.checkpointFlow.first()
                if (savedCheckpoint != null && savedCheckpoint.remainingDomains.isNotEmpty()) {
                    val partialRows = hydrateCheckpointRows(savedCheckpoint)
                    if (partialRows.isNotEmpty()) {
                        _fetcherRows.value = partialRows
                    }
                    _fetcherProgress.value = savedCheckpoint.completedDomains.size to savedCheckpoint.allTargetDomains.size
                    _wallElapsedSec.value = savedCheckpoint.elapsedWallSec
                    _liveStatusLabel.value = if (savedCheckpoint.pauseReason == CheckpointPauseReason.WAF_BLOCKED) {
                        "Paused (WAF)"
                    } else {
                        "Paused"
                    }
                    _fetcherStatus.value = "Saved checkpoint: ${savedCheckpoint.completedDomains.size} of ${savedCheckpoint.allTargetDomains.size} processed · ${savedCheckpoint.remainingDomains.size} remaining."
                } else {
                    val latestRows = repository.loadLatestRunResults()
                    if (latestRows.isNotEmpty()) {
                        _fetcherRows.value = latestRows
                        _fetcherProgress.value = latestRows.size to latestRows.size
                        _liveStatusLabel.value = "Idle"
                        val okCount = latestRows.count { it is AccountResult.Ok }
                        _fetcherStatus.value = "Loaded last saved run ($okCount of ${latestRows.size} signed in)."
                    }
                }

                // Hydrate Bonus Finder saved checkpoint if present
                val savedBonusCp = repository.bonusCheckpointFlow.first()
                if (savedBonusCp != null && savedBonusCp.remainingDomains.isNotEmpty()) {
                    _bonusProgress.value = savedBonusCp.completedDomains.size to savedBonusCp.allTargetDomains.size
                    _bonusWallElapsedSec.value = savedBonusCp.elapsedWallSec
                    _bonusLiveStatusLabel.value = if (savedBonusCp.pauseReason == CheckpointPauseReason.WAF_BLOCKED) {
                        "Paused (WAF)"
                    } else {
                        "Paused"
                    }
                    _bonusStatus.value = "Saved bonus scan checkpoint: ${savedBonusCp.completedDomains.size} of ${savedBonusCp.allTargetDomains.size} scanned · ${savedBonusCp.remainingDomains.size} remaining."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                _fetcherStatus.value = "Vault initialized (skipped oversized history load: ${t.message ?: "recovered"})."
            }
        }
    }

    private suspend fun hydrateCheckpointRows(checkpoint: FetchCheckpoint): List<AccountResult> {
        val targetSet = checkpoint.allTargetDomains.toSet()
        val remainingSet = checkpoint.remainingDomains.toSet()
        val merged = LinkedHashMap<String, AccountResult>(checkpoint.completedDomains.size)

        val partialId = checkpoint.partialRunId
        if (partialId != null && partialId > 0L) {
            val fromRun = try {
                repository.loadRunResults(partialId)
            } catch (_: Throwable) {
                emptyList()
            }
            for (row in fromRun) {
                if (row.domain in targetSet && row.domain !in remainingSet) {
                    merged[row.domain] = row
                }
            }
        }

        // Fill any completed domains missing from the partial run (e.g. after duplicate cleanup) from latest vault rows
        if (merged.size < checkpoint.completedDomains.size) {
            val latestByDomain = try {
                repository.loadLatestSnapshotsMap()
            } catch (_: Throwable) {
                emptyMap()
            }
            for (dom in checkpoint.completedDomains) {
                if (dom !in remainingSet && !merged.containsKey(dom)) {
                    latestByDomain[dom]?.let { merged[dom] = it }
                }
            }
        }
        return merged.values.toList()
    }

    fun isAllowBackgroundRunsEnabled(): Boolean = allowBackgroundState

    fun isAnyRunActive(): Boolean = _fetcherRunning.value || _bonusRunning.value

    /**
     * Builds the foreground notification state synchronously from backing StateFlows so
     * [VaultBackgroundRunService.onStartCommand] never races against async combine() updates.
     */
    fun buildCurrentNotificationState(overrideAllowBackground: Boolean? = null): BackgroundRunNotificationState {
        val allow = overrideAllowBackground ?: allowBackgroundState
        val fetchOn = _fetcherRunning.value
        val bonusOn = _bonusRunning.value
        val fetchProg = _fetcherProgress.value
        val bonusProg = _bonusProgress.value
        val bLoaded = _bonusRunLoaded.value
        val bFailed = _bonusRunFailed.value
        val fWorkers = repository.engine.activeWorkers.value
        val bWorkers = repository.engine.bonusActiveWorkers.value
        val lane = _activeFetchLane.value
        val fetchRate = _fetcherRateSnapshot.value
        val bonusRate = _bonusRateSnapshot.value

        return when {
            fetchOn && bonusOn -> {
                val done = fetchProg.first + bonusProg.first
                val total = fetchProg.second + bonusProg.second
                BackgroundRunNotificationState(
                    isRunning = true,
                    allowBackground = allow,
                    title = "Pokies Vault · 2 active runs",
                    subtitle = "Accounts ${fetchProg.first}/${fetchProg.second} · Bonuses ${bonusProg.first}/${bonusProg.second}",
                    progressDone = done,
                    progressTotal = total
                )
            }
            fetchOn -> {
                val pct = if (fetchProg.second > 0) (fetchProg.first * 100) / fetchProg.second else 0
                val lanePart = if (lane != FetchActiveLane.IDLE) "${lane.label} · " else ""
                val ratePart = if (fetchRate.sitesPerSec >= 0.05) {
                    " · ${fetchRate.rateLabel}" + (fetchRate.etaSeconds?.let { " · ETA ${fetchRate.etaLabel}" } ?: "")
                } else ""
                BackgroundRunNotificationState(
                    isRunning = true,
                    allowBackground = allow,
                    title = "Account Fetcher · ${fetchProg.first}/${fetchProg.second} ($pct%)",
                    subtitle = "${lanePart}${fWorkers}w$ratePart",
                    progressDone = fetchProg.first,
                    progressTotal = fetchProg.second
                )
            }
            bonusOn -> {
                val pct = if (bonusProg.second > 0) (bonusProg.first * 100) / bonusProg.second else 0
                val ratePart = if (bonusRate.sitesPerSec >= 0.05) {
                    " · ${bonusRate.rateLabel}" + (bonusRate.etaSeconds?.let { " · ETA ${bonusRate.etaLabel}" } ?: "")
                } else ""
                BackgroundRunNotificationState(
                    isRunning = true,
                    allowBackground = allow,
                    title = "Bonus Finder · ${bonusProg.first}/${bonusProg.second} ($pct%)",
                    subtitle = "$bLoaded loaded · ${bWorkers}w$ratePart",
                    progressDone = bonusProg.first,
                    progressTotal = bonusProg.second
                )
            }
            else -> BackgroundRunNotificationState(
                isRunning = false,
                allowBackground = allow
            )
        }
    }

    fun emitMessage(msg: String) {
        _snackbarEvents.tryEmit(msg)
    }

    fun setAllowBackgroundRuns(enabled: Boolean) {
        allowBackgroundState = enabled
        syncForegroundService(forceAllowValue = enabled)
        scope.launch {
            repository.setAllowBackgroundRuns(enabled)
            syncForegroundService(forceAllowValue = enabled)
            if (enabled) {
                _snackbarEvents.tryEmit("Background runs ON — scans will keep running if you minimize or lock your phone.")
            } else {
                _snackbarEvents.tryEmit("Background runs OFF — scans pause and save checkpoints if you close the app.")
            }
        }
    }

    fun refreshOutgoingIp() {
        egressJob?.cancel()
        egressJob = scope.launch {
            _egressState.update { it.copy(isLoading = true, error = null) }
            val result = repository.engine.lookupEgressInfo()
            result.onSuccess { info ->
                _egressState.value = CoordinatorEgressState(isLoading = false, info = info, error = null)
            }.onFailure { err ->
                _egressState.update { prev ->
                    prev.copy(
                        isLoading = false,
                        error = err.message ?: "Unable to detect outgoing IP"
                    )
                }
            }
        }
    }

    // --- Account Fetcher Control ---
    fun setShowRejectedSites(show: Boolean) {
        _showRejected.value = show
    }

    fun resumeCheckpoint() {
        val cp = checkpointFlow.value
        if (cp == null || cp.remainingDomains.isEmpty()) {
            _snackbarEvents.tryEmit("No paused checkpoint to resume.")
            return
        }
        startFetch(cp.remainingDomains, resumeCheckpoint = cp)
    }

    fun discardCheckpoint() {
        scope.launch {
            repository.clearCheckpoint()
            if (!_fetcherRunning.value) {
                _liveStatusLabel.value = "Idle"
                _fetcherStatus.value = "Checkpoint cleared. Ready for a new fetch."
            }
            _snackbarEvents.tryEmit("Saved checkpoint discarded.")
        }
    }

    fun startFetch(
        domainsToFetch: List<String>,
        resumeCheckpoint: FetchCheckpoint? = null
    ) {
        val creds = credentialsFlow.value.validPairs()
        if (creds.isEmpty()) {
            _fetcherStatus.value = "Enter your phone number and password before fetching."
            return
        }

        val initialAllTargets: List<String>
        val initialDispatch: List<String>
        if (resumeCheckpoint != null) {
            initialAllTargets = resumeCheckpoint.allTargetDomains.distinct()
            initialDispatch = resumeCheckpoint.remainingDomains
                .map { DomainUtil.normalizeDomain(it) }
                .filter { DomainUtil.isPublicDomain(it) }
                .distinct()
        } else {
            val normalized = domainsToFetch
                .map { DomainUtil.normalizeDomain(it) }
                .filter { DomainUtil.isPublicDomain(it) }
                .distinct()
            initialAllTargets = normalized
            initialDispatch = normalized
        }

        if (initialDispatch.isEmpty()) {
            _fetcherStatus.value = "No sites selected. Bookmark accounts, enter a domain, or run a full scan."
            return
        }

        fetchJob?.cancel()
        fetchWallTimerJob?.cancel()
        fetchJob = scope.launch {
            val prefs = preferencesFlow.value
            val preset = prefs.fetchSpeedPreset
            var skippedDeadCount = 0
            val allTargetDomains: List<String>
            val dispatchDomains: List<String>
            if (resumeCheckpoint == null && prefs.skipKnownDead && initialDispatch.size > 1) {
                val healthMap = try {
                    repository.loadDomainHealthMap()
                } catch (_: Throwable) {
                    emptyMap()
                }
                val (filtered, skipped) = ScanTelemetryUtil.filterKnownDeadDomains(
                    domains = initialDispatch,
                    healthMap = healthMap,
                    skipKnownDead = true
                )
                skippedDeadCount = skipped
                if (filtered.isEmpty()) {
                    _fetcherStatus.value = "All ${initialDispatch.size} selected domains are marked dead in Domain Intel. Disable 'Skip known dead' or re-check Intel."
                    _snackbarEvents.tryEmit("All selected domains are marked dead in Domain Intel.")
                    return@launch
                }
                allTargetDomains = filtered
                dispatchDomains = filtered
            } else {
                allTargetDomains = initialAllTargets
                dispatchDomains = initialDispatch
            }

            val runStartedAt = resumeCheckpoint?.runStartedAt ?: System.currentTimeMillis()
            val segmentStartedAt = System.currentTimeMillis()
            val baseElapsedWallSec = resumeCheckpoint?.elapsedWallSec ?: 0L
            val segmentCompletedCounter = AtomicInteger(0)
            val collectedMap = LinkedHashMap<String, AccountResult>(allTargetDomains.size)
            val pendingSnapshots = LinkedHashMap<String, AccountResult>()
            val pendingSessionsMutex = Mutex()
            val pendingSessions = LinkedHashMap<String, SavedDomainSession>()
            var lastSessionFlushMs = System.currentTimeMillis()
            val pendingExpiredMutex = Mutex()
            val pendingExpiredTokens = LinkedHashSet<String>()
            var lastExpiredFlushMs = System.currentTimeMillis()
            val pendingHostsMutex = Mutex()
            val pendingHosts = LinkedHashMap<String, HostTarget>()
            var lastHostFlushMs = System.currentTimeMillis()
            var activeRunId: Long? = resumeCheckpoint?.partialRunId

            suspend fun flushPendingHosts(force: Boolean) {
                val batch: List<HostTarget> = pendingHostsMutex.withLock {
                    if (pendingHosts.isEmpty()) return
                    val now = System.currentTimeMillis()
                    if (!force && pendingHosts.size < 20 && (now - lastHostFlushMs) < 4000L) {
                        return
                    }
                    val copy = pendingHosts.values.toList()
                    pendingHosts.clear()
                    lastHostFlushMs = now
                    copy
                }
                try {
                    repository.saveHostTargetsBatch(batch)
                } catch (_: Throwable) {
                }
            }

            suspend fun flushPendingSessions(force: Boolean) {
                val batch: Map<String, SavedDomainSession> = pendingSessionsMutex.withLock {
                    if (pendingSessions.isEmpty()) return
                    val now = System.currentTimeMillis()
                    if (!force && pendingSessions.size < 20 && (now - lastSessionFlushMs) < 4000L) {
                        return
                    }
                    val copy = LinkedHashMap(pendingSessions)
                    pendingSessions.clear()
                    lastSessionFlushMs = now
                    copy
                }
                try {
                    repository.saveDomainSessionsBatch(batch)
                } catch (_: Throwable) {
                    // Best effort — next flush or future login can refresh
                }
            }

            suspend fun flushPendingExpiredTokens(force: Boolean) {
                val batch: List<String> = pendingExpiredMutex.withLock {
                    if (pendingExpiredTokens.isEmpty()) return
                    val now = System.currentTimeMillis()
                    if (!force && pendingExpiredTokens.size < 20 && (now - lastExpiredFlushMs) < 4000L) {
                        return
                    }
                    val copy = pendingExpiredTokens.toList()
                    pendingExpiredTokens.clear()
                    lastExpiredFlushMs = now
                    copy
                }
                try {
                    repository.clearDomainTokensBatch(batch)
                } catch (_: Throwable) {
                }
            }

            suspend fun flushPendingSnapshotsToRoom(forceAllIfNew: Boolean = false): Long? {
                val delta = if (activeRunId == null && forceAllIfNew) {
                    collectedMap.values.toList()
                } else {
                    pendingSnapshots.values.toList()
                }
                if (delta.isEmpty() && activeRunId != null && !forceAllIfNew) {
                    return activeRunId
                }
                if (collectedMap.isEmpty() && delta.isEmpty()) {
                    return activeRunId
                }
                return try {
                    val savedId = repository.persistRunProgress(
                        existingRunId = activeRunId,
                        startedAt = runStartedAt,
                        targetCount = allTargetDomains.size,
                        allRows = collectedMap.values,
                        deltaRows = delta
                    )
                    activeRunId = savedId
                    pendingSnapshots.clear()
                    savedId
                } catch (_: Throwable) {
                    activeRunId
                }
            }

            _fetcherRunning.value = true
            _activeFetchLane.value = FetchActiveLane.IDLE
            _fetcherRateSnapshot.value = ScanRateSnapshot()
            _viewingRunId.value = null
            _cooldownSeconds.value = 0
            _cooldownReason.value = ""
            _sessionReusedCount.value = 0
            _fetcherDiffs.value = emptyList()
            _showRejected.value = false
            _wallElapsedSec.value = baseElapsedWallSec
            val skippedDeadSuffix = if (skippedDeadCount > 0) " (skipped $skippedDeadCount dead)" else ""
            _liveStatusLabel.value = if (resumeCheckpoint != null) "Loading checkpoint…" else "Running (${preset.displayName})…"
            _fetcherStatus.value = when {
                resumeCheckpoint != null ->
                    "Loading saved progress and resuming ${dispatchDomains.size} remaining sites (${preset.displayName})…"
                dispatchDomains.size == 1 ->
                    "Checking ${dispatchDomains.first()}…"
                else ->
                    "${preset.displayName} mode checking ${dispatchDomains.size} sites$skippedDeadSuffix (${preset.subtitle})…"
            }
            syncForegroundService()

            try {
                val previousOkMap = latestAccountsFlow.value
                val hostCache = try {
                    repository.loadHostCacheMap()
                } catch (_: Throwable) {
                    emptyMap()
                }
                val savedSessions = try {
                    repository.loadDomainSessionsMap()
                } catch (_: Throwable) {
                    emptyMap()
                }

                if (resumeCheckpoint != null) {
                    val priorRows = hydrateCheckpointRows(resumeCheckpoint)
                    val dispatchSet = dispatchDomains.toSet()
                    val targetSet = allTargetDomains.toSet()
                    for (row in priorRows) {
                        if (row.domain in targetSet && row.domain !in dispatchSet) {
                            collectedMap[row.domain] = row
                        }
                    }
                    // Also merge any in-memory rows not yet in Room
                    for (row in _fetcherRows.value) {
                        if (row.domain in targetSet && row.domain !in dispatchSet && !collectedMap.containsKey(row.domain)) {
                            collectedMap[row.domain] = row
                        }
                    }
                } else if (allTargetDomains.size > 1) {
                    repository.clearCheckpoint()
                }

                _fetcherProgress.value = collectedMap.size to allTargetDomains.size
                _fetcherRows.value = collectedMap.values.toList()
                _liveStatusLabel.value = "Running (${preset.displayName})…"
                _fetcherStatus.value = when {
                    resumeCheckpoint != null ->
                        "Resuming ${dispatchDomains.size} remaining sites (${preset.displayName}: ${preset.subtitle})…"
                    dispatchDomains.size == 1 ->
                        "Checking ${dispatchDomains.first()}…"
                    else ->
                        "${preset.displayName} mode checking ${dispatchDomains.size} sites$skippedDeadSuffix (${preset.subtitle})…"
                }
                syncForegroundService()

                fetchWallTimerJob = scope.launch {
                    while (isActive) {
                        delay(1000L)
                        val elapsedMs = System.currentTimeMillis() - segmentStartedAt
                        val segSec = elapsedMs / 1000L
                        _wallElapsedSec.value = baseElapsedWallSec + segSec
                        val segDone = segmentCompletedCounter.get()
                        val remaining = (dispatchDomains.size - segDone).coerceAtLeast(0)
                        _fetcherRateSnapshot.value = ScanTelemetryUtil.computeThroughputAndEta(
                            completedInSegment = segDone,
                            remainingCount = remaining,
                            segmentElapsedMs = elapsedMs
                        )
                    }
                }

                var lastProgressPush = 0L
                var lastRowsPush = 0L
                var sinceLastCheckpoint = 0
                var sinceLastRoomFlush = 0

                val report = repository.engine.fetchAccountsBatch(
                    domains = dispatchDomains,
                    credentials = creds,
                    hostCache = hostCache,
                    preset = preset,
                    savedSessions = savedSessions,
                    onHostDiscovered = { target ->
                        pendingHostsMutex.withLock {
                            pendingHosts[target.domain] = target
                        }
                        flushPendingHosts(force = false)
                    },
                    onSessionSaved = { session ->
                        val key = session.domain.trim().lowercase()
                        if (key.isNotBlank()) {
                            pendingSessionsMutex.withLock {
                                pendingSessions[key] = session
                            }
                            flushPendingSessions(force = false)
                        }
                    },
                    onSessionExpired = { dom ->
                        val key = dom.trim().lowercase()
                        if (key.isNotBlank()) {
                            pendingExpiredMutex.withLock {
                                pendingExpiredTokens.add(key)
                            }
                            flushPendingExpiredTokens(force = false)
                        }
                    },
                    onSessionReused = { _sessionReusedCount.update { it + 1 } },
                    onCooldownChanged = { remSec, reason ->
                        _cooldownSeconds.value = remSec
                        _cooldownReason.value = reason
                        if (remSec > 0) {
                            _liveStatusLabel.value = "Cooling down (${remSec}s)…"
                            _fetcherStatus.value = "${reason.ifBlank { "Rate-limit cooldown" }} — auto-resuming in ${remSec}s…"
                        } else if (_fetcherRunning.value) {
                            _liveStatusLabel.value = "Running (${preset.displayName})…"
                        }
                    },
                    onLaneChanged = { lane ->
                        _activeFetchLane.value = lane
                    },
                    onResult = { res ->
                        collectedMap[res.domain] = res
                        pendingSnapshots[res.domain] = res
                        val segDone = segmentCompletedCounter.incrementAndGet()
                        sinceLastCheckpoint++
                        sinceLastRoomFlush++
                        val now = System.currentTimeMillis()
                        val doneCount = collectedMap.size
                        val isComplete = doneCount >= allTargetDomains.size

                        // Lightweight progress counters stay silky-smooth at 150ms
                        if (now - lastProgressPush >= 150L || isComplete) {
                            lastProgressPush = now
                            _fetcherProgress.value = doneCount to allTargetDomains.size
                            _fetcherRateSnapshot.value = ScanTelemetryUtil.computeThroughputAndEta(
                                completedInSegment = segDone,
                                remainingCount = (dispatchDomains.size - segDone).coerceAtLeast(0),
                                segmentElapsedMs = now - segmentStartedAt
                            )
                        }

                        // Decouple heavy full-list UI copies (750ms during big scans; instant for small runs)
                        val rowIntervalMs = if (doneCount <= 25) 150L else 750L
                        if (now - lastRowsPush >= rowIntervalMs || isComplete) {
                            lastRowsPush = now
                            _fetcherRows.value = collectedMap.values.toList()
                        }

                        if (allTargetDomains.size > 25 && sinceLastCheckpoint >= 15) {
                            sinceLastCheckpoint = 0
                            if (sinceLastRoomFlush >= 40) {
                                sinceLastRoomFlush = 0
                                flushPendingSnapshotsToRoom(forceAllIfNew = false)
                                flushPendingSessions(force = false)
                                flushPendingExpiredTokens(force = false)
                                flushPendingHosts(force = false)
                            }
                            val doneDomains = collectedMap.keys.toList()
                            val remaining = allTargetDomains.filter { !collectedMap.containsKey(it) }
                            if (remaining.isNotEmpty()) {
                                val elapsedSec = baseElapsedWallSec + (now - segmentStartedAt) / 1000L
                                try {
                                    repository.saveCheckpoint(
                                        FetchCheckpoint(
                                            runStartedAt = runStartedAt,
                                            allTargetDomains = allTargetDomains,
                                            completedDomains = doneDomains,
                                            remainingDomains = remaining,
                                            pauseReason = CheckpointPauseReason.INTERRUPTED,
                                            pausedAtIp = _egressState.value.info?.ip,
                                            pauseDetail = "In-progress checkpoint",
                                            elapsedWallSec = elapsedSec,
                                            partialRunId = activeRunId,
                                            savedAt = now
                                        )
                                    )
                                } catch (_: Throwable) {
                                }
                            }
                        }
                    }
                )

                val totalWallMs = (baseElapsedWallSec * 1000L) + (System.currentTimeMillis() - segmentStartedAt)
                val totalWallSec = (totalWallMs + 500L) / 1000L
                _wallElapsedSec.value = totalWallSec
                flushPendingExpiredTokens(force = true)
                flushPendingSessions(force = true)
                flushPendingHosts(force = true)

                if (report.wasCircuitBroken) {
                    val trippedSet = report.trippedDomains.toSet()
                    val finalPartialRows = collectedMap.values.toList()
                    val partialRunId = flushPendingSnapshotsToRoom(forceAllIfNew = activeRunId == null)

                    val completedClean = collectedMap.keys.filter { it !in trippedSet }
                    val unstarted = dispatchDomains.filter { !collectedMap.containsKey(it) }
                    val remaining = (unstarted + report.trippedDomains).distinct()

                    val checkpoint = FetchCheckpoint(
                        runStartedAt = runStartedAt,
                        allTargetDomains = allTargetDomains,
                        completedDomains = completedClean,
                        remainingDomains = remaining,
                        pauseReason = CheckpointPauseReason.WAF_BLOCKED,
                        pausedAtIp = _egressState.value.info?.ip,
                        pauseDetail = report.pauseDetail.ifBlank { "Burst of firewall / rate-limit blocks detected" },
                        elapsedWallSec = totalWallSec,
                        partialRunId = partialRunId,
                        savedAt = System.currentTimeMillis()
                    )
                    repository.saveCheckpoint(checkpoint)

                    _viewingRunId.value = partialRunId
                    _fetcherRows.value = finalPartialRows
                    _fetcherProgress.value = completedClean.size to allTargetDomains.size
                    _liveStatusLabel.value = "Paused (WAF)"
                    _fetcherStatus.value = "Auto-paused to protect your IP (${report.pauseDetail}). ${remaining.size} sites queued to resume."
                    _snackbarEvents.tryEmit("Auto-paused: rate-limit / firewall block detected. Wait or switch network before resuming.")
                    if (allowBackgroundState) {
                        VaultBackgroundRunService.postRunSummaryNotification(
                            context = appContext,
                            notificationId = VaultBackgroundRunService.ALERT_NOTIFICATION_ID_FETCH,
                            title = "Account Scan Auto-Paused (WAF)",
                            body = "${remaining.size} sites remaining — tap to review and resume safely."
                        )
                    }
                } else {
                    val finalRows = collectedMap.values.toList()
                    repository.clearCheckpoint()
                    val runId = flushPendingSnapshotsToRoom(forceAllIfNew = activeRunId == null)
                    _viewingRunId.value = runId
                    _fetcherRows.value = finalRows
                    _fetcherProgress.value = finalRows.size to allTargetDomains.size

                    val diffs = buildList {
                        for (r in finalRows) {
                            if (r is AccountResult.Ok) {
                                val prev = previousOkMap[r.domain]
                                if (prev != null && abs(prev.cash - r.cash) >= 0.01) {
                                    add("${r.domain}: ${FormatUtil.money(prev.cash)} → ${FormatUtil.money(r.cash)}")
                                }
                            }
                        }
                    }
                    _fetcherDiffs.value = diffs
                    val okRows = finalRows.filterIsInstance<AccountResult.Ok>()
                    val okCount = okRows.size
                    val totalCash = okRows.sumOf { it.cash }
                    val elapsedSec = totalWallMs / 1000.0
                    _liveStatusLabel.value = "Done"
                    val reusedNote = if (report.sessionsReused > 0) {
                        " · ${report.sessionsReused} saved sessions reused"
                    } else ""
                    _fetcherStatus.value = String.format(
                        Locale.US,
                        "Completed in %.1fs · %d of %d signed in%s · saved to vault.",
                        elapsedSec,
                        okCount,
                        finalRows.size,
                        reusedNote
                    )
                    if (allowBackgroundState && allTargetDomains.size > 1) {
                        VaultBackgroundRunService.postRunSummaryNotification(
                            context = appContext,
                            notificationId = VaultBackgroundRunService.ALERT_NOTIFICATION_ID_FETCH,
                            title = "Account Scan Complete",
                            body = "$okCount of ${finalRows.size} signed in · ${FormatUtil.money(totalCash)} total cash."
                        )
                    }
                }
            } catch (_: CancellationException) {
                withContext(NonCancellable) {
                    val totalWallMs = (baseElapsedWallSec * 1000L) + (System.currentTimeMillis() - segmentStartedAt)
                    val totalWallSec = (totalWallMs + 500L) / 1000L
                    _wallElapsedSec.value = totalWallSec
                    flushPendingExpiredTokens(force = true)
                    flushPendingSessions(force = true)
                    flushPendingHosts(force = true)
                    val partial = collectedMap.values.toList()
                    val partialRunId = flushPendingSnapshotsToRoom(forceAllIfNew = activeRunId == null)
                    if (partial.isNotEmpty()) {
                        _fetcherRows.value = partial
                        _fetcherProgress.value = partial.size to allTargetDomains.size
                    }
                    val remaining = allTargetDomains.filter { !collectedMap.containsKey(it) }
                    if (remaining.isNotEmpty() && allTargetDomains.size > 1) {
                        try {
                            repository.saveCheckpoint(
                                FetchCheckpoint(
                                    runStartedAt = runStartedAt,
                                    allTargetDomains = allTargetDomains,
                                    completedDomains = collectedMap.keys.toList(),
                                    remainingDomains = remaining,
                                    pauseReason = CheckpointPauseReason.USER_STOPPED,
                                    pausedAtIp = _egressState.value.info?.ip,
                                    pauseDetail = "Paused by you",
                                    elapsedWallSec = totalWallSec,
                                    partialRunId = partialRunId,
                                    savedAt = System.currentTimeMillis()
                                )
                            )
                        } catch (_: Throwable) {
                        }
                        _liveStatusLabel.value = "Paused"
                        _fetcherStatus.value = "Paused. ${partial.size} of ${allTargetDomains.size} saved · ${remaining.size} remaining."
                    } else {
                        _liveStatusLabel.value = "Stopped"
                        _fetcherStatus.value = "Stopped. ${partial.count { it is AccountResult.Ok }} completed hits were saved."
                    }
                }
            } catch (t: Throwable) {
                withContext(NonCancellable) {
                    flushPendingExpiredTokens(force = true)
                    flushPendingSessions(force = true)
                    flushPendingHosts(force = true)
                    val partialRunId = flushPendingSnapshotsToRoom(forceAllIfNew = activeRunId == null)
                    val remaining = allTargetDomains.filter { !collectedMap.containsKey(it) }
                    if (remaining.isNotEmpty() && allTargetDomains.size > 1) {
                        try {
                            val totalWallSec = baseElapsedWallSec + (System.currentTimeMillis() - segmentStartedAt) / 1000L
                            repository.saveCheckpoint(
                                FetchCheckpoint(
                                    runStartedAt = runStartedAt,
                                    allTargetDomains = allTargetDomains,
                                    completedDomains = collectedMap.keys.toList(),
                                    remainingDomains = remaining,
                                    pauseReason = CheckpointPauseReason.INTERRUPTED,
                                    pausedAtIp = _egressState.value.info?.ip,
                                    pauseDetail = "Recovered from error: ${t.message ?: t.javaClass.simpleName}",
                                    elapsedWallSec = totalWallSec,
                                    partialRunId = partialRunId,
                                    savedAt = System.currentTimeMillis()
                                )
                            )
                        } catch (_: Throwable) {
                        }
                    }
                    _liveStatusLabel.value = "Paused"
                    val reason = t.message?.take(120) ?: t.javaClass.simpleName
                    _fetcherStatus.value = "Fetch interrupted safely ($reason). Progress saved — tap Resume to continue."
                    _snackbarEvents.tryEmit("Fetch paused safely: $reason")
                }
            } finally {
                fetchWallTimerJob?.cancel()
                fetchWallTimerJob = null
                _activeFetchLane.value = FetchActiveLane.IDLE
                _fetcherRateSnapshot.value = ScanRateSnapshot()
                _cooldownSeconds.value = 0
                _cooldownReason.value = ""
                _fetcherRunning.value = false
                syncForegroundService()
            }
        }
    }

    fun stopFetch() {
        fetchJob?.cancel()
    }

    /**
     * Persists a one-time WebView Captcha Sign-In session, immediately syncs the account's
     * balances and lit bonuses via /users/syncData, and merges the fresh result into the live UI and Room.
     */
    fun completeCaptchaSignIn(
        rawDomain: String,
        accessId: String,
        accessToken: String,
        host: String = "",
        merchantId: String = ""
    ) {
        val domain = DomainUtil.normalizeDomain(rawDomain)
        if (!DomainUtil.isPublicDomain(domain)) return
        scope.launch {
            _snackbarEvents.tryEmit("Session captured for $domain — syncing account…")
            val outcome = repository.syncWithCapturedSession(
                rawDomain = domain,
                accessId = accessId,
                accessToken = accessToken,
                host = host,
                merchantId = merchantId
            )
            when (val res = outcome.result) {
                is AccountResult.Ok -> {
                    val now = System.currentTimeMillis()
                    repository.persistFetchRun(now, 1, listOf(res))
                    _fetcherRows.update { existing ->
                        val filtered = existing.filterNot { it.domain == domain }
                        listOf(res) + filtered
                    }
                    _snackbarEvents.tryEmit("Saved session & synced $domain — future runs will fetch automatically!")
                }
                is AccountResult.Err -> {
                    _snackbarEvents.tryEmit("Captured $domain login, but sync returned: ${res.message}")
                }
            }
        }
    }

    fun loadHistoricalRun(runId: Long, runSummary: FetchRunSummary?) {
        scope.launch {
            val rows = repository.loadRunResults(runId)
            _fetcherRows.value = rows
            _fetcherProgress.value = rows.size to (runSummary?.targetCount ?: rows.size)
            _wallElapsedSec.value = if (runSummary != null && runSummary.completedAt > runSummary.startedAt) {
                (runSummary.completedAt - runSummary.startedAt + 500L) / 1000L
            } else {
                0L
            }
            _liveStatusLabel.value = "Saved run"
            _viewingRunId.value = runId
            _fetcherDiffs.value = emptyList()
            _fetcherStatus.value = "Viewing saved run (${rows.count { it is AccountResult.Ok }} of ${rows.size} signed in)."
        }
    }

    // --- Bonus Finder Control ---
    fun resumeBonusCheckpoint() {
        val cp = bonusCheckpointFlow.value
        if (cp == null || cp.remainingDomains.isEmpty()) {
            _snackbarEvents.tryEmit("No paused bonus scan checkpoint to continue.")
            return
        }
        startBonusScan(cp.remainingDomains, resumeCheckpoint = cp)
    }

    fun discardBonusCheckpoint() {
        scope.launch {
            repository.clearBonusCheckpoint()
            if (!_bonusRunning.value) {
                _bonusLiveStatusLabel.value = "Idle"
                _bonusStatus.value = "Saved bonus scan checkpoint cleared."
            }
            _snackbarEvents.tryEmit("Bonus scan checkpoint discarded.")
        }
    }

    fun startBonusScan(
        domainsToScan: List<String>,
        resumeCheckpoint: FetchCheckpoint? = null
    ) {
        val initialAllTargets: List<String>
        val initialDispatch: List<String>
        if (resumeCheckpoint != null) {
            initialAllTargets = resumeCheckpoint.allTargetDomains.distinct()
            initialDispatch = resumeCheckpoint.remainingDomains
                .map { DomainUtil.normalizeDomain(it) }
                .filter { DomainUtil.isPublicDomain(it) }
                .distinct()
        } else {
            val normalized = domainsToScan
                .map { DomainUtil.normalizeDomain(it) }
                .filter { DomainUtil.isPublicDomain(it) }
                .distinct()
            initialAllTargets = normalized
            initialDispatch = normalized
        }

        if (initialDispatch.isEmpty()) {
            _bonusStatus.value = "No domains to scan."
            return
        }

        bonusScanJob?.cancel()
        bonusWallTimerJob?.cancel()
        bonusScanJob = scope.launch {
            val prefs = preferencesFlow.value
            val preset: FetchSpeedPreset = prefs.bonusSpeedPreset
            var skippedDeadCount = 0
            val allTargetDomains: List<String>
            val dispatchDomains: List<String>
            if (resumeCheckpoint == null && prefs.skipKnownDead && initialDispatch.size > 1) {
                val healthMap = try {
                    repository.loadDomainHealthMap()
                } catch (_: Throwable) {
                    emptyMap()
                }
                val (filtered, skipped) = ScanTelemetryUtil.filterKnownDeadDomains(
                    domains = initialDispatch,
                    healthMap = healthMap,
                    skipKnownDead = true
                )
                skippedDeadCount = skipped
                if (filtered.isEmpty()) {
                    _bonusStatus.value = "All ${initialDispatch.size} selected domains are marked dead in Domain Intel. Disable 'Skip known dead' or re-check Intel."
                    _snackbarEvents.tryEmit("All selected domains are marked dead in Domain Intel.")
                    return@launch
                }
                allTargetDomains = filtered
                dispatchDomains = filtered
            } else {
                allTargetDomains = initialAllTargets
                dispatchDomains = initialDispatch
            }

            val runStartedAt = resumeCheckpoint?.runStartedAt ?: System.currentTimeMillis()
            val segmentStartedAt = System.currentTimeMillis()
            val baseElapsedWallSec = resumeCheckpoint?.elapsedWallSec ?: 0L
            val segmentCompletedCounter = AtomicInteger(0)
            val hostCache = repository.loadHostCacheMap()

            val completedSet = LinkedHashSet<String>()
            if (resumeCheckpoint != null) {
                completedSet.addAll(resumeCheckpoint.completedDomains)
            } else if (allTargetDomains.size > 1) {
                repository.clearBonusCheckpoint()
            }

            var runLoaded = 0
            var runFailed = 0
            var totalLatencyMs = 0L
            var totalRetries = 0
            var totalAttempts = 0

            _bonusRunning.value = true
            _bonusRateSnapshot.value = ScanRateSnapshot()
            _bonusProgress.value = completedSet.size to allTargetDomains.size
            _bonusWallElapsedSec.value = baseElapsedWallSec
            if (resumeCheckpoint == null) {
                _bonusScanLatencyMs.value = 0L
                _bonusRetries.value = 0
                _bonusAttempts.value = 0
                _bonusRunLoaded.value = 0
                _bonusRunFailed.value = 0
            }
            val skippedDeadSuffix = if (skippedDeadCount > 0) " (skipped $skippedDeadCount dead)" else ""
            _bonusLiveStatusLabel.value = "Running (${preset.displayName})…"
            _bonusStatus.value = when {
                resumeCheckpoint != null ->
                    "Continuing ${dispatchDomains.size} remaining domains (${preset.displayName}: ${preset.bonusSubtitle})…"
                dispatchDomains.size == 1 ->
                    "Loading public bonuses for ${dispatchDomains.first()}…"
                else ->
                    "${preset.displayName} mode scanning ${dispatchDomains.size} domains$skippedDeadSuffix (${preset.bonusSubtitle})…"
            }
            syncForegroundService()

            bonusWallTimerJob = scope.launch {
                while (isActive) {
                    delay(1000L)
                    val elapsedMs = System.currentTimeMillis() - segmentStartedAt
                    val segSec = elapsedMs / 1000L
                    _bonusWallElapsedSec.value = baseElapsedWallSec + segSec
                    val segDone = segmentCompletedCounter.get()
                    val remaining = (dispatchDomains.size - segDone).coerceAtLeast(0)
                    _bonusRateSnapshot.value = ScanTelemetryUtil.computeThroughputAndEta(
                        completedInSegment = segDone,
                        remainingCount = remaining,
                        segmentElapsedMs = elapsedMs
                    )
                }
            }

            val bonusFlushMutex = Mutex()
            val pendingBonusResults = LinkedHashMap<String, PublicCatalogueResult>()
            val pendingBonusHosts = LinkedHashMap<String, HostTarget>()
            var lastBonusRoomFlushMs = System.currentTimeMillis()

            suspend fun flushPendingBonusRoomWrites(force: Boolean) {
                val (bonusBatch, hostBatch) = bonusFlushMutex.withLock {
                    val now = System.currentTimeMillis()
                    val shouldFlush = force ||
                        pendingBonusResults.size >= 20 ||
                        pendingBonusHosts.size >= 20 ||
                        ((pendingBonusResults.isNotEmpty() || pendingBonusHosts.isNotEmpty()) && (now - lastBonusRoomFlushMs) >= 1500L)
                    if (!shouldFlush) return
                    val bCopy = pendingBonusResults.values.toList()
                    val hCopy = pendingBonusHosts.values.toList()
                    pendingBonusResults.clear()
                    pendingBonusHosts.clear()
                    lastBonusRoomFlushMs = now
                    bCopy to hCopy
                }
                try {
                    if (bonusBatch.isNotEmpty()) {
                        repository.savePublicBonusResultsBatch(bonusBatch)
                    }
                    if (hostBatch.isNotEmpty()) {
                        repository.saveHostTargetsBatch(hostBatch)
                    }
                } catch (_: Throwable) {
                }
            }

            var lastUiPush = 0L
            var sinceLastCheckpoint = 0

            _bonusCooldownSeconds.value = 0
            try {
                val report = repository.engine.scanPublicBonusesBatch(
                    domains = dispatchDomains,
                    hostCache = hostCache,
                    preset = preset,
                    onHostDiscovered = { target ->
                        bonusFlushMutex.withLock {
                            pendingBonusHosts[target.domain] = target
                        }
                        flushPendingBonusRoomWrites(force = false)
                    },
                    onCooldownChanged = { remSec, reason ->
                        _bonusCooldownSeconds.value = remSec
                        if (remSec > 0) {
                            _bonusLiveStatusLabel.value = "Cooling down (${remSec}s)…"
                            _bonusStatus.value = "${reason.ifBlank { "Rate-limit cooldown" }} — auto-resuming in ${remSec}s…"
                        } else if (_bonusRunning.value) {
                            _bonusLiveStatusLabel.value = "Running (${preset.displayName})…"
                        }
                    },
                    onResult = { res ->
                        bonusFlushMutex.withLock {
                            pendingBonusResults[res.domain] = res
                        }
                        completedSet.add(res.domain)
                        val segDone = segmentCompletedCounter.incrementAndGet()
                        if (res.ok) runLoaded++ else runFailed++
                        totalLatencyMs += res.latencyMs
                        totalRetries += res.retriesUsed
                        totalAttempts += res.attemptsUsed
                        sinceLastCheckpoint++

                        val isComplete = completedSet.size >= allTargetDomains.size
                        flushPendingBonusRoomWrites(force = isComplete || allTargetDomains.size <= 5)

                        val now = System.currentTimeMillis()
                        if (now - lastUiPush >= 120L || isComplete) {
                            lastUiPush = now
                            _bonusProgress.value = completedSet.size to allTargetDomains.size
                            _bonusRunLoaded.value = runLoaded
                            _bonusRunFailed.value = runFailed
                            _bonusScanLatencyMs.value = totalLatencyMs
                            _bonusRetries.value = totalRetries
                            _bonusAttempts.value = totalAttempts
                            _bonusRateSnapshot.value = ScanTelemetryUtil.computeThroughputAndEta(
                                completedInSegment = segDone,
                                remainingCount = (dispatchDomains.size - segDone).coerceAtLeast(0),
                                segmentElapsedMs = now - segmentStartedAt
                            )
                        }

                        // Periodic durable checkpoint during larger bonus scans (>25 domains)
                        if (allTargetDomains.size > 25 && sinceLastCheckpoint >= 20) {
                            sinceLastCheckpoint = 0
                            flushPendingBonusRoomWrites(force = true)
                            val remaining = allTargetDomains.filter { it !in completedSet }
                            if (remaining.isNotEmpty()) {
                                val elapsedSec = baseElapsedWallSec + (now - segmentStartedAt) / 1000L
                                repository.saveBonusCheckpoint(
                                    FetchCheckpoint(
                                        runStartedAt = runStartedAt,
                                        allTargetDomains = allTargetDomains,
                                        completedDomains = completedSet.toList(),
                                        remainingDomains = remaining,
                                        pauseReason = CheckpointPauseReason.INTERRUPTED,
                                        pausedAtIp = _egressState.value.info?.ip,
                                        pauseDetail = "In-progress bonus scan checkpoint",
                                        elapsedWallSec = elapsedSec,
                                        savedAt = now
                                    )
                                )
                            }
                        }
                    }
                )

                flushPendingBonusRoomWrites(force = true)

                val totalWallMs = (baseElapsedWallSec * 1000L) + (System.currentTimeMillis() - segmentStartedAt)
                val totalWallSec = (totalWallMs + 500L) / 1000L
                _bonusWallElapsedSec.value = totalWallSec
                _bonusRunLoaded.value = runLoaded
                _bonusRunFailed.value = runFailed
                _bonusScanLatencyMs.value = totalLatencyMs
                _bonusRetries.value = totalRetries
                _bonusAttempts.value = totalAttempts

                if (report.wasCircuitBroken) {
                    val trippedSet = report.trippedDomains.toSet()
                    val completedClean = completedSet.filter { it !in trippedSet }
                    val unstarted = dispatchDomains.filter { it !in completedSet }
                    val remaining = (unstarted + report.trippedDomains).distinct()

                    repository.saveBonusCheckpoint(
                        FetchCheckpoint(
                            runStartedAt = runStartedAt,
                            allTargetDomains = allTargetDomains,
                            completedDomains = completedClean,
                            remainingDomains = remaining,
                            pauseReason = CheckpointPauseReason.WAF_BLOCKED,
                            pausedAtIp = _egressState.value.info?.ip,
                            pauseDetail = report.pauseDetail.ifBlank { "Burst of firewall / rate-limit blocks detected" },
                            elapsedWallSec = totalWallSec,
                            savedAt = System.currentTimeMillis()
                        )
                    )

                    _bonusProgress.value = completedClean.size to allTargetDomains.size
                    _bonusLiveStatusLabel.value = "Paused (WAF)"
                    _bonusStatus.value = "Auto-paused (${report.pauseDetail}). ${remaining.size} domains queued to continue."
                    _snackbarEvents.tryEmit("Bonus scan auto-paused on firewall / rate-limit blocks.")
                    if (allowBackgroundState) {
                        VaultBackgroundRunService.postRunSummaryNotification(
                            context = appContext,
                            notificationId = VaultBackgroundRunService.ALERT_NOTIFICATION_ID_BONUS,
                            title = "Bonus Scan Auto-Paused (WAF)",
                            body = "${remaining.size} domains remaining — tap to resume."
                        )
                    }
                } else {
                    if (allTargetDomains.size > 1 || resumeCheckpoint != null) {
                        repository.clearBonusCheckpoint()
                    }
                    _bonusProgress.value = completedSet.size to allTargetDomains.size
                    _bonusLiveStatusLabel.value = "Done"
                    val elapsedSec = totalWallMs / 1000.0
                    _bonusStatus.value = String.format(
                        Locale.US,
                        "Completed in %.1fs · %d loaded · %d failed · no password sent.",
                        elapsedSec,
                        runLoaded,
                        runFailed
                    )
                    if (allowBackgroundState && allTargetDomains.size > 1) {
                        VaultBackgroundRunService.postRunSummaryNotification(
                            context = appContext,
                            notificationId = VaultBackgroundRunService.ALERT_NOTIFICATION_ID_BONUS,
                            title = "Bonus Scan Complete",
                            body = "$runLoaded loaded · $runFailed failed across ${completedSet.size} domains."
                        )
                    }
                }
            } catch (_: CancellationException) {
                withContext(NonCancellable) {
                    flushPendingBonusRoomWrites(force = true)
                    val totalWallMs = (baseElapsedWallSec * 1000L) + (System.currentTimeMillis() - segmentStartedAt)
                    val totalWallSec = (totalWallMs + 500L) / 1000L
                    _bonusWallElapsedSec.value = totalWallSec
                    _bonusRunLoaded.value = runLoaded
                    _bonusRunFailed.value = runFailed
                    _bonusScanLatencyMs.value = totalLatencyMs
                    _bonusRetries.value = totalRetries
                    _bonusAttempts.value = totalAttempts
                    _bonusProgress.value = completedSet.size to allTargetDomains.size

                    val remaining = allTargetDomains.filter { it !in completedSet }
                    if (remaining.isNotEmpty() && allTargetDomains.size > 1) {
                        repository.saveBonusCheckpoint(
                            FetchCheckpoint(
                                runStartedAt = runStartedAt,
                                allTargetDomains = allTargetDomains,
                                completedDomains = completedSet.toList(),
                                remainingDomains = remaining,
                                pauseReason = CheckpointPauseReason.USER_STOPPED,
                                pausedAtIp = _egressState.value.info?.ip,
                                pauseDetail = "Paused by you",
                                elapsedWallSec = totalWallSec,
                                savedAt = System.currentTimeMillis()
                            )
                        )
                        _bonusLiveStatusLabel.value = "Paused"
                        _bonusStatus.value = "Paused. ${completedSet.size} of ${allTargetDomains.size} saved · ${remaining.size} remaining."
                    } else {
                        _bonusLiveStatusLabel.value = "Stopped"
                        _bonusStatus.value = "Stopped. $runLoaded loaded · $runFailed failed."
                    }
                }
            } catch (t: Throwable) {
                withContext(NonCancellable) {
                    flushPendingBonusRoomWrites(force = true)
                    val remaining = allTargetDomains.filter { it !in completedSet }
                    if (remaining.isNotEmpty() && allTargetDomains.size > 1) {
                        try {
                            val totalWallSec = baseElapsedWallSec + (System.currentTimeMillis() - segmentStartedAt) / 1000L
                            repository.saveBonusCheckpoint(
                                FetchCheckpoint(
                                    runStartedAt = runStartedAt,
                                    allTargetDomains = allTargetDomains,
                                    completedDomains = completedSet.toList(),
                                    remainingDomains = remaining,
                                    pauseReason = CheckpointPauseReason.INTERRUPTED,
                                    pausedAtIp = _egressState.value.info?.ip,
                                    pauseDetail = "Recovered from error: ${t.message ?: t.javaClass.simpleName}",
                                    elapsedWallSec = totalWallSec,
                                    savedAt = System.currentTimeMillis()
                                )
                            )
                        } catch (_: Throwable) {
                        }
                    }
                    _bonusLiveStatusLabel.value = "Paused"
                    val reason = t.message?.take(120) ?: t.javaClass.simpleName
                    _bonusStatus.value = "Bonus scan paused safely ($reason). Progress saved."
                    _snackbarEvents.tryEmit("Bonus scan paused safely: $reason")
                }
            } finally {
                bonusWallTimerJob?.cancel()
                bonusWallTimerJob = null
                _bonusRateSnapshot.value = ScanRateSnapshot()
                _bonusCooldownSeconds.value = 0
                _bonusRunning.value = false
                syncForegroundService()
            }
        }
    }

    fun stopBonusScan() {
        bonusScanJob?.cancel()
    }

    fun stopAllRuns() {
        fetchJob?.cancel()
        bonusScanJob?.cancel()
    }

    fun stopAllRunsForAppClose() {
        if (!allowBackgroundState) {
            fetchJob?.cancel()
            bonusScanJob?.cancel()
        }
    }

    fun resetAfterClearAll() {
        fetchJob?.cancel()
        fetchWallTimerJob?.cancel()
        bonusScanJob?.cancel()
        bonusWallTimerJob?.cancel()
        _fetcherRows.value = emptyList()
        _fetcherProgress.value = 0 to 0
        _wallElapsedSec.value = 0L
        _liveStatusLabel.value = "Idle"
        _cooldownSeconds.value = 0
        _cooldownReason.value = ""
        _sessionReusedCount.value = 0
        _activeFetchLane.value = FetchActiveLane.IDLE
        _fetcherRateSnapshot.value = ScanRateSnapshot()
        _fetcherDiffs.value = emptyList()
        _bonusProgress.value = 0 to 0
        _bonusWallElapsedSec.value = 0L
        _bonusScanLatencyMs.value = 0L
        _bonusRetries.value = 0
        _bonusAttempts.value = 0
        _bonusRunLoaded.value = 0
        _bonusRunFailed.value = 0
        _bonusCooldownSeconds.value = 0
        _bonusRateSnapshot.value = ScanRateSnapshot()
        _bonusLiveStatusLabel.value = "Idle"
        _bonusStatus.value = "Scan public promotions across your domain list without signing in."
        syncForegroundService(forceAllowValue = false)
    }

    fun reloadLatestRunRows() {
        scope.launch {
            val latestRows = repository.loadLatestRunResults()
            if (latestRows.isNotEmpty()) {
                _fetcherRows.value = latestRows
            }
        }
    }

    private fun syncForegroundService(forceAllowValue: Boolean? = null) {
        val allow = forceAllowValue ?: allowBackgroundState
        val active = _fetcherRunning.value || _bonusRunning.value
        val intent = Intent(appContext, VaultBackgroundRunService::class.java)
        try {
            if (allow && active) {
                intent.action = VaultBackgroundRunService.ACTION_START_OR_SYNC
                ContextCompat.startForegroundService(appContext, intent)
            } else {
                appContext.stopService(intent)
            }
        } catch (_: Exception) {
            // Best effort if OS restricts FGS transition from background state
        }
    }
}
