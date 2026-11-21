package com.aistudio.pokiesvault.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aistudio.pokiesvault.data.model.VaultThemeId
import com.aistudio.pokiesvault.ui.theme.LocalVaultPalette
import com.aistudio.pokiesvault.ui.theme.VaultThemes

@Composable
fun AppDrawer(
    activeTheme: VaultThemeId,
    savedAccountsCount: Int,
    hiddenDomainsCount: Int,
    onSelectTheme: (VaultThemeId) -> Unit,
    onAddDomainClick: () -> Unit,
    onExportBackupClick: (includeCredentials: Boolean) -> Unit,
    onImportBackupClick: () -> Unit,
    onRestoreHiddenClick: () -> Unit,
    onClearCachesClick: () -> Unit,
    onClearDeviceClick: () -> Unit,
    onCloseDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalVaultPalette.current
    var showConfirmClear by remember { mutableStateOf(false) }
    var showExportOptions by remember { mutableStateOf(false) }
    var includeCredsInExport by remember { mutableStateOf(false) }

    ModalDrawerSheet(
        modifier = modifier.width(316.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerContentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Vault Settings",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "$savedAccountsCount saved accounts · Room local vault",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                IconButton(
                    onClick = onCloseDrawer,
                    modifier = Modifier.testTag("drawer_close_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "APPEARANCE · 5 ACCENT THEMES",
                color = palette.accentA,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                VaultThemes.all.forEach { themePalette ->
                    val selected = themePalette.id == activeTheme
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectTheme(themePalette.id) }
                            .testTag("theme_option_${themePalette.id.name.lowercase()}"),
                        shape = MaterialTheme.shapes.small,
                        color = if (selected) themePalette.accentA.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(
                            1.dp,
                            if (selected) themePalette.accentA else MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(themePalette.accentA)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(themePalette.accentB)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(themePalette.accentC)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = themePalette.id.displayName,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = themePalette.id.subtitle,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Active theme",
                                    tint = themePalette.accentA,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "VAULT BACKUP & MANAGEMENT",
                color = palette.accentA,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.height(8.dp))

            DrawerActionRow(
                icon = Icons.Default.Add,
                title = "Add custom domain",
                subtitle = "Add a site across all 4 modules",
                onClick = {
                    onAddDomainClick()
                    onCloseDrawer()
                },
                modifier = Modifier.testTag("drawer_add_domain")
            )

            DrawerActionRow(
                icon = Icons.Default.FileDownload,
                title = "Export vault backup (JSON)",
                subtitle = "Save domains, bookmarks & snapshots",
                onClick = { showExportOptions = true },
                modifier = Modifier.testTag("drawer_export_backup")
            )

            DrawerActionRow(
                icon = Icons.Default.FileUpload,
                title = "Import vault backup (JSON)",
                subtitle = "Merge a previously exported JSON file",
                onClick = {
                    onImportBackupClick()
                    onCloseDrawer()
                },
                modifier = Modifier.testTag("drawer_import_backup")
            )

            if (hiddenDomainsCount > 0) {
                DrawerActionRow(
                    icon = Icons.Default.Restore,
                    title = "Restore hidden domains ($hiddenDomainsCount)",
                    subtitle = "Bring back sites removed in Domain Intel",
                    onClick = {
                        onRestoreHiddenClick()
                        onCloseDrawer()
                    },
                    modifier = Modifier.testTag("drawer_restore_hidden")
                )
            }

            DrawerActionRow(
                icon = Icons.Default.CleaningServices,
                title = "Clear volatile caches",
                subtitle = "Reset host IDs, ping statuses & promo cache",
                onClick = {
                    onClearCachesClick()
                    onCloseDrawer()
                },
                modifier = Modifier.testTag("drawer_clear_caches")
            )

            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = palette.live,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Credentials are encrypted via Android Keystore (AES-256-GCM) on this device only.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            DrawerActionRow(
                icon = Icons.Default.DeleteOutline,
                title = "Clear all device data",
                subtitle = "Wipes saved sites, history & encrypted login",
                accentTint = palette.dead,
                onClick = { showConfirmClear = true },
                modifier = Modifier.testTag("drawer_clear_device")
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (showExportOptions) {
        AlertDialog(
            onDismissRequest = { showExportOptions = false },
            title = { Text("Export vault backup", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column {
                    Text(
                        "Exports custom domains, saved bookmarks, visited flags, latest balance snapshots, and your active theme as a JSON file.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { includeCredsInExport = !includeCredsInExport },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = includeCredsInExport,
                            onCheckedChange = { includeCredsInExport = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = palette.accentA,
                                checkmarkColor = palette.ink
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Include login credentials (off by default for safety)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExportOptions = false
                        onExportBackupClick(includeCredsInExport)
                        onCloseDrawer()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.accentA,
                        contentColor = palette.ink
                    ),
                    modifier = Modifier.testTag("confirm_export_json_btn")
                ) {
                    Text("Choose file", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportOptions = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large
        )
    }

    if (showConfirmClear) {
        AlertDialog(
            onDismissRequest = { showConfirmClear = false },
            title = { Text("Clear all device data?", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    "Removes saved sites, fetch history, caches, and encrypted credentials from this device.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmClear = false
                        onClearDeviceClick()
                        onCloseDrawer()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.dead,
                        contentColor = palette.ink
                    ),
                    modifier = Modifier.testTag("confirm_clear_device_btn")
                ) {
                    Text("Clear all", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmClear = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large
        )
    }
}

@Composable
private fun DrawerActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentTint: Color? = null
) {
    val palette = LocalVaultPalette.current
    val iconColor = accentTint ?: palette.accentA
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    color = accentTint ?: MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
