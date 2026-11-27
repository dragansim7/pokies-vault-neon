package com.aistudio.pokiesvault.util

import com.aistudio.pokiesvault.data.model.AccountResult
import com.aistudio.pokiesvault.data.model.BonusMode
import com.aistudio.pokiesvault.data.model.BonusView
import com.aistudio.pokiesvault.data.model.SiteGroup
import com.aistudio.pokiesvault.data.model.SortMode
import java.util.Locale

/**
 * Shared deterministic sorting, turnover calculation, and chosen-bonus resolution
 * used identically by both the Directory card list and the Bonus Fetcher results.
 */
object SiteOrderUtil {
    private val digitRegex = Regex("[1-9]")
    private val depositGetPromoRegex = Regex("\\$\\s*\\d+\\s*get\\s*\\$?\\d+", RegexOption.IGNORE_CASE)
    private val depositKeywordRegex = Regex(
        "\\b(first\\s+deposit|welcome\\s+deposit|deposit\\s+bonus|deposit\\s+match|reload\\s+bonus|top\\s*up|top-up|min\\s+deposit|deposit\\s+\\$?\\d+)\\b",
        RegexOption.IGNORE_CASE
    )

    fun sortRequiresSelectedBonus(mode: SortMode): Boolean =
        mode == SortMode.CHOSEN_AMOUNT ||
            mode == SortMode.CHOSEN_MAX ||
            mode == SortMode.CHOSEN_TURNOVER

    fun chosenBonus(account: AccountResult.Ok?, mode: BonusMode): BonusView? {
        if (account == null) return null
        return when (mode) {
            BonusMode.NEW -> account.newMember
            BonusMode.SHARE -> account.share
            BonusMode.COMMISSION -> account.commission
            BonusMode.REFERRAL -> account.referral
            BonusMode.REBATE -> account.rebate
            BonusMode.NONE -> account.share
        }
    }

    fun isDepositPromoName(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        return depositGetPromoRegex.containsMatchIn(trimmed) ||
            depositKeywordRegex.containsMatchIn(trimmed)
    }

    fun bonusCategoryKey(
        name: String,
        custom: String = "",
        transactionType: String = ""
    ): String? {
        val tx = transactionType.uppercase(Locale.US)
        if (tx == "COMMISSION") return "commission"
        if (tx == "REBATE") return "rebate"
        val c = custom.lowercase(Locale.US)
        val n = name.lowercase(Locale.US)
        return when {
            c.contains("share") -> "share"
            c.contains("commission") -> "commission"
            c.contains("rebate") -> "rebate"
            c.contains("refer") || c.contains("downline") || c.contains("invite") -> "referral"
            c.contains("welcome") || c.contains("signup") || c.contains("newmember") || c.contains("newregister") -> "new"
            n.contains("commission") -> "commission"
            n.contains("share") -> "share"
            n.contains("rebate") || n.contains("cashback") || n.contains("cash back") -> "rebate"
            n.contains("refer") || n.contains("invite") || n.contains("downline") || n.contains("mate") -> "referral"
            n.contains("welcome") || n.contains("sign up") || n.contains("signup") ||
                n.contains("new member") || n.contains("new register") -> "new"
            else -> null
        }
    }

    /**
     * True when the server marked this bonus as lit/claimable on the signed-in account
     * (including mystery "?" or random range "$2–$8" bonuses).
     */
    fun isLitBonus(bonus: BonusView?): Boolean {
        if (bonus == null || !bonus.lit || bonus.isPercent) return false
        return true
    }

    /**
     * True when a lit bonus carries a concrete numeric dollar amount (used by numeric sorts).
     */
    fun bonusHasRealAmount(bonus: BonusView?): Boolean {
        if (!isLitBonus(bonus)) return false
        if (bonus!!.amount.isFinite() && bonus.amount > 0.0) return true
        val disp = bonus.amountDisplay.trim()
        if (disp.isNotEmpty() && !disp.contains("%") && disp != "—" && disp != "-" && digitRegex.containsMatchIn(disp)) {
            return true
        }
        return false
    }

