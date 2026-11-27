package com.aistudio.pokiesvault.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.pokiesvault.PokiesVaultApp
import com.aistudio.pokiesvault.data.model.AccountResult
import com.aistudio.pokiesvault.data.model.BonusAmountSort
import com.aistudio.pokiesvault.data.model.BonusMode
import com.aistudio.pokiesvault.data.model.BonusStatusFilter
import com.aistudio.pokiesvault.data.model.CardColor
import com.aistudio.pokiesvault.data.model.DomainHealthRecord
import com.aistudio.pokiesvault.data.model.EgressIpInfo
import com.aistudio.pokiesvault.data.model.FallbackCred
import com.aistudio.pokiesvault.data.model.FetchCheckpoint
import com.aistudio.pokiesvault.data.model.FetchRunSummary
import com.aistudio.pokiesvault.data.model.FetchSpeedPreset
import com.aistudio.pokiesvault.data.model.PublicBonus
import com.aistudio.pokiesvault.data.model.PublicBonusKind
import com.aistudio.pokiesvault.data.model.PublicBonusSiteCache
import com.aistudio.pokiesvault.data.model.PublicBonusSlot
import com.aistudio.pokiesvault.data.model.SavedDomainSession
import com.aistudio.pokiesvault.data.model.SiteGroup
import com.aistudio.pokiesvault.data.model.SiteRecord
import com.aistudio.pokiesvault.data.model.SiteStatus
import com.aistudio.pokiesvault.data.model.SortMode
import com.aistudio.pokiesvault.data.model.VaultCredentials
import com.aistudio.pokiesvault.data.model.VaultPreferences
import com.aistudio.pokiesvault.data.model.VaultThemeId
import com.aistudio.pokiesvault.data.model.ViewMode
import com.aistudio.pokiesvault.util.DomainUtil
import com.aistudio.pokiesvault.util.FormatUtil
import com.aistudio.pokiesvault.util.SiteOrderUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

data class EgressUiState(
    val isLoading: Boolean = true,
    val info: EgressIpInfo? = null,
    val error: String? = null
)

data class DirectorySiteItem(
    val rank: Int,
    val site: SiteRecord,
    val relativeAge: String,
    val status: SiteStatus,
    val latencyMs: Long,
    val account: AccountResult.Ok?,
    val errorRow: AccountResult.Err? = null,
    val sameCasinoCount: Int = 0,
    val hasSignupBonus: Boolean = false,
    val hasReferlink: Boolean = false,
    val popularityScore: Double = 0.0,
    val walletText: String? = null,
    val bonusText: String? = null
)

data class DirectoryUiState(
    val preferences: VaultPreferences = VaultPreferences(),
    val searchQuery: String = "",
    val items: List<DirectorySiteItem> = emptyList(),
    val groupedItems: Map<SiteGroup, List<DirectorySiteItem>> = emptyMap(),
    val isGroupedMode: Boolean = true,
    val totalSitesCount: Int = 0,
    val visitedCount: Int = 0,
    val mineCount: Int = 0,
    val totalCachedCash: Double = 0.0,
    val isLoading: Boolean = true
)

data class RejectedReasonGroup(
    val code: String,
    val label: String,
    val rows: List<AccountResult.Err>,
    val isRetryable: Boolean
)

data class FetcherUiState(
    val credentials: VaultCredentials = VaultCredentials(),
    val speedPreset: FetchSpeedPreset = FetchSpeedPreset.SAFE,
    val activeCheckpoint: FetchCheckpoint? = null,
    val isFetching: Boolean = false,
    val progressDone: Int = 0,
    val progressTotal: Int = 0,
    val activeWorkers: Int = 0,
    val peakWorkers: Int = 0,
    val workerLimit: Int = 6,
    val allowBackgroundRuns: Boolean = false,
    val liveStatusLabel: String = "Idle",
    val wallElapsedSec: Long = 0L,
    val signInElapsedSec: Long = 0L,
    val totalRetries: Int = 0,
    val totalAttempts: Int = 0,
    val statusMessage: String = "Enter your phone and password to fetch wallets and bonuses safely.",
    val validAccounts: List<AccountResult.Ok> = emptyList(),
    val emptyAccounts: List<AccountResult.Ok> = emptyList(),
    val validItems: List<DirectorySiteItem> = emptyList(),
    val emptyItems: List<DirectorySiteItem> = emptyList(),
    val rejectedAccounts: List<AccountResult.Err> = emptyList(),
    val rejectedGroups: List<RejectedReasonGroup> = emptyList(),
    val retryableDomains: List<String> = emptyList(),
    val totalCash: Double = 0.0,
    val rawSignedInCount: Int = 0,
    val rawRejectedCount: Int = 0,
    val rawWithBalanceCount: Int = 0,
    val rawNothingWaitingCount: Int = 0,
    val sortMode: SortMode = SortMode.DEFAULT,
    val bonusMode: BonusMode = BonusMode.NONE,
    val cardColor: CardColor = CardColor.CLASSIC,
    val walletsMode: Boolean = true,
    val notVisitedFilter: Boolean = false,
    val signupOnlyFilter: Boolean = false,
    val mineOnlyFilter: Boolean = false,
    val fetchDiffs: List<String> = emptyList(),
    val showRejectedSites: Boolean = false,
    val mineTargetDomains: List<String> = emptyList(),
    val allTargetDomains: List<String> = emptyList(),
    val fetchRuns: List<FetchRunSummary> = emptyList(),
    val viewingRunId: Long? = null,
    val cooldownSeconds: Int = 0,
    val cooldownReason: String = "",
    val sessionReusedCount: Int = 0,
    val savedSessionDomains: Set<String> = emptySet()
)

data class BonusFinderItem(
    val domain: String,
    val rank: Int,
    val cache: PublicBonusSiteCache?,
    val depositBest: PublicBonus?,
    val signupBest: PublicBonus?,
    val shareBest: PublicBonus?,
    val dailyBest: PublicBonus?,
    val birthdayBest: PublicBonus?
)

data class BonusFinderUiState(
    val searchQuery: String = "",
    val statusFilter: BonusStatusFilter = BonusStatusFilter.ALL,
    val selectedSlots: Set<PublicBonusSlot> = emptySet(),
    val listOrderSlot: PublicBonusSlot? = null,
    val amountSort: BonusAmountSort = BonusAmountSort.DOMAIN_AZ,
    val speedPreset: FetchSpeedPreset = FetchSpeedPreset.SAFE,
    val activeCheckpoint: FetchCheckpoint? = null,
    val items: List<BonusFinderItem> = emptyList(),
    val totalDomains: Int = 0,
    val loadedCount: Int = 0,
    val loadedTotalCount: Int = 0,
    val failedTotalCount: Int = 0,
    val unloadedTotalCount: Int = 0,
    val withOffersCount: Int = 0,
    val failedDomains: List<String> = emptyList(),
    val unloadedDomains: List<String> = emptyList(),
    val allTargetDomains: List<String> = emptyList(),
    val isScanning: Boolean = false,
    val progressDone: Int = 0,
    val progressTotal: Int = 0,
    val activeWorkers: Int = 0,
    val peakWorkers: Int = 0,
    val workerLimit: Int = 6,
    val allowBackgroundRuns: Boolean = false,
    val liveStatusLabel: String = "Idle",
    val wallElapsedSec: Long = 0L,
    val scanElapsedSec: Long = 0L,
    val totalRetries: Int = 0,
    val totalAttempts: Int = 0,
    val runLoadedCount: Int = 0,
    val runFailedCount: Int = 0,
    val statusMessage: String = "Scan public promotions across your domain list without signing in."
)

