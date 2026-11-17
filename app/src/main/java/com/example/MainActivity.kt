package com.example

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.ActiveDialog
import com.example.model.MainTab
import com.example.ui.components.ObBottomNavigationBar
import com.example.ui.components.ObNavigationRail
import com.example.ui.components.ObWindowHeader
import com.example.ui.dialogs.OpenBulletDialogHost
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.ConfigsScreen
import com.example.ui.screens.HitsDbScreen
import com.example.ui.screens.ProxiesScreen
import com.example.ui.screens.RunnerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.ToolsScreen
import com.example.ui.screens.WordlistsScreen
import com.example.ui.theme.ObColors
import com.example.ui.theme.OpenBulletTheme
import com.example.viewmodel.OpenBulletViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        setContent {
            val viewModel: OpenBulletViewModel = viewModel()
            OpenBulletApp(viewModel = viewModel)
        }
    }
}

@Composable
fun OpenBulletApp(
    viewModel: OpenBulletViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.statusBannerMessage) {
        val msg = state.statusBannerMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
            viewModel.clearBannerMessage()
        }
    }

    BackHandler(
        enabled = state.activeDialog != ActiveDialog.None ||
            state.tabHistory.isNotEmpty() ||
            state.activeTab != MainTab.RUNNER
    ) {
        viewModel.navigateBack()
    }

    OpenBulletTheme(accentColorOverride = Color(state.settings.accentHex)) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(ObColors.ChromeBackground)
        ) {
            val isCompact = maxWidth < 600.dp

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = ObColors.WorkspaceBackground,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (isCompact) {
                        ObBottomNavigationBar(
                            activeTab = state.activeTab,
                            onSelectTab = viewModel::selectTab
                        )
                    }
                },
                snackbarHost = {
                    SnackbarHost(hostState = snackbarHostState) { data ->
                        Snackbar(
                            snackbarData = data,
                            containerColor = ObColors.SurfaceTableHeader,
                            contentColor = ObColors.TextPrimary
                        )
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    ObWindowHeader(
                        isCompact = isCompact,
                        isRunning = state.isRunning,
                        activeTab = state.activeTab,
                        timestampsEnabled = state.settings.showTimestampsInConsole,
                        onSelectTab = viewModel::selectTab,
                        onToggleTimestamps = viewModel::toggleTimestampLogging,
                        onSnapshotClick = viewModel::captureSnapshotSummary
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        if (!isCompact) {
                            ObNavigationRail(
                                activeTab = state.activeTab,
                                onSelectTab = viewModel::selectTab
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            AnimatedContent(
                                targetState = state.activeTab,
                                transitionSpec = {
                                    fadeIn(animationSpec = tween(180)) togetherWith
                                        fadeOut(animationSpec = tween(140))
                                },
                                label = "tab_transition"
                            ) { tab ->
                                when (tab) {
                                    MainTab.RUNNER -> RunnerScreen(
                                        state = state,
                                        onStartLineChange = viewModel::updateStartLine,
                                        onBotsChange = viewModel::updateBotsCount,
                                        onEditStartClick = { viewModel.openDialog(ActiveDialog.EditStartLine) },
                                        onEditBotsClick = { viewModel.openDialog(ActiveDialog.EditBotsCount) },
                                        onProxyModeSelect = viewModel::selectProxyMode,
                                        onStartStopClick = viewModel::toggleStartStop,
                                        onResultTabSelect = viewModel::selectResultTab,
                                        onOpenConfigDialog = { viewModel.openDialog(ActiveDialog.SelectConfig) },
                                        onOpenWordlistDialog = { viewModel.openDialog(ActiveDialog.SelectWordlist) },
                                        onClearConsole = viewModel::clearConsoleLogs,
                                        onBackClick = { viewModel.navigateBack() },
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    MainTab.PROXIES -> ProxiesScreen(
                                        proxies = state.proxies,
                                        activeFilter = state.proxyFilter,
                                        isCheckingProxies = state.isCheckingProxies,
                                        proxyCheckStatusText = state.proxyCheckStatusText,
                                        onFilterChange = viewModel::setProxyFilter,
                                        onAddClick = { viewModel.openDialog(ActiveDialog.AddProxies) },
                                        onToggleCheckProxies = viewModel::toggleProxyCheckWork,
                                        onDeleteProxy = viewModel::deleteProxy,
                                        onClearAll = viewModel::clearProxies,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    MainTab.WORDLISTS -> WordlistsScreen(
                                        wordlists = state.wordlists,
                                        selectedWordlistId = state.selectedWordlist?.id,
                                        onAddClick = { viewModel.openDialog(ActiveDialog.AddWordlist) },
                                        onSelectForRunner = {
                                            viewModel.selectWordlist(it)
                                            viewModel.selectTab(MainTab.RUNNER)
                                        },
                                        onDeleteWordlist = viewModel::deleteWordlist,
                                        onClearAll = viewModel::clearWordlists,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    MainTab.CONFIGS -> ConfigsScreen(
                                        configs = state.configs,
                                        selectedPreviewId = state.selectedConfigPreviewId,
                                        activeRunnerConfigId = state.selectedConfig?.id,
                                        onNewConfigClick = { viewModel.openDialog(ActiveDialog.AddConfig) },
                                        onSelectPreview = viewModel::selectConfigForPreview,
                                        onUseInRunner = {
                                            viewModel.selectConfig(it)
                                            viewModel.selectTab(MainTab.RUNNER)
                                        },
                                        onDeleteConfig = viewModel::deleteConfig,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    MainTab.HITS_DB -> HitsDbScreen(
                                        hits = state.hitsDb,
                                        searchQuery = state.hitsSearchQuery,
                                        typeFilter = state.hitsTypeFilter,
                                        onSearchChange = viewModel::updateHitsSearchQuery,
                                        onTypeFilterChange = viewModel::setHitsTypeFilter,
                                        onDeleteHit = viewModel::deleteHitItem,
                                        onClearAll = viewModel::clearHitsDb,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    MainTab.TOOLS -> ToolsScreen(
                                        tools = state.tools,
                                        viewModel = viewModel,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    MainTab.SETTINGS -> SettingsScreen(
                                        settings = state.settings,
                                        viewModel = viewModel,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    MainTab.ABOUT -> AboutScreen(
                                        nativeEngineBanner = state.nativeEngineBanner,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }
            }

            OpenBulletDialogHost(
                activeDialog = state.activeDialog,
                currentStartLine = state.startLine,
                currentBotsCount = state.botsCount,
                configs = state.configs,
                wordlists = state.wordlists,
                onDismiss = viewModel::dismissDialog,
                onSelectConfig = viewModel::selectConfig,
                onSelectWordlist = viewModel::selectWordlist,
                onUpdateStartLine = viewModel::updateStartLine,
                onUpdateBotsCount = viewModel::updateBotsCount,
                onOpenNewConfigDialog = { viewModel.openDialog(ActiveDialog.AddConfig) },
                onOpenAddWordlistDialog = { viewModel.openDialog(ActiveDialog.AddWordlist) },
                onCreateConfig = viewModel::addConfig,
                onCreateWordlist = viewModel::addWordlist,
                onImportProxies = viewModel::addProxies,
                onImportProxiesFromUrl = viewModel::importProxiesFromUrl
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 960, heightDp = 640)
@Composable
private fun OpenBulletWidePreview() {
    OpenBulletTheme {
        RunnerScreen(
            state = com.example.model.OpenBulletUiState(),
            onStartLineChange = {},
            onBotsChange = {},
            onEditStartClick = {},
            onEditBotsClick = {},
            onProxyModeSelect = {},
            onStartStopClick = {},
            onResultTabSelect = {},
            onOpenConfigDialog = {},
            onOpenWordlistDialog = {},
            onClearConsole = {},
            onBackClick = {}
        )
    }
}
