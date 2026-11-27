package com.aistudio.pokiesvault.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aistudio.pokiesvault.R
import com.aistudio.pokiesvault.data.model.BonusMode
import com.aistudio.pokiesvault.data.model.CardColor
import com.aistudio.pokiesvault.data.model.SortMode
import com.aistudio.pokiesvault.data.model.ViewMode
import com.aistudio.pokiesvault.ui.components.DomainWalletCard
import com.aistudio.pokiesvault.ui.components.EmptyStateCard
import com.aistudio.pokiesvault.ui.components.ResponsibleGamblingFooter
import com.aistudio.pokiesvault.ui.theme.LocalVaultPalette
import com.aistudio.pokiesvault.ui.theme.MonoTextStyles
import com.aistudio.pokiesvault.ui.viewmodel.VaultViewModel
import com.aistudio.pokiesvault.util.FormatUtil

@Composable
fun DirectoryScreen(
    viewModel: VaultViewModel,
    isExpanded: Boolean,
    modifier: Modifier = Modifier
) {
    val state by viewModel.directoryState.collectAsStateWithLifecycle()
    val searchInput by viewModel.directorySearchInput.collectAsStateWithLifecycle()
    val palette = LocalVaultPalette.current
    val prefs = state.preferences

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 900.dp)
        ) {
            // Elevated Control Deck
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    OutlinedTextField(
                        value = searchInput,
                        onValueChange = { viewModel.onDirectorySearchChanged(it) },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.search_directory_hint),
                                color = palette.faint,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            if (searchInput.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onDirectorySearchChanged("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
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
                            .testTag("directory_search_field")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VaultFilterChip(
                            label = if (prefs.notVisited) "Not Visited: On" else "Not Visited: Off",
                            selected = prefs.notVisited,
                            icon = Icons.Default.VisibilityOff,
                            onClick = { viewModel.toggleNotVisited() },
                            tag = "chip_not_visited"
                        )
                        VaultFilterChip(
                            label = "View: ${prefs.viewMode.label}",
                            selected = prefs.viewMode != ViewMode.GROUPS,
                            icon = Icons.Default.GridView,
                            onClick = { viewModel.cycleViewMode() },
                            tag = "chip_view_mode"
                        )
                        VaultFilterChip(
                            label = if (prefs.wallets) "Wallets: On" else "Wallets: Off",
                            selected = prefs.wallets,
                            icon = Icons.Default.AccountBalanceWallet,
                            onClick = { viewModel.toggleWallets() },
                            tag = "chip_wallets"
                        )
                        val hasActiveSortFilter = prefs.sortMode != SortMode.DEFAULT ||
                            prefs.bonusMode != BonusMode.NONE ||
                            prefs.cardColor != CardColor.CLASSIC ||
                            prefs.signupOnly
                        val sortChipLabel = buildString {
                            append("⇅ ")
                            append(
                                if (prefs.sortMode == SortMode.DEFAULT) "Sort / Filter" else prefs.sortMode.shortLabel
                            )
                            if (prefs.bonusMode != BonusMode.NONE) {
                                append(" · ${prefs.bonusMode.emoji}")
                            }
                        }
                        VaultFilterChip(
                            label = sortChipLabel,
                            selected = hasActiveSortFilter,
                            icon = Icons.Default.Tune,
                            onClick = { viewModel.setSortFilterOpen(true) },
                            tag = "chip_sort_filter"
                        )
                        VaultFilterChip(
                            label = "My accounts (${state.mineCount})",
                            selected = prefs.mineOnly,
                            icon = Icons.Default.Bookmark,
                            onClick = { viewModel.toggleMineOnly() },
                            tag = "chip_mine_only"
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val activeSummary = buildString {
                            append("${state.totalSitesCount} sites · ${state.visitedCount} visited")
                            if (prefs.signupOnly) append(" · Signup only")
                        }
                        Text(
                            text = activeSummary,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MonoTextStyles.metricSmall
                        )
                        if (state.totalCachedCash > 0.001) {
                            Text(
                                text = "Vault cash ${FormatUtil.money(state.totalCachedCash)}",
                                color = palette.live,
                                style = MonoTextStyles.metricSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Main Catalog List
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                contentPadding = PaddingValues(top = 6.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.items.isEmpty() && !state.isLoading) {
                    item(key = "empty_directory") {
                        EmptyStateCard(
                            icon = Icons.Default.SearchOff,
                            title = "No matching sites",
                            subtitle = "Try clearing your search or filter chips, or add a custom domain.",
                            actionLabel = "Add custom domain",
                            onAction = { viewModel.setAddDomainOpen(true) }
                        )
                    }
                } else if (state.isGroupedMode) {
                    state.groupedItems.forEach { (group, groupItems) ->
                        item(key = "header_${group.name}") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = group.label.uppercase(),
                                    color = palette.accentA,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "${groupItems.size}",
                                    color = palette.faint,
                                    style = MonoTextStyles.metricSmall
                                )
                            }
                        }

                        if (isExpanded) {
                            val pairs = groupItems.chunked(2)
                            items(
                                items = pairs,
                                key = { pair -> "grp_${group.name}_${pair.first().site.domain}" }
                            ) { pair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    pair.forEach { item ->
                                        LaunchedEffect(item.site.domain) {
                                            viewModel.enqueueStatusProbeIfStale(item.site.domain)
                                        }
                                        DomainWalletCard(
                                            item = item,
                                            walletMode = prefs.wallets,
                                            bonusMode = prefs.bonusMode,
                                            cardColor = prefs.cardColor,
                                            sortMode = prefs.sortMode,
                                            compact = false,
                                            onCardClick = { viewModel.markVisited(item.site.domain) },
                                            onDetailClick = { viewModel.openSiteDetail(item.site.domain) },
                                            onBookmarkToggle = { viewModel.toggleMine(item.site.domain) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        } else {
                            items(items = groupItems, key = { it.site.domain }) { item ->
                                LaunchedEffect(item.site.domain) {
                                    viewModel.enqueueStatusProbeIfStale(item.site.domain)
                                }
                                DomainWalletCard(
                                    item = item,
                                    walletMode = prefs.wallets,
                                    bonusMode = prefs.bonusMode,
                                    cardColor = prefs.cardColor,
                                    sortMode = prefs.sortMode,
                                    compact = false,
                                    onCardClick = { viewModel.markVisited(item.site.domain) },
                                    onDetailClick = { viewModel.openSiteDetail(item.site.domain) },
                                    onBookmarkToggle = { viewModel.toggleMine(item.site.domain) }
                                )
                            }
                        }
                    }
                } else if (prefs.viewMode == ViewMode.TWO || isExpanded) {
                    val cols = if (isExpanded && prefs.viewMode == ViewMode.TWO) 3 else 2
                    val rows = state.items.chunked(cols)
                    items(
                        items = rows,
                        key = { chunk -> "grid_${chunk.first().site.domain}" }
                    ) { chunk ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            chunk.forEach { item ->
                                LaunchedEffect(item.site.domain) {
                                    viewModel.enqueueStatusProbeIfStale(item.site.domain)
                                }
                                DomainWalletCard(
                                    item = item,
                                    walletMode = prefs.wallets,
                                    bonusMode = prefs.bonusMode,
                                    cardColor = prefs.cardColor,
                                    sortMode = prefs.sortMode,
                                    compact = prefs.viewMode == ViewMode.TWO,
                                    onCardClick = { viewModel.markVisited(item.site.domain) },
                                    onDetailClick = { viewModel.openSiteDetail(item.site.domain) },
                                    onBookmarkToggle = { viewModel.toggleMine(item.site.domain) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(cols - chunk.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    items(items = state.items, key = { it.site.domain }) { item ->
                        LaunchedEffect(item.site.domain) {
                            viewModel.enqueueStatusProbeIfStale(item.site.domain)
                        }
                        DomainWalletCard(
                            item = item,
                            walletMode = prefs.wallets,
                            bonusMode = prefs.bonusMode,
                            cardColor = prefs.cardColor,
                            sortMode = prefs.sortMode,
                            compact = false,
                            onCardClick = { viewModel.markVisited(item.site.domain) },
                            onDetailClick = { viewModel.openSiteDetail(item.site.domain) },
                            onBookmarkToggle = { viewModel.toggleMine(item.site.domain) }
                        )
                    }
                }

                item(key = "directory_footer") {
                    ResponsibleGamblingFooter()
                }
            }
        }
    }
}

@Composable
private fun VaultFilterChip(
    label: String,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    tag: String
) {
    val palette = LocalVaultPalette.current
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(15.dp)
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = palette.accentA.copy(alpha = 0.16f),
            selectedLabelColor = palette.accentA,
            selectedLeadingIconColor = palette.accentA,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurface,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outline,
            selectedBorderColor = palette.accentA
        ),
        modifier = Modifier.testTag(tag)
    )
}
