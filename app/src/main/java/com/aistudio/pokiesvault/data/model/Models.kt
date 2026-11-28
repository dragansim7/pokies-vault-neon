package com.aistudio.pokiesvault.data.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
enum class SiteGroup(val label: String) {
    PROMOTED("Promoted"),
    NEW("New"),
    BEST("Best"),
    UNVERIFIED("Unverified"),
    SHARE("Instant share bonuses"),
    LATEST("Latest"),
    CUSTOM("Added by you"),
    CONFIRMED_SCAMS("Confirmed scams")
}

@Serializable
data class CatalogSite(
    val domain: String,
    val group: SiteGroup,
    val addedAt: String = ""
)

@Serializable
enum class SiteStatus(val label: String) {
    UNKNOWN("Unchecked"),
    CHECKING("Checking"),
    LIVE("Live"),
    SLOW("Slow"),
    REDIRECT("Redirect"),
    DEAD("Dead")
}

@Serializable
enum class ViewMode(val label: String) {
    GROUPS("Default"),
    ONE("1 Column"),
    TWO("2 Columns")
}

@Serializable
enum class SortMode(val label: String, val shortLabel: String) {
    DEFAULT("Default order", "Sort / Filter"),
    AZ("Domain name — A → Z", "A → Z"),
    ZA("Domain name — Z → A", "Z → A"),
    BALANCE("Highest balance", "Balance ↓"),
    TURNOVER("Best turnover", "Best turnover"),
    CASHOUT("Lowest cash-out", "Cash-out"),
    ERRORS("Errors", "Errors"),
    POPULAR("Most popular", "Most popular"),
    NO_REFER("Accounts without referlinks", "No referlink"),
    CHOSEN_AMOUNT("Chosen bonus — biggest amount", "Bonus amount ↓"),
    CHOSEN_MAX("Chosen bonus — highest max", "Bonus max ↓"),
    CHOSEN_TURNOVER("Chosen bonus — lowest turnover", "Bonus turnover ↑"),
    NEW("Newest", "Newest")
}

@Serializable
enum class BonusMode(val label: String, val emoji: String, val apiKey: String) {
    NONE("None", "", ""),
    NEW("Signup bonus", "🎁", "new"),
    SHARE("Share bonus", "🤝", "share"),
    COMMISSION("Commission", "💸", "commission"),
    REFERRAL("Referral bonus", "👥", "referral"),
    REBATE("Rebate bonus", "♻️", "rebate")
}

@Serializable
enum class CardColor(val label: String, val swatchHex: Long) {
    CLASSIC("Off", 0L),
    PINK("Pink", 0xFFFF4FD8L),
    BLUE("Blue", 0xFF3AA0FFL),
    PURPLE("Purple", 0xFFA855F7L),
    GREEN("Green", 0xFF3DDC97L),
    TEAL("Teal", 0xFF2EE6D6L),
    YELLOW("Yellow", 0xFFFFE14AL),
    GOLD("Gold", 0xFFF0B429L),
    ORANGE("Orange", 0xFFFF8A2AL),
    RED("Red", 0xFFFF4D6AL),
    WHITE("White", 0xFFE8EEFFL)
}

@Serializable
enum class VaultThemeId(val displayName: String, val subtitle: String) {
    VAULT("Vault", "Cyan, violet & magenta"),
    EMERALD("Emerald", "Mint & champagne gold"),
    ARCTIC("Arctic", "Glacier blue & steel"),
    EMBER("Ember", "Molten amber & copper"),
    ROYAL("Royal", "Sovereign gold & amethyst")
}

@Serializable
data class FallbackCred(
    val phone: String = "",
    val password: String = ""
)

@Serializable
data class VaultCredentials(
    val phone: String = "",
    val password: String = "",
    val fallbacks: List<FallbackCred> = listOf(FallbackCred(), FallbackCred(), FallbackCred())
) {
    fun validPairs(): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        val mainPhone = phone.trim()
        if (mainPhone.isNotBlank() && password.isNotBlank()) {
            list.add(mainPhone to password)
        }
        fallbacks.forEach { fb ->
            val fp = fb.phone.trim()
            if (fp.isNotBlank() && fb.password.isNotBlank()) {
                list.add(fp to fb.password)
            }
        }
        return list.distinct()
    }
}