data class IntelDomainRow(
    val site: SiteRecord,
    val status: SiteStatus,
    val latencyMs: Long,
    val checkedAt: Long,
    val isSelected: Boolean
)

data class IntelUiState(
    val filterQuery: String = "",
    val statusFilter: SiteStatus? = null,
    val rows: List<IntelDomainRow> = emptyList(),
    val selectedDomains: Set<String> = emptySet(),
    val totalCount: Int = 0,
    val upCount: Int = 0,
    val slowCount: Int = 0,
    val redirectCount: Int = 0,
    val downCount: Int = 0,
    val isChecking: Boolean = false,
    val isFetchingLatestList: Boolean = false,
    val progressDone: Int = 0,
    val progressTotal: Int = 0,
    val statusMessage: String = "Unified list shared across Directory, Fetcher, and Bonus Finder."
)

data class VaultGlobalUiState(
    val themeId: VaultThemeId = VaultThemeId.VAULT,
    val allowBackgroundRuns: Boolean = false,
    val hasActiveBackgroundRun: Boolean = false,
    val savedAccountsCount: Int = 0,
    val hiddenDomainsCount: Int = 0,
    val isAddDomainOpen: Boolean = false,
    val isSortFilterOpen: Boolean = false,
    val isNotVisitedAuthAlertOpen: Boolean = false,
    val isConfirmScanOpen: Boolean = false,
    val isFetchHistoryOpen: Boolean = false,
    val selectedDetailDomain: String? = null
)

