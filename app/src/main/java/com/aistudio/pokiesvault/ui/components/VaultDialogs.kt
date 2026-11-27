package com.aistudio.pokiesvault.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aistudio.pokiesvault.data.model.BonusMode
import com.aistudio.pokiesvault.data.model.CardColor
import com.aistudio.pokiesvault.data.model.FetchRunSummary
import com.aistudio.pokiesvault.data.model.SortMode
import com.aistudio.pokiesvault.ui.theme.LocalVaultPalette
import com.aistudio.pokiesvault.ui.theme.MonoTextStyles
import com.aistudio.pokiesvault.ui.theme.VaultCardColors
import com.aistudio.pokiesvault.ui.viewmodel.DirectorySiteItem
import com.aistudio.pokiesvault.util.FormatUtil
import com.aistudio.pokiesvault.util.SiteOrderUtil

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SortFilterSheet(
    currentSort: SortMode,
    currentBonus: BonusMode,
    currentCardColor: CardColor,
    notVisited: Boolean,
    signupOnly: Boolean,
    mineOnly: Boolean,
    onCardColorSelected: (CardColor) -> Unit,
    onApply: (SortMode, BonusMode, Boolean, Boolean, Boolean) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalVaultPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var draftSort by remember(currentSort) { mutableStateOf(currentSort) }
    var draftBonus by remember(currentBonus) { mutableStateOf(currentBonus) }
    var draftNotVisited by remember(notVisited) { mutableStateOf(notVisited) }
    var draftSignupOnly by remember(signupOnly) { mutableStateOf(signupOnly) }
    var draftMineOnly by remember(mineOnly) { mutableStateOf(mineOnly) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "⇅ Sort / Filter",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // --- SORT BY ---
                Text(
                    text = "SORT BY",
                    color = palette.accentA,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Same order for the homepage cards and the bonus fetcher results.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(6.dp))

                SortMode.entries.forEach { mode ->
                    val selected = draftSort == mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { draftSort = mode }
                            .padding(vertical = 2.dp)
                            .testTag("sort_mode_${mode.name.lowercase()}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = { draftSort = mode },
                            colors = RadioButtonDefaults.colors(selectedColor = palette.accentA)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = mode.label,
                            color = if (selected) palette.accentA else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                if (SiteOrderUtil.sortRequiresSelectedBonus(draftSort) && draftBonus == BonusMode.NONE) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tip: Pick a Bonus Display below so the chosen-bonus sort targets that exact bonus (defaults to Share bonus when None).",
                        color = palette.slow,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(14.dp))

                // --- BONUS DISPLAY ---
                Text(
                    text = "BONUS DISPLAY",
                    color = palette.accentA,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Highlights that bonus on every card. The three \"chosen bonus\" sorts above use it.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(6.dp))

                BonusMode.entries.forEach { mode ->
                    val selected = draftBonus == mode
                    val displayLabel = if (mode.emoji.isNotEmpty()) "${mode.emoji} ${mode.label}" else mode.label
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { draftBonus = mode }
                            .padding(vertical = 2.dp)
                            .testTag("bonus_mode_${mode.name.lowercase()}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = { draftBonus = mode },
                            colors = RadioButtonDefaults.colors(selectedColor = palette.accentA)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = displayLabel,
                            color = if (selected) palette.accentA else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(14.dp))

                // --- CARD COLOR (INSTANT APPLY) ---
                Text(
                    text = "CARD COLOR",
                    color = palette.accentA,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Glow on the domain cards. Changes straight away.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CardColor.entries.forEach { colorOption ->
                        val selected = currentCardColor == colorOption
                        val swatchColor = VaultCardColors.glowColorFor(colorOption)
                        val activeBorder = swatchColor ?: palette.accentA
                        Surface(
                            modifier = Modifier
                                .clickable { onCardColorSelected(colorOption) }
                                .testTag("card_color_${colorOption.name.lowercase()}"),
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) {
                                activeBorder.copy(alpha = 0.18f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            border = BorderStroke(
                                width = if (selected) 1.6.dp else 1.dp,
                                color = if (selected) activeBorder else MaterialTheme.colorScheme.outline
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (swatchColor != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(swatchColor)
                                    )
                                }
                                Text(
                                    text = colorOption.label,
                                    color = if (selected) {
                                        swatchColor ?: palette.accentA
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(14.dp))

                // --- FILTER CHECKBOXES ---
                Text(
                    text = "FILTER",
                    color = palette.accentA,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { draftNotVisited = !draftNotVisited }
                        .padding(vertical = 2.dp)
                        .testTag("sheet_not_visited_toggle"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = draftNotVisited,
                        onCheckedChange = { draftNotVisited = it },
                        colors = CheckboxDefaults.colors(checkedColor = palette.accentA, checkmarkColor = palette.ink)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Not visited domains only", style = MaterialTheme.typography.bodyMedium)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { draftSignupOnly = !draftSignupOnly }
                        .padding(vertical = 2.dp)
                        .testTag("sheet_signup_only_toggle"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = draftSignupOnly,
                        onCheckedChange = { draftSignupOnly = it },
                        colors = CheckboxDefaults.colors(checkedColor = palette.accentA, checkmarkColor = palette.ink)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Signup bonuses only", style = MaterialTheme.typography.bodyMedium)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { draftMineOnly = !draftMineOnly }
                        .padding(vertical = 2.dp)
                        .testTag("sheet_mine_only_toggle"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = draftMineOnly,
                        onCheckedChange = { draftMineOnly = it },
                        colors = CheckboxDefaults.colors(checkedColor = palette.accentA, checkmarkColor = palette.ink)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("My bookmarked accounts only", style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            // --- RESET & APPLY FOOTER ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        draftSort = SortMode.DEFAULT
                        draftBonus = BonusMode.NONE
                        draftNotVisited = false
                        draftSignupOnly = false
                        draftMineOnly = false
                        onReset()
                    },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("sort_sheet_reset_btn")
                ) {
                    Text("Reset", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = {
                        onApply(
                            draftSort,
                            draftBonus,
                            draftNotVisited,
                            draftSignupOnly,
                            draftMineOnly
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.accentA,
                        contentColor = palette.ink
                    ),
                    modifier = Modifier
                        .weight(1.4f)
                        .heightIn(min = 48.dp)
                        .testTag("sort_sheet_apply_btn")
                ) {
                    Text("Apply", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun NotVisitedSignInAlertDialog(
    onGoToFetcher: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalVaultPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sign in required", style = MaterialTheme.typography.titleLarge) },
        text = {
            Text(
                text = "Please sign in first through the Bonus Fetcher. No fetch is required.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onGoToFetcher()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.accentA,
                    contentColor = palette.ink
                ),
                modifier = Modifier.testTag("not_visited_go_fetcher_btn")
            ) {
                Text("Open Bonus Fetcher", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("not_visited_alert_dismiss_btn")
            ) {
                Text("OK", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large
    )
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
                "Runs using your selected speed preset with automatic rate-limit protection and resumable checkpoints across $totalCount domains. Matches are bookmarked and saved locally.",
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
    bonusMode: BonusMode,
    cardColor: CardColor,
    sortMode: SortMode,
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
                DomainWalletCard(
                    item = item,
                    walletMode = true,
                    bonusMode = bonusMode,
                    cardColor = cardColor,
                    sortMode = sortMode,
                    compact = false,
                    initialExpanded = true,
                    onCardClick = {},
                    onDetailClick = {},
                    onBookmarkToggle = { onToggleBookmark(item.site.domain) }
                )
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