@Serializable
data class SavedDomainSession(
    val domain: String,
    val host: String = "",
    val merchantId: String = "",
    val accessId: String = "",
    val accessToken: String = "",
    val winningPhone: String = "",
    val updatedAt: Long = 0L
) {
    val hasToken: Boolean
        get() = accessId.isNotBlank() && accessToken.isNotBlank()
}

@Serializable
data class BonusView(
    val name: String,
    val amount: Double,
    val amountDisplay: String,
    val minWithdrawal: Double? = null,
    val maxWithdrawal: Double? = null,
    val rollover: Double? = null,
    val isPercent: Boolean = false,
    val maxDisplay: String? = null,
    val detail: String? = null,
    val lit: Boolean = true,
    val mobileAppOnly: Boolean = false,
    val packetId: Long = 0L,
    val transactionId: Long = 0L,
    val angpaoId: Long = 0L,
    val luckyId: Long = 0L
)

@Serializable
data class WalletView(
    val id: String,
    val name: String,
    val balance: Double
)

@Serializable
data class EgressIpInfo(
    val ip: String,
    val isp: String,
    val location: String,
    val fetchedAt: Long = 0L
)

@Serializable
sealed class AccountResult {
    abstract val domain: String
    abstract val latencyMs: Long
    abstract val retriesUsed: Int
    abstract val attemptsUsed: Int
    abstract val fetchedAt: Long

    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    data class Ok(
        override val domain: String,
        val hasData: Boolean,
        val cash: Double,
        val cashWalletName: String,
        val cashMinWithdrawal: Double?,
        val cashMaxWithdrawal: Double?,
        val wallets: List<WalletView>,
        val commission: BonusView?,
        val share: BonusView?,
        val referral: BonusView?,
        @JsonNames("new", "newMember")
        val newMember: BonusView?,
        val rebate: BonusView?,
        @JsonNames("claimableBonuses", "availableBonuses")
        val availableBonuses: List<BonusView>,
        override val latencyMs: Long = 0L,
        override val retriesUsed: Int = 0,
        override val fetchedAt: Long = 0L,
        override val attemptsUsed: Int = 1
    ) : AccountResult() {
        val claimableBonuses: List<BonusView>
            get() = availableBonuses

        fun bonusFor(mode: BonusMode): BonusView? = when (mode) {
            BonusMode.COMMISSION -> commission
            BonusMode.SHARE -> share
            BonusMode.REFERRAL -> referral
            BonusMode.NEW -> newMember
            BonusMode.REBATE -> rebate
            BonusMode.NONE -> null
        }

        fun bonusForKey(apiKey: String): BonusView? = when (apiKey) {
            "commission" -> commission
            "share" -> share
            "referral" -> referral
            "new" -> newMember
            "rebate" -> rebate
            else -> null
        }
    }

    @Serializable
    data class Err(
        override val domain: String,
        val code: String,
        val message: String,
        override val latencyMs: Long = 0L,
        override val retriesUsed: Int = 0,
        override val fetchedAt: Long = 0L,
        override val attemptsUsed: Int = 1
    ) : AccountResult()
}

@Serializable
enum class PublicBonusSlot(val label: String) {
    DEPOSIT("Deposit Match"),
    SIGNUP("Free Signup"),
    SHARE("Share/Invite"),
    DAILY("Daily Free"),
    BIRTHDAY("Birthday"),
    OTHER("Other")
}

@Serializable
enum class PublicBonusKind {
    FIXED, PERCENT, RANDOM, UNSET
}

@Serializable
data class PublicBonus(
    val id: String,
    val name: String,
    val slot: PublicBonusSlot,
    val kind: PublicBonusKind,
    val amount: Double,
    val amountDisplay: String,
    val minWithdraw: Double? = null,
    val maxWithdraw: Double? = null,
    val rollover: Double? = null,
    val minDeposit: Double? = null
)

@Serializable
enum class BonusAmountSort(val label: String) {
    DOMAIN_AZ("Domain A–Z"),
    DEPOSIT("Highest Deposit Match"),
    AMOUNT("Highest Bonus Amount"),
    WITHDRAW("Highest Max Withdrawal"),
    TURNOVER("Lowest Turnover/Wager")
}

