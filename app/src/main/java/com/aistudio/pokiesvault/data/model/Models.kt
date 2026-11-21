package com.aistudio.pokiesvault.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class SiteGroup(val label: String) {
    NEW("New"),
    BEST("Best"),
    UNVERIFIED("Unverified"),
    SHARE("Instant share bonuses"),
    LATEST("Latest"),
    CUSTOM("Added by you")
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
    LIVE("Up"),
    SLOW("Slow"),
    REDIRECT("Redirect"),
    DEAD("Down")
}

@Serializable
enum class ViewMode(val label: String) {
    GROUPS("Groups"),
    ONE("1-Col"),
    TWO("2-Col")
}

@Serializable
enum class SortMode(val label: String) {
    DEFAULT("Featured groups"),
    AZ("Domain A–Z"),
    ZA("Domain Z–A"),
    NEW("Newest added"),
    BALANCE("Wallet balance")
}

@Serializable
enum class BonusMode(val label: String) {
    NONE("No badge"),
    NEW("Welcome"),
    SHARE("Share"),
    COMMISSION("Commission"),
    REFERRAL("Referral"),
    REBATE("Rebate")
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
data class BonusView(
    val name: String,
    val amount: Double,
    val amountDisplay: String,
    val minWithdrawal: Double? = null,
    val maxWithdrawal: Double? = null,
    val rollover: Double? = null,
    val isPercent: Boolean = false
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
        val newMember: BonusView?,
        val rebate: BonusView?,
        val availableBonuses: List<BonusView>,
        override val latencyMs: Long = 0L,
        override val retriesUsed: Int = 0,
        override val fetchedAt: Long = 0L,
        override val attemptsUsed: Int = 1
    ) : AccountResult()

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

data class VaultPreferences(
    val themeId: VaultThemeId = VaultThemeId.VAULT,
    val viewMode: ViewMode = ViewMode.GROUPS,
    val sortMode: SortMode = SortMode.DEFAULT,
    val bonusMode: BonusMode = BonusMode.NONE,
    val notVisited: Boolean = false,
    val wallets: Boolean = true,
    val mineOnly: Boolean = false
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