    /**
     * Returns ALL server-confirmed lit claimable bonuses for this account
     * (including standalone offers like DAILY FREE and GRAB THE APP as well as any lit
     * COM / SHARE / REF / NEW / REBATE / ANGPAO packets), excluding unlit template offers.
     */
    fun litClaimableBonuses(account: AccountResult.Ok?): List<BonusView> {
        if (account == null) return emptyList()

        val seenKeys = LinkedHashSet<String>()
        val result = mutableListOf<BonusView>()

        fun addIfUnique(b: BonusView) {
            if (!isLitBonus(b)) return
            val hasPacketIdentity = b.packetId != 0L || b.transactionId != 0L ||
                b.angpaoId != 0L || b.luckyId != 0L
            val dedupeKey = if (hasPacketIdentity) {
                "id:${b.packetId}:${b.transactionId}:${b.angpaoId}:${b.luckyId}"
            } else {
                "name:${b.name.trim().lowercase(Locale.US)}|${b.amountDisplay.trim().lowercase(Locale.US)}"
            }
            if (seenKeys.add(dedupeKey)) {
                result.add(b)
            }
        }

        for (b in account.availableBonuses) {
            addIfUnique(b)
        }

        // Safety net for legacy cached snapshots where a lit summary box wasn't in availableBonuses
        listOfNotNull(
            account.commission,
            account.share,
            account.referral,
            account.newMember,
            account.rebate
        ).forEach { boxBonus ->
            val alreadyByName = result.any {
                it.name.equals(boxBonus.name, ignoreCase = true) &&
                    it.amountDisplay.equals(boxBonus.amountDisplay, ignoreCase = true)
            }
            if (!alreadyByName) {
                addIfUnique(boxBonus)
            }
        }

        result.sortWith(
            compareBy<BonusView> { bonusTurnover(it) ?: Double.POSITIVE_INFINITY }
                .thenByDescending { it.amount }
                .thenBy { it.name.lowercase(Locale.US) }
        )
        return result
    }

    /**
     * Computes effective wagering/withdrawal multiplier (minWithdrawal / amount).
     * Returns null for mystery/random bonuses without a fixed live dollar amount.
     */
    fun bonusTurnover(bonus: BonusView?): Double? {
        if (!isLitBonus(bonus)) return null
        val minW = bonus!!.minWithdrawal
        if (bonus.amount > 0.0 && minW != null && minW > 0.0) {
            val ratio = minW / bonus.amount
            if (ratio.isFinite() && ratio > 0.0) return ratio
        }
        return null
    }

    fun formatTurnover(value: Double?): String {
        if (value == null || !value.isFinite() || value <= 0.0) return ""
        val formatted = if (value >= 10.0) {
            String.format(Locale.US, "%.1f", value)
        } else {
            String.format(Locale.US, "%.2f", value)
        }.trimEnd('0').trimEnd('.')
        return "${formatted}×"
    }

    fun bestTurnover(account: AccountResult.Ok?): Double? {
        if (account == null) return null
        var best: Double? = null
        val candidates = litClaimableBonuses(account)

        for (b in candidates) {
            val t = bonusTurnover(b)
            if (t != null && (best == null || t < best)) {
                best = t
            }
        }
        return best
    }

    fun hasAnyRealBonus(account: AccountResult.Ok?): Boolean {
        if (account == null) return false
        return isLitBonus(account.commission) ||
            isLitBonus(account.share) ||
            isLitBonus(account.referral) ||
            isLitBonus(account.newMember) ||
            isLitBonus(account.rebate) ||
            account.availableBonuses.any { isLitBonus(it) }
    }

    fun effectiveCash(account: AccountResult.Ok?): Double {
        if (account == null) return 0.0
        val held = account.wallets.filter { it.balance > 0.001 }
        if (held.isNotEmpty()) return held.sumOf { it.balance }
        return if (account.cash > 0.001) account.cash else 0.0
    }

    fun popularityScore(group: SiteGroup, catalogOrderIndex: Int = 0): Double {
        val base = when (group) {
            SiteGroup.PROMOTED -> 400.0
            SiteGroup.BEST -> 300.0
            SiteGroup.SHARE -> 200.0
            SiteGroup.NEW -> 150.0
            SiteGroup.CUSTOM -> 100.0
            SiteGroup.LATEST -> 50.0
            SiteGroup.UNVERIFIED -> 20.0
            SiteGroup.CONFIRMED_SCAMS -> 0.0
        }
        return base + (1.0 / (catalogOrderIndex.coerceAtLeast(0) + 1.0))
    }

    fun hasReferlink(account: AccountResult.Ok?): Boolean {
        if (account == null) return false
        return isLitBonus(account.referral)
    }

    private fun bottomTier(hasPrimary: Boolean, result: AccountResult?): Int {
        if (result is AccountResult.Err) return 3
        if (!hasPrimary) {
            val ok = result as? AccountResult.Ok
            return if (hasAnyRealBonus(ok)) 1 else 2
        }
        return 0
    }

    data class SortableEntry(
        val domain: String,
        val group: SiteGroup,
        val addedAt: String,
        val catalogIndex: Int,
        val result: AccountResult?,
        val isDead: Boolean = false
    )

    private data class PrimaryMetric(
        val value: Double,
        val has: Boolean,
        val ascending: Boolean
    )

