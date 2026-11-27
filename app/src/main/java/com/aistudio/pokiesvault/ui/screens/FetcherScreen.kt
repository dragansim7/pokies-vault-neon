package com.aistudio.pokiesvault.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aistudio.pokiesvault.R
import com.aistudio.pokiesvault.data.model.BonusMode
import com.aistudio.pokiesvault.data.model.SortMode
import com.aistudio.pokiesvault.ui.components.BackgroundBatteryHintBanner
import com.aistudio.pokiesvault.ui.components.CaptchaSignInDialog
import com.aistudio.pokiesvault.ui.components.DomainWalletCard
import com.aistudio.pokiesvault.ui.components.EmptyStateCard
import com.aistudio.pokiesvault.ui.components.GroupedRejectedSection
import com.aistudio.pokiesvault.ui.components.LiveFetchStatsCard
import com.aistudio.pokiesvault.ui.components.OutgoingIpCard
import com.aistudio.pokiesvault.ui.components.ResponsibleGamblingFooter
import com.aistudio.pokiesvault.ui.components.SpeedPresetSelector
import com.aistudio.pokiesvault.ui.components.WafPauseAndCheckpointBanner
import com.aistudio.pokiesvault.ui.theme.LocalVaultPalette
import com.aistudio.pokiesvault.ui.theme.MonoTextStyles
import com.aistudio.pokiesvault.ui.viewmodel.VaultViewModel
import com.aistudio.pokiesvault.util.DomainUtil
import com.aistudio.pokiesvault.util.FormatUtil