@Serializable
enum class BonusStatusFilter(val label: String) {
    ALL("All"),
    LOADED("Loaded"),
    FAILED("Failed"),
    UNLOADED("Unloaded")
}

data class SiteRecord(
    val domain: String,
    val group: SiteGroup,
    val addedAt: String,
    val isMine: Boolean,
    val isVisited: Boolean,
    val isHidden: Boolean
)

data class DomainHealthRecord(
    val domain: String,
    val status: SiteStatus,
    val latencyMs: Long,
    val checkedAt: Long
)

data class PublicBonusSiteCache(
    val domain: String,
    val ok: Boolean,
    val bonuses: List<PublicBonus>,
    val errorMessage: String?,
    val fetchedAt: Long
)

data class FetchRunSummary(
    val runId: Long,
    val startedAt: Long,
    val completedAt: Long,
    val targetCount: Int,
    val okCount: Int,
    val withDataCount: Int,
    val errCount: Int,
    val totalCash: Double
)

data class WorkerLaneProfile(
    val minWorkers: Int,
    val initialWorkers: Int,
    val maxWorkers: Int,
    val globalStaggerMs: Long,
    val globalJitterMs: Long,
    val perIpMaxInFlight: Int,
    val perIpMinGapMs: Long
)

@Serializable
enum class FetchSpeedPreset(
    val displayName: String,
    val tierTitle: String,
    val tierBadge: String,
    val subtitle: String,
    val fetcherSubtitle: String,
    val fetcherChipMetric: String,
    val bonusSubtitle: String,
    val bonusChipMetric: String,
    val minWorkers: Int,
    val initialWorkers: Int,
    val maxWorkers: Int,
    val startGapMs: Long,
    val jitterMs: Long,
    val coldLoginProfile: WorkerLaneProfile,
    val savedSessionProfile: WorkerLaneProfile,
    val bonusScanProfile: WorkerLaneProfile
) {
    GENTLE(
        displayName = "Slow (Safe)",
        tierTitle = "Slow",
        tierBadge = "(Safe)",
        subtitle = "3–5w login · 5–7w saved",
        fetcherSubtitle = "3–5w login · 5–7w saved",
        fetcherChipMetric = "3–5w · 7w⚡",
        bonusSubtitle = "4–6w · 700ms IP gap",
        bonusChipMetric = "4–6w · 700ms",
        minWorkers = 3,
        initialWorkers = 3,
        maxWorkers = 5,
        startGapMs = 250L,
        jitterMs = 80L,
        coldLoginProfile = WorkerLaneProfile(
            minWorkers = 3,
            initialWorkers = 3,
            maxWorkers = 5,
            globalStaggerMs = 250L,
            globalJitterMs = 80L,
            perIpMaxInFlight = 1,
            perIpMinGapMs = 1200L
        ),
        savedSessionProfile = WorkerLaneProfile(
            minWorkers = 5,
            initialWorkers = 5,
            maxWorkers = 7,
            globalStaggerMs = 150L,
            globalJitterMs = 50L,
            perIpMaxInFlight = 2,
            perIpMinGapMs = 600L
        ),
        bonusScanProfile = WorkerLaneProfile(
            minWorkers = 4,
            initialWorkers = 4,
            maxWorkers = 6,
            globalStaggerMs = 150L,
            globalJitterMs = 50L,
            perIpMaxInFlight = 1,
            perIpMinGapMs = 700L
        )
    ),
    SAFE(
        displayName = "Medium (Default)",
        tierTitle = "Medium",
        tierBadge = "(Default)",
        subtitle = "6–8w login · 8–12w saved",
        fetcherSubtitle = "6–8w login · 8–12w saved",
        fetcherChipMetric = "6–8w · 12w⚡",
        bonusSubtitle = "8–12w · 400ms IP gap",
        bonusChipMetric = "8–12w · 400ms",
        minWorkers = 6,
        initialWorkers = 6,
        maxWorkers = 8,
        startGapMs = 120L,
        jitterMs = 50L,
        coldLoginProfile = WorkerLaneProfile(
            minWorkers = 6,
            initialWorkers = 6,
            maxWorkers = 8,
            globalStaggerMs = 120L,
            globalJitterMs = 50L,
            perIpMaxInFlight = 1,
            perIpMinGapMs = 800L
        ),
        savedSessionProfile = WorkerLaneProfile(
            minWorkers = 8,
            initialWorkers = 8,
            maxWorkers = 12,
            globalStaggerMs = 60L,
            globalJitterMs = 30L,
            perIpMaxInFlight = 2,
            perIpMinGapMs = 400L
        ),
        bonusScanProfile = WorkerLaneProfile(
            minWorkers = 8,
            initialWorkers = 8,
            maxWorkers = 12,
            globalStaggerMs = 60L,
            globalJitterMs = 30L,
            perIpMaxInFlight = 2,
            perIpMinGapMs = 400L
        )
    ),
    BALANCED(
        displayName = "Fast (Caution)",
        tierTitle = "Fast",
        tierBadge = "(Caution)",
        subtitle = "8–12w login · 12–16w saved",
        fetcherSubtitle = "8–12w login · 12–16w saved",
        fetcherChipMetric = "8–12w · 16w⚡",
        bonusSubtitle = "12–16w · 280ms IP gap",
        bonusChipMetric = "12–16w · 280ms",
        minWorkers = 8,
        initialWorkers = 8,
        maxWorkers = 12,
        startGapMs = 60L,
        jitterMs = 30L,
        coldLoginProfile = WorkerLaneProfile(
            minWorkers = 8,
            initialWorkers = 8,
            maxWorkers = 12,
            globalStaggerMs = 60L,
            globalJitterMs = 30L,
            perIpMaxInFlight = 2,
            perIpMinGapMs = 550L
        ),
        savedSessionProfile = WorkerLaneProfile(
            minWorkers = 12,
            initialWorkers = 12,
            maxWorkers = 16,
            globalStaggerMs = 30L,
            globalJitterMs = 20L,
            perIpMaxInFlight = 3,
            perIpMinGapMs = 280L
        ),
        bonusScanProfile = WorkerLaneProfile(
            minWorkers = 12,
            initialWorkers = 12,
            maxWorkers = 16,
            globalStaggerMs = 30L,
            globalJitterMs = 20L,
            perIpMaxInFlight = 3,
            perIpMinGapMs = 280L
        )
    )
}

