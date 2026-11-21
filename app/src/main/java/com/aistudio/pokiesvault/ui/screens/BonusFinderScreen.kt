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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.OpenInNew
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aistudio.pokiesvault.R
import com.aistudio.pokiesvault.data.model.BonusAmountSort
import com.aistudio.pokiesvault.data.model.PublicBonus
import com.aistudio.pokiesvault.data.model.PublicBonusSlot
import com.aistudio.pokiesvault.ui.components.EmptyStateCard
import com.aistudio.pokiesvault.ui.components.ResponsibleGamblingFooter
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "bonus_header_panel") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = stringResource(R.string.bonus_finder_title),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = stringResource(R.string.bonus_finder_subtitle),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = searchInput,
                            onValueChange = { viewModel.onBonusSearchChanged(it) },
                            placeholder = { Text(stringResource(R.string.search_bonuses_hint), color = palette.faint) },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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

                        Spacer(modifier = Modifier.height(10.dp))
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

                        Spacer(modifier = Modifier.height(6.dp))
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
                            if (state.selectedSlots.isNotEmpty() || state.amountSort != BonusAmountSort.DOMAIN_AZ) {
                                TextButton(onClick = { viewModel.clearBonusFilters() }) {
                                    Text("Reset", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        AnimatedVisibility(visible = sortPanelOpen) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
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

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val firstBatch = state.items.take(20).map { it.domain }
                                    viewModel.scanPublicBonuses(firstBatch)
                                },
                                enabled = !state.isScanning && state.items.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = palette.accentA,
                                    contentColor = palette.ink
                                ),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier
                                    .weight(1.2f)
                                    .testTag("bonus_scan_top20_btn")
                            ) {
                                Text("Scan top 20", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { viewModel.scanPublicBonuses(state.items.map { it.domain }) },
                                enabled = !state.isScanning && state.items.isNotEmpty(),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("bonus_scan_all_btn")
                            ) {
                                Text("Scan all (${state.items.size})", color = palette.accentA)
                            }
                            if (state.isScanning) {
                                Button(
                                    onClick = { viewModel.stopBonusScan() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = palette.dead.copy(alpha = 0.2f),
                                        contentColor = palette.dead
                                    ),
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.testTag("bonus_stop_btn")
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = "Stop scan", modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${state.statusMessage} · ${state.loadedCount}/${state.totalDomains} cached",
                            color = palette.accentA,
                            style = MonoTextStyles.metricSmall
                        )
                        if (state.isScanning && state.progressTotal > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { (state.progressDone.toFloat() / state.progressTotal.toFloat()).coerceIn(0f, 1f) },
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

            if (state.items.isEmpty()) {
                item(key = "bonus_empty") {
                    EmptyStateCard(
                        icon = Icons.Default.CardGiftcard,
                        title = "No matching public bonuses",
                        subtitle = "Scan domains above to cache public promotions, or clear active slot filters.",
                        actionLabel = "Clear filters",
                        onAction = { viewModel.clearBonusFilters() }
                    )
                }
            } else {
                items(items = state.items, key = { it.domain }) { item ->
                    PublicBonusSiteCard(
                        item = item,
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
private fun PublicBonusSiteCard(
    item: BonusFinderItem,
    onScanSingle: () -> Unit
) {
    val context = LocalContext.current
    val palette = LocalVaultPalette.current
    var expanded by remember(item.domain) { mutableStateOf(false) }
    val cached = item.cache

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bonus_card_${item.domain}"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "#${item.rank}",
                        color = palette.faint,
                        style = MonoTextStyles.metricSmall
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.domain,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (cached == null) {
                        TextButton(onClick = onScanSingle) {
                            Text("Load", color = palette.accentA)
                        }
                    }
                    IconButton(onClick = { openExternalUrl(context, "https://${item.domain}/") }) {
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
                    text = "Not loaded yet. Tap Load or Scan top 20.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            } else if (!cached.ok) {
                Text(
                    text = cached.errorMessage ?: "Public bonus list unavailable.",
                    color = palette.dead,
                    style = MaterialTheme.typography.bodySmall
                )
            } else if (cached.bonuses.isEmpty()) {
                Text(
                    text = "No public promotions published.",
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
                        contentDescription = null,
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
