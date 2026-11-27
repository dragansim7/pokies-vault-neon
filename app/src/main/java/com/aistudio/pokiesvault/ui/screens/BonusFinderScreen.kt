package com.aistudio.pokiesvault.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aistudio.pokiesvault.R
import com.aistudio.pokiesvault.data.model.BonusAmountSort
import com.aistudio.pokiesvault.data.model.BonusStatusFilter
import com.aistudio.pokiesvault.data.model.PublicBonus
import com.aistudio.pokiesvault.data.model.PublicBonusSlot
import com.aistudio.pokiesvault.ui.components.BackgroundBatteryHintBanner
import com.aistudio.pokiesvault.ui.components.EmptyStateCard
import com.aistudio.pokiesvault.ui.components.LiveBonusScanStatsCard
import com.aistudio.pokiesvault.ui.components.OutgoingIpCard
import com.aistudio.pokiesvault.ui.components.ResponsibleGamblingFooter
import com.aistudio.pokiesvault.ui.components.SpeedPresetSelector
import com.aistudio.pokiesvault.ui.components.SpeedSelectorMode
import com.aistudio.pokiesvault.ui.components.WafPauseAndCheckpointBanner
import com.aistudio.pokiesvault.ui.components.openExternalUrl
import com.aistudio.pokiesvault.ui.theme.LocalVaultPalette
import com.aistudio.pokiesvault.ui.theme.MonoTextStyles
import com.aistudio.pokiesvault.ui.viewmodel.BonusFinderItem
import com.aistudio.pokiesvault.ui.viewmodel.VaultViewModel
import com.aistudio.pokiesvault.util.FormatUtil

private val FILTERABLE_SLOTS = listOf(
    PublicBonusSlot.DEPOSIT,
    PublicBonusSlot.SIGNUP,
    PublicBonusSlot.SHARE,
    PublicBonusSlot.DAILY,
    PublicBonusSlot.BIRTHDAY
)