@Composable
fun FetcherScreen(
    viewModel: VaultViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.fetcherState.collectAsStateWithLifecycle()
    val egressState by viewModel.egressState.collectAsStateWithLifecycle()
    val captchaSignInDomain by viewModel.captchaSignInDomain.collectAsStateWithLifecycle()
    val palette = LocalVaultPalette.current

    var showPassword by remember { mutableStateOf(false) }
    var showFallbacks by remember { mutableStateOf(false) }
    var singleDomainInput by remember { mutableStateOf("") }

    var phoneInput by remember(state.credentials.phone) { mutableStateOf(state.credentials.phone) }
    var passwordInput by remember(state.credentials.password) { mutableStateOf(state.credentials.password) }

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
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
        ) {
            item(key = "fetcher_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.fetcher_title),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.fetcher_subtitle),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    OutlinedButton(
                        onClick = { viewModel.setFetchHistoryOpen(true) },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.testTag("fetch_history_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Fetch history",
                            tint = palette.accentA,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("History (${state.fetchRuns.size})", color = palette.accentA)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Outgoing Device IP Panel
            item(key = "fetcher_outgoing_ip") {
                OutgoingIpCard(
                    egressState = egressState,
                    onRefresh = { viewModel.refreshOutgoingIp() }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Live Fetch Telemetry Panel
            item(key = "fetcher_live_stats") {
                LiveFetchStatsCard(state = state)
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Battery optimization shortcut when Background Runs is ON
            if (state.allowBackgroundRuns) {
                item(key = "fetcher_bg_battery_hint") {
                    BackgroundBatteryHintBanner(
                        allowBackgroundRuns = state.allowBackgroundRuns,
                        tagPrefix = "fetcher_"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // Actionable WAF Auto-Pause or Saved Checkpoint Banner
            val checkpoint = state.activeCheckpoint
            if (checkpoint != null && !state.isFetching) {
                item(key = "fetcher_checkpoint_banner") {
                    WafPauseAndCheckpointBanner(
                        checkpoint = checkpoint,
                        currentIp = egressState.info?.ip,
                        isRefreshingIp = egressState.isLoading,
                        isFetching = state.isFetching,
                        onRefreshIp = { viewModel.refreshOutgoingIp() },
                        onResume = { viewModel.resumeCheckpoint() },
                        onDiscard = { viewModel.discardCheckpoint() }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // Credentials + Speed Preset + Concurrency Console Card
            item(key = "fetcher_controls") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Adaptive Pool Telemetry Strip
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${state.speedPreset.displayName.uppercase()} POOL · ${state.workerLimit} MAX WORKERS",
                                color = palette.accentA,
                                style = MonoTextStyles.metricSmall,
                                fontWeight = FontWeight.Bold
                            )
                            if (state.isFetching) {
                                Text(
                                    text = "${state.activeWorkers} active · ${state.progressDone}/${state.progressTotal}",
                                    color = palette.live,
                                    style = MonoTextStyles.metricSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Selectable Speed Preset (Slow (Safe) / Medium (Default) / Fast (Caution))
                        SpeedPresetSelector(
                            selected = state.speedPreset,
                            enabled = !state.isFetching,
                            onSelect = { viewModel.setFetchSpeedPreset(it) }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Phone number", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = phoneInput,
                            onValueChange = {
                                phoneInput = it
                                viewModel.updateCredentials(it, passwordInput)
                            },
                            placeholder = { Text("04…", color = palette.faint) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
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
                                .testTag("phone_input")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text("Password", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = {
                                passwordInput = it
                                viewModel.updateCredentials(phoneInput, it)
                            },
                            placeholder = { Text("Enter account password", color = palette.faint) },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        contentDescription = if (showPassword) "Hide password" else "Show password",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
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
                                .testTag("password_input")
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(
                            onClick = { showFallbacks = !showFallbacks },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.testTag("toggle_fallbacks_btn")
                        ) {
                            Text(
                                text = if (showFallbacks) "Hide fallback logins" else "Fallback logins (up to 3)",
                                color = palette.accentA,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        AnimatedVisibility(visible = showFallbacks) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.credentials.fallbacks.forEachIndexed { index, fb ->
                                    var fbPhone by remember(fb.phone) { mutableStateOf(fb.phone) }
                                    var fbPass by remember(fb.password) { mutableStateOf(fb.password) }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = fbPhone,
                                            onValueChange = {
                                                fbPhone = it
                                                viewModel.updateFallback(index, it, fbPass)
                                            },
                                            label = { Text("Fallback ${index + 2} phone") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                            singleLine = true,
                                            shape = MaterialTheme.shapes.small,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = fbPass,
                                            onValueChange = {
                                                fbPass = it
                                                viewModel.updateFallback(index, fbPhone, it)
                                            },
                                            label = { Text("Password") },
                                            visualTransformation = PasswordVisualTransformation(),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                            singleLine = true,
                                            shape = MaterialTheme.shapes.small,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Primary Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.startFetch(state.mineTargetDomains) },
                                enabled = !state.isFetching,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = palette.accentA,
                                    contentColor = palette.ink
                                ),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier
                                    .weight(1.4f)
                                    .testTag("fetch_my_accounts_btn")
                            ) {
                                if (state.isFetching) {
                                    CircularProgressIndicator(
                                        color = palette.ink,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = null,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = "Fetch (${state.mineTargetDomains.size})",
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (state.isFetching) {
                                Button(
                                    onClick = { viewModel.stopFetch() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = palette.dead.copy(alpha = 0.2f),
                                        contentColor = palette.dead
                                    ),
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("stop_fetch_btn")
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Stop", fontWeight = FontWeight.Bold)
                                }
                            } else if (state.retryableDomains.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = { viewModel.retryFailedFetches() },
                                    border = BorderStroke(1.dp, palette.slow),
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("retry_failed_btn")
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = palette.slow,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Retry (${state.retryableDomains.size})", color = palette.slow)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Single domain quick check
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = singleDomainInput,
                                onValueChange = { singleDomainInput = it },
                                placeholder = { Text("Single domain, e.g. 9au.com", color = palette.faint) },
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
                                    .testTag("single_domain_input")
                            )
                            Button(
                                onClick = {
                                    val normalized = DomainUtil.normalizeDomain(singleDomainInput)
                                    if (DomainUtil.isPublicDomain(normalized)) {
                                        viewModel.startFetch(listOf(normalized))
                                    } else {
                                        viewModel.emitMessage("Enter a valid domain like 9au.com")
                                    }
                                },
                                enabled = !state.isFetching && singleDomainInput.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = palette.accentA
                                ),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.testTag("check_single_btn")
                            ) {
                                Text("Check", fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        TextButton(
                            onClick = { viewModel.setConfirmScanOpen(true) },
                            enabled = !state.isFetching && state.allTargetDomains.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("scan_all_btn")
                        ) {
                            Text(
                                text = "Scan full catalog (${state.allTargetDomains.size} sites) to discover accounts",
                                color = palette.accentB,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = state.statusMessage,
                            color = palette.accentA,
                            style = MaterialTheme.typography.bodySmall
                        )

                        if (state.progressTotal > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            val fraction = (state.progressDone.toFloat() / state.progressTotal.toFloat()).coerceIn(0f, 1f)
                            LinearProgressIndicator(
                                progress = { fraction },
                                color = palette.accentA,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                            )
                        }
                    }
                }
            }

            // Balance Diffs Banner
            if (state.fetchDiffs.isNotEmpty()) {
                item(key = "fetcher_diffs") {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = palette.live.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, palette.live.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Balance changes since previous run",
                                color = palette.live,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            state.fetchDiffs.forEach { diff ->
                                Text(
                                    text = "• $diff",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MonoTextStyles.metricSmall
                                )
                            }
                        }
                    }
                }
            }

            // Shared Sort / Filter Status Bar for Fetcher Results
            item(key = "fetcher_sort_filter_bar") {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Order: ${state.sortMode.label}",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            val filterMeta = buildList {
                                if (state.bonusMode != BonusMode.NONE) {
                                    add("${state.bonusMode.emoji} ${state.bonusMode.label}")
                                }
                                if (state.notVisitedFilter) add("Not visited")
                                if (state.signupOnlyFilter) add("Signup only")
                                if (state.mineOnlyFilter) add("Bookmarked")
                            }.joinToString(" · ")
                            if (filterMeta.isNotBlank()) {
                                Text(
                                    text = filterMeta,
                                    color = palette.accentA,
                                    style = MonoTextStyles.metricSmall
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = { viewModel.setSortFilterOpen(true) },
                            border = BorderStroke(
                                1.dp,
                                if (state.sortMode != SortMode.DEFAULT || state.bonusMode != BonusMode.NONE) {
                                    palette.accentA
                                } else {
                                    MaterialTheme.colorScheme.outline
                                }
                            ),
                            modifier = Modifier.testTag("fetcher_sort_filter_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Open Sort and Filter",
                                tint = palette.accentA,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("⇅ Sort / Filter", color = palette.accentA)
                        }
                    }
                }
            }

            // Section 1: Accounts with balance or bonuses
            item(key = "section_valid_header") {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ACCOUNTS WITH VALUE · ${state.validItems.size}",
                        color = palette.accentA,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    if (state.validItems.isNotEmpty()) {
                        Text(
                            text = FormatUtil.money(state.totalCash),
                            color = palette.live,
                            style = MonoTextStyles.balanceMedium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (state.validItems.isEmpty()) {
                item(key = "empty_valid") {
                    EmptyStateCard(
                        icon = Icons.Default.Bolt,
                        title = "No matching balances or bonuses",
                        subtitle = if (state.rawSignedInCount > 0) {
                            "Adjust your active Sort / Filter options above to see hidden signed-in accounts."
                        } else {
                            "Enter your credentials above and tap Fetch to inspect wallets safely."
                        }
                    )
                }
            } else {
                items(items = state.validItems, key = { "ok_${it.site.domain}" }) { item ->
                    DomainWalletCard(
                        item = item,
                        walletMode = true,
                        bonusMode = state.bonusMode,
                        cardColor = state.cardColor,
                        sortMode = state.sortMode,
                        compact = false,
                        onCardClick = { viewModel.markVisited(item.site.domain) },
                        onDetailClick = { viewModel.openSiteDetail(item.site.domain) },
                        onBookmarkToggle = { viewModel.toggleMine(item.site.domain) },
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            // Section 2: Signed in, zero waiting
            if (state.emptyItems.isNotEmpty()) {
                item(key = "section_empty_header") {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "SIGNED IN · NOTHING WAITING (${state.emptyItems.size})",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                items(items = state.emptyItems, key = { "empty_${it.site.domain}" }) { item ->
                    DomainWalletCard(
                        item = item,
                        walletMode = true,
                        bonusMode = state.bonusMode,
                        cardColor = state.cardColor,
                        sortMode = state.sortMode,
                        compact = false,
                        onCardClick = { viewModel.markVisited(item.site.domain) },
                        onDetailClick = { viewModel.openSiteDetail(item.site.domain) },
                        onBookmarkToggle = { viewModel.toggleMine(item.site.domain) },
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            // Section 3: Grouped Rejected / Partial Failures
            if (state.rejectedGroups.isNotEmpty()) {
                item(key = "section_rejected_grouped") {
                    Spacer(modifier = Modifier.height(16.dp))
                    GroupedRejectedSection(
                        groups = state.rejectedGroups,
                        totalRejected = state.rejectedAccounts.size,
                        retryableCount = state.retryableDomains.size,
                        isFetching = state.isFetching,
                        onRetryCode = { code -> viewModel.retryRejectedCode(code) },
                        onRetryAllRecoverable = { viewModel.retryFailedFetches() },
                        onSolveCaptcha = { domain -> viewModel.openCaptchaSignIn(domain) }
                    )
                }
            }

            item(key = "fetcher_footer") {
                ResponsibleGamblingFooter()
            }
        }
    }

    val activeCaptchaDomain = captchaSignInDomain
    if (activeCaptchaDomain != null) {
        CaptchaSignInDialog(
            domain = activeCaptchaDomain,
            savedPhone = state.credentials.phone,
            savedPassword = state.credentials.password,
            onDismiss = { viewModel.dismissCaptchaSignIn() },
            onSessionCaptured = { accessId, accessToken, host, merchantId ->
                viewModel.completeCaptchaSignIn(
                    domain = activeCaptchaDomain,
                    accessId = accessId,
                    accessToken = accessToken,
                    host = host,
                    merchantId = merchantId
                )
            }
        )
    }
}
