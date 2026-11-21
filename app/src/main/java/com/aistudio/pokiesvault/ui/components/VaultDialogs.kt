package com.aistudio.pokiesvault.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aistudio.pokiesvault.data.model.BonusMode
import com.aistudio.pokiesvault.data.model.FetchRunSummary
import com.aistudio.pokiesvault.data.model.SortMode
import com.aistudio.pokiesvault.ui.theme.LocalVaultPalette
import com.aistudio.pokiesvault.ui.theme.MonoTextStyles
import com.aistudio.pokiesvault.ui.viewmodel.DirectorySiteItem
import com.aistudio.pokiesvault.util.FormatUtil

@Composable
fun AddDomainDialog(
    onDismiss: () -> Unit,
    onAdd: (String, (String?) -> Unit) -> Unit
) {
    val palette = LocalVaultPalette.current
    var domainInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a domain", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text(
                    "Saved into your unified registry across Directory, Fetcher, Bonus Finder, and Domain Intel.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = domainInput,
                    onValueChange = {
                        domainInput = it
                        errorMessage = null
                    },
                    placeholder = { Text("e.g. 9au.com", color = palette.faint) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.accentA,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("domain_input")
                )
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = errorMessage!!,
                        color = palette.dead,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAdd(domainInput) { err ->
                        if (err != null) {
                            errorMessage = err
                        } else {
                            onDismiss()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.accentA,
                    contentColor = palette.ink
                ),
                modifier = Modifier.testTag("save_domain_button")
            ) {
                Text("Save site", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large
    )
}

@Composable
fun PasteDomainsDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit
) {
    val palette = LocalVaultPalette.current
    var rawText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paste domains", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text(
                    "Paste one or more domains separated by lines, commas, or spaces.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    placeholder = { Text("9au.com\nyeah96.com", color = palette.faint) },
                    minLines = 4,
                    maxLines = 8,
                    shape = MaterialTheme.shapes.small,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.accentA,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("paste_domains_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onImport(rawText)
                    onDismiss()
                },
                enabled = rawText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.accentA,
                    contentColor = palette.ink
                ),
                modifier = Modifier.testTag("confirm_paste_domains_btn")
            ) {
                Text("Import", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortFilterSheet(
    currentSort: SortMode,
    currentBonus: BonusMode,
    mineOnly: Boolean,
    onSortSelected: (SortMode) -> Unit,
    onBonusSelected: (BonusMode) -> Unit,
    onMineOnlyToggled: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalVaultPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text("Sort & card badges", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(14.dp))

            Text("SORT ORDER", color = palette.accentA, style = MaterialTheme.typography.labelSmall)
            Spacer(modifier = Modifier.height(6.dp))
            SortMode.entries.forEach { mode ->
                val selected = currentSort == mode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSortSelected(mode) }
                        .padding(vertical = 4.dp)
                        .testTag("sort_mode_${mode.name.lowercase()}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = { onSortSelected(mode) },
                        colors = RadioButtonDefaults.colors(selectedColor = palette.accentA)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = mode.label,
                        color = if (selected) palette.accentA else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text("BONUS BADGE ON CARDS", color = palette.accentA, style = MaterialTheme.typography.labelSmall)
            Spacer(modifier = Modifier.height(6.dp))
            BonusMode.entries.forEach { mode ->
                val selected = currentBonus == mode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onBonusSelected(mode) }
                        .padding(vertical = 3.dp)
                        .testTag("bonus_mode_${mode.name.lowercase()}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = { onBonusSelected(mode) },
                        colors = RadioButtonDefaults.colors(selectedColor = palette.accentA)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = mode.label,
                        color = if (selected) palette.accentA else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onMineOnlyToggled() }
                    .padding(vertical = 4.dp)
                    .testTag("sheet_mine_only_toggle"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = mineOnly,
                    onCheckedChange = { onMineOnlyToggled() },
                    colors = CheckboxDefaults.colors(checkedColor = palette.accentA, checkmarkColor = palette.ink)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("My bookmarked accounts only", style = MaterialTheme.typography.bodyLarge)
            }

            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.accentA,
                    contentColor = palette.ink
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sort_sheet_done_btn")
            ) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ConfirmScanDialog(
    totalCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val palette = LocalVaultPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scan all $totalCount listed sites?", style = MaterialTheme.typography.titleLarge) },
        text = {
            Text(
                "Runs the adaptive 6–8 worker pool across $totalCount domains using your saved credentials, automatically bookmarking matches and persisting results locally. You can stop at any time.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.accentA,
                    contentColor = palette.ink
                ),
                modifier = Modifier.testTag("confirm_scan_button")
            ) {
                Text("Start full scan", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteDetailSheet(
    item: DirectorySiteItem,
    onFetchNow: (String) -> Unit,
    onToggleBookmark: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val palette = LocalVaultPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SiteAvatar(domain = item.site.domain)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.site.domain, style = MaterialTheme.typography.titleLarge)
                    StatusDotLabel(
                        status = item.status,
                        latencyMs = item.latencyMs,
                        extraText = item.site.group.label
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        openExternalUrl(context, "https://${item.site.domain}/")
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.accentA,
                        contentColor = palette.ink
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Open site", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = {
                        onFetchNow(item.site.domain)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Fetch now", color = palette.accentA)
                }
                OutlinedButton(
                    onClick = { onToggleBookmark(item.site.domain) }
                ) {
                    Text(
                        if (item.site.isMine) "Saved" else "Save",
                        color = if (item.site.isMine) palette.live else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            if (item.account != null) {
                AccountResultCard(row = item.account)
            } else {
                Text(
                    text = "No cached balance snapshot yet. Tap Fetch now to check this site.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FetchHistorySheet(
    runs: List<FetchRunSummary>,
    activeRunId: Long?,
    onSelectRun: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalVaultPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text("Saved fetch history", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Tap any past run stored in Room to inspect its balances and failures.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (runs.isEmpty()) {
                Text(
                    "No fetch runs recorded on this device yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 20.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(runs, key = { it.runId }) { run ->
                        val isActive = run.runId == activeRunId
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectRun(run.runId) }
                                .testTag("history_run_${run.runId}"),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(
                                1.dp,
                                if (isActive) palette.accentA else MaterialTheme.colorScheme.outline
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = FormatUtil.formatTimestamp(run.completedAt),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "${run.okCount}/${run.targetCount} signed in · ${run.errCount} rejected",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MonoTextStyles.metricSmall
                                    )
                                }
                                Text(
                                    text = FormatUtil.money(run.totalCash),
                                    color = palette.live,
                                    style = MonoTextStyles.balanceMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
