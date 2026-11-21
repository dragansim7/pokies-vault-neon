package com.aistudio.pokiesvault.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.pokiesvault.data.model.AccountResult
import com.aistudio.pokiesvault.data.model.BonusAmountSort
import com.aistudio.pokiesvault.data.model.BonusMode
import com.aistudio.pokiesvault.data.model.DomainHealthRecord
import com.aistudio.pokiesvault.data.model.FallbackCred
import com.aistudio.pokiesvault.data.model.FetchRunSummary
import com.aistudio.pokiesvault.data.model.PublicBonus
import com.aistudio.pokiesvault.data.model.PublicBonusKind
import com.aistudio.pokiesvault.data.model.PublicBonusSiteCache
import com.aistudio.pokiesvault.data.model.PublicBonusSlot
import com.aistudio.pokiesvault.data.model.SiteGroup
import com.aistudio.pokiesvault.data.model.SiteRecord
import com.aistudio.pokiesvault.data.model.SiteStatus
import com.aistudio.pokiesvault.data.model.SortMode
import com.aistudio.pokiesvault.data.model.VaultCredentials
import com.aistudio.pokiesvault.data.model.VaultPreferences
import com.aistudio.pokiesvault.data.model.VaultThemeId
import com.aistudio.pokiesvault.data.model.ViewMode
import com.aistudio.pokiesvault.data.repository.VaultRepository
import com.aistudio.pokiesvault.util.DomainUtil
import com.aistudio.pokiesvault.util.FormatUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import kotlin.math.abs