@Composable
fun BonusFinderScreen(
    viewModel: VaultViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.bonusFinderState.collectAsStateWithLifecycle()
    val egressState by viewModel.egressState.collectAsStateWithLifecycle()
    val searchInput by viewModel.bonusSearchInput.collectAsStateWithLifecycle()
    val palette = LocalVaultPalette.current
    var sortPanelOpen by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 860.dp)
                .padding(horizontal = 14.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Live Outgoing Egress IP Card
            item(key = "bonus_outgoing_ip_card") {
                OutgoingIpCard(
                    egressState = egressState,
                    onRefresh = { viewModel.refreshOutgoingIp() },
                    noteText = "Public bonus scan from this device · casinos see this IP"
                )
            }

            // 2. Live Bonus Scan Telemetry Box (matching LIVE FETCH panel)
            item(key = "live_bonus_stats_card") {
                LiveBonusScanStatsCard(state = state)
            }

            // 2b. Battery optimization shortcut when Background Runs is ON
            if (state.allowBackgroundRuns) {
                item(key = "bonus_bg_battery_hint") {
                    BackgroundBatteryHintBanner(
                        allowBackgroundRuns = state.allowBackgroundRuns,
                        tagPrefix = "bonus_"
                    )
                }
            }

            // 3. Actionable Checkpoint / WAF Auto-Pause Banner with Continue (N)
            val checkpoint = state.activeCheckpoint
            if (checkpoint != null && !state.isScanning) {
                item(key = "bonus_checkpoint_banner") {
                    WafPauseAndCheckpointBanner(
                        checkpoint = checkpoint,
                        currentIp = egressState.info?.ip,
                        isRefreshingIp = egressState.isLoading,
                        isFetching = state.isScanning,
                        onRefreshIp = { viewModel.refreshOutgoingIp() },
                        onResume = { viewModel.resumeBonusCheckpoint() },
                        onDiscard = { viewModel.discardBonusCheckpoint() },
                        resumeLabelPrefix = "Continue",
                        tagPrefix = "bonus_"
                    )
                }
            }

            // 4. Bonus Scan Console: Speed Preset + Start / Stop / Continue / Retry Failed Controls
            item(key = "bonus_console_panel") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.bonus_finder_title),
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                Text(
                                    text = "${state.speedPreset.displayName.uppercase()} POOL · ${state.workerLimit} MAX WORKERS",
                                    color = palette.accentA,
                                    style = MonoTextStyles.metricSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "${state.loadedTotalCount}/${state.totalDomains} ok",
                                color = palette.live,
                                style = MonoTextStyles.metricSmall,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        // Independent Speed Preset Selector for Bonus Finder
                        SpeedPresetSelector(
                            selected = state.speedPreset,
                            enabled = !state.isScanning,
                            onSelect = { viewModel.setBonusSpeedPreset(it) },
                            title = "BONUS SCAN SPEED PRESET",
                            tagPrefix = "bonus_speed_preset",
                            mode = SpeedSelectorMode.BONUS_FINDER
                        )

                        // Primary Start / Stop / Continue Action Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (state.isScanning) {
                                Button(
                                    onClick = { viewModel.stopBonusScan() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = palette.dead,
                                        contentColor = palette.ink
                                    ),
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("bonus_stop_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Stop,
                                        contentDescription = "Stop bonus scan",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Stop & Save Checkpoint", fontWeight = FontWeight.ExtraBold)
                                }
                            } else {
                                if (checkpoint != null) {
                                    Button(
                                        onClick = { viewModel.resumeBonusCheckpoint() },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = palette.live,
                                            contentColor = palette.ink
                                        ),
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier
                                            .weight(1.25f)
                                            .heightIn(min = 48.dp)
                                            .testTag("bonus_continue_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Continue (${checkpoint.remainingDomains.size})",
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            val targets = state.unloadedDomains.ifEmpty {
                                                state.items.map { it.domain }
                                            }.take(20)
                                            viewModel.scanPublicBonuses(targets)
                                        },
                                        enabled = state.allTargetDomains.isNotEmpty(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = palette.accentA,
                                            contentColor = palette.ink
                                        ),
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier
                                            .weight(1.1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("bonus_scan_top20_btn")
                                    ) {
                                        Text(
                                            text = "Scan Top 20",
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1
                                        )
                                    }
                                }

                                val scanTargets = if (state.unloadedDomains.isNotEmpty()) {
                                    state.unloadedDomains
                                } else {
                                    state.allTargetDomains
                                }
                                val scanLabel = if (state.unloadedDomains.isNotEmpty()) {
                                    "Unloaded (${state.unloadedDomains.size})"
                                } else {
                                    "Scan All (${state.allTargetDomains.size})"
                                }
                                OutlinedButton(
                                    onClick = { viewModel.scanPublicBonuses(scanTargets) },
                                    enabled = scanTargets.isNotEmpty(),
                                    border = BorderStroke(1.dp, palette.accentA.copy(alpha = 0.7f)),
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("bonus_scan_all_btn")
                                ) {
                                    Text(
                                        text = scanLabel,
                                        color = palette.accentA,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Secondary Row: Retry Failed (N) & Scan All when unloaded/checkpoint exists
                        if (!state.isScanning && (state.failedDomains.isNotEmpty() || checkpoint != null)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (state.failedDomains.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = { viewModel.retryFailedBonuses() },
                                        border = BorderStroke(1.dp, palette.slow),
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier
                                            .weight(1.2f)
                                            .heightIn(min = 48.dp)
                                            .testTag("bonus_retry_failed_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            tint = palette.slow,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Retry Failed (${state.failedDomains.size})",
                                            color = palette.slow,
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1
                                        )
                                    }
                                }
                                if (checkpoint != null) {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.scanPublicBonuses(state.allTargetDomains)
                                        },
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier
                                            .weight(0.9f)
                                            .heightIn(min = 48.dp)
                                            .testTag("bonus_rescan_all_btn")
                                    ) {
                                        Text(
                                            text = "Scan All",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        // Live Status & Progress Bar
                        Text(
                            text = state.statusMessage,
                            color = palette.accentA,
                            style = MonoTextStyles.metricSmall
                        )
                        if (state.isScanning && state.progressTotal > 0) {
                            LinearProgressIndicator(
                                progress = {
                                    (state.progressDone.toFloat() / state.progressTotal.toFloat()).coerceIn(0f, 1f)
                                },
                                color = palette.accentA,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                            )
                        }
                    }
                }
            }

            // 5. Status Filter Tabs: All | Loaded | Failed | Unloaded
            item(key = "bonus_status_tabs") {
                BonusStatusTabRow(
                    selected = state.statusFilter,
                    allCount = state.totalDomains,
                    loadedCount = state.loadedTotalCount,
                    failedCount = state.failedTotalCount,
                    unloadedCount = state.unloadedTotalCount,
                    onSelect = { viewModel.setBonusStatusFilter(it) }
                )
            }

            // 6. Search + Slot Chips + Metric Ranking Panel
            item(key = "bonus_filters_panel") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        OutlinedTextField(
                            value = searchInput,
                            onValueChange = { viewModel.onBonusSearchChanged(it) },
                            placeholder = {
                                Text(stringResource(R.string.search_bonuses_hint), color = palette.faint)
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingIcon = {
                                if (searchInput.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.onBonusSearchChanged("") }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = MaterialTheme.shapes.small,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = palette.accentA,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("bonus_search_field")
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FILTERABLE_SLOTS.forEach { slot ->
                                val selected = state.selectedSlots.contains(slot)
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.toggleBonusSlotFilter(slot) },
                                    label = { Text(slot.label, style = MaterialTheme.typography.labelMedium) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = palette.accentA.copy(alpha = 0.16f),
                                        selectedLabelColor = palette.accentA,
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    modifier = Modifier.testTag("bonus_slot_${slot.name.lowercase()}")
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { sortPanelOpen = !sortPanelOpen },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.testTag("bonus_sort_toggle_btn")
                            ) {
                                Text(
                                    text = "Ranking: ${state.amountSort.label}",
                                    color = palette.accentA,
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Icon(
                                    imageVector = if (sortPanelOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = palette.accentA
                                )
                            }
                            if (state.selectedSlots.isNotEmpty() ||
                                state.amountSort != BonusAmountSort.DOMAIN_AZ ||
                                state.statusFilter != BonusStatusFilter.ALL ||
                                searchInput.isNotEmpty()
                            ) {
                                TextButton(onClick = { viewModel.clearBonusFilters() }) {
                                    Text("Reset", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        AnimatedVisibility(visible = sortPanelOpen) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("METRIC SORT", color = palette.faint, style = MaterialTheme.typography.labelSmall)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    BonusAmountSort.entries.forEach { sort ->
                                        val selected = state.amountSort == sort
                                        FilterChip(
                                            selected = selected,
                                            onClick = {
                                                viewModel.setBonusAmountSort(sort)
                                                if (sort != BonusAmountSort.DOMAIN_AZ &&
                                                    sort != BonusAmountSort.DEPOSIT &&
                                                    state.listOrderSlot == null
                                                ) {
                                                    viewModel.setBonusListOrderSlot(PublicBonusSlot.SIGNUP)
                                                }
                                            },
                                            label = { Text(sort.label, style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }
                                if (state.amountSort != BonusAmountSort.DOMAIN_AZ && state.amountSort != BonusAmountSort.DEPOSIT) {
                                    Text("RANK BY CATEGORY", color = palette.faint, style = MaterialTheme.typography.labelSmall)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        FILTERABLE_SLOTS.forEach { slot ->
                                            FilterChip(
                                                selected = state.listOrderSlot == slot,
                                                onClick = { viewModel.setBonusListOrderSlot(slot) },
                                                label = { Text(slot.label, style = MaterialTheme.typography.labelSmall) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 7. Domain List (filtered by Status Tab + Search + Slots)
            if (state.items.isEmpty()) {
                item(key = "bonus_empty") {
                    val emptyTitle = when (state.statusFilter) {
                        BonusStatusFilter.LOADED -> "No loaded domains match"
                        BonusStatusFilter.FAILED -> "No failed domains"
                        BonusStatusFilter.UNLOADED -> "All domains have been scanned"
                        BonusStatusFilter.ALL -> "No matching public bonuses"
                    }
                    val emptySubtitle = when (state.statusFilter) {
                        BonusStatusFilter.FAILED -> "Great news — none of your scanned domains failed to load."
                        BonusStatusFilter.UNLOADED -> "Switch to Loaded or Failed tabs to review cached results."
                        else -> "Scan domains above to load public promotions, or reset active filters."
                    }
                    EmptyStateCard(
                        icon = Icons.Default.CardGiftcard,
                        title = emptyTitle,
                        subtitle = emptySubtitle,
                        actionLabel = "Show All",
                        onAction = { viewModel.clearBonusFilters() }
                    )
                }
            } else {
                items(items = state.items, key = { it.domain }) { item ->
                    PublicBonusSiteCard(
                        item = item,
                        isScanning = state.isScanning,
                        onScanSingle = { viewModel.scanPublicBonuses(listOf(item.domain)) }
                    )
                }
            }

            item(key = "bonus_footer") {
                ResponsibleGamblingFooter()
            }
        }
    }
}

@Composable
private fun BonusStatusTabRow(
    selected: BonusStatusFilter,
    allCount: Int,
    loadedCount: Int,
    failedCount: Int,
    unloadedCount: Int,
    onSelect: (BonusStatusFilter) -> Unit
) {
    val palette = LocalVaultPalette.current
    val tabs = listOf(
        Triple(BonusStatusFilter.ALL, allCount, palette.accentA),
        Triple(BonusStatusFilter.LOADED, loadedCount, palette.live),
        Triple(BonusStatusFilter.FAILED, failedCount, palette.dead),
        Triple(BonusStatusFilter.UNLOADED, unloadedCount, MaterialTheme.colorScheme.onSurfaceVariant)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tabs.forEach { (tab, count, tone) ->
            val isSelected = tab == selected
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) tone.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = if (isSelected) tone else MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable { onSelect(tab) }
                    .testTag("bonus_tab_${tab.name.lowercase()}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = tab.label,
                        color = if (isSelected) tone else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = tone.copy(alpha = if (isSelected) 0.25f else 0.12f)
                    ) {
                        Text(
                            text = "$count",
                            color = tone,
                            style = MonoTextStyles.metricSmall,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PublicBonusSiteCard(
    item: BonusFinderItem,
    isScanning: Boolean,
    onScanSingle: () -> Unit
) {
    val context = LocalContext.current
    val palette = LocalVaultPalette.current
    var expanded by remember(item.domain) { mutableStateOf(false) }
    val cached = item.cache

    val statusBadge: Pair<String, Color> = when {
        cached == null -> "Unloaded" to MaterialTheme.colorScheme.onSurfaceVariant
        cached.ok -> "Loaded" to palette.live
        else -> "Failed" to palette.dead
    }
    val cardBorderColor = when {
        cached == null -> MaterialTheme.colorScheme.outline
        cached.ok -> palette.live.copy(alpha = 0.35f)
        else -> palette.dead.copy(alpha = 0.45f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bonus_card_${item.domain}"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, cardBorderColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "#${item.rank}",
                        color = palette.faint,
                        style = MonoTextStyles.metricSmall
                    )
                    Text(
                        text = item.domain,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = statusBadge.second.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, statusBadge.second.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = statusBadge.first,
                            color = statusBadge.second,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val actionLabel = when {
                        cached == null -> "Load"
                        !cached.ok -> "Retry"
                        else -> "Reload"
                    }
                    val actionColor = when {
                        cached == null -> palette.accentA
                        !cached.ok -> palette.slow
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    TextButton(
                        onClick = onScanSingle,
                        enabled = !isScanning,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("bonus_card_action_${item.domain}")
                    ) {
                        Text(
                            text = actionLabel,
                            color = actionColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = { openExternalUrl(context, "https://${item.domain}/") },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Open ${item.domain}",
                            tint = palette.accentA,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (cached == null) {
                Text(
                    text = "Not loaded yet. Tap Load or start a scan above.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            } else if (!cached.ok) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = palette.dead.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, palette.dead.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Text(
                        text = cached.errorMessage ?: "Public bonus list unavailable.",
                        color = palette.dead,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            } else if (cached.bonuses.isEmpty()) {
                Text(
                    text = "Loaded · No public promotions published.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PublicSlotTile("Deposit", item.depositBest, Modifier.weight(1f))
                    PublicSlotTile("Signup", item.signupBest, Modifier.weight(1f))
                    PublicSlotTile("Share", item.shareBest, Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PublicSlotTile("Daily", item.dailyBest, Modifier.weight(1f))
                    PublicSlotTile("Birthday", item.birthdayBest, Modifier.weight(1f))
                    Spacer(modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "All ${cached.bonuses.size} public offers",
                        color = palette.accentA,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse offers" else "Expand offers",
                        tint = palette.accentA,
                        modifier = Modifier.size(18.dp)
                    )
                }

                AnimatedVisibility(visible = expanded) {
                    Column(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        cached.bonuses.forEach { bonus ->
                            val meta = buildString {
                                if (bonus.rollover != null) append("${bonus.rollover.toInt()}× ")
                                if (bonus.maxWithdraw != null) append("Max ${FormatUtil.moneyCompact(bonus.maxWithdraw)}")
                            }.trim()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = bonus.name + if (meta.isNotBlank()) " · $meta" else "",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = bonus.amountDisplay,
                                    color = palette.live,
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

@Composable
private fun PublicSlotTile(
    label: String,
    bonus: PublicBonus?,
    modifier: Modifier = Modifier
) {
    val palette = LocalVaultPalette.current
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(label, color = palette.faint, style = MaterialTheme.typography.labelSmall)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = bonus?.amountDisplay ?: "—",
                color = if (bonus != null) palette.live else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MonoTextStyles.metricSmall,
                fontWeight = if (bonus != null) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (bonus?.rollover != null) {
                Text(
                    text = "${bonus.rollover.toInt()}× wager",
                    color = palette.faint,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }
        }
    }
}
