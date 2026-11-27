package com.aistudio.pokiesvault

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.ui.draw.clip
import androidx.core.content.ContextCompat
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aistudio.pokiesvault.ui.components.AddDomainDialog
import com.aistudio.pokiesvault.ui.components.AppDrawer
import com.aistudio.pokiesvault.ui.components.ConfirmScanDialog
import com.aistudio.pokiesvault.ui.components.FetchHistorySheet
import com.aistudio.pokiesvault.ui.components.NotVisitedSignInAlertDialog
import com.aistudio.pokiesvault.ui.components.SiteDetailSheet
import com.aistudio.pokiesvault.ui.components.SortFilterSheet
import com.aistudio.pokiesvault.ui.navigation.BonusesRoute
import com.aistudio.pokiesvault.ui.navigation.DirectoryRoute
import com.aistudio.pokiesvault.ui.navigation.FetcherRoute
import com.aistudio.pokiesvault.ui.navigation.IntelRoute
import com.aistudio.pokiesvault.ui.screens.BonusFinderScreen
import com.aistudio.pokiesvault.ui.screens.DirectoryScreen
import com.aistudio.pokiesvault.ui.screens.DomainIntelScreen
import com.aistudio.pokiesvault.ui.screens.FetcherScreen
import com.aistudio.pokiesvault.ui.theme.LocalVaultPalette
import com.aistudio.pokiesvault.ui.theme.OutfitFontFamily
import com.aistudio.pokiesvault.ui.theme.PokiesVaultTheme
import com.aistudio.pokiesvault.ui.viewmodel.VaultViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class NavDestinationItem(
    val route: Any,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val VAULT_DESTINATIONS = listOf(
    NavDestinationItem(DirectoryRoute, R.string.nav_directory, Icons.Filled.GridView, Icons.Outlined.GridView, "nav_tab_directory"),
    NavDestinationItem(FetcherRoute, R.string.nav_fetcher, Icons.Filled.Bolt, Icons.Outlined.Bolt, "nav_tab_fetcher"),
    NavDestinationItem(BonusesRoute, R.string.nav_bonuses, Icons.Filled.CardGiftcard, Icons.Outlined.CardGiftcard, "nav_tab_bonuses"),
    NavDestinationItem(IntelRoute, R.string.nav_intel, Icons.Filled.Dns, Icons.Outlined.Dns, "nav_tab_intel")
)

class MainActivity : ComponentActivity() {
    private val viewModel: VaultViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val globalState by viewModel.globalState.collectAsStateWithLifecycle()
            val dirState by viewModel.directoryState.collectAsStateWithLifecycle()
            val fetcherState by viewModel.fetcherState.collectAsStateWithLifecycle()

            PokiesVaultTheme(themeId = globalState.themeId) {
                val context = LocalContext.current
                val navController = rememberNavController()
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                var pendingExportIncludeCreds by remember { mutableStateOf(false) }

                // SAF Launchers (zero broad storage permissions required)
                val exportJsonLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/json")
                ) { uri ->
                    if (uri != null) {
                        scope.launch {
                            try {
                                val payload = viewModel.buildBackupJson(pendingExportIncludeCreds)
                                withContext(Dispatchers.IO) {
                                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                                        stream.write(payload.toByteArray(Charsets.UTF_8))
                                    }
                                }
                                viewModel.emitMessage("Vault backup exported.")
                            } catch (e: Exception) {
                                viewModel.emitMessage("Export failed: ${e.message ?: "could not write file"}")
                            }
                        }
                    }
                }

                val importJsonLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) {
                        scope.launch {
                            try {
                                val raw = withContext(Dispatchers.IO) {
                                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                                }
                                if (!raw.isNullOrBlank()) {
                                    viewModel.importBackupJson(raw)
                                } else {
                                    viewModel.emitMessage("Selected backup file was empty.")
                                }
                            } catch (e: Exception) {
                                viewModel.emitMessage("Import failed: ${e.message ?: "could not read file"}")
                            }
                        }
                    }
                }

                val importTxtLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) {
                        scope.launch {
                            try {
                                val raw = withContext(Dispatchers.IO) {
                                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                                }
                                if (!raw.isNullOrBlank()) {
                                    viewModel.importDomainsText(raw)
                                } else {
                                    viewModel.emitMessage("Text file was empty.")
                                }
                            } catch (e: Exception) {
                                viewModel.emitMessage("Import failed: ${e.message ?: "could not read file"}")
                            }
                        }
                    }
                }

                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    viewModel.setAllowBackgroundRuns(true)
                    if (!granted) {
                        viewModel.emitMessage("Background runs enabled (enable notifications in system settings to see progress bar).")
                    }
                }

                fun toggleBackgroundRuns() {
                    val turningOn = !globalState.allowBackgroundRuns
                    if (!turningOn) {
                        viewModel.setAllowBackgroundRuns(false)
                        return
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val hasPerm = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!hasPerm) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            return
                        }
                    }
                    viewModel.setAllowBackgroundRuns(true)
                }

                LaunchedEffect(Unit) {
                    viewModel.snackbarEvents.collect { msg ->
                        snackbarHostState.showSnackbar(msg)
                    }
                }

                fun navigateTo(route: Any) {
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        AppDrawer(
                            activeTheme = globalState.themeId,
                            savedAccountsCount = globalState.savedAccountsCount,
                            hiddenDomainsCount = globalState.hiddenDomainsCount,
                            onSelectTheme = { viewModel.selectTheme(it) },
                            onAddDomainClick = { viewModel.setAddDomainOpen(true) },
                            onExportBackupClick = { includeCreds ->
                                pendingExportIncludeCreds = includeCreds
                                exportJsonLauncher.launch("pokies-vault-backup.json")
                            },
                            onImportBackupClick = {
                                importJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                            },
                            onRestoreHiddenClick = { viewModel.restoreHiddenDomains() },
                            onClearCachesClick = { viewModel.clearVolatileCaches() },
                            onClearDeviceClick = { viewModel.clearAllDeviceData() },
                            onCloseDrawer = { scope.launch { drawerState.close() } }
                        )
                    }
                ) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val isExpanded = maxWidth >= 600.dp

                        Scaffold(
                            topBar = {
                                VaultTopBar(
                                    allowBackgroundRuns = globalState.allowBackgroundRuns,
                                    hasActiveBackgroundRun = globalState.hasActiveBackgroundRun,
                                    onTitleClick = { navigateTo(DirectoryRoute) },
                                    onToggleBackgroundRuns = { toggleBackgroundRuns() },
                                    onSettingsClick = { scope.launch { drawerState.open() } }
                                )
                            },
                            bottomBar = {
                                if (!isExpanded) {
                                    VaultBottomNavBar(
                                        currentDestination = currentDestination,
                                        onNavigate = { navigateTo(it) }
                                    )
                                }
                            },
                            snackbarHost = { SnackbarHost(snackbarHostState) },
                            containerColor = MaterialTheme.colorScheme.background,
                            contentWindowInsets = WindowInsets.safeDrawing
                        ) { innerPadding ->
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                if (isExpanded) {
                                    VaultNavigationRail(
                                        currentDestination = currentDestination,
                                        onNavigate = { navigateTo(it) }
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                ) {
                                    NavHost(
                                        navController = navController,
                                        startDestination = DirectoryRoute
                                    ) {
                                        composable<DirectoryRoute> {
                                            DirectoryScreen(
                                                viewModel = viewModel,
                                                isExpanded = isExpanded
                                            )
                                        }
                                        composable<FetcherRoute> {
                                            FetcherScreen(viewModel = viewModel)
                                        }
                                        composable<BonusesRoute> {
                                            BonusFinderScreen(viewModel = viewModel)
                                        }
                                        composable<IntelRoute> {
                                            DomainIntelScreen(
                                                viewModel = viewModel,
                                                onImportTxtClick = {
                                                    importTxtLauncher.launch(arrayOf("text/plain", "*/*"))
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Shared Dialogs & Sheets
                if (globalState.isAddDomainOpen) {
                    AddDomainDialog(
                        onDismiss = { viewModel.setAddDomainOpen(false) },
                        onAdd = { raw, cb -> viewModel.addSingleDomain(raw, markAsMine = true, onResult = cb) }
                    )
                }

                if (globalState.isSortFilterOpen) {
                    SortFilterSheet(
                        currentSort = dirState.preferences.sortMode,
                        currentBonus = dirState.preferences.bonusMode,
                        currentCardColor = dirState.preferences.cardColor,
                        notVisited = dirState.preferences.notVisited,
                        signupOnly = dirState.preferences.signupOnly,
                        mineOnly = dirState.preferences.mineOnly,
                        onCardColorSelected = { viewModel.setCardColor(it) },
                        onApply = { sort, bonus, notVis, signup, mine ->
                            viewModel.applySortFilter(sort, bonus, notVis, signup, mine)
                        },
                        onReset = { viewModel.resetSortFilter() },
                        onDismiss = { viewModel.setSortFilterOpen(false) }
                    )
                }

                if (globalState.isNotVisitedAuthAlertOpen) {
                    NotVisitedSignInAlertDialog(
                        onGoToFetcher = { navigateTo(FetcherRoute) },
                        onDismiss = { viewModel.setNotVisitedAuthAlertOpen(false) }
                    )
                }

                if (globalState.isConfirmScanOpen) {
                    ConfirmScanDialog(
                        totalCount = fetcherState.allTargetDomains.size,
                        onDismiss = { viewModel.setConfirmScanOpen(false) },
                        onConfirm = { viewModel.startFetch(fetcherState.allTargetDomains) }
                    )
                }

                if (globalState.isFetchHistoryOpen) {
                    FetchHistorySheet(
                        runs = fetcherState.fetchRuns,
                        activeRunId = fetcherState.viewingRunId,
                        onSelectRun = { viewModel.loadHistoricalRun(it) },
                        onDismiss = { viewModel.setFetchHistoryOpen(false) }
                    )
                }

                val detailDomain = globalState.selectedDetailDomain
                if (detailDomain != null) {
                    val detailItem = viewModel.getDetailForDomain(detailDomain)
                    if (detailItem != null) {
                        SiteDetailSheet(
                            item = detailItem,
                            bonusMode = dirState.preferences.bonusMode,
                            cardColor = dirState.preferences.cardColor,
                            sortMode = dirState.preferences.sortMode,
                            onFetchNow = { d ->
                                navigateTo(FetcherRoute)
                                viewModel.startFetch(listOf(d))
                            },
                            onToggleBookmark = { viewModel.toggleMine(it) },
                            onDismiss = { viewModel.openSiteDetail(null) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultTopBar(
    allowBackgroundRuns: Boolean,
    hasActiveBackgroundRun: Boolean,
    onTitleClick: () -> Unit,
    onToggleBackgroundRuns: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val palette = LocalVaultPalette.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        color = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "POKIES VAULT",
                style = TextStyle(
                    brush = palette.brandGradient,
                    fontFamily = OutfitFontFamily,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.1.sp
                ),
                modifier = Modifier
                    .clickable { onTitleClick() }
                    .padding(vertical = 4.dp)
                    .testTag("brand_title")
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val bgTint = if (allowBackgroundRuns) {
                    if (hasActiveBackgroundRun) palette.live else palette.accentA
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (allowBackgroundRuns) {
                        bgTint.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    border = BorderStroke(
                        width = if (allowBackgroundRuns) 1.3.dp else 1.dp,
                        color = if (allowBackgroundRuns) bgTint.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clickable { onToggleBackgroundRuns() }
                        .testTag("bg_runs_toggle_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (hasActiveBackgroundRun) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(palette.live)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = stringResource(R.string.bg_runs_toggle_cd),
                                tint = bgTint,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Text(
                            text = if (allowBackgroundRuns) {
                                stringResource(R.string.bg_runs_on_label)
                            } else {
                                stringResource(R.string.bg_runs_off_label)
                            },
                            color = bgTint,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.6.sp
                            )
                        )
                    }
                }

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, palette.accentB.copy(alpha = 0.6f))
                ) {
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("nav_settings_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = stringResource(R.string.open_settings),
                            tint = palette.accentA,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultBottomNavBar(
    currentDestination: androidx.navigation.NavDestination?,
    onNavigate: (Any) -> Unit
) {
    val palette = LocalVaultPalette.current
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        VAULT_DESTINATIONS.forEach { dest ->
            val selected = currentDestination?.hasRoute(dest.route::class) == true
            val label = stringResource(dest.labelRes)
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(dest.route) },
                icon = {
                    Icon(
                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                        contentDescription = label
                    )
                },
                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = palette.accentA,
                    selectedTextColor = palette.accentA,
                    indicatorColor = palette.accentA.copy(alpha = 0.16f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.testTag(dest.testTag)
            )
        }
    }
}

@Composable
private fun VaultNavigationRail(
    currentDestination: androidx.navigation.NavDestination?,
    onNavigate: (Any) -> Unit
) {
    val palette = LocalVaultPalette.current
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.padding(end = 4.dp)
    ) {
        Spacer(modifier = Modifier.size(8.dp))
        VAULT_DESTINATIONS.forEach { dest ->
            val selected = currentDestination?.hasRoute(dest.route::class) == true
            val label = stringResource(dest.labelRes)
            NavigationRailItem(
                selected = selected,
                onClick = { onNavigate(dest.route) },
                icon = {
                    Icon(
                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                        contentDescription = label
                    )
                },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = palette.accentA,
                    selectedTextColor = palette.accentA,
                    indicatorColor = palette.accentA.copy(alpha = 0.16f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.testTag(dest.testTag)
            )
        }
    }
}