@Serializable
enum class CheckpointPauseReason {
    WAF_BLOCKED,
    USER_STOPPED,
    INTERRUPTED
}

@Serializable
data class FetchCheckpoint(
    val runStartedAt: Long,
    val allTargetDomains: List<String>,
    val completedDomains: List<String>,
    val remainingDomains: List<String>,
    val pauseReason: CheckpointPauseReason,
    val pausedAtIp: String? = null,
    val pauseDetail: String = "",
    val elapsedWallSec: Long = 0L,
    val partialRunId: Long? = null,
    val savedAt: Long = 0L
)

enum class FetchActiveLane(val label: String) {
    IDLE(""),
    DNS_CLUSTERING("DNS Pre-Pass"),
    SAVED_FAST("Saved Fast Lane"),
    COLD_LOGIN("Cold Login Lane")
}

data class VaultPreferences(
    val themeId: VaultThemeId = VaultThemeId.VAULT,
    val viewMode: ViewMode = ViewMode.GROUPS,
    val sortMode: SortMode = SortMode.DEFAULT,
    val bonusMode: BonusMode = BonusMode.NONE,
    val cardColor: CardColor = CardColor.CLASSIC,
    val notVisited: Boolean = false,
    val signupOnly: Boolean = false,
    val wallets: Boolean = true,
    val mineOnly: Boolean = false,
    val fetchSpeedPreset: FetchSpeedPreset = FetchSpeedPreset.SAFE,
    val bonusSpeedPreset: FetchSpeedPreset = FetchSpeedPreset.SAFE,
    val allowBackgroundRuns: Boolean = false,
    val skipKnownDead: Boolean = false
)

@Serializable
data class VaultBackupPayload(
    val version: Int = 2,
    val exportedAt: Long,
    val themeId: VaultThemeId = VaultThemeId.VAULT,
    val customSites: List<CatalogSite> = emptyList(),
    val mineDomains: List<String> = emptyList(),
    val visitedDomains: List<String> = emptyList(),
    val hiddenDomains: List<String> = emptyList(),
    val latestAccounts: List<AccountResult.Ok> = emptyList(),
    val credentials: VaultCredentials? = null
)
