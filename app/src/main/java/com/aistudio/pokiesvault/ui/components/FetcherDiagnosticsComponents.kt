package com.aistudio.pokiesvault.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aistudio.pokiesvault.R
import com.aistudio.pokiesvault.data.model.CheckpointPauseReason
import com.aistudio.pokiesvault.data.model.FetchCheckpoint
import com.aistudio.pokiesvault.data.model.FetchSpeedPreset
import com.aistudio.pokiesvault.ui.theme.LocalVaultPalette
import com.aistudio.pokiesvault.ui.theme.MonoTextStyles
import com.aistudio.pokiesvault.ui.viewmodel.RejectedReasonGroup
import com.aistudio.pokiesvault.util.BatteryOptimizationHelper

enum class SpeedSelectorMode {
    FETCHER,
    BONUS_FINDER
}

/**
 * Interactive 3-tier speed selector (Slow (Safe) / Medium (Default) / Fast (Caution))
 * that tunes worker concurrency and per-IP spacing independently for Fetcher and Bonus Finder.
 */
@Composable
fun SpeedPresetSelector(
    selected: FetchSpeedPreset,
    enabled: Boolean,
    onSelect: (FetchSpeedPreset) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "FETCH SPEED PRESET",
    tagPrefix: String = "speed_preset",
    mode: SpeedSelectorMode = SpeedSelectorMode.FETCHER
) {
    val palette = LocalVaultPalette.current
    val headerMetric = when (mode) {
        SpeedSelectorMode.FETCHER -> selected.fetcherSubtitle
        SpeedSelectorMode.BONUS_FINDER -> selected.bonusSubtitle
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp
                )
            )
            Text(
                text = headerMetric,
                color = palette.accentA,
                style = MonoTextStyles.metricSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FetchSpeedPreset.entries.forEach { preset ->
                val isSelected = preset == selected
                val containerColor = if (isSelected) {
                    palette.accentA.copy(alpha = 0.16f).compositeOver(MaterialTheme.colorScheme.surfaceVariant)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
                val borderColor = if (isSelected) {
                    palette.accentA
                } else {
                    MaterialTheme.colorScheme.outline
                }

                val isCaution = preset == FetchSpeedPreset.BALANCED
                val badgeColor = when {
                    isCaution -> palette.slow
                    isSelected -> palette.accentA.copy(alpha = 0.88f)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                val chipMetric = when (mode) {
                    SpeedSelectorMode.FETCHER -> preset.fetcherChipMetric
                    SpeedSelectorMode.BONUS_FINDER -> preset.bonusChipMetric
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = containerColor,
                    border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 64.dp)
                        .testTag("${tagPrefix}_${preset.name.lowercase()}")
                        .clickable(enabled = enabled) { onSelect(preset) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = preset.tierTitle,
                            color = if (isSelected) palette.accentA else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = preset.tierBadge,
                            color = badgeColor,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = chipMetric,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MonoTextStyles.metricSmall.copy(fontSize = 10.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Subtle, Play-compliant battery settings shortcut shown when Background Runs is ON
 * and Android currently applies battery optimization restrictions to Pokies Vault.
 */
@Composable
fun BackgroundBatteryHintBanner(
    allowBackgroundRuns: Boolean,
    modifier: Modifier = Modifier,
    tagPrefix: String = ""
) {
    if (!allowBackgroundRuns) return

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val palette = LocalVaultPalette.current
    var dismissed by remember { mutableStateOf(false) }
    var isUnrestricted by remember {
        mutableStateOf(BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context))
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isUnrestricted = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (isUnrestricted || dismissed) return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("${tagPrefix}bg_battery_hint_banner"),
        shape = RoundedCornerShape(14.dp),
        color = palette.slow.copy(alpha = 0.09f).compositeOver(MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, palette.slow.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.BatteryChargingFull,
                contentDescription = null,
                tint = palette.slow,
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.bg_battery_hint_title),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Set Battery → Unrestricted to keep minimized scans alive.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(
                onClick = { BatteryOptimizationHelper.openBatterySettings(context) },
                border = BorderStroke(1.dp, palette.slow),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("${tagPrefix}bg_battery_settings_btn")
            ) {
                Text(
                    text = stringResource(R.string.bg_battery_hint_action),
                    color = palette.slow,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
            }
            IconButton(
                onClick = { dismissed = true },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("${tagPrefix}bg_battery_dismiss_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss battery hint",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun formatGapLabel(gapMs: Long): String {
    return if (gapMs >= 1000L && gapMs % 1000L == 0L) {
        "${gapMs / 1000L}s"
    } else if (gapMs >= 1000L) {
        "${gapMs / 1000.0}s"
    } else {
        "${gapMs}ms"
    }
}

/**
 * Actionable recovery banner displayed when the WAF circuit breaker auto-pauses a run
 * or when a paused checkpoint is saved on disk.
 */
@Composable
fun WafPauseAndCheckpointBanner(
    checkpoint: FetchCheckpoint,
    currentIp: String?,
    isRefreshingIp: Boolean,
    isFetching: Boolean,
    onRefreshIp: () -> Unit,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
    resumeLabelPrefix: String = "Resume",
    tagPrefix: String = ""
) {
    val palette = LocalVaultPalette.current
    val isWafPause = checkpoint.pauseReason == CheckpointPauseReason.WAF_BLOCKED
    val accentColor = if (isWafPause) palette.slow else palette.accentA
    val bgTint = accentColor.copy(alpha = 0.09f).compositeOver(MaterialTheme.colorScheme.surface)
    val ipChanged = isWafPause &&
        !currentIp.isNullOrBlank() &&
        !checkpoint.pausedAtIp.isNullOrBlank() &&
        currentIp != checkpoint.pausedAtIp

    val doneCount = checkpoint.completedDomains.size
    val totalCount = checkpoint.allTargetDomains.size.coerceAtLeast(doneCount + checkpoint.remainingDomains.size)
    val remainingCount = checkpoint.remainingDomains.size

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("${tagPrefix}checkpoint_banner"),
        shape = RoundedCornerShape(18.dp),
        color = bgTint,
        border = BorderStroke(1.2.dp, accentColor.copy(alpha = 0.65f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (isWafPause) Icons.Default.Shield else Icons.Rounded.Bookmark,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isWafPause) {
                            "Auto-Paused: Firewall / Rate Limit"
                        } else {
                            "Saved Run Checkpoint"
                        },
                        color = accentColor,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "$doneCount / $totalCount processed · $remainingCount remaining",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MonoTextStyles.metricSmall
                    )
                }
            }

            if (isWafPause) {
                if (checkpoint.pauseDetail.isNotBlank()) {
                    Text(
                        text = "Trigger: ${checkpoint.pauseDetail}. Requests were stopped immediately to avoid extending an IP cooldown.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Network / IP check guidance
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Before resuming:",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "1. Toggle Airplane Mode for 15 seconds (or switch Wi-Fi / Mobile Data) to get a fresh IP, or wait for cooldown.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "2. Tap Refresh IP below, consider switching to Slow (Safe) mode, then Resume Remaining.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )

                        if (!checkpoint.pausedAtIp.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            if (ipChanged) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.testTag("new_ip_confirmed_badge")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = palette.live,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "New IP confirmed: ${checkpoint.pausedAtIp} → $currentIp",
                                        color = palette.live,
                                        style = MonoTextStyles.metricSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Text(
                                    text = "Paused at IP: ${checkpoint.pausedAtIp}" +
                                        if (!currentIp.isNullOrBlank()) " (current: $currentIp)" else "",
                                    color = palette.slow,
                                    style = MonoTextStyles.metricSmall
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onResume,
                    enabled = !isFetching,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (ipChanged) palette.live else accentColor,
                        contentColor = palette.ink
                    ),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .weight(1.4f)
                        .heightIn(min = 48.dp)
                        .testTag("${tagPrefix}resume_checkpoint_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$resumeLabelPrefix ($remainingCount)",
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }

                if (isWafPause) {
                    OutlinedButton(
                        onClick = onRefreshIp,
                        enabled = !isRefreshingIp,
                        border = BorderStroke(1.dp, palette.accentA),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("banner_refresh_ip_btn")
                    ) {
                        if (isRefreshingIp) {
                            CircularProgressIndicator(
                                color = palette.accentA,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(15.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Sync,
                                contentDescription = null,
                                tint = palette.accentA,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "IP",
                            color = palette.accentA,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                TextButton(
                    onClick = onDiscard,
                    enabled = !isFetching,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("${tagPrefix}discard_checkpoint_btn")
                ) {
                    Text(
                        text = "Discard",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * Grouped rejection diagnostics panel with category summary chips, expandable domain lists,
 * and per-category or all-recoverable retry actions.
 */
@Composable
fun GroupedRejectedSection(
    groups: List<RejectedReasonGroup>,
    totalRejected: Int,
    retryableCount: Int,
    isFetching: Boolean,
    onRetryCode: (String) -> Unit,
    onRetryAllRecoverable: () -> Unit,
    onSolveCaptcha: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalVaultPalette.current
    val expandedMap = remember { mutableStateMapOf<String, Boolean>() }
    val showAllMap = remember { mutableStateMapOf<String, Boolean>() }
    var selectedChipCode by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("grouped_rejected_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "REJECTED BREAKDOWN · $totalRejected",
                color = palette.dead,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold
            )

            if (retryableCount > 0) {
                OutlinedButton(
                    onClick = onRetryAllRecoverable,
                    enabled = !isFetching,
                    border = BorderStroke(1.dp, palette.slow),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("retry_all_recoverable_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = palette.slow,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Retry Recoverable ($retryableCount)",
                        color = palette.slow,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Category summary chips row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(items = groups, key = { "chip_${it.code}" }) { group ->
                val tone = reasonToneColor(group.code)
                val isHighlighted = selectedChipCode == group.code
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = tone.copy(alpha = if (isHighlighted) 0.24f else 0.12f),
                    border = BorderStroke(1.dp, tone.copy(alpha = if (isHighlighted) 0.9f else 0.45f)),
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .testTag("rejected_chip_${group.code}")
                        .clickable {
                            val next = !(expandedMap[group.code] ?: false)
                            expandedMap[group.code] = next
                            selectedChipCode = if (next) group.code else null
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = group.label,
                            color = tone,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${group.rows.size}",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MonoTextStyles.metricSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        // Expandable group cards
        groups.forEach { group ->
            val tone = reasonToneColor(group.code)
            val isExpanded = expandedMap[group.code] ?: false
            val showAll = showAllMap[group.code] ?: false
            val visibleRows = if (showAll || group.rows.size <= 30) {
                group.rows
            } else {
                group.rows.take(30)
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    if (isExpanded) tone.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rejected_group_${group.code}")
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clickable {
                                expandedMap[group.code] = !isExpanded
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = group.label,
                                    color = tone,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = tone.copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = "${group.rows.size}",
                                        color = tone,
                                        style = MonoTextStyles.metricSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = reasonHintText(group.code),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (group.isRetryable) {
                                TextButton(
                                    onClick = { onRetryCode(group.code) },
                                    enabled = !isFetching,
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("retry_group_${group.code}_btn")
                                ) {
                                    Text(
                                        text = "Retry",
                                        color = palette.accentA,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isExpanded) "Collapse ${group.label}" else "Expand ${group.label}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    AnimatedVisibility(visible = isExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
                        ) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(modifier = Modifier.height(6.dp))
                            visibleRows.forEach { err ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 5.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = err.domain,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = err.message,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (group.code == "captcha") {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        OutlinedButton(
                                            onClick = { onSolveCaptcha(err.domain) },
                                            enabled = !isFetching,
                                            border = BorderStroke(1.dp, palette.accentC),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            shape = MaterialTheme.shapes.small,
                                            modifier = Modifier
                                                .heightIn(min = 48.dp)
                                                .testTag("solve_captcha_${err.domain}")
                                        ) {
                                            Text(
                                                text = "Solve Captcha",
                                                color = palette.accentC,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    } else if (err.latencyMs > 0L) {
                                        Text(
                                            text = "${err.latencyMs}ms",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MonoTextStyles.metricSmall
                                        )
                                    }
                                }
                            }
                            if (!showAll && group.rows.size > 30) {
                                TextButton(
                                    onClick = { showAllMap[group.code] = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("show_all_group_${group.code}_btn")
                                ) {
                                    Text(
                                        text = "Show all ${group.rows.size} domains",
                                        color = palette.accentA,
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

@Composable
private fun reasonToneColor(code: String): Color {
    val palette = LocalVaultPalette.current
    return when (code) {
        "blocked" -> palette.dead
        "rate_limit" -> palette.slow
        "timeout" -> palette.slow
        "unreachable" -> MaterialTheme.colorScheme.onSurfaceVariant
        "captcha" -> palette.accentC
        "invalid_login" -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> palette.accentB
    }
}

private fun reasonHintText(code: String): String = when (code) {
    "blocked" -> "Firewall or WAF rejected request · retry after cooldown or IP change"
    "rate_limit" -> "HTTP 429 throttle · switch to Slow (Safe) or wait before retrying"
    "timeout" -> "Site took too long to respond · safe to retry"
    "unreachable" -> "Site offline or did not return API JSON"
    "captcha" -> "Merchant requires AliyunCaptcha once · tap Solve Captcha to save session"
    "invalid_login" -> "No matching account for your configured phone/password"
    else -> "Unexpected sign-in error"
}