@OptIn(FlowPreview::class)
class VaultViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PokiesVaultApp
    private val repository = app.repository
    private val coordinator = app.coordinator

    val snackbarEvents: SharedFlow<String> = coordinator.snackbarEvents

    val egressState: StateFlow<EgressUiState> = coordinator.egressState
        .map { EgressUiState(isLoading = it.isLoading, info = it.info, error = it.error) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, EgressUiState())

    // --- Shared Dialog & Modal State ---
    private val _modalState = MutableStateFlow(VaultGlobalUiState())

    private val _captchaSignInDomain = MutableStateFlow<String?>(null)
    val captchaSignInDomain: StateFlow<String?> = _captchaSignInDomain.asStateFlow()

    // --- Raw Input Flows ---
    private val _directorySearch = MutableStateFlow("")
    private val _bonusSearch = MutableStateFlow("")
    private val _bonusStatusFilter = MutableStateFlow(BonusStatusFilter.ALL)
    private val _bonusSelectedSlots = MutableStateFlow<Set<PublicBonusSlot>>(emptySet())
    private val _bonusListOrderSlot = MutableStateFlow<PublicBonusSlot?>(null)
    private val _bonusAmountSort = MutableStateFlow(BonusAmountSort.DOMAIN_AZ)
    private val _intelSearch = MutableStateFlow("")
    private val _intelStatusFilter = MutableStateFlow<SiteStatus?>(null)
    private val _intelSelectedDomains = MutableStateFlow<Set<String>>(emptySet())

    // --- Local Ephemeral State ---
    private val _checkingDomains = MutableStateFlow<Set<String>>(emptySet())
    private val _intelRunning = MutableStateFlow(false)
    private val _intelFetchingLatest = MutableStateFlow(false)
    private val _intelProgress = MutableStateFlow(0 to 0)
    private val _intelStatus = MutableStateFlow("Unified list shared across Directory, Fetcher, and Bonus Finder.")

    private var intelCheckJob: Job? = null
    private var intelLatestFetchJob: Job? = null
    private var credSaveJob: Job? = null

    private val inFlightProbeMutex = Mutex()
    private val inFlightProbes = mutableSetOf<String>()
    private val statusTtlMs = 15L * 60L * 1000L // 15 minutes cache TTL

    private val preferencesFlow = coordinator.preferencesFlow
    private val credentialsFlow = coordinator.credentialsFlow
    private val checkpointFlow = coordinator.checkpointFlow
    private val bonusCheckpointFlow = coordinator.bonusCheckpointFlow

    private val visibleSitesFlow = repository.visibleSitesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val healthMapFlow = repository.domainHealthFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val latestAccountsFlow = repository.latestAccountsByDomainFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val latestResultsFlow = repository.latestResultsByDomainFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val sameCasinoCountsFlow = repository.sameCasinoCountsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val domainSessionsFlow = repository.domainSessionsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val globalState: StateFlow<VaultGlobalUiState> = combine(
        preferencesFlow,
        visibleSitesFlow,
        repository.hiddenCountFlow,
        coordinator.hasActiveRun,
        _modalState
    ) { prefs, sites, hiddenCount, activeRun, modals ->
        modals.copy(
            themeId = prefs.themeId,
            allowBackgroundRuns = prefs.allowBackgroundRuns,
            hasActiveBackgroundRun = prefs.allowBackgroundRuns && activeRun,
            savedAccountsCount = sites.count { it.isMine },
            hiddenDomainsCount = hiddenCount
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VaultGlobalUiState())

    // --- Module 1: Directory State ---
    val directoryState: StateFlow<DirectoryUiState> = combine(
        preferencesFlow,
        visibleSitesFlow,
        healthMapFlow,
        latestAccountsFlow,
        latestResultsFlow,
        sameCasinoCountsFlow,
        _checkingDomains,
        _directorySearch.debounce(120)
    ) { arr ->
        val prefs = arr[0] as VaultPreferences
        @Suppress("UNCHECKED_CAST")
        val sites = arr[1] as List<SiteRecord>
        @Suppress("UNCHECKED_CAST")
        val healthMap = arr[2] as Map<String, DomainHealthRecord>
        @Suppress("UNCHECKED_CAST")
        val accountsMap = arr[3] as Map<String, AccountResult.Ok>
        @Suppress("UNCHECKED_CAST")
        val resultsMap = arr[4] as Map<String, AccountResult>
        @Suppress("UNCHECKED_CAST")
        val sameCasinoMap = arr[5] as Map<String, Int>
        @Suppress("UNCHECKED_CAST")
        val checking = arr[6] as Set<String>
        val query = (arr[7] as String).trim()

        val catalogIndexByDomain = HashMap<String, Int>(sites.size)
        sites.forEachIndexed { idx, s -> catalogIndexByDomain[s.domain] = idx }

        val filtered = sites.filter { site ->
            val ok = accountsMap[site.domain]
            if (prefs.mineOnly && !site.isMine) return@filter false
            if (prefs.notVisited && site.isVisited) return@filter false
            if (prefs.signupOnly && !SiteOrderUtil.isLitBonus(ok?.newMember)) return@filter false
            if (query.isNotEmpty() && !DomainUtil.matchesSearch(site.domain, query)) return@filter false
            true
        }

        val entries = filtered.map { site ->
            val health = healthMap[site.domain]
            val status = when {
                checking.contains(site.domain) -> SiteStatus.CHECKING
                health != null -> health.status
                else -> SiteStatus.UNKNOWN
            }
            val latestRes: AccountResult? = resultsMap[site.domain] ?: accountsMap[site.domain]
            val sortable = SiteOrderUtil.SortableEntry(
                domain = site.domain,
                group = site.group,
                addedAt = site.addedAt,
                catalogIndex = catalogIndexByDomain[site.domain] ?: 0,
                result = latestRes,
                isDead = status == SiteStatus.DEAD
            )
            Triple(site, status, sortable)
        }

        val sorted = entries.sortedWith { a, b ->
            SiteOrderUtil.compareEntries(a.third, b.third, prefs.sortMode, prefs.bonusMode)
        }

        val uiItems = sorted.mapIndexed { idx, (site, status, sortable) ->
            val health = healthMap[site.domain]
            val acc = (sortable.result as? AccountResult.Ok) ?: accountsMap[site.domain]
            val err = sortable.result as? AccountResult.Err
            val walletText = if (prefs.wallets && acc != null) {
                "${FormatUtil.money(SiteOrderUtil.effectiveCash(acc))} · ${acc.cashWalletName}"
            } else null
            val chosen = if (prefs.bonusMode != BonusMode.NONE) {
                acc?.bonusFor(prefs.bonusMode)
            } else null
            val bonusText = chosen?.let { "${it.name} ${it.amountDisplay}" }

            DirectorySiteItem(
                rank = idx + 1,
                site = site,
                relativeAge = FormatUtil.timeAgo(site.addedAt),
                status = status,
                latencyMs = health?.latencyMs ?: 0L,
                account = acc,
                errorRow = err,
                sameCasinoCount = sameCasinoMap[site.domain] ?: 0,
                hasSignupBonus = SiteOrderUtil.isLitBonus(acc?.newMember),
                hasReferlink = SiteOrderUtil.hasReferlink(acc),
                popularityScore = SiteOrderUtil.popularityScore(site.group, sortable.catalogIndex),
                walletText = walletText,
                bonusText = bonusText
            )
        }

        val isGrouped = prefs.viewMode == ViewMode.GROUPS &&
            query.isEmpty() &&
            !prefs.mineOnly &&
            !prefs.notVisited &&
            !prefs.signupOnly &&
            prefs.sortMode == SortMode.DEFAULT

        val grouped = if (isGrouped) {
            val orderedGroups = listOf(
                SiteGroup.PROMOTED,
                SiteGroup.CUSTOM,
                SiteGroup.NEW,
                SiteGroup.BEST,
                SiteGroup.SHARE,
                SiteGroup.UNVERIFIED,
                SiteGroup.LATEST,
                SiteGroup.CONFIRMED_SCAMS
            )
            LinkedHashMap<SiteGroup, List<DirectorySiteItem>>().apply {
                for (g in orderedGroups) {
                    val bucket = uiItems.filter { it.site.group == g }
                    if (bucket.isNotEmpty()) put(g, bucket)
                }
            }
        } else {
            emptyMap()
        }

        DirectoryUiState(
            preferences = prefs,
            searchQuery = _directorySearch.value,
            items = uiItems,
            groupedItems = grouped,
            isGroupedMode = isGrouped,
            totalSitesCount = uiItems.size,
            visitedCount = uiItems.count { it.site.isVisited },
            mineCount = sites.count { it.isMine },
            totalCachedCash = accountsMap.values.sumOf { SiteOrderUtil.effectiveCash(it) },
            isLoading = false
        )
    }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DirectoryUiState())

    val directorySearchInput: StateFlow<String> = _directorySearch.asStateFlow()

    // --- Module 2: Account Fetcher State (Backed by Coordinator) ---
    val fetcherState: StateFlow<FetcherUiState> = combine(
        credentialsFlow,
        visibleSitesFlow,
        healthMapFlow,
        sameCasinoCountsFlow,
        coordinator.fetcherRows,
        coordinator.fetcherRunning,
        coordinator.fetcherProgress,
        coordinator.fetcherStatus,
        coordinator.fetcherDiffs,
        coordinator.showRejected,
        repository.fetchRunsFlow,
        repository.engine.activeWorkers,
        repository.engine.peakWorkers,
        repository.engine.currentWorkerLimit,
        coordinator.liveStatusLabel,
        coordinator.wallElapsedSec,
        coordinator.viewingRunId,
        preferencesFlow,
        checkpointFlow,
        coordinator.cooldownSeconds,
        coordinator.cooldownReason,
        coordinator.sessionReusedCount,
        domainSessionsFlow
    ) { arr ->
        val creds = arr[0] as VaultCredentials
        @Suppress("UNCHECKED_CAST")
        val sites = arr[1] as List<SiteRecord>
        @Suppress("UNCHECKED_CAST")
        val healthMap = arr[2] as Map<String, DomainHealthRecord>
        @Suppress("UNCHECKED_CAST")
        val sameCasinoMap = arr[3] as Map<String, Int>
        @Suppress("UNCHECKED_CAST")
        val rows = arr[4] as List<AccountResult>
        val running = arr[5] as Boolean
        @Suppress("UNCHECKED_CAST")
        val progress = arr[6] as Pair<Int, Int>
        val statusMsg = arr[7] as String
        @Suppress("UNCHECKED_CAST")
        val diffs = arr[8] as List<String>
        val showRej = arr[9] as Boolean
        @Suppress("UNCHECKED_CAST")
        val runs = arr[10] as List<FetchRunSummary>
        val activeW = arr[11] as Int
        val peakW = arr[12] as Int
        val limitW = arr[13] as Int
        val liveLabel = arr[14] as String
        val wallSec = arr[15] as Long
        val viewRunId = arr[16] as Long?
        val prefs = arr[17] as VaultPreferences
        val checkpoint = arr[18] as FetchCheckpoint?
        val cooldownSec = arr[19] as Int
        val cooldownReason = arr[20] as String
        val reusedCount = arr[21] as Int
        @Suppress("UNCHECKED_CAST")
        val savedSessionsMap = arr[22] as Map<String, SavedDomainSession>

        val siteByDomain = HashMap<String, SiteRecord>(sites.size)
        val catalogIndexByDomain = HashMap<String, Int>(sites.size)
        sites.forEachIndexed { idx, s ->
            siteByDomain[s.domain] = s
            catalogIndexByDomain[s.domain] = idx
        }

        val allOk = rows.filterIsInstance<AccountResult.Ok>()
        val allErr = rows.filterIsInstance<AccountResult.Err>()
        val rawSignedInCount = allOk.size
        val rawRejectedCount = allErr.size
        val rawWithBalanceCount = allOk.count { SiteOrderUtil.effectiveCash(it) > 0.001 }
        val rawNothingWaitingCount = allOk.count { !it.hasData }

        fun matchesFilters(domain: String, ok: AccountResult.Ok?): Boolean {
            val site = siteByDomain[domain]
            if (prefs.mineOnly && site?.isMine != true) return false
            if (prefs.notVisited && site?.isVisited == true) return false
            if (prefs.signupOnly && !SiteOrderUtil.isLitBonus(ok?.newMember)) return false
            return true
        }

        val sortableByDomain = HashMap<String, SiteOrderUtil.SortableEntry>(rows.size)
        for (res in rows) {
            val site = siteByDomain[res.domain]
            val health = healthMap[res.domain]
            sortableByDomain[res.domain] = SiteOrderUtil.SortableEntry(
                domain = res.domain,
                group = site?.group ?: SiteGroup.CUSTOM,
                addedAt = site?.addedAt ?: "",
                catalogIndex = catalogIndexByDomain[res.domain] ?: Int.MAX_VALUE,
                result = res,
                isDead = health?.status == SiteStatus.DEAD
            )
        }

        val effectiveSort = prefs.sortMode
        val valid = allOk
            .filter { it.hasData && matchesFilters(it.domain, it) }
            .sortedWith { a, b ->
                SiteOrderUtil.compareEntries(
                    sortableByDomain.getValue(a.domain),
                    sortableByDomain.getValue(b.domain),
                    effectiveSort,
                    prefs.bonusMode
                )
            }
        val empty = allOk
            .filter { !it.hasData && matchesFilters(it.domain, it) }
            .sortedWith { a, b ->
                SiteOrderUtil.compareEntries(
                    sortableByDomain.getValue(a.domain),
                    sortableByDomain.getValue(b.domain),
                    effectiveSort,
                    prefs.bonusMode
                )
            }
        val rejected = allErr.sortedWith { a, b ->
            SiteOrderUtil.compareEntries(
                sortableByDomain.getValue(a.domain),
                sortableByDomain.getValue(b.domain),
                effectiveSort,
                prefs.bonusMode
            )
        }

        fun buildFetcherItems(list: List<AccountResult.Ok>, startRank: Int): List<DirectorySiteItem> =
            list.mapIndexed { idx, ok ->
                val site = siteByDomain[ok.domain] ?: SiteRecord(
                    domain = ok.domain,
                    group = SiteGroup.CUSTOM,
                    addedAt = "",
                    isMine = true,
                    isVisited = false,
                    isHidden = false
                )
                val health = healthMap[ok.domain]
                val status = health?.status ?: SiteStatus.LIVE
                val chosen = if (prefs.bonusMode != BonusMode.NONE) ok.bonusFor(prefs.bonusMode) else null
                DirectorySiteItem(
                    rank = startRank + idx,
                    site = site,
                    relativeAge = FormatUtil.timeAgo(site.addedAt),
                    status = status,
                    latencyMs = ok.latencyMs.takeIf { it > 0L } ?: (health?.latencyMs ?: 0L),
                    account = ok,
                    errorRow = null,
                    sameCasinoCount = sameCasinoMap[ok.domain] ?: 0,
                    hasSignupBonus = SiteOrderUtil.isLitBonus(ok.newMember),
                    hasReferlink = SiteOrderUtil.hasReferlink(ok),
                    popularityScore = SiteOrderUtil.popularityScore(
                        site.group,
                        catalogIndexByDomain[ok.domain] ?: 0
                    ),
                    walletText = "${FormatUtil.money(SiteOrderUtil.effectiveCash(ok))} · ${ok.cashWalletName}",
                    bonusText = chosen?.let { "${it.name} ${it.amountDisplay}" }
                )
            }

        val validItems = buildFetcherItems(valid, 1)
        val emptyItems = buildFetcherItems(empty, valid.size + 1)

        val knownCodes = setOf("blocked", "rate_limit", "timeout", "unreachable", "captcha", "invalid_login")
        val byCode = rejected.groupBy { err -> if (err.code in knownCodes) err.code else "other" }
        val groupSpecs = listOf(
            Triple("blocked", "Blocked / Firewall", true),
            Triple("rate_limit", "Rate Limited", true),
            Triple("timeout", "Timed Out", true),
            Triple("unreachable", "Unreachable", true),
            Triple("captcha", "Captcha", false),
            Triple("invalid_login", "Not Your Account", false),
            Triple("other", "Other", true)
        )
        val rejectedGroups = groupSpecs.mapNotNull { (code, label, retryableFlag) ->
            val groupRows = byCode[code].orEmpty()
            if (groupRows.isEmpty()) null else RejectedReasonGroup(code, label, groupRows, retryableFlag)
        }

        val retryable = rejected
            .filter { it.code in setOf("blocked", "rate_limit", "timeout", "unreachable", "other") }
            .map { it.domain }

        val mine = sites.filter { it.isMine }.map { it.domain }
        val visited = sites.filter { it.isVisited }.map { it.domain }
        val mineTargets = mine.ifEmpty { visited }

        val totalLatencyMs = rows.sumOf { it.latencyMs }
        val signInSec = (totalLatencyMs + 500L) / 1000L
        val totalRetries = rows.sumOf { it.retriesUsed }
        val totalAttempts = rows.sumOf { it.attemptsUsed }

        val effectiveWorkerLimit = if (running) limitW else prefs.fetchSpeedPreset.coldLoginProfile.maxWorkers

        FetcherUiState(
            credentials = creds,
            speedPreset = prefs.fetchSpeedPreset,
            activeCheckpoint = checkpoint?.takeIf { it.remainingDomains.isNotEmpty() },
            isFetching = running,
            progressDone = progress.first,
            progressTotal = progress.second,
            activeWorkers = activeW,
            peakWorkers = peakW,
            workerLimit = effectiveWorkerLimit,
            allowBackgroundRuns = prefs.allowBackgroundRuns,
            liveStatusLabel = liveLabel,
            wallElapsedSec = wallSec,
            signInElapsedSec = signInSec,
            totalRetries = totalRetries,
            totalAttempts = totalAttempts,
            statusMessage = statusMsg,
            validAccounts = valid,
            emptyAccounts = empty,
            validItems = validItems,
            emptyItems = emptyItems,
            rejectedAccounts = rejected,
            rejectedGroups = rejectedGroups,
            retryableDomains = retryable,
            totalCash = valid.sumOf { SiteOrderUtil.effectiveCash(it) },
            rawSignedInCount = rawSignedInCount,
            rawRejectedCount = rawRejectedCount,
            rawWithBalanceCount = rawWithBalanceCount,
            rawNothingWaitingCount = rawNothingWaitingCount,
            sortMode = prefs.sortMode,
            bonusMode = prefs.bonusMode,
            cardColor = prefs.cardColor,
            walletsMode = prefs.wallets,
            notVisitedFilter = prefs.notVisited,
            signupOnlyFilter = prefs.signupOnly,
            mineOnlyFilter = prefs.mineOnly,
            fetchDiffs = diffs,
            showRejectedSites = showRej,
            mineTargetDomains = mineTargets,
            allTargetDomains = sites.map { it.domain },
            fetchRuns = runs,
            viewingRunId = viewRunId,
            cooldownSeconds = cooldownSec,
            cooldownReason = cooldownReason,
            sessionReusedCount = reusedCount,
            savedSessionDomains = savedSessionsMap.filterValues { it.hasToken }.keys
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FetcherUiState())

    // --- Module 3: Bonus Finder State (Backed by Coordinator + Live Telemetry) ---
    val bonusFinderState: StateFlow<BonusFinderUiState> = combine(
        visibleSitesFlow,
        repository.publicBonusesFlow,
        _bonusSearch.debounce(120),
        _bonusStatusFilter,
        _bonusSelectedSlots,
        _bonusListOrderSlot,
        _bonusAmountSort,
        preferencesFlow,
        bonusCheckpointFlow,
        coordinator.bonusRunning,
        coordinator.bonusProgress,
        coordinator.bonusStatus,
        coordinator.bonusLiveStatusLabel,
        coordinator.bonusWallElapsedSec,
        coordinator.bonusScanLatencyMs,
        coordinator.bonusRetries,
        coordinator.bonusAttempts,
        coordinator.bonusRunLoaded,
        coordinator.bonusRunFailed,
        repository.engine.bonusActiveWorkers,
        repository.engine.bonusPeakWorkers,
        repository.engine.bonusCurrentWorkerLimit
    ) { arr ->
        @Suppress("UNCHECKED_CAST")
        val sites = arr[0] as List<SiteRecord>
        @Suppress("UNCHECKED_CAST")
        val cache = arr[1] as Map<String, PublicBonusSiteCache>
        val query = (arr[2] as String).trim().lowercase(Locale.US)
        val statusFilter = arr[3] as BonusStatusFilter
        @Suppress("UNCHECKED_CAST")
        val selectedSlots = arr[4] as Set<PublicBonusSlot>
        val listOrderSlot = arr[5] as PublicBonusSlot?
        val amountSort = arr[6] as BonusAmountSort
        val prefs = arr[7] as VaultPreferences
        val bonusCheckpoint = arr[8] as FetchCheckpoint?
        val running = arr[9] as Boolean
        @Suppress("UNCHECKED_CAST")
        val progress = arr[10] as Pair<Int, Int>
        val statusMsg = arr[11] as String
        val liveLabel = arr[12] as String
        val wallSec = arr[13] as Long
        val scanLatencyMs = arr[14] as Long
        val retries = arr[15] as Int
        val attempts = arr[16] as Int
        val runLoaded = arr[17] as Int
        val runFailed = arr[18] as Int
        val activeW = arr[19] as Int
        val peakW = arr[20] as Int
        val limitW = arr[21] as Int

        val domains = sites.map { it.domain }
        val loadedDomains = ArrayList<String>()
        val failedDomains = ArrayList<String>()
        val unloadedDomains = ArrayList<String>()
        var withOffersCount = 0

        for (d in domains) {
            val row = cache[d]
            when {
                row == null -> unloadedDomains.add(d)
                row.ok -> {
                    loadedDomains.add(d)
                    if (row.bonuses.isNotEmpty()) withOffersCount++
                }
                else -> failedDomains.add(d)
            }
        }

        // Apply status tab filter first
        val byStatus = when (statusFilter) {
            BonusStatusFilter.ALL -> domains
            BonusStatusFilter.LOADED -> loadedDomains
            BonusStatusFilter.FAILED -> failedDomains
            BonusStatusFilter.UNLOADED -> unloadedDomains
        }

        var filtered = byStatus.filter { domain ->
            if (query.isEmpty()) return@filter true
            if (domain.contains(query)) return@filter true
            val cached = cache[domain]
            cached?.bonuses?.any { it.name.lowercase(Locale.US).contains(query) } == true ||
                cached?.errorMessage?.lowercase(Locale.US)?.contains(query) == true
        }

        if (selectedSlots.isNotEmpty()) {
            filtered = filtered.filter { domain ->
                val row = cache[domain] ?: return@filter false
                row.ok && row.bonuses.any { it.slot != PublicBonusSlot.OTHER && selectedSlots.contains(it.slot) }
            }
        }

        val sorted = if (amountSort == BonusAmountSort.DOMAIN_AZ ||
            (amountSort != BonusAmountSort.DEPOSIT && listOrderSlot == null)
        ) {
            filtered.sorted()
        } else {
            val metricSlot = if (amountSort == BonusAmountSort.DEPOSIT) PublicBonusSlot.DEPOSIT else listOrderSlot!!
            val ascending = amountSort == BonusAmountSort.TURNOVER
            filtered.sortedWith { a, b ->
                val left = metricOf(cache[a]?.bonuses.orEmpty(), metricSlot, amountSort)
                val right = metricOf(cache[b]?.bonuses.orEmpty(), metricSlot, amountSort)
                when {
                    left == null && right == null -> a.compareTo(b)
                    left == null -> 1
                    right == null -> -1
                    else -> {
                        val cmp = if (ascending) left.compareTo(right) else right.compareTo(left)
                        if (cmp != 0) cmp else a.compareTo(b)
                    }
                }
            }
        }

        val items = sorted.mapIndexed { idx, domain ->
            val row = cache[domain]
            val bonuses = if (row?.ok == true) row.bonuses else emptyList()
            BonusFinderItem(
                domain = domain,
                rank = idx + 1,
                cache = row,
                depositBest = bestPublicBonus(bonuses, PublicBonusSlot.DEPOSIT),
                signupBest = bestPublicBonus(bonuses, PublicBonusSlot.SIGNUP),
                shareBest = bestPublicBonus(bonuses, PublicBonusSlot.SHARE),
                dailyBest = bestPublicBonus(bonuses, PublicBonusSlot.DAILY),
                birthdayBest = bestPublicBonus(bonuses, PublicBonusSlot.BIRTHDAY)
            )
        }

        val scanSec = (scanLatencyMs + 500L) / 1000L
        val effectiveWorkerLimit = if (running) limitW else prefs.bonusSpeedPreset.bonusScanProfile.maxWorkers

        BonusFinderUiState(
            searchQuery = _bonusSearch.value,
            statusFilter = statusFilter,
            selectedSlots = selectedSlots,
            listOrderSlot = listOrderSlot,
            amountSort = amountSort,
            speedPreset = prefs.bonusSpeedPreset,
            activeCheckpoint = bonusCheckpoint?.takeIf { it.remainingDomains.isNotEmpty() },
            items = items,
            totalDomains = domains.size,
            loadedCount = loadedDomains.size + failedDomains.size,
            loadedTotalCount = loadedDomains.size,
            failedTotalCount = failedDomains.size,
            unloadedTotalCount = unloadedDomains.size,
            withOffersCount = withOffersCount,
            failedDomains = failedDomains,
            unloadedDomains = unloadedDomains,
            allTargetDomains = domains,
            isScanning = running,
            progressDone = progress.first,
            progressTotal = progress.second,
            activeWorkers = activeW,
            peakWorkers = peakW,
            workerLimit = effectiveWorkerLimit,
            allowBackgroundRuns = prefs.allowBackgroundRuns,
            liveStatusLabel = liveLabel,
            wallElapsedSec = wallSec,
            scanElapsedSec = scanSec,
            totalRetries = retries,
            totalAttempts = attempts,
            runLoadedCount = runLoaded,
            runFailedCount = runFailed,
            statusMessage = statusMsg
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BonusFinderUiState())

    val bonusSearchInput: StateFlow<String> = _bonusSearch.asStateFlow()

    // --- Module 4: Domain Intel State ---
    val intelState: StateFlow<IntelUiState> = combine(
        visibleSitesFlow,
        healthMapFlow,
        _checkingDomains,
        _intelSearch.debounce(100),
        _intelStatusFilter,
        _intelSelectedDomains,
        _intelRunning,
        _intelFetchingLatest,
        _intelProgress,
        _intelStatus
    ) { arr ->
        @Suppress("UNCHECKED_CAST")
        val sites = arr[0] as List<SiteRecord>
        @Suppress("UNCHECKED_CAST")
        val healthMap = arr[1] as Map<String, DomainHealthRecord>
        @Suppress("UNCHECKED_CAST")
        val checking = arr[2] as Set<String>
        val query = (arr[3] as String).trim().lowercase(Locale.US)
        val statusFilter = arr[4] as SiteStatus?
        @Suppress("UNCHECKED_CAST")
        val selectedSet = arr[5] as Set<String>
        val running = arr[6] as Boolean
        val fetchingLatest = arr[7] as Boolean
        @Suppress("UNCHECKED_CAST")
        val progress = arr[8] as Pair<Int, Int>
        val statusMsg = arr[9] as String

        var up = 0
        var slow = 0
        var redirect = 0
        var down = 0

        val allRows = sites.map { site ->
            val h = healthMap[site.domain]
            val st = when {
                checking.contains(site.domain) -> SiteStatus.CHECKING
                h != null -> h.status
                else -> SiteStatus.UNKNOWN
            }
            when (st) {
                SiteStatus.LIVE -> up++
                SiteStatus.SLOW -> slow++
                SiteStatus.REDIRECT -> redirect++
                SiteStatus.DEAD -> down++
                else -> Unit
            }
            IntelDomainRow(
                site = site,
                status = st,
                latencyMs = h?.latencyMs ?: 0L,
                checkedAt = h?.checkedAt ?: 0L,
                isSelected = selectedSet.contains(site.domain)
            )
        }

        val filtered = allRows.filter { row ->
            if (query.isNotEmpty() && !row.site.domain.contains(query)) return@filter false
            if (statusFilter != null && row.status != statusFilter) return@filter false
            true
        }

        IntelUiState(
            filterQuery = _intelSearch.value,
            statusFilter = statusFilter,
            rows = filtered,
            selectedDomains = selectedSet,
            totalCount = sites.size,
            upCount = up,
            slowCount = slow,
            redirectCount = redirect,
            downCount = down,
            isChecking = running,
            isFetchingLatestList = fetchingLatest,
            progressDone = progress.first,
            progressTotal = progress.second,
            statusMessage = statusMsg
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), IntelUiState())

    val intelSearchInput: StateFlow<String> = _intelSearch.asStateFlow()

    override fun onCleared() {
        super.onCleared()
        // If Background Runs is OFF, cleanly stop active runs (saving checkpoints) when the app is dismissed.
        // If Background Runs is ON, leave coordinator + Foreground Service running uninterrupted.
        coordinator.stopAllRunsForAppClose()
    }

    fun refreshOutgoingIp() = coordinator.refreshOutgoingIp()

    fun setAllowBackgroundRuns(enabled: Boolean) = coordinator.setAllowBackgroundRuns(enabled)

    // --- Modal & Detail Actions ---
    fun setAddDomainOpen(open: Boolean) = _modalState.update { it.copy(isAddDomainOpen = open) }
    fun setSortFilterOpen(open: Boolean) = _modalState.update { it.copy(isSortFilterOpen = open) }
    fun setNotVisitedAuthAlertOpen(open: Boolean) = _modalState.update { it.copy(isNotVisitedAuthAlertOpen = open) }
    fun setConfirmScanOpen(open: Boolean) = _modalState.update { it.copy(isConfirmScanOpen = open) }
    fun setFetchHistoryOpen(open: Boolean) = _modalState.update { it.copy(isFetchHistoryOpen = open) }
    fun openSiteDetail(domain: String?) = _modalState.update { it.copy(selectedDetailDomain = domain) }

    fun getDetailForDomain(domain: String): DirectorySiteItem? {
        return directoryState.value.items.firstOrNull { it.site.domain == domain }
            ?: fetcherState.value.validItems.firstOrNull { it.site.domain == domain }
            ?: fetcherState.value.emptyItems.firstOrNull { it.site.domain == domain }
    }

    fun hasSavedCredentials(): Boolean = credentialsFlow.value.validPairs().isNotEmpty()

    // --- Theme & Preferences ---
    fun selectTheme(themeId: VaultThemeId) {
        viewModelScope.launch { repository.setTheme(themeId) }
    }

    fun onDirectorySearchChanged(query: String) {
        _directorySearch.value = query
    }

    fun toggleNotVisited() {
        val turningOn = !preferencesFlow.value.notVisited
        if (turningOn && !hasSavedCredentials()) {
            setNotVisitedAuthAlertOpen(true)
            return
        }
        viewModelScope.launch { repository.setNotVisited(turningOn) }
    }

    fun toggleWallets() {
        viewModelScope.launch { repository.setWallets(!preferencesFlow.value.wallets) }
    }

    fun toggleMineOnly() {
        viewModelScope.launch { repository.setMineOnly(!preferencesFlow.value.mineOnly) }
    }

    fun cycleViewMode() {
        val next = when (preferencesFlow.value.viewMode) {
            ViewMode.GROUPS -> ViewMode.ONE
            ViewMode.ONE -> ViewMode.TWO
            ViewMode.TWO -> ViewMode.GROUPS
        }
        viewModelScope.launch { repository.setViewMode(next) }
    }

    fun setSortMode(mode: SortMode) {
        viewModelScope.launch { repository.setSortMode(mode) }
    }

    fun setBonusMode(mode: BonusMode) {
        viewModelScope.launch { repository.setBonusMode(mode) }
    }

    fun setCardColor(color: CardColor) {
        viewModelScope.launch { repository.setCardColor(color) }
    }

    fun applySortFilter(
        sortMode: SortMode,
        bonusMode: BonusMode,
        notVisited: Boolean,
        signupOnly: Boolean,
        mineOnly: Boolean
    ) {
        val allowedNotVisited = if (notVisited && !hasSavedCredentials()) {
            setNotVisitedAuthAlertOpen(true)
            false
        } else {
            notVisited
        }
        viewModelScope.launch {
            repository.applySortFilter(
                sortMode = sortMode,
                bonusMode = bonusMode,
                notVisited = allowedNotVisited,
                signupOnly = signupOnly,
                mineOnly = mineOnly
            )
        }
        setSortFilterOpen(false)
    }

    fun resetSortFilter() {
        viewModelScope.launch { repository.resetSortFilter() }
    }

    fun toggleMine(domain: String) {
        val currentlyMine = visibleSitesFlow.value.firstOrNull { it.domain == domain }?.isMine == true
        viewModelScope.launch { repository.toggleMine(domain, currentlyMine) }
    }

    fun markVisited(domain: String) {
        viewModelScope.launch { repository.markVisited(domain) }
    }

    fun addSingleDomain(raw: String, markAsMine: Boolean = true, onResult: (String?) -> Unit = {}) {
        val normalized = DomainUtil.normalizeDomain(raw)
        if (!DomainUtil.isPublicDomain(normalized)) {
            onResult("Enter a valid domain like 9au.com")
            return
        }
        viewModelScope.launch {
            repository.addDomains(listOf(normalized), markAsMine = markAsMine)
            onResult(null)
            coordinator.emitMessage("Added $normalized to your vault.")
        }
    }

    // --- Bounded TTL Status Probe for Directory ---
    fun enqueueStatusProbeIfStale(domain: String) {
        val cached = healthMapFlow.value[domain]
        val now = System.currentTimeMillis()
        if (cached != null && (now - cached.checkedAt) < statusTtlMs) return

        viewModelScope.launch {
            val acquired = inFlightProbeMutex.withLock {
                if (inFlightProbes.contains(domain)) false else {
                    inFlightProbes.add(domain)
                    true
                }
            }
            if (!acquired) return@launch
            _checkingDomains.update { it + domain }
            try {
                val record = repository.engine.probeSingleBounded(domain)
                repository.saveDomainHealth(record)
            } finally {
                _checkingDomains.update { it - domain }
                inFlightProbeMutex.withLock { inFlightProbes.remove(domain) }
            }
        }
    }

    // --- Credentials ---
    fun updateCredentials(phone: String, password: String) {
        val updated = credentialsFlow.value.copy(phone = phone, password = password)
        scheduleCredentialSave(updated)
    }

    fun updateFallback(index: Int, phone: String, password: String) {
        val list = credentialsFlow.value.fallbacks.toMutableList()
        while (list.size < 3) list.add(FallbackCred())
        if (index in list.indices) {
            list[index] = FallbackCred(phone, password)
            scheduleCredentialSave(credentialsFlow.value.copy(fallbacks = list))
        }
    }

    private fun scheduleCredentialSave(updated: VaultCredentials) {
        credSaveJob?.cancel()
        credSaveJob = viewModelScope.launch {
            repository.saveCredentials(updated)
        }
    }

    // --- Module 2: Account Fetcher Commands ---
    fun setFetchSpeedPreset(preset: FetchSpeedPreset) {
        viewModelScope.launch { repository.setFetchSpeedPreset(preset) }
    }

    fun resumeCheckpoint() = coordinator.resumeCheckpoint()

    fun discardCheckpoint() = coordinator.discardCheckpoint()

    fun retryRejectedCode(code: String) {
        val group = fetcherState.value.rejectedGroups.firstOrNull { it.code == code }
        val domains = group?.rows?.map { it.domain }.orEmpty()
        if (domains.isEmpty()) {
            coordinator.emitMessage("No sites in that category to retry.")
            return
        }
        coordinator.startFetch(domains)
    }

    fun startFetch(
        domainsToFetch: List<String>,
        resumeCheckpoint: FetchCheckpoint? = null
    ) = coordinator.startFetch(domainsToFetch, resumeCheckpoint)

    fun retryFailedFetches() {
        val retryTargets = fetcherState.value.retryableDomains
        if (retryTargets.isEmpty()) {
            coordinator.emitMessage("No recoverable failures in this run.")
            return
        }
        coordinator.startFetch(retryTargets)
    }

    fun stopFetch() = coordinator.stopFetch()

    fun setShowRejectedSites(show: Boolean) = coordinator.setShowRejectedSites(show)

    fun loadHistoricalRun(runId: Long) {
        val runSummary = fetcherState.value.fetchRuns.firstOrNull { it.runId == runId }
        coordinator.loadHistoricalRun(runId, runSummary)
        setFetchHistoryOpen(false)
    }

    fun openCaptchaSignIn(domain: String) {
        _captchaSignInDomain.value = DomainUtil.normalizeDomain(domain)
    }

    fun dismissCaptchaSignIn() {
        _captchaSignInDomain.value = null
    }

    fun completeCaptchaSignIn(
        domain: String,
        accessId: String,
        accessToken: String,
        host: String = "",
        merchantId: String = ""
    ) {
        _captchaSignInDomain.value = null
        coordinator.completeCaptchaSignIn(domain, accessId, accessToken, host, merchantId)
    }

    // --- Module 3: Bonus Finder Commands ---
    fun onBonusSearchChanged(query: String) {
        _bonusSearch.value = query
    }

    fun setBonusStatusFilter(filter: BonusStatusFilter) {
        _bonusStatusFilter.value = filter
    }

    fun setBonusSpeedPreset(preset: FetchSpeedPreset) {
        viewModelScope.launch { repository.setBonusSpeedPreset(preset) }
    }

    fun toggleBonusSlotFilter(slot: PublicBonusSlot) {
        _bonusSelectedSlots.update { current ->
            if (current.contains(slot)) current - slot else current + slot
        }
    }

    fun setBonusListOrderSlot(slot: PublicBonusSlot?) {
        _bonusListOrderSlot.value = slot
    }

    fun setBonusAmountSort(sort: BonusAmountSort) {
        _bonusAmountSort.value = sort
    }

    fun clearBonusFilters() {
        _bonusStatusFilter.value = BonusStatusFilter.ALL
        _bonusSelectedSlots.value = emptySet()
        _bonusListOrderSlot.value = null
        _bonusAmountSort.value = BonusAmountSort.DOMAIN_AZ
        _bonusSearch.value = ""
    }

    fun scanPublicBonuses(domains: List<String>) {
        coordinator.startBonusScan(domains)
    }

    fun resumeBonusCheckpoint() {
        coordinator.resumeBonusCheckpoint()
    }

    fun discardBonusCheckpoint() {
        coordinator.discardBonusCheckpoint()
    }

    fun retryFailedBonuses() {
        val failed = bonusFinderState.value.failedDomains
        if (failed.isEmpty()) {
            coordinator.emitMessage("No failed domains to retry.")
            return
        }
        coordinator.startBonusScan(failed)
    }

    fun stopBonusScan() {
        coordinator.stopBonusScan()
    }

    // --- Module 4: Domain Intel Actions ---
    fun onIntelSearchChanged(query: String) {
        _intelSearch.value = query
    }

    fun toggleIntelStatusFilter(status: SiteStatus?) {
        _intelStatusFilter.update { cur -> if (cur == status) null else status }
    }

    fun toggleIntelSelect(domain: String) {
        _intelSelectedDomains.update { cur ->
            if (cur.contains(domain)) cur - domain else cur + domain
        }
    }

    fun toggleIntelSelectAllShown() {
        val shown = intelState.value.rows.map { it.site.domain }
        if (shown.isEmpty()) return
        _intelSelectedDomains.update { cur ->
            val allSelected = shown.all { cur.contains(it) }
            if (allSelected) cur - shown.toSet() else cur + shown
        }
    }

    fun removeSelectedIntelDomains() {
        val toRemove = _intelSelectedDomains.value.toList()
        if (toRemove.isEmpty()) return
        viewModelScope.launch {
            repository.hideDomains(toRemove)
            _intelSelectedDomains.value = emptySet()
            _intelStatus.value = "Removed ${toRemove.size} domain(s) from Directory, Fetcher, and Bonus Finder."
            coordinator.emitMessage("Removed ${toRemove.size} domain(s).")
        }
    }

    fun importDomainsText(rawText: String, onFinished: (() -> Unit)? = null) {
        val domains = DomainUtil.extractDomainsFromText(rawText)
        if (domains.isEmpty()) {
            _intelStatus.value = "No valid domains found in list."
            coordinator.emitMessage("No valid domains found. Use one domain per line.")
            onFinished?.invoke()
            return
        }
        viewModelScope.launch {
            try {
                val added = repository.addDomains(domains, markAsMine = false)
                _intelStatus.value = "Processed ${domains.size} domain(s) · added $added new domain(s)."
                coordinator.emitMessage("Added $added new domain(s) (${domains.size} processed).")
            } finally {
                onFinished?.invoke()
            }
        }
    }

    fun addLatestDomainsList() {
        if (_intelFetchingLatest.value) return
        intelLatestFetchJob?.cancel()
        intelLatestFetchJob = viewModelScope.launch {
            _intelFetchingLatest.value = true
            _intelStatus.value = "Fetching latest domain list…"
            val result = repository.engine.fetchLatestDomainsList()
            result.onSuccess { extractedDomains ->
                val onePerLineText = extractedDomains.joinToString("\n")
                importDomainsText(onePerLineText) {
                    _intelFetchingLatest.value = false
                }
            }.onFailure { err ->
                _intelFetchingLatest.value = false
                val msg = err.message?.takeIf { it.isNotBlank() } ?: "Could not load latest domain list."
                _intelStatus.value = "Latest list error: $msg"
                coordinator.emitMessage("Failed to load latest list: $msg")
            }
        }
    }

    fun runIntelHealthCheck() {
        val selected = _intelSelectedDomains.value
        val allDomains = visibleSitesFlow.value.map { it.domain }
        val targets = if (selected.isNotEmpty()) {
            allDomains.filter { selected.contains(it) }
        } else {
            allDomains
        }
        if (targets.isEmpty()) {
            _intelStatus.value = "Add a domain before running health checks."
            return
        }

        intelCheckJob?.cancel()
        intelCheckJob = viewModelScope.launch {
            _intelRunning.value = true
            _intelProgress.value = 0 to targets.size

            try {
                if (selected.isEmpty() && targets.size > 40) {
                    _intelStatus.value = "Syncing ${targets.size} domains from live status feed…"
                    val feed = repository.engine.fetchBulkFeed()
                    if (feed != null && feed.isNotEmpty()) {
                        val now = System.currentTimeMillis()
                        val batch = targets.map { d ->
                            DomainHealthRecord(
                                domain = d,
                                status = feed[d] ?: SiteStatus.UNKNOWN,
                                latencyMs = 0L,
                                checkedAt = now
                            )
                        }
                        repository.saveDomainHealthBatch(batch)
                        _intelProgress.value = targets.size to targets.size
                        _intelStatus.value = "Synced live status for ${targets.size} domains."
                        return@launch
                    }
                }

                _intelStatus.value = "Probing ${targets.size} domain(s) directly…"
                var done = 0
                _checkingDomains.update { it + targets }
                repository.engine.probeDomainsBatch(targets) { record ->
                    repository.saveDomainHealth(record)
                    _checkingDomains.update { it - record.domain }
                    done++
                    _intelProgress.value = done to targets.size
                }
                _intelStatus.value = "Directly probed $done domain(s) with latency metrics."
            } catch (_: CancellationException) {
                _intelStatus.value = "Health check stopped. Completed results stay saved."
            } finally {
                _checkingDomains.update { it - targets.toSet() }
                _intelRunning.value = false
            }
        }
    }

    fun stopIntelHealthCheck() {
        intelCheckJob?.cancel()
    }

    // --- Settings, Cache & SAF Backup ---
    fun restoreHiddenDomains() {
        viewModelScope.launch {
            repository.restoreHiddenDomains()
            coordinator.emitMessage("Restored all hidden domains.")
        }
    }

    fun clearVolatileCaches() {
        viewModelScope.launch {
            repository.clearVolatileCaches()
            coordinator.emitMessage("Cleared host, health, and public bonus caches.")
        }
    }

    fun clearAllDeviceData() {
        intelCheckJob?.cancel()
        coordinator.resetAfterClearAll()
        viewModelScope.launch {
            repository.clearAllDeviceData()
            _intelSelectedDomains.value = emptySet()
            coordinator.emitMessage("Cleared all local vault data.")
        }
    }

    suspend fun buildBackupJson(includeCredentials: Boolean): String {
        return repository.exportVaultJson(includeCredentials)
    }

    fun importBackupJson(rawJson: String) {
        viewModelScope.launch {
            val result = repository.importVaultJson(rawJson)
            result.onSuccess { msg ->
                coordinator.reloadLatestRunRows()
                coordinator.emitMessage(msg)
            }.onFailure { err ->
                coordinator.emitMessage(err.message ?: "Could not import backup.")
            }
        }
    }

    fun emitMessage(msg: String) = coordinator.emitMessage(msg)

    // --- Pure Helper Math for Bonus Finder ---
    private fun publicBonusRank(bonus: PublicBonus): Double = when (bonus.kind) {
        PublicBonusKind.FIXED -> 3_000_000.0 + bonus.amount
        PublicBonusKind.PERCENT -> 2_000_000.0 + bonus.amount
        PublicBonusKind.RANDOM -> 1_000_000.0 + bonus.amount
        PublicBonusKind.UNSET -> 0.0
    }

    private fun bestPublicBonus(bonuses: List<PublicBonus>, slot: PublicBonusSlot): PublicBonus? {
        val matches = bonuses.filter { it.slot == slot }
        if (matches.isEmpty()) return null
        return if (slot == PublicBonusSlot.DEPOSIT) {
            matches.maxByOrNull { if (it.kind == PublicBonusKind.PERCENT) it.amount else -1.0 }
        } else {
            matches.maxByOrNull { publicBonusRank(it) }
        }
    }

    private fun metricOf(
        bonuses: List<PublicBonus>,
        slot: PublicBonusSlot,
        sort: BonusAmountSort
    ): Double? {
        val bonus = bestPublicBonus(bonuses, slot) ?: return null
        if (bonus.kind == PublicBonusKind.UNSET) return null
        return when (sort) {
            BonusAmountSort.TURNOVER -> bonus.rollover ?: 0.0
            BonusAmountSort.WITHDRAW -> bonus.maxWithdraw
            BonusAmountSort.DEPOSIT, BonusAmountSort.AMOUNT -> bonus.amount
            BonusAmountSort.DOMAIN_AZ -> null
        }
    }
}
