package com.aistudio.pokiesvault.ui.screens

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aistudio.pokiesvault.R
import com.aistudio.pokiesvault.data.model.SiteStatus
import com.aistudio.pokiesvault.ui.components.EmptyStateCard
import com.aistudio.pokiesvault.ui.components.PasteDomainsDialog
import com.aistudio.pokiesvault.ui.components.ResponsibleGamblingFooter
import com.aistudio.pokiesvault.ui.components.StatusDotLabel
import com.aistudio.pokiesvault.ui.theme.LocalVaultPalette
import com.aistudio.pokiesvault.ui.theme.MonoTextStyles
import com.aistudio.pokiesvault.ui.viewmodel.VaultViewModel

@Composable
fun DomainIntelScreen(
    viewModel: VaultViewModel,
    onImportTxtClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.intelState.collectAsStateWithLifecycle()
    val searchInput by viewModel.intelSearchInput.collectAsStateWithLifecycle()
    val palette = LocalVaultPalette.current

    var entryInput by remember { mutableStateOf("") }
    var pasteDialogOpen by remember { mutableStateOf(false) }

    val allShownSelected = state.rows.isNotEmpty() && state.rows.all { it.isSelected }

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
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item(key = "intel_header") {
                Column {
                    Text(
                        text = stringResource(R.string.domain_intel_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.domain_intel_subtitle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Telemetry Summary Pills (double as quick status filters)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IntelStatPill(
                            label = "${state.totalCount} total",
                            selected = state.statusFilter == null,
                            accent = palette.accentA,
                            onClick = { viewModel.toggleIntelStatusFilter(null) },
                            tag = "intel_pill_all"
                        )
                        IntelStatPill(
                            label = "${state.upCount} up",
                            selected = state.statusFilter == SiteStatus.LIVE,
                            accent = palette.live,
                            onClick = { viewModel.toggleIntelStatusFilter(SiteStatus.LIVE) },
                            tag = "intel_pill_live"
                        )
                        IntelStatPill(
                            label = "${state.slowCount} slow",
                            selected = state.statusFilter == SiteStatus.SLOW,
                            accent = palette.slow,
                            onClick = { viewModel.toggleIntelStatusFilter(SiteStatus.SLOW) },
                            tag = "intel_pill_slow"
                        )
                        IntelStatPill(
                            label = "${state.redirectCount} redirect",
                            selected = state.statusFilter == SiteStatus.REDIRECT,
                            accent = palette.accentB,
                            onClick = { viewModel.toggleIntelStatusFilter(SiteStatus.REDIRECT) },
                            tag = "intel_pill_redirect"
                        )
                        IntelStatPill(
                            label = "${state.downCount} down",
                            selected = state.statusFilter == SiteStatus.DEAD,
                            accent = palette.dead,
                            onClick = { viewModel.toggleIntelStatusFilter(SiteStatus.DEAD) },
                            tag = "intel_pill_dead"
                        )
                    }
                }
            }

            item(key = "intel_tools") {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Add single domain row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = entryInput,
                                onValueChange = { entryInput = it },
                                placeholder = { Text("Add domain, e.g. yeah96.com", color = palette.faint) },
                                singleLine = true,
                                shape = MaterialTheme.shapes.small,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = palette.accentA,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("intel_add_domain_input")
                            )
                            Button(
                                onClick = {
                                    viewModel.addSingleDomain(entryInput, markAsMine = false) { err ->
                                        if (err != null) {
                                            viewModel.emitMessage(err)
                                        } else {
                                            entryInput = ""
                                        }
                                    }
                                },
                                enabled = entryInput.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = palette.accentA,
                                    contentColor = palette.ink
                                ),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.testTag("intel_add_domain_btn")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add", fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = searchInput,
                            onValueChange = { viewModel.onIntelSearchChanged(it) },
                            placeholder = { Text(stringResource(R.string.search_intel_hint), color = palette.faint) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchInput.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.onIntelSearchChanged("") }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear filter")
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
                                .testTag("intel_search_field")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Action buttons row 1: Import .txt + Paste
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onImportTxtClick,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("intel_import_txt_btn")
                            ) {
                                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Import .txt")
                            }
                            OutlinedButton(
                                onClick = { pasteDialogOpen = true },
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("intel_paste_list_btn")
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Paste list")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Action buttons row 2: Check Status + Stop/Remove
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.runIntelHealthCheck() },
                                enabled = !state.isChecking && state.totalCount > 0,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = palette.accentA,
                                    contentColor = palette.ink
                                ),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier
                                    .weight(1.3f)
                                    .testTag("intel_check_status_btn")
                            ) {
                                Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (state.selectedDomains.isNotEmpty()) {
                                        "Probe ${state.selectedDomains.size} selected"
                                    } else {
                                        "Check all status"
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (state.isChecking) {
                                Button(
                                    onClick = { viewModel.stopIntelHealthCheck() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = palette.dead.copy(alpha = 0.2f),
                                        contentColor = palette.dead
                                    ),
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier
                                        .weight(0.9f)
                                        .testTag("intel_stop_btn")
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Stop", fontWeight = FontWeight.Bold)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { viewModel.removeSelectedIntelDomains() },
                                    enabled = state.selectedDomains.isNotEmpty(),
                                    border = BorderStroke(
                                        1.dp,
                                        if (state.selectedDomains.isNotEmpty()) palette.dead else MaterialTheme.colorScheme.outline
                                    ),
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier
                                        .weight(0.9f)
                                        .testTag("intel_remove_selected_btn")
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = if (state.selectedDomains.isNotEmpty()) palette.dead else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (state.selectedDomains.isNotEmpty()) "Remove (${state.selectedDomains.size})" else "Remove",
                                        color = if (state.selectedDomains.isNotEmpty()) palette.dead else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.statusMessage,
                            color = palette.accentA,
                            style = MonoTextStyles.metricSmall
                        )
                        if (state.isChecking && state.progressTotal > 0) {
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

            if (state.rows.isNotEmpty()) {
                item(key = "intel_select_all") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleIntelSelectAllShown() }
                            .padding(vertical = 4.dp)
                            .testTag("intel_select_all_row"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = allShownSelected,
                            onCheckedChange = { viewModel.toggleIntelSelectAllShown() },
                            colors = CheckboxDefaults.colors(
                                checkedColor = palette.accentA,
                                checkmarkColor = palette.ink
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (allShownSelected) {
                                "Deselect ${state.rows.size} shown"
                            } else {
                                "Select ${state.rows.size} shown"
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                items(items = state.rows, key = { it.site.domain }) { row ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleIntelSelect(row.site.domain) }
                            .testTag("intel_row_${row.site.domain}"),
                        shape = MaterialTheme.shapes.small,
                        color = if (row.isSelected) palette.accentA.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (row.isSelected) palette.accentA.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = row.isSelected,
                                onCheckedChange = { viewModel.toggleIntelSelect(row.site.domain) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = palette.accentA,
                                    checkmarkColor = palette.ink
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = row.site.domain,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            StatusDotLabel(
                                status = row.status,
                                latencyMs = row.latencyMs
                            )
                        }
                    }
                }
            } else {
                item(key = "intel_empty") {
                    EmptyStateCard(
                        icon = Icons.Default.Dns,
                        title = "No matching domains",
                        subtitle = "Clear your status filter or add domains above to monitor uptime."
                    )
                }
            }

            item(key = "intel_footer") {
                ResponsibleGamblingFooter()
            }
        }
    }

    if (pasteDialogOpen) {
        PasteDomainsDialog(
            onDismiss = { pasteDialogOpen = false },
            onImport = { raw -> viewModel.importDomainsText(raw) }
        )
    }
}

@Composable
private fun IntelStatPill(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    tag: String
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (selected) accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) accent else MaterialTheme.colorScheme.outline),
        modifier = Modifier.testTag(tag)
    ) {
        Text(
            text = label,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
            style = MonoTextStyles.metricSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}
