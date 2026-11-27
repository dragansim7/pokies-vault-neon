package com.aistudio.pokiesvault.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.pokiesvault.data.model.AccountResult
import com.aistudio.pokiesvault.data.model.BonusMode
import com.aistudio.pokiesvault.data.model.BonusView
import com.aistudio.pokiesvault.data.model.CardColor
import com.aistudio.pokiesvault.data.model.SiteStatus
import com.aistudio.pokiesvault.data.model.SortMode
import com.aistudio.pokiesvault.ui.theme.LocalVaultPalette
import com.aistudio.pokiesvault.ui.theme.MonoTextStyles
import com.aistudio.pokiesvault.ui.theme.VaultCardColors
import com.aistudio.pokiesvault.ui.viewmodel.DirectorySiteItem
import com.aistudio.pokiesvault.util.FormatUtil
import com.aistudio.pokiesvault.util.SiteOrderUtil

@Composable
fun DomainWalletCard(
    item: DirectorySiteItem,
    walletMode: Boolean,
    bonusMode: BonusMode,
    cardColor: CardColor,
    sortMode: SortMode,
    compact: Boolean = false,
    initialExpanded: Boolean = false,
    onCardClick: () -> Unit,
    onDetailClick: () -> Unit,
    onBookmarkToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalVaultPalette.current
    val domain = item.site.domain
    val account = item.account
    val showWalletBody = walletMode && account != null && !compact
    val litBonuses = remember(account) { SiteOrderUtil.litClaimableBonuses(account) }
    val activeWalletCount = remember(account) {
        account?.wallets?.count { it.balance > 0.001 } ?: 0
    }
    val hasExpandableDetails = showWalletBody && (litBonuses.isNotEmpty() || activeWalletCount > 1)

    var expanded by remember(domain, initialExpanded) { mutableStateOf(initialExpanded && hasExpandableDetails) }

    val customGlow = VaultCardColors.glowColorFor(cardColor)
    val defaultGoldBorder = if (account != null && account.hasData) {
        VaultCardColors.GoldAccent.copy(alpha = 0.55f)
    } else if (item.site.isMine) {
        palette.accentA.copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.outline
    }

    val borderColor = customGlow?.copy(alpha = 0.85f) ?: defaultGoldBorder
    val borderWidth = if (customGlow != null) 1.5.dp else 1.dp
    val cardBg = if (customGlow != null) {
        customGlow.copy(alpha = 0.055f).compositeOver(MaterialTheme.colorScheme.surface)
    } else if (showWalletBody && account.hasData) {
        VaultCardColors.GoldAccent.copy(alpha = 0.03f).compositeOver(MaterialTheme.colorScheme.surface)
    } else {
        MaterialTheme.colorScheme.surface
    }

    val shape = RoundedCornerShape(16.dp)
    val glowModifier = if (customGlow != null) {
        modifier
            .fillMaxWidth()
            .shadow(
                elevation = 10.dp,
                shape = shape,
                ambientColor = customGlow,
                spotColor = customGlow
            )
    } else {
        modifier.fillMaxWidth()
    }

    Surface(
        modifier = glowModifier
            .clickable {
                onCardClick()
                openExternalUrl(context, "https://$domain/")
            }
            .testTag("site_card_$domain"),
        shape = shape,
        color = cardBg,
        border = BorderStroke(borderWidth, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (compact) 10.dp else 12.dp, vertical = if (compact) 10.dp else 11.dp)
        ) {
            // --- Top Header Row ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                if (!compact) {
                    SiteAvatar(
                        domain = domain,
                        modifier = Modifier
                            .size(38.dp)
                            .border(
                                width = 1.dp,
                                color = (customGlow ?: VaultCardColors.GoldAccent).copy(alpha = 0.45f),
                                shape = RoundedCornerShape(11.dp)
                            )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }

                // Rank badge + Domain + Age/Status meta
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RankBadge(
                            rank = item.rank,
                            accent = customGlow ?: VaultCardColors.GoldAccent,
                            compact = compact
                        )
                        Text(
                            text = domain,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Metadata line: "2d ago · Link · Active" (or Live / Slow / Dead)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        val statusDotColor = when {
                            account != null && item.status == SiteStatus.UNKNOWN -> palette.live
                            else -> statusColorFor(item.status)
                        }
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(statusDotColor)
                        )
                        val metaLine = buildString {
                            if (item.relativeAge.isNotBlank()) {
                                append(item.relativeAge)
                                append(" · ")
                            }
                            if (item.hasReferlink) {
                                append("Link · ")
                            }
                            val statusText = when {
                                account != null && (item.status == SiteStatus.LIVE || item.status == SiteStatus.UNKNOWN) -> "Active"
                                else -> item.status.label
                            }
                            append(statusText)
                            if (!compact && item.latencyMs > 0L && item.status != SiteStatus.CHECKING) {
                                append(" · ")
                                append(FormatUtil.formatLatency(item.latencyMs))
                            }
                        }
                        Text(
                            text = metaLine,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MonoTextStyles.metricSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Same casino cluster badge when multiple domains share merchantId/resolvedHost
                    if (!compact && item.sameCasinoCount > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        val suffix = if (item.sameCasinoCount == 1) "other domain" else "other domains"
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                        ) {
                            Text(
                                text = "Same casino · ${item.sameCasinoCount} $suffix",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Right Cluster: Balance + limits + Expand Chevron / Bookmark
                if (walletMode && account != null && !compact) {
                    val cash = SiteOrderUtil.effectiveCash(account)
                    Row(verticalAlignment = Alignment.Top) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (cash > 0.001) FormatUtil.money(cash) else "$0",
                                color = if (cash > 0.001) palette.live else MaterialTheme.colorScheme.onSurface,
                                style = MonoTextStyles.balanceLarge.copy(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                maxLines = 1
                            )
                            val minStr = account.cashMinWithdrawal?.let { FormatUtil.moneyCompact(it) }
                            val maxStr = account.cashMaxWithdrawal?.let { FormatUtil.moneyCompact(it) }
                            if (minStr != null || maxStr != null) {
                                val limits = buildString {
                                    if (minStr != null) append("↓ $minStr")
                                    if (minStr != null && maxStr != null) append(" · ")
                                    if (maxStr != null) append("↑ $maxStr")
                                }
                                Text(
                                    text = limits,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MonoTextStyles.metricSmall.copy(fontSize = 11.sp),
                                    maxLines = 1
                                )
                            } else {
                                Text(
                                    text = account.cashWalletName,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MonoTextStyles.metricSmall.copy(fontSize = 11.sp),
                                    maxLines = 1
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        if (hasExpandableDetails) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                            ) {
                                IconButton(
                                    onClick = { expanded = !expanded },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .minimumInteractiveComponentSize()
                                        .testTag("expand_bonuses_$domain")
                                ) {
                                    Icon(
                                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                        contentDescription = if (expanded) "Collapse $domain bonuses" else "Expand $domain bonuses",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        } else {
                            IconButton(
                                onClick = onBookmarkToggle,
                                modifier = Modifier
                                    .size(36.dp)
                                    .minimumInteractiveComponentSize()
                                    .testTag("bookmark_button_$domain")
                            ) {
                                Icon(
                                    imageVector = if (item.site.isMine) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                    contentDescription = if (item.site.isMine) "Remove $domain from saved accounts" else "Save $domain",
                                    tint = if (item.site.isMine) palette.accentA else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }
                } else {
                    // Compact or Wallets-Off right action
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (walletMode && account != null && compact) {
                            val cash = SiteOrderUtil.effectiveCash(account)
                            Text(
                                text = if (cash > 0.001) FormatUtil.moneyCompact(cash) else "$0",
                                color = if (cash > 0.001) palette.live else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MonoTextStyles.metricSmall,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(end = 2.dp)
                            )
                        }
                        IconButton(
                            onClick = onBookmarkToggle,
                            modifier = Modifier
                                .size(34.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("bookmark_button_$domain")
                        ) {
                            Icon(
                                imageVector = if (item.site.isMine) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = if (item.site.isMine) "Remove $domain from saved accounts" else "Save $domain",
                                tint = if (item.site.isMine) palette.accentA else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // --- Error Banner when sorting by Errors or latest snapshot failed ---
            if (item.errorRow != null && (sortMode == SortMode.ERRORS || (!walletMode && account == null))) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = palette.dead.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, palette.dead.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = item.errorRow.message,
                        color = palette.dead,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }

            // --- Chosen Bonus Strip when Wallets is Off or in 2-Column Compact Mode ---
            if (bonusMode != BonusMode.NONE && (!showWalletBody || compact)) {
                val chosen = account?.bonusFor(bonusMode)
                if (chosen != null && SiteOrderUtil.isLitBonus(chosen)) {
                    Spacer(modifier = Modifier.height(6.dp))
                    ChosenBonusHighlightStrip(
                        bonusMode = bonusMode,
                        bonus = chosen,
                        accent = customGlow ?: VaultCardColors.GoldAccent
                    )
                }
            }

            // --- 5-Box Metric Row: COM | SHARE | REF | NEW | REBATE ---
            if (showWalletBody && account != null) {
                Spacer(modifier = Modifier.height(10.dp))
                BonusMetricFiveBoxRow(
                    account = account,
                    chosenBonusMode = bonusMode,
                    highlightAccent = customGlow ?: VaultCardColors.GoldAccent
                )

                // --- Expandable Lit-Only Claimable Bonus Sections ---
                AnimatedVisibility(
                    visible = expanded && hasExpandableDetails,
                    enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)),
                    exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        litBonuses.forEach { bonus ->
                            DetailedBonusRow(
                                bonus = bonus,
                                highlightAccent = customGlow ?: VaultCardColors.GoldAccent
                            )
                        }

                        // Secondary Footer inside expanded view: quick bookmark + inspect action
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val bonusLabel = if (litBonuses.size == 1) {
                                "1 lit claimable bonus"
                            } else {
                                "${litBonuses.size} lit claimable bonuses"
                            }
                            Text(
                                text = if (activeWalletCount > 1) {
                                    "$activeWalletCount active wallets · $bonusLabel"
                                } else {
                                    bonusLabel
                                },
                                color = palette.faint,
                                style = MonoTextStyles.metricSmall
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = if (item.site.isMine) "Saved ✓" else "Save account",
                                    color = if (item.site.isMine) palette.live else palette.accentA,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable { onBookmarkToggle() }
                                        .padding(vertical = 4.dp)
                                )
                                Text(
                                    text = "Details",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier
                                        .clickable { onDetailClick() }
                                        .padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RankBadge(
    rank: Int,
    accent: Color,
    compact: Boolean
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = accent.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.65f))
    ) {
        Text(
            text = "$rank.",
            color = MaterialTheme.colorScheme.onSurface,
            style = MonoTextStyles.metricSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = if (compact) 11.sp else 12.sp
            ),
            modifier = Modifier.padding(horizontal = if (compact) 5.dp else 7.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun BonusMetricFiveBoxRow(
    account: AccountResult.Ok,
    chosenBonusMode: BonusMode,
    highlightAccent: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        MetricBonusBox(
            label = "COM",
            bonus = account.commission,
            isCommission = true,
            isChosen = chosenBonusMode == BonusMode.COMMISSION,
            accent = highlightAccent,
            modifier = Modifier.weight(1f)
        )
        MetricBonusBox(
            label = "SHARE",
            bonus = account.share,
            isCommission = false,
            isChosen = chosenBonusMode == BonusMode.SHARE,
            accent = highlightAccent,
            modifier = Modifier.weight(1f)
        )
        MetricBonusBox(
            label = "REF",
            bonus = account.referral,
            isCommission = false,
            isChosen = chosenBonusMode == BonusMode.REFERRAL,
            accent = highlightAccent,
            modifier = Modifier.weight(1f)
        )
        MetricBonusBox(
            label = "NEW",
            bonus = account.newMember,
            isCommission = false,
            isChosen = chosenBonusMode == BonusMode.NEW,
            accent = highlightAccent,
            modifier = Modifier.weight(1f)
        )
        MetricBonusBox(
            label = "REBATE",
            bonus = account.rebate,
            isCommission = false,
            isChosen = chosenBonusMode == BonusMode.REBATE,
            accent = highlightAccent,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MetricBonusBox(
    label: String,
    bonus: BonusView?,
    isCommission: Boolean,
    isChosen: Boolean,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val palette = LocalVaultPalette.current
    val active = SiteOrderUtil.isLitBonus(bonus)
    val turnoverText = if (!isCommission) SiteOrderUtil.formatTurnover(SiteOrderUtil.bonusTurnover(bonus)) else ""

    val boxBorderColor = when {
        isChosen -> accent
        active -> VaultCardColors.GoldAccent.copy(alpha = 0.85f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
    }
    val boxBorderWidth = when {
        isChosen -> 2.dp
        active -> 1.3.dp
        else -> 1.dp
    }
    val boxBg = when {
        isChosen -> accent.copy(alpha = 0.16f).compositeOver(VaultCardColors.ObsidianRowBg)
        active -> VaultCardColors.GoldWarmSurface
        else -> VaultCardColors.ObsidianRowBg
    }

    Surface(
        modifier = modifier.height(78.dp),
        shape = RoundedCornerShape(12.dp),
        color = boxBg,
        border = BorderStroke(boxBorderWidth, boxBorderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 3.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                color = when {
                    isChosen -> accent
                    active -> VaultCardColors.GoldAccent
                    else -> palette.faint
                },
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.4.sp
                ),
                maxLines = 1
            )

            Text(
                text = if (active) bonus!!.amountDisplay.ifBlank { "?" } else "—",
                color = if (active) MaterialTheme.colorScheme.onSurface else palette.faint,
                style = MonoTextStyles.metricSmall.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            val limitsText = if (active && bonus != null) {
                val maxPart = bonus.maxDisplay
                    ?: bonus.maxWithdrawal?.let { FormatUtil.moneyCompact(it) }
                    ?: "—"
                if (isCommission) {
                    val betPart = bonus.rollover?.let { FormatUtil.moneyCompact(it) } ?: "—"
                    "Bet $betPart ↑$maxPart"
                } else {
                    val minPart = bonus.minWithdrawal?.let { FormatUtil.moneyCompact(it) } ?: "—"
                    "↓$minPart ↑$maxPart"
                }
            } else {
                if (isCommission) "Bet — ↑—" else "↓— ↑—"
            }

            Text(
                text = limitsText,
                color = if (active) MaterialTheme.colorScheme.onSurfaceVariant else palette.faint.copy(alpha = 0.7f),
                style = MonoTextStyles.metricSmall.copy(fontSize = 8.5.sp),
                maxLines = 1,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center
            )

            if (active && turnoverText.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isChosen) accent.copy(alpha = 0.24f) else VaultCardColors.GoldPillSurface
                ) {
                    Text(
                        text = turnoverText,
                        color = if (isChosen) accent else VaultCardColors.GoldAccent,
                        style = MonoTextStyles.metricSmall.copy(
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        ),
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun DetailedBonusRow(
    bonus: BonusView,
    highlightAccent: Color
) {
    val palette = LocalVaultPalette.current
    val minStr = bonus.minWithdrawal?.let { "Min ${FormatUtil.moneyCompact(it)}" }
    val maxText = bonus.maxDisplay ?: bonus.maxWithdrawal?.let { FormatUtil.moneyCompact(it) }
    val maxStr = maxText?.let { "Max $it" }
    val turnoverStr = SiteOrderUtil.formatTurnover(SiteOrderUtil.bonusTurnover(bonus))
    val subtitle = bonus.detail?.takeIf { it.isNotBlank() }
        ?: listOfNotNull(
            minStr,
            maxStr,
            turnoverStr.takeIf { it.isNotBlank() }
        ).joinToString(" · ")

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = VaultCardColors.ObsidianRowBg,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = bonus.name.uppercase(),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.3.sp
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (bonus.mobileAppOnly) {
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = palette.accentA.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, palette.accentA.copy(alpha = 0.55f))
                        ) {
                            Text(
                                text = "APP",
                                color = palette.accentA,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                ),
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MonoTextStyles.metricSmall.copy(fontSize = 11.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = bonus.amountDisplay.ifBlank { "?" },
                color = highlightAccent,
                style = MonoTextStyles.balanceMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ChosenBonusHighlightStrip(
    bonusMode: BonusMode,
    bonus: BonusView,
    accent: Color
) {
    val turnover = SiteOrderUtil.formatTurnover(SiteOrderUtil.bonusTurnover(bonus))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = accent.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.65f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 9.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${bonusMode.emoji} ${bonusMode.label}",
                color = accent,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = bonus.amountDisplay.ifBlank { "?" },
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MonoTextStyles.metricSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                if (turnover.isNotBlank()) {
                    Text(
                        text = turnover,
                        color = accent,
                        style = MonoTextStyles.metricSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