    fun compareEntries(
        a: SortableEntry,
        b: SortableEntry,
        sortMode: SortMode,
        bonusMode: BonusMode
    ): Int {
        fun azDelta(): Int = naturalDomainCompare(a.domain, b.domain)

        if (a.isDead != b.isDead && sortMode != SortMode.ERRORS) {
            return if (a.isDead) 1 else -1
        }

        if (sortMode == SortMode.DEFAULT) {
            val idxCmp = a.catalogIndex.compareTo(b.catalogIndex)
            return if (idxCmp != 0) idxCmp else azDelta()
        }
        if (sortMode == SortMode.AZ) return azDelta()
        if (sortMode == SortMode.ZA) return -azDelta()

        if (sortMode == SortMode.ERRORS) {
            val errA = a.result as? AccountResult.Err
            val errB = b.result as? AccountResult.Err
            val hasErrA = errA != null
            val hasErrB = errB != null
            if (hasErrA != hasErrB) return if (hasErrA) -1 else 1
            if (errA != null && errB != null) {
                val msgCmp = errA.message.lowercase(Locale.US).compareTo(errB.message.lowercase(Locale.US))
                if (msgCmp != 0) return msgCmp
            }
            return azDelta()
        }

        val okA = a.result as? AccountResult.Ok
        val okB = b.result as? AccountResult.Ok

        if (sortMode == SortMode.NO_REFER) {
            fun rank(ok: AccountResult.Ok?): Int = when {
                ok == null -> 1
                hasReferlink(ok) -> 2
                else -> 0
            }
            val r = rank(okA) - rank(okB)
            return if (r != 0) r else azDelta()
        }

        fun primary(entry: SortableEntry, ok: AccountResult.Ok?): PrimaryMetric? = when (sortMode) {
            SortMode.BALANCE -> {
                val v = if (ok != null) effectiveCash(ok) else Double.NaN
                PrimaryMetric(v, ok != null && v.isFinite(), ascending = false)
            }
            SortMode.TURNOVER -> {
                val v = bestTurnover(ok)
                PrimaryMetric(v ?: Double.POSITIVE_INFINITY, v != null && v.isFinite(), ascending = true)
            }
            SortMode.CASHOUT -> {
                val v = ok?.cashMinWithdrawal
                PrimaryMetric(v ?: Double.POSITIVE_INFINITY, v != null && v > 0.0, ascending = true)
            }
            SortMode.CHOSEN_AMOUNT -> {
                val bonus = chosenBonus(ok, bonusMode)
                val v = if (bonusHasRealAmount(bonus)) bonus!!.amount else Double.NaN
                PrimaryMetric(v, v.isFinite() && v > 0.0, ascending = false)
            }
            SortMode.CHOSEN_MAX -> {
                val bonus = chosenBonus(ok, bonusMode)
                val v = if (bonus?.lit == true && bonus.maxWithdrawal != null) bonus.maxWithdrawal else Double.NaN
                PrimaryMetric(v, v.isFinite() && v > 0.0, ascending = false)
            }
            SortMode.CHOSEN_TURNOVER -> {
                val bonus = chosenBonus(ok, bonusMode)
                val v = bonusTurnover(bonus)
                PrimaryMetric(v ?: Double.POSITIVE_INFINITY, v != null && v.isFinite(), ascending = true)
            }
            SortMode.NEW -> {
                val ts = FormatUtil.parseIsoEpochMs(entry.addedAt).toDouble()
                PrimaryMetric(ts, ts > 0.0, ascending = false)
            }
            SortMode.POPULAR -> {
                val score = popularityScore(entry.group, entry.catalogIndex)
                PrimaryMetric(score, score > 0.0, ascending = false)
            }
            else -> null
        }

        val pa = primary(a, okA)
        val pb = primary(b, okB)
        if (pa != null && pb != null) {
            val ta = bottomTier(pa.has, a.result)
            val tb = bottomTier(pb.has, b.result)
            if (ta != tb) return ta - tb
            if (ta != 0) return azDelta()
            val delta = if (pa.ascending) pa.value.compareTo(pb.value) else pb.value.compareTo(pa.value)
            return if (delta != 0) delta else azDelta()
        }

        return azDelta()
    }

    private fun naturalDomainCompare(a: String, b: String): Int {
        val la = a.lowercase(Locale.US)
        val lb = b.lowercase(Locale.US)
        var ia = 0
        var ib = 0
        while (ia < la.length && ib < lb.length) {
            val ca = la[ia]
            val cb = lb[ib]
            if (ca.isDigit() && cb.isDigit()) {
                var numA = 0L
                while (ia < la.length && la[ia].isDigit()) {
                    numA = numA * 10L + (la[ia] - '0')
                    ia++
                }
                var numB = 0L
                while (ib < lb.length && lb[ib].isDigit()) {
                    numB = numB * 10L + (lb[ib] - '0')
                    ib++
                }
                if (numA != numB) return numA.compareTo(numB)
            } else {
                if (ca != cb) return ca.compareTo(cb)
                ia++
                ib++
            }
        }
        return la.length.compareTo(lb.length)
    }
}
