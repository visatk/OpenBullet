package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.ActiveDialog
import com.example.model.MainTab
import com.example.model.ProxyStatus
import com.example.ui.components.ObBottomNavigationBar
import com.example.ui.components.ObNavigationRail
import com.example.ui.components.ObWindowHeader
import com.example.ui.dialogs.OpenBulletDialogHost
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.ConfigEditorScreen
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
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingNotificationAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        pendingNotificationAction?.invoke()
        pendingNotificationAction = null
    }

    fun runWithNotificationPermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                pendingNotificationAction = action
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        action()
    }

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
            state.configEditor != null ||
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
                    if (isCompact && state.configEditor == null) {
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
                            val activeEditor = state.configEditor
                            if (activeEditor != null) {
                                val usableProxies = state.proxies.count {
                                    it.status == ProxyStatus.AVAILABLE || it.status == ProxyStatus.UNTESTED
                                }
                                ConfigEditorScreen(
                                    editorState = activeEditor,
                                    usableProxyCount = usableProxies,
                                    onBack = viewModel::closeConfigEditor,
                                    onMetadataChange = viewModel::updateEditorMetadata,
                                    onApplyPreset = viewModel::applyTemplateToEditor,
                                    onSelectSubTab = viewModel::selectEditorSubTab,
                                    onAddBlock = viewModel::addBlockToEditor,
                                    onUpdateBlock = viewModel::updateBlockInEditor,
                                    onMoveBlock = viewModel::moveBlockInEditor,
                                    onDeleteBlock = viewModel::deleteBlockFromEditor,
                                    onRawScriptChange = viewModel::updateEditorRawScript,
                                    onDebugInputChange = viewModel::updateEditorDebugInputs,
                                    onRunDebug = viewModel::runConfigDebug,
                                    onExportLoli = {
                                        val exported = viewModel.buildActiveEditorConfigExport(
                                            com.example.network.ConfigExportFormat.LOLI
                                        )
                                        if (exported != null) {
                                            val (fileName, content) = exported
                                            copyAndShareTextContent(context, fileName, content)
                                            viewModel.showBannerMessage("Copied & exporting $fileName")
                                        }
                                    },
                                    onSave = viewModel::saveEditedConfig,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
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
                                            onStartStopClick = {
                                                if (state.isRunning || state.isPaused) {
                                                    viewModel.toggleStartStop()
                                                } else {
                                                    runWithNotificationPermission { viewModel.toggleStartStop() }
                                                }
                                            },
                                            onPauseResumeClick = {
                                                if (state.isRunning) {
                                                    viewModel.pauseOrResumeRunner()
                                                } else {
                                                    runWithNotificationPermission { viewModel.pauseOrResumeRunner() }
                                                }
                                            },
                                            onResultTabSelect = viewModel::selectResultTab,
                                            onInspectResultRow = { row ->
                                                viewModel.openDialog(ActiveDialog.InspectResult(row))
                                            },
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
                                            onToggleCheckProxies = {
                                                if (state.isCheckingProxies) {
                                                    viewModel.toggleProxyCheckWork()
                                                } else {
                                                    runWithNotificationPermission { viewModel.toggleProxyCheckWork() }
                                                }
                                            },
                                            onRemoveBad = viewModel::removeBadAndBannedProxies,
                                            onExportProxies = {
                                                val txt = viewModel.buildAliveProxiesExportText()
                                                if (txt.isBlank()) {
                                                    viewModel.showBannerMessage("No proxies to export")
                                                } else {
                                                    shareTextContent(context, "OpenBullet Proxies", txt)
                                                }
                                            },
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
                                            onNewConfigClick = { viewModel.openConfigEditor(null) },
                                            onImportConfigClick = { viewModel.openDialog(ActiveDialog.ImportConfig) },
                                            onExportConfigLoli = { cfg ->
                                                val content = viewModel.buildSingleConfigExport(
                                                    cfg,
                                                    com.example.network.ConfigExportFormat.LOLI
                                                )
                                                val subject = "${cfg.name}.loli"
                                                copyAndShareTextContent(context, subject, content)
                                                viewModel.showBannerMessage("Exported & copied $subject")
                                            },
                                            onExportConfigJson = { cfg ->
                                                val content = viewModel.buildSingleConfigExport(
                                                    cfg,
                                                    com.example.network.ConfigExportFormat.JSON
                                                )
                                                val subject = "${cfg.name}.json"
                                                copyAndShareTextContent(context, subject, content)
                                                viewModel.showBannerMessage("Exported & copied $subject")
                                            },
                                            onExportAllJson = {
                                                val content = viewModel.buildAllConfigsJsonExport()
                                                if (content.isNotBlank()) {
                                                    copyAndShareTextContent(context, "openbullet_configs_backup.json", content)
                                                    viewModel.showBannerMessage("Exported all ${state.configs.size} configs (.json)")
                                                }
                                            },
                                            onEditConfig = { viewModel.openConfigEditor(it) },
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
                                            onExportHits = {
                                                val txt = viewModel.buildHitsExportText()
                                                if (txt.isBlank()) {
                                                    viewModel.showBannerMessage("No hits to export")
                                                } else {
                                                    shareTextContent(context, "OpenBullet Hits", txt)
                                                }
                                            },
                                            onCopyHit = { hit -> viewModel.showBannerMessage("Copied ${hit.data}") },
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
                onOpenNewConfigDialog = { viewModel.openConfigEditor(null) },
                onOpenAddWordlistDialog = { viewModel.openDialog(ActiveDialog.AddWordlist) },
                onCreateConfig = viewModel::addConfig,
                onImportConfigPayload = viewModel::importConfigPayload,
                onImportConfigFromUrl = viewModel::importConfigFromUrl,
                onCreateWordlist = viewModel::addWordlist,
                onImportProxies = viewModel::addProxies,
                onImportProxiesFromUrl = viewModel::importProxiesFromUrl
            )
        }
    }
}

private fun copyAndShareTextContent(context: Context, subject: String, content: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
    cm?.setPrimaryClip(android.content.ClipData.newPlainText(subject, content))
    shareTextContent(context, subject, content)
}

private fun shareTextContent(context: Context, subject: String, content: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, content)
    }
    context.startActivity(Intent.createChooser(intent, subject))
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
