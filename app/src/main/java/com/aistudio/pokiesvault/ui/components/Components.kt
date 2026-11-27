package com.aistudio.pokiesvault.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aistudio.pokiesvault.R
import com.aistudio.pokiesvault.data.model.AccountResult
import com.aistudio.pokiesvault.data.model.BonusView
import com.aistudio.pokiesvault.data.model.SiteStatus
import com.aistudio.pokiesvault.ui.theme.LocalVaultPalette
import com.aistudio.pokiesvault.ui.theme.MonoTextStyles
import com.aistudio.pokiesvault.ui.viewmodel.DirectorySiteItem
import com.aistudio.pokiesvault.util.FormatUtil

fun openExternalUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}

@Composable
fun statusColorFor(status: SiteStatus): Color {
    val palette = LocalVaultPalette.current
    return when (status) {
        SiteStatus.LIVE -> palette.live
        SiteStatus.SLOW -> palette.slow
        SiteStatus.REDIRECT -> palette.accentB
        SiteStatus.DEAD -> palette.dead
        SiteStatus.CHECKING -> palette.accentA
        SiteStatus.UNKNOWN -> palette.faint
    }
}

@Composable
fun SiteAvatar(
    domain: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalVaultPalette.current
    val tones = remember(palette) {
        listOf(palette.accentA, palette.accentB, palette.accentC, palette.live)
    }
    val color = tones[(domain.firstOrNull()?.code ?: 0).mod(tones.size)]
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = FormatUtil.initials(domain),
            color = color,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
fun StatusDotLabel(
    status: SiteStatus,
    latencyMs: Long = 0L,
    extraText: String = "",
    modifier: Modifier = Modifier
) {
    val color = statusColorFor(status)
    val label = buildString {
        append(status.label)
        if (latencyMs > 0L && status != SiteStatus.CHECKING) {
            append(" · ")
            append(FormatUtil.formatLatency(latencyMs))
        }
        if (extraText.isNotBlank()) {
            append(" · ")
            append(extraText)
        }
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MonoTextStyles.metricSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SiteCard(
    item: DirectorySiteItem,
    compact: Boolean,
    onCardClick: () -> Unit,
    onDetailClick: () -> Unit,
    onBookmarkToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalVaultPalette.current
    val domain = item.site.domain

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                onCardClick()
                openExternalUrl(context, "https://$domain/")
            }
            .testTag("site_card_$domain"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (item.site.isMine) palette.accentA.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline
        )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = if (compact) 10.dp else 12.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!compact) {
                SiteAvatar(domain = domain)
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = domain,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                StatusDotLabel(
                    status = item.status,
                    latencyMs = item.latencyMs,
                    extraText = if (!compact && item.site.isVisited) "Visited" else ""
                )
                if (!item.walletText.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.walletText,
                        color = palette.live,
                        style = MonoTextStyles.metricSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!compact && !item.bonusText.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.bonusText,
                        color = palette.accentC,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (!compact && item.account != null) {
                IconButton(
                    onClick = onDetailClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("site_detail_button_$domain")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = "Inspect $domain account",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            IconButton(
                onClick = onBookmarkToggle,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("bookmark_button_$domain")
            ) {
                Icon(
                    imageVector = if (item.site.isMine) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = if (item.site.isMine) "Remove $domain from my accounts" else "Save $domain to my accounts",
                    tint = if (item.site.isMine) palette.accentA else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun AccountResultCard(
    row: AccountResult.Ok,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalVaultPalette.current
    var expandedBonuses by remember(row.domain) { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("result_card_${row.domain}"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (row.cash > 0.01) palette.live.copy(alpha = 0.32f) else MaterialTheme.colorScheme.outline
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    SiteAvatar(domain = row.domain, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = row.domain,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (row.latencyMs > 0L) {
                            Text(
                                text = buildString {
                                    append(FormatUtil.formatLatency(row.latencyMs))
                                    if (row.retriesUsed > 0) append(" · ${row.retriesUsed} retry")
                                },
                                color = palette.faint,
                                style = MonoTextStyles.metricSmall
                            )
                        }
                    }
                }
                TextButton(
                    onClick = { openExternalUrl(context, "https://${row.domain}/") },
                    modifier = Modifier.testTag("open_site_btn_${row.domain}")
                ) {
                    Text("Open", color = palette.accentA, style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.OpenInNew,
                        contentDescription = "Open ${row.domain}",
                        tint = palette.accentA,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = FormatUtil.money(row.cash),
                    color = if (row.cash > 0.001) palette.live else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MonoTextStyles.balanceLarge
                )
                Spacer(modifier = Modifier.width(8.dp))
                val limits = buildString {
                    append(row.cashWalletName)
                    if (row.cashMinWithdrawal != null) append(" · Min ${FormatUtil.moneyCompact(row.cashMinWithdrawal)}")
                    if (row.cashMaxWithdrawal != null) append(" · Max ${FormatUtil.moneyCompact(row.cashMaxWithdrawal)}")
                }
                Text(
                    text = limits,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BonusGridItem(label = "Commission", bonus = row.commission, modifier = Modifier.weight(1f))
                BonusGridItem(label = "Share", bonus = row.share, modifier = Modifier.weight(1f))
                BonusGridItem(label = "Referral", bonus = row.referral, modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BonusGridItem(label = "Welcome", bonus = row.newMember, modifier = Modifier.weight(1f))
                BonusGridItem(label = "Rebate", bonus = row.rebate, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.weight(1f))
            }

            val litBonuses = remember(row) { com.aistudio.pokiesvault.util.SiteOrderUtil.litClaimableBonuses(row) }
            if (litBonuses.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedBonuses = !expandedBonuses }
                        .testTag("expand_bonuses_${row.domain}"),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Lit claimable bonuses (${litBonuses.size})",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelMedium
                            )
                            Icon(
                                imageVector = if (expandedBonuses) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (expandedBonuses) "Collapse bonuses" else "Expand bonuses",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        AnimatedVisibility(
                            visible = expandedBonuses,
                            enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)),
                            exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow))
                        ) {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                litBonuses.forEach { bonus ->
                                    val turnStr = com.aistudio.pokiesvault.util.SiteOrderUtil.formatTurnover(
                                        com.aistudio.pokiesvault.util.SiteOrderUtil.bonusTurnover(bonus)
                                    )
                                    val appTag = if (bonus.mobileAppOnly) " [APP]" else ""
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = bonus.name + appTag + if (turnStr.isNotBlank()) " · $turnStr" else "",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = bonus.amountDisplay.ifBlank { "?" },
                                            color = palette.accentA,
                                            style = MonoTextStyles.metricSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BonusGridItem(
    label: String,
    bonus: BonusView?,
    modifier: Modifier = Modifier
) {
    val palette = LocalVaultPalette.current
    val isLit = com.aistudio.pokiesvault.util.SiteOrderUtil.isLitBonus(bonus)
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp)) {
            Text(
                text = label,
                color = palette.faint,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            if (isLit && bonus != null) {
                Text(
                    text = bonus.amountDisplay.ifBlank { "?" },
                    color = palette.live,
                    style = MonoTextStyles.metricSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = "—",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MonoTextStyles.metricSmall
                )
            }
        }
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val palette = LocalVaultPalette.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(palette.accentA.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = palette.accentA,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(14.dp))
                TextButton(onClick = onAction) {
                    Text(actionLabel, color = palette.accentA, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
fun ResponsibleGamblingFooter(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val palette = LocalVaultPalette.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = palette.dead.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, palette.dead.copy(alpha = 0.7f))
            ) {
                Text(
                    text = "18+",
                    color = palette.dead,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Gamble responsibly",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.gamble_responsibly),
            color = palette.accentA,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clickable { openExternalUrl(context, "https://www.gamblinghelponline.org.au/") }
                .padding(vertical = 4.dp)
                .testTag("gambling_help_link")
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.vault_disclaimer),
            color = palette.faint,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    }
}