data class DirectorySiteItem(
    val site: SiteRecord,
    val status: SiteStatus,
    val latencyMs: Long,
    val account: AccountResult.Ok?,
    val walletText: String?,
    val bonusText: String?
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

data class FetcherUiState(
    val credentials: VaultCredentials = VaultCredentials(),
    val isFetching: Boolean = false,
    val progressDone: Int = 0,
    val progressTotal: Int = 0,
    val activeWorkers: Int = 0,
    val workerLimit: Int = 6,
    val statusMessage: String = "Enter your phone and password to fetch wallets and bonuses concurrently.",
    val validAccounts: List<AccountResult.Ok> = emptyList(),
    val emptyAccounts: List<AccountResult.Ok> = emptyList(),
    val rejectedAccounts: List<AccountResult.Err> = emptyList(),
    val retryableDomains: List<String> = emptyList(),
    val totalCash: Double = 0.0,
    val fetchDiffs: List<String> = emptyList(),
    val showRejectedSites: Boolean = false,
    val mineTargetDomains: List<String> = emptyList(),
    val allTargetDomains: List<String> = emptyList(),
    val fetchRuns: List<FetchRunSummary> = emptyList(),
    val viewingRunId: Long? = null
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
    val selectedSlots: Set<PublicBonusSlot> = emptySet(),
    val listOrderSlot: PublicBonusSlot? = null,
    val amountSort: BonusAmountSort = BonusAmountSort.DOMAIN_AZ,
    val items: List<BonusFinderItem> = emptyList(),
    val totalDomains: Int = 0,
    val loadedCount: Int = 0,
    val isScanning: Boolean = false,
    val progressDone: Int = 0,
    val progressTotal: Int = 0,
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
    val progressDone: Int = 0,
    val progressTotal: Int = 0,
    val statusMessage: String = "Unified list shared across Directory, Fetcher, and Bonus Finder."
)

data class VaultGlobalUiState(
    val themeId: VaultThemeId = VaultThemeId.VAULT,
    val savedAccountsCount: Int = 0,
    val hiddenDomainsCount: Int = 0,
    val isAddDomainOpen: Boolean = false,
    val isSortFilterOpen: Boolean = false,
    val isConfirmScanOpen: Boolean = false,
    val isFetchHistoryOpen: Boolean = false,
    val selectedDetailDomain: String? = null
)

@OptIn(FlowPreview::class)
class VaultViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VaultRepository(application)

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    // --- Shared Dialog & Modal State ---
    private val _modalState = MutableStateFlow(VaultGlobalUiState())

    // --- Raw Input Flows ---
    private val _directorySearch = MutableStateFlow("")
    private val _bonusSearch = MutableStateFlow("")
    private val _bonusSelectedSlots = MutableStateFlow<Set<PublicBonusSlot>>(emptySet())
    private val _bonusListOrderSlot = MutableStateFlow<PublicBonusSlot?>(null)
    private val _bonusAmountSort = MutableStateFlow(BonusAmountSort.DOMAIN_AZ)
    private val _intelSearch = MutableStateFlow("")
    private val _intelStatusFilter = MutableStateFlow<SiteStatus?>(null)
    private val _intelSelectedDomains = MutableStateFlow<Set<String>>(emptySet())

    // --- Ephemeral Checking/Scanning State ---
    private val _checkingDomains = MutableStateFlow<Set<String>>(emptySet())
    private val _fetcherRows = MutableStateFlow<List<AccountResult>>(emptyList())
    private val _fetcherRunning = MutableStateFlow(false)
    private val _fetcherProgress = MutableStateFlow(0 to 0)
    private val _fetcherStatus = MutableStateFlow("Enter your phone and password to fetch wallets and bonuses concurrently.")
    private val _fetcherDiffs = MutableStateFlow<List<String>>(emptyList())
    private val _showRejected = MutableStateFlow(false)
    private val _viewingRunId = MutableStateFlow<Long?>(null)

    private val _bonusRunning = MutableStateFlow(false)
    private val _bonusProgress = MutableStateFlow(0 to 0)
    private val _bonusStatus = MutableStateFlow("Scan public promotions across your domain list without signing in.")

    private val _intelRunning = MutableStateFlow(false)
    private val _intelProgress = MutableStateFlow(0 to 0)
    private val _intelStatus = MutableStateFlow("Unified list shared across Directory, Fetcher, and Bonus Finder.")

    private var fetchJob: Job? = null
    private var bonusScanJob: Job? = null
    private var intelCheckJob: Job? = null
    private var credSaveJob: Job? = null

    private val inFlightProbeMutex = Mutex()
    private val inFlightProbes = mutableSetOf<String>()
    private val statusTtlMs = 15L * 60L * 1000L // 15 minutes cache TTL

    private val preferencesFlow = repository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, VaultPreferences())

    private val visibleSitesFlow = repository.visibleSitesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val healthMapFlow = repository.domainHealthFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val latestAccountsFlow = repository.latestAccountsByDomainFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val credentialsFlow = repository.credentialsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, VaultCredentials())

    val globalState: StateFlow<VaultGlobalUiState> = combine(
        preferencesFlow,
        visibleSitesFlow,
        repository.hiddenCountFlow,
        _modalState
    ) { prefs, sites, hiddenCount, modals ->
        modals.copy(
            themeId = prefs.themeId,
            savedAccountsCount = sites.count { it.isMine },
            hiddenDomainsCount = hiddenCount
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VaultGlobalUiState())

    // --- Module 1: Directory State (Off-main-thread filter/group/sort pipeline) ---
    val directoryState: StateFlow<DirectoryUiState> = combine(
        preferencesFlow,
        visibleSitesFlow,
        healthMapFlow,
        latestAccountsFlow,
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
        val checking = arr[4] as Set<String>
        val query = (arr[5] as String).trim().lowercase(Locale.US)

        val filtered = sites.filter { site ->
            if (prefs.mineOnly && !site.isMine) return@filter false
            if (prefs.notVisited && site.isVisited) return@filter false
            if (query.isNotEmpty() && !site.domain.contains(query)) return@filter false
            true
        }

        val sorted = when (prefs.sortMode) {
            SortMode.AZ -> filtered.sortedBy { it.domain }
            SortMode.ZA -> filtered.sortedByDescending { it.domain }
            SortMode.NEW -> filtered.sortedByDescending { it.addedAt }
            SortMode.BALANCE -> filtered.sortedWith(
                compareByDescending<SiteRecord> { accountsMap[it.domain]?.cash ?: -1.0 }
                    .thenBy { it.domain }
            )
            SortMode.DEFAULT -> filtered
        }

        val uiItems = sorted.map { site ->
            val health = healthMap[site.domain]
            val status = when {
                checking.contains(site.domain) -> SiteStatus.CHECKING
                health != null -> health.status
                else -> SiteStatus.UNKNOWN
            }
            val acc = accountsMap[site.domain]
            val walletText = if (prefs.wallets && acc != null) {
                "${FormatUtil.money(acc.cash)} · ${acc.cashWalletName}"
            } else null
            val bonusText = if (acc != null) {
                when (prefs.bonusMode) {
                    BonusMode.NEW -> acc.newMember?.let { "${it.name} ${it.amountDisplay}" }
                    BonusMode.SHARE -> acc.share?.let { "${it.name} ${it.amountDisplay}" }
                    BonusMode.COMMISSION -> acc.commission?.let { "${it.name} ${it.amountDisplay}" }
                    BonusMode.REFERRAL -> acc.referral?.let { "${it.name} ${it.amountDisplay}" }
                    BonusMode.REBATE -> acc.rebate?.let { "${it.name} ${it.amountDisplay}" }
                    BonusMode.NONE -> null
                }
            } else null

            DirectorySiteItem(
                site = site,
                status = status,
                latencyMs = health?.latencyMs ?: 0L,
                account = acc,
                walletText = walletText,
                bonusText = bonusText
            )
        }

        val isGrouped = prefs.viewMode == ViewMode.GROUPS &&
            query.isEmpty() &&
            !prefs.mineOnly &&
            !prefs.notVisited &&
            prefs.sortMode == SortMode.DEFAULT

        val grouped = if (isGrouped) {
            val orderedGroups = listOf(
                SiteGroup.CUSTOM,
                SiteGroup.NEW,
                SiteGroup.BEST,
                SiteGroup.SHARE,
                SiteGroup.UNVERIFIED,
                SiteGroup.LATEST
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
            totalCachedCash = accountsMap.values.sumOf { it.cash },
            isLoading = false
        )
    }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DirectoryUiState())

    val directorySearchInput: StateFlow<String> = _directorySearch.asStateFlow()

    // --- Module 2: Account Fetcher State ---
    val fetcherState: StateFlow<FetcherUiState> = combine(
        credentialsFlow,
        visibleSitesFlow,
        _fetcherRows,
        _fetcherRunning,
        _fetcherProgress,
        _fetcherStatus,
        _fetcherDiffs,
        _showRejected,
        repository.fetchRunsFlow,
        repository.engine.activeWorkers,
        repository.engine.currentWorkerLimit,
        _viewingRunId
    ) { arr ->
        val creds = arr[0] as VaultCredentials
        @Suppress("UNCHECKED_CAST")
        val sites = arr[1] as List<SiteRecord>
        @Suppress("UNCHECKED_CAST")
        val rows = arr[2] as List<AccountResult>
        val running = arr[3] as Boolean
        @Suppress("UNCHECKED_CAST")
        val progress = arr[4] as Pair<Int, Int>
        val statusMsg = arr[5] as String
        @Suppress("UNCHECKED_CAST")
        val diffs = arr[6] as List<String>
        val showRej = arr[7] as Boolean
        @Suppress("UNCHECKED_CAST")
        val runs = arr[8] as List<FetchRunSummary>
        val activeW = arr[9] as Int
        val limitW = arr[10] as Int
        val viewRunId = arr[11] as Long?

        val okList = rows.filterIsInstance<AccountResult.Ok>()
        val valid = okList.filter { it.hasData }.sortedWith(compareByDescending<AccountResult.Ok> { it.cash }.thenBy { it.domain })
        val empty = okList.filter { !it.hasData }.sortedBy { it.domain }
        val rejected = rows.filterIsInstance<AccountResult.Err>().sortedBy { it.domain }
        val retryable = rejected
            .filter { it.code in setOf("timeout", "unreachable", "rate_limit", "other") }
            .map { it.domain }

        val mine = sites.filter { it.isMine }.map { it.domain }
        val visited = sites.filter { it.isVisited }.map { it.domain }
        val mineTargets = mine.ifEmpty { visited }

        FetcherUiState(
            credentials = creds,
            isFetching = running,
            progressDone = progress.first,
            progressTotal = progress.second,
            activeWorkers = activeW,
            workerLimit = limitW,
            statusMessage = statusMsg,
            validAccounts = valid,
            emptyAccounts = empty,
            rejectedAccounts = rejected,
            retryableDomains = retryable,
            totalCash = valid.sumOf { it.cash },
            fetchDiffs = diffs,
            showRejectedSites = showRej,
            mineTargetDomains = mineTargets,
            allTargetDomains = sites.map { it.domain },
            fetchRuns = runs,
            viewingRunId = viewRunId
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FetcherUiState())

    // --- Module 3: Bonus Finder State ---
    val bonusFinderState: StateFlow<BonusFinderUiState> = combine(
        visibleSitesFlow,
        repository.publicBonusesFlow,
        _bonusSearch.debounce(120),
        _bonusSelectedSlots,
        _bonusListOrderSlot,
        _bonusAmountSort,
        _bonusRunning,
        _bonusProgress,
        _bonusStatus
    ) { arr ->
        @Suppress("UNCHECKED_CAST")
        val sites = arr[0] as List<SiteRecord>
        @Suppress("UNCHECKED_CAST")
        val cache = arr[1] as Map<String, PublicBonusSiteCache>
        val query = (arr[2] as String).trim().lowercase(Locale.US)
        @Suppress("UNCHECKED_CAST")
        val selectedSlots = arr[3] as Set<PublicBonusSlot>
        val listOrderSlot = arr[4] as PublicBonusSlot?
        val amountSort = arr[5] as BonusAmountSort
        val running = arr[6] as Boolean
        @Suppress("UNCHECKED_CAST")
        val progress = arr[7] as Pair<Int, Int>
        val statusMsg = arr[8] as String

        val domains = sites.map { it.domain }
        var filtered = domains.filter { domain ->
            if (query.isEmpty()) return@filter true
            if (domain.contains(query)) return@filter true
            val cached = cache[domain]
            cached?.bonuses?.any { it.name.lowercase(Locale.US).contains(query) } == true
        }

        if (selectedSlots.isNotEmpty()) {
            filtered = filtered.filter { domain ->
                val row = cache[domain] ?: return@filter false
                row.bonuses.any { it.slot != PublicBonusSlot.OTHER && selectedSlots.contains(it.slot) }
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
            val bonuses = row?.bonuses.orEmpty()
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

        BonusFinderUiState(
            searchQuery = _bonusSearch.value,
            selectedSlots = selectedSlots,
            listOrderSlot = listOrderSlot,
            amountSort = amountSort,
            items = items,
            totalDomains = domains.size,
            loadedCount = domains.count { cache.containsKey(it) },
            isScanning = running,
            progressDone = progress.first,
            progressTotal = progress.second,
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
        @Suppress("UNCHECKED_CAST")
        val progress = arr[7] as Pair<Int, Int>
        val statusMsg = arr[8] as String

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
            progressDone = progress.first,
            progressTotal = progress.second,
            statusMessage = statusMsg
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), IntelUiState())

    val intelSearchInput: StateFlow<String> = _intelSearch.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureInitialized()
            val latestRows = repository.loadLatestRunResults()
            if (latestRows.isNotEmpty()) {
                _fetcherRows.value = latestRows
                val okCount = latestRows.count { it is AccountResult.Ok }
                _fetcherStatus.value = "Loaded last saved run ($okCount of ${latestRows.size} signed in)."
            }
        }
    }

    // --- Modal & Detail Actions ---
    fun setAddDomainOpen(open: Boolean) = _modalState.update { it.copy(isAddDomainOpen = open) }
    fun setSortFilterOpen(open: Boolean) = _modalState.update { it.copy(isSortFilterOpen = open) }
    fun setConfirmScanOpen(open: Boolean) = _modalState.update { it.copy(isConfirmScanOpen = open) }
    fun setFetchHistoryOpen(open: Boolean) = _modalState.update { it.copy(isFetchHistoryOpen = open) }
    fun openSiteDetail(domain: String?) = _modalState.update { it.copy(selectedDetailDomain = domain) }

    fun getDetailForDomain(domain: String): DirectorySiteItem? {
        return directoryState.value.items.firstOrNull { it.site.domain == domain }
    }

    // --- Theme & Preferences ---
    fun selectTheme(themeId: VaultThemeId) {
        viewModelScope.launch { repository.setTheme(themeId) }
    }

    fun onDirectorySearchChanged(query: String) {
        _directorySearch.value = query
    }

    fun toggleNotVisited() {
        viewModelScope.launch { repository.setNotVisited(!preferencesFlow.value.notVisited) }
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
            _snackbarEvents.tryEmit("Added $normalized to your vault.")
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

    // --- Module 2: Adaptive Parallel Account Fetcher ---
    fun startFetch(domainsToFetch: List<String>) {
        val creds = credentialsFlow.value.validPairs()
        if (creds.isEmpty()) {
            _fetcherStatus.value = "Enter your phone number and password before fetching."
            return
        }
        val uniqueDomains = domainsToFetch
            .map { DomainUtil.normalizeDomain(it) }
            .filter { DomainUtil.isPublicDomain(it) }
            .distinct()
        if (uniqueDomains.isEmpty()) {
            _fetcherStatus.value = "No sites selected. Bookmark accounts, enter a domain, or run a full scan."
            return
        }

        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            val previousOkMap = latestAccountsFlow.value
            val hostCache = repository.loadHostCacheMap()

            _fetcherRunning.value = true
            _viewingRunId.value = null
            _fetcherProgress.value = 0 to uniqueDomains.size
            _fetcherRows.value = emptyList()
            _fetcherDiffs.value = emptyList()
            _showRejected.value = false
            _fetcherStatus.value = if (uniqueDomains.size == 1) {
                "Checking ${uniqueDomains.first()}…"
            } else {
                "Adaptive pool checking ${uniqueDomains.size} sites…"
            }

            val collected = ArrayList<AccountResult>(uniqueDomains.size)
            var lastUiPush = 0L

            try {
                repository.engine.fetchAccountsBatch(
                    domains = uniqueDomains,
                    credentials = creds,
                    hostCache = hostCache,
                    onHostDiscovered = { target -> repository.saveHostTarget(target) },
                    onResult = { res ->
                        collected.add(res)
                        val now = System.currentTimeMillis()
                        if (now - lastUiPush >= 120L || collected.size == uniqueDomains.size) {
                            lastUiPush = now
                            _fetcherProgress.value = collected.size to uniqueDomains.size
                            _fetcherRows.value = collected.toList()
                        }
                    }
                )

                val finalRows = collected.toList()
                val runId = repository.persistFetchRun(startedAt, uniqueDomains.size, finalRows)
                _viewingRunId.value = runId
                _fetcherRows.value = finalRows
                _fetcherProgress.value = finalRows.size to uniqueDomains.size

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
                val okCount = finalRows.count { it is AccountResult.Ok }
                val elapsedSec = ((System.currentTimeMillis() - startedAt) / 1000.0)
                _fetcherStatus.value = String.format(
                    Locale.US,
                    "Completed in %.1fs · %d of %d signed in · saved to vault.",
                    elapsedSec,
                    okCount,
                    finalRows.size
                )
            } catch (_: CancellationException) {
                if (collected.isNotEmpty()) {
                    val partial = collected.toList()
                    repository.persistFetchRun(startedAt, uniqueDomains.size, partial)
                    _fetcherRows.value = partial
                }
                _fetcherStatus.value = "Stopped. ${collected.count { it is AccountResult.Ok }} completed hits were saved."
            } finally {
                _fetcherRunning.value = false
            }
        }
    }

    fun retryFailedFetches() {
        val retryTargets = fetcherState.value.retryableDomains
        if (retryTargets.isEmpty()) {
            _snackbarEvents.tryEmit("No retryable network failures in this run.")
            return
        }
        startFetch(retryTargets)
    }

    fun stopFetch() {
        fetchJob?.cancel()
    }

    fun setShowRejectedSites(show: Boolean) {
        _showRejected.value = show
    }

    fun loadHistoricalRun(runId: Long) {
        viewModelScope.launch {
            val rows = repository.loadRunResults(runId)
            _fetcherRows.value = rows
            _viewingRunId.value = runId
            _fetcherDiffs.value = emptyList()
            _fetcherStatus.value = "Viewing saved run (${rows.count { it is AccountResult.Ok }} of ${rows.size} signed in)."
            setFetchHistoryOpen(false)
        }
    }

    // --- Module 3: Bonus Finder Actions ---
    fun onBonusSearchChanged(query: String) {
        _bonusSearch.value = query
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
        _bonusSelectedSlots.value = emptySet()
        _bonusListOrderSlot.value = null
        _bonusAmountSort.value = BonusAmountSort.DOMAIN_AZ
        _bonusSearch.value = ""
    }

    fun scanPublicBonuses(domains: List<String>) {
        val unique = domains.distinct()
        if (unique.isEmpty()) {
            _bonusStatus.value = "No domains to scan."
            return
        }
        bonusScanJob?.cancel()
        bonusScanJob = viewModelScope.launch {
            val hostCache = repository.loadHostCacheMap()
            _bonusRunning.value = true
            _bonusProgress.value = 0 to unique.size
            _bonusStatus.value = "Scanning public offers across ${unique.size} domains…"
            var done = 0
            try {
                repository.engine.scanPublicBonusesBatch(
                    domains = unique,
                    hostCache = hostCache,
                    onHostDiscovered = { target -> repository.saveHostTarget(target) },
                    onResult = { result ->
                        repository.savePublicBonusResult(result)
                        done++
                        _bonusProgress.value = done to unique.size
                    }
                )
                _bonusStatus.value = "Public promotions updated ($done scanned). No password was sent."
            } catch (_: CancellationException) {
                _bonusStatus.value = "Bonus scan stopped ($done / ${unique.size} saved)."
            } finally {
                _bonusRunning.value = false
            }
        }
    }

    fun stopBonusScan() {
        bonusScanJob?.cancel()
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
            _snackbarEvents.tryEmit("Removed ${toRemove.size} domain(s).")
        }
    }

    fun importDomainsText(rawText: String) {
        val domains = DomainUtil.extractDomainsFromText(rawText)
        if (domains.isEmpty()) {
            _snackbarEvents.tryEmit("No valid domains found. Use one domain per line.")
            return
        }
        viewModelScope.launch {
            val added = repository.addDomains(domains, markAsMine = false)
            _intelStatus.value = "Imported $added new domain(s) from ${domains.size} valid entries."
            _snackbarEvents.tryEmit("Imported $added new domain(s).")
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
            _snackbarEvents.tryEmit("Restored all hidden domains.")
        }
    }

    fun clearVolatileCaches() {
        viewModelScope.launch {
            repository.clearVolatileCaches()
            _snackbarEvents.tryEmit("Cleared host, health, and public bonus caches.")
        }
    }

    fun clearAllDeviceData() {
        fetchJob?.cancel()
        bonusScanJob?.cancel()
        intelCheckJob?.cancel()
        viewModelScope.launch {
            repository.clearAllDeviceData()
            _fetcherRows.value = emptyList()
            _fetcherDiffs.value = emptyList()
            _intelSelectedDomains.value = emptySet()
            _snackbarEvents.tryEmit("Cleared all local vault data.")
        }
    }

    suspend fun buildBackupJson(includeCredentials: Boolean): String {
        return repository.exportVaultJson(includeCredentials)
    }

    fun importBackupJson(rawJson: String) {
        viewModelScope.launch {
            val result = repository.importVaultJson(rawJson)
            result.onSuccess { msg ->
                val latestRows = repository.loadLatestRunResults()
                if (latestRows.isNotEmpty()) _fetcherRows.value = latestRows
                _snackbarEvents.tryEmit(msg)
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not import backup.")
            }
        }
    }

    fun emitMessage(msg: String) {
        _snackbarEvents.tryEmit(msg)
    }

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
