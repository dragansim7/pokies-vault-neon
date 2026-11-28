package com.aistudio.pokiesvault.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.pokiesvault.data.model.FetchActiveLane
import com.aistudio.pokiesvault.ui.theme.LocalVaultPalette
import com.aistudio.pokiesvault.ui.theme.MonoTextStyles
import com.aistudio.pokiesvault.ui.viewmodel.BonusFinderUiState
import com.aistudio.pokiesvault.ui.viewmodel.EgressUiState
import com.aistudio.pokiesvault.ui.viewmodel.FetcherUiState

@Composable
fun OutgoingIpCard(
    egressState: EgressUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    noteText: String = "Sign-in from this device · casinos see this IP"
) {
    val palette = LocalVaultPalette.current
    val info = egressState.info

    val infiniteTransition = rememberInfiniteTransition(label = "ip_refresh_spin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ip_refresh_angle"
    )

    val cardBg = palette.live.copy(alpha = 0.065f).compositeOver(MaterialTheme.colorScheme.surface)
    val borderColor = if (egressState.error != null && info == null) {
        palette.dead.copy(alpha = 0.55f)
    } else {
        palette.live.copy(alpha = 0.52f)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("outgoing_ip_card"),
        shape = RoundedCornerShape(18.dp),
        color = cardBg,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Outgoing IP:",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 19.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = when {
                            info != null -> info.ip
                            egressState.isLoading -> "Checking IP…"
                            else -> "Unavailable"
                        },
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MonoTextStyles.balanceLarge.copy(
                            fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("outgoing_ip_value")
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (egressState.error != null && info == null) {
                            palette.dead
                        } else {
                            palette.live
                        },
                        modifier = Modifier.testTag("local_device_ip_badge")
                    ) {
                        Text(
                            text = "LOCAL DEVICE IP",
                            color = palette.ink,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp,
                                fontSize = 10.sp
                            ),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    IconButton(
                        onClick = onRefresh,
                        enabled = !egressState.isLoading,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("refresh_ip_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Sync,
                            contentDescription = "Refresh outgoing IP",
                            tint = palette.accentA,
                            modifier = Modifier
                                .size(22.dp)
                                .rotate(if (egressState.isLoading) spinAngle else 0f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ISP & Location line with soft glowing status indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(palette.live.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                if (egressState.error != null && info == null) palette.dead else palette.live
                            )
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                val networkText = when {
                    info != null -> listOf(info.isp, info.location)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                        .ifBlank { "Local network" }
                    egressState.isLoading -> "Detecting ISP and region…"
                    else -> egressState.error ?: "Tap refresh to retry IP lookup"
                }
                Text(
                    text = networkText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("outgoing_ip_isp")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sign-in provenance note
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = palette.accentA,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = noteText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun LiveFetchStatsCard(
    state: FetcherUiState,
    modifier: Modifier = Modifier
) {
    val palette = LocalVaultPalette.current
    val percent = if (state.progressTotal > 0) {
        ((state.progressDone * 100) / state.progressTotal).coerceIn(0, 100)
    } else {
        0
    }
    val signedIn = state.rawSignedInCount
    val rejected = state.rawRejectedCount
    val withBalance = state.rawWithBalanceCount
    val nothingWaiting = state.rawNothingWaitingCount
    val queue = if (state.isFetching) {
        (state.progressTotal - state.progressDone - state.activeWorkers).coerceAtLeast(0)
    } else {
        0
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("live_fetch_stats_card"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LIVE FETCH",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.6.sp
                    )
                )
                if (state.isFetching && state.activeLane != FetchActiveLane.IDLE) {
                    val laneColor = when (state.activeLane) {
                        FetchActiveLane.SAVED_FAST -> palette.live
                        FetchActiveLane.COLD_LOGIN -> palette.accentA
                        FetchActiveLane.DNS_CLUSTERING -> palette.accentB
                        FetchActiveLane.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = laneColor.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, laneColor.copy(alpha = 0.55f)),
                        modifier = Modifier.testTag("live_fetch_lane_badge")
                    ) {
                        Text(
                            text = state.activeLane.label.uppercase(),
                            color = laneColor,
                            style = MonoTextStyles.metricSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "From this phone",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 19.sp
                )
            )

            Text(
                text = "${state.progressDone} / ${state.progressTotal} processed · $percent%",
                color = MaterialTheme.colorScheme.onSurface,
                style = MonoTextStyles.balanceLarge.copy(
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.testTag("live_fetch_processed_line")
            )

            Text(
                text = state.liveStatusLabel,
                color = when {
                    state.cooldownSeconds > 0 -> palette.slow
                    state.isFetching -> palette.accentA
                    state.liveStatusLabel.contains("WAF", ignoreCase = true) -> palette.slow
                    state.liveStatusLabel.startsWith("Paused", ignoreCase = true) -> palette.accentA
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (state.cooldownSeconds > 0 || state.liveStatusLabel.contains("WAF", ignoreCase = true)) {
                    FontWeight.SemiBold
                } else {
                    FontWeight.Normal
                },
                modifier = Modifier.testTag("live_fetch_status_line")
            )

            if (state.cooldownSeconds > 0) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = palette.slow.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, palette.slow.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("live_fetch_cooldown_banner")
                ) {
                    Text(
                        text = "${state.cooldownReason.ifBlank { "Auto-cooldown" }} · resuming automatically in ${state.cooldownSeconds}s",
                        color = palette.slow,
                        style = MonoTextStyles.metricSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            Text(
                text = "$signedIn signed in · $rejected rejected · $withBalance with a balance · $nothingWaiting nothing waiting",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("live_fetch_outcome_line")
            )

            Text(
                text = "Queue $queue · workers ${state.activeWorkers} · peak ${state.peakWorkers}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MonoTextStyles.metricSmall.copy(fontSize = 13.sp),
                modifier = Modifier.testTag("live_fetch_workers_line")
            )

            val fetchRateSuffix = if (state.isFetching && state.rateSnapshot.sitesPerSec >= 0.05) {
                val etaPart = state.rateSnapshot.etaSeconds?.let { " · ETA ${state.rateSnapshot.etaLabel}" } ?: ""
                " · ${state.rateSnapshot.rateLabel}$etaPart"
            } else {
                ""
            }
            Text(
                text = "Wall ${state.wallElapsedSec}s · sign-in ${state.signInElapsedSec}s$fetchRateSuffix",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MonoTextStyles.metricSmall.copy(fontSize = 13.sp),
                modifier = Modifier.testTag("live_fetch_timing_line")
            )

            val sessionSuffix = when {
                state.sessionReusedCount > 0 -> " · ${state.sessionReusedCount} sessions reused"
                state.savedSessionDomains.isNotEmpty() -> " · ${state.savedSessionDomains.size} saved sessions"
                else -> ""
            }
            Text(
                text = "Retries ${state.totalRetries} · attempts ${state.totalAttempts}$sessionSuffix",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MonoTextStyles.metricSmall.copy(fontSize = 13.sp),
                modifier = Modifier.testTag("live_fetch_retries_line")
            )
        }
    }
}

@Composable
fun LiveBonusScanStatsCard(
    state: BonusFinderUiState,
    modifier: Modifier = Modifier
) {
    val palette = LocalVaultPalette.current
    val percent = if (state.progressTotal > 0) {
        ((state.progressDone * 100) / state.progressTotal).coerceIn(0, 100)
    } else {
        0
    }
    val queue = if (state.isScanning) {
        (state.progressTotal - state.progressDone - state.activeWorkers).coerceAtLeast(0)
    } else {
        0
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("live_bonus_stats_card"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "LIVE BONUS SCAN",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp
                )
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "From this phone",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 19.sp
                )
            )

            Text(
                text = "${state.progressDone} / ${state.progressTotal} processed · $percent%",
                color = MaterialTheme.colorScheme.onSurface,
                style = MonoTextStyles.balanceLarge.copy(
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.testTag("live_bonus_processed_line")
            )

            Text(
                text = state.liveStatusLabel,
                color = when {
                    state.isScanning -> palette.accentA
                    state.liveStatusLabel.contains("WAF", ignoreCase = true) -> palette.slow
                    state.liveStatusLabel.startsWith("Paused", ignoreCase = true) -> palette.accentA
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (state.liveStatusLabel.contains("WAF", ignoreCase = true)) {
                    FontWeight.SemiBold
                } else {
                    FontWeight.Normal
                },
                modifier = Modifier.testTag("live_bonus_status_line")
            )

            Text(
                text = "${state.loadedTotalCount} loaded · ${state.failedTotalCount} failed · ${state.withOffersCount} with offers · ${state.unloadedTotalCount} unloaded",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("live_bonus_outcome_line")
            )

            Text(
                text = "Queue $queue · workers ${state.activeWorkers} · peak ${state.peakWorkers}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MonoTextStyles.metricSmall.copy(fontSize = 13.sp),
                modifier = Modifier.testTag("live_bonus_workers_line")
            )

            val bonusRateSuffix = if (state.isScanning && state.rateSnapshot.sitesPerSec >= 0.05) {
                val etaPart = state.rateSnapshot.etaSeconds?.let { " · ETA ${state.rateSnapshot.etaLabel}" } ?: ""
                " · ${state.rateSnapshot.rateLabel}$etaPart"
            } else {
                ""
            }
            Text(
                text = "Wall ${state.wallElapsedSec}s · scan ${state.scanElapsedSec}s$bonusRateSuffix",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MonoTextStyles.metricSmall.copy(fontSize = 13.sp),
                modifier = Modifier.testTag("live_bonus_timing_line")
            )

            Text(
                text = "Retries ${state.totalRetries} · attempts ${state.totalAttempts}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MonoTextStyles.metricSmall.copy(fontSize = 13.sp),
                modifier = Modifier.testTag("live_bonus_retries_line")
            )
        }
    }
}
