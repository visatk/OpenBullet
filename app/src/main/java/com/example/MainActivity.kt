package com.example

import android.Manifest
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.example.model.AppThemeMode
import com.example.model.MainTab
import com.example.model.RunnerUiState
import com.example.ui.components.ObBottomNavigationBar
import com.example.ui.components.ObNavigationDrawerSheet
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
import com.example.ui.theme.OpenBulletTheme
import com.example.util.FileExportHelper
import com.example.viewmodel.AppShellViewModel
import com.example.viewmodel.AppViewModelFactory
import com.example.viewmodel.ConfigsViewModel
import com.example.viewmodel.HitsDbViewModel
import com.example.viewmodel.ProxiesViewModel
import com.example.viewmodel.RunnerViewModel
import com.example.viewmodel.SettingsViewModel
import com.example.viewmodel.ToolsViewModel
import com.example.viewmodel.UiEvent
import com.example.viewmodel.WordlistsViewModel
import kotlinx.coroutines.flow.merge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        setContent {
            OpenBulletApp(activity = this)
        }
    }
}

@Composable
fun OpenBulletApp(
    activity: ComponentActivity? = null,
    viewModelFactory: AppViewModelFactory = remember { AppViewModelFactory() }
) {
    val shellVm: AppShellViewModel = viewModel(factory = viewModelFactory)
    val runnerVm: RunnerViewModel = viewModel(factory = viewModelFactory)
    val proxiesVm: ProxiesViewModel = viewModel(factory = viewModelFactory)
    val wordlistsVm: WordlistsViewModel = viewModel(factory = viewModelFactory)
    val configsVm: ConfigsViewModel = viewModel(factory = viewModelFactory)
    val hitsVm: HitsDbViewModel = viewModel(factory = viewModelFactory)
    val toolsVm: ToolsViewModel = viewModel(factory = viewModelFactory)
    val settingsVm: SettingsViewModel = viewModel(factory = viewModelFactory)

    val shellState by shellVm.uiState.collectAsStateWithLifecycle()
    val activeDialog by shellVm.activeDialog.collectAsStateWithLifecycle()
    val runnerState by runnerVm.uiState.collectAsStateWithLifecycle()
    val proxiesState by proxiesVm.uiState.collectAsStateWithLifecycle()
    val wordlistsState by wordlistsVm.uiState.collectAsStateWithLifecycle()
    val configsState by configsVm.uiState.collectAsStateWithLifecycle()
    val hitsState by hitsVm.uiState.collectAsStateWithLifecycle()
    val toolsState by toolsVm.uiState.collectAsStateWithLifecycle()
    val settingsState by settingsVm.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingNotificationAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val resolvedDarkTheme = when (shellState.themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
    }

    DisposableEffect(activity, resolvedDarkTheme) {
        if (activity != null) {
            val barStyle = if (resolvedDarkTheme) {
                SystemBarStyle.dark(AndroidColor.TRANSPARENT)
            } else {
                SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
            }
            activity.enableEdgeToEdge(
                statusBarStyle = barStyle,
                navigationBarStyle = barStyle
            )
        }
        onDispose {}
    }

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

    LaunchedEffect(Unit) {
        merge(
            shellVm.events,
            runnerVm.events,
            proxiesVm.events,
            wordlistsVm.events,
            configsVm.events,
            hitsVm.events,
            toolsVm.events,
            settingsVm.events
        ).collect { event ->
            when (event) {
                is UiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        duration = SnackbarDuration.Short
                    )
                }
                is UiEvent.ShareFile -> {
                    val shared = FileExportHelper.shareTextFile(
                        context = context,
                        fileName = event.fileName,
                        content = event.content,
                        mimeType = event.mimeType,
                        alsoCopyToClipboard = event.alsoCopyToClipboard
                    )
                    if (!shared) {
                        snackbarHostState.showSnackbar(
                            message = "Copied ${event.fileName} (no share handler found)",
                            duration = SnackbarDuration.Short
                        )
                    }
                }
                is UiEvent.CopyText -> {
                    FileExportHelper.copyToClipboard(context, event.label, event.text)
                    snackbarHostState.showSnackbar(
                        message = event.feedbackMessage,
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    }

    val openEditor = configsState.editor
    BackHandler(
        enabled = activeDialog != ActiveDialog.None ||
            openEditor != null ||
            shellState.tabHistory.isNotEmpty() ||
            shellState.activeTab != MainTab.RUNNER
    ) {
        shellVm.navigateBack(
            hasOpenEditor = openEditor != null,
            closeEditor = configsVm::closeEditor
        )
    }

    OpenBulletTheme(
        themeMode = shellState.themeMode,
        useDynamicColor = shellState.useDynamicColor,
        accentColorOverride = Color(shellState.accentHex)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        ) {
            val isCompact = maxWidth < 600.dp
            val isExpanded = maxWidth >= 840.dp

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (isCompact && openEditor == null) {
                        ObBottomNavigationBar(
                            shellState = shellState,
                            onSelectTab = { tab ->
                                if (openEditor != null) configsVm.closeEditor()
                                shellVm.selectTab(tab)
                            }
                        )
                    }
                },
                snackbarHost = {
                    SnackbarHost(hostState = snackbarHostState) { data ->
                        Snackbar(
                            snackbarData = data,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
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
                        isRunning = shellState.isRunnerActive,
                        activeTab = shellState.activeTab,
                        timestampsEnabled = shellState.showTimestampsInConsole,
                        onSelectTab = { tab ->
                            if (openEditor != null) configsVm.closeEditor()
                            shellVm.selectTab(tab)
                        },
                        onToggleTimestamps = shellVm::toggleTimestamps,
                        onSnapshotClick = runnerVm::captureSnapshotSummary
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        when {
                            isExpanded -> {
                                ObNavigationDrawerSheet(
                                    shellState = shellState,
                                    onSelectTab = { tab ->
                                        if (openEditor != null) configsVm.closeEditor()
                                        shellVm.selectTab(tab)
                                    }
                                )
                            }
                            !isCompact -> {
                                ObNavigationRail(
                                    shellState = shellState,
                                    onSelectTab = { tab ->
                                        if (openEditor != null) configsVm.closeEditor()
                                        shellVm.selectTab(tab)
                                    }
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            if (openEditor != null) {
                                ConfigEditorScreen(
                                    editorState = openEditor,
                                    usableProxyCount = configsState.usableProxyCount,
                                    onBack = configsVm::closeEditor,
                                    onMetadataChange = configsVm::updateEditorMetadata,
                                    onApplyPreset = configsVm::applyPresetToEditor,
                                    onSelectSubTab = configsVm::selectEditorSubTab,
                                    onAddBlock = configsVm::addBlockToEditor,
                                    onUpdateBlock = configsVm::updateBlockInEditor,
                                    onMoveBlock = configsVm::moveBlockInEditor,
                                    onDeleteBlock = configsVm::deleteBlockFromEditor,
                                    onRawScriptChange = configsVm::updateEditorRawScript,
                                    onDebugInputChange = configsVm::updateEditorDebugInputs,
                                    onRunDebug = configsVm::runConfigDebug,
                                    onExportLoli = configsVm::exportActiveEditorLoli,
                                    onSave = { loadInRunner ->
                                        val loaded = configsVm.saveEditedConfig(loadInRunner)
                                        if (loaded) shellVm.selectTab(MainTab.RUNNER)
                                    },
                                    onCopyFeedback = shellVm::showMessage,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                AnimatedContent(
                                    targetState = shellState.activeTab,
                                    transitionSpec = {
                                        fadeIn(animationSpec = tween(180)) togetherWith
                                            fadeOut(animationSpec = tween(140))
                                    },
                                    label = "tab_transition"
                                ) { tab ->
                                    when (tab) {
                                        MainTab.RUNNER -> RunnerScreen(
                                            state = runnerState,
                                            onStartLineDraft = runnerVm::updateStartLineDraft,
                                            onStartLineCommit = runnerVm::commitStartLine,
                                            onBotsDraft = runnerVm::updateBotsCountDraft,
                                            onBotsCommit = runnerVm::commitBotsCount,
                                            onEditStartClick = { shellVm.openDialog(ActiveDialog.EditStartLine) },
                                            onEditBotsClick = { shellVm.openDialog(ActiveDialog.EditBotsCount) },
                                            onProxyModeSelect = runnerVm::selectProxyMode,
                                            onStartStopClick = {
                                                if (runnerState.isRunning || runnerState.isPaused) {
                                                    runnerVm.toggleStartStop()
                                                } else {
                                                    runWithNotificationPermission { runnerVm.toggleStartStop() }
                                                }
                                            },
                                            onPauseResumeClick = {
                                                if (runnerState.isRunning) {
                                                    runnerVm.pauseOrResumeRunner()
                                                } else {
                                                    runWithNotificationPermission { runnerVm.pauseOrResumeRunner() }
                                                }
                                            },
                                            onResultTabSelect = runnerVm::selectResultTab,
                                            onInspectResultRow = { row ->
                                                shellVm.openDialog(ActiveDialog.InspectResult(row))
                                            },
                                            onOpenConfigDialog = { shellVm.openDialog(ActiveDialog.SelectConfig) },
                                            onOpenWordlistDialog = { shellVm.openDialog(ActiveDialog.SelectWordlist) },
                                            onLoadStarterSample = runnerVm::loadStarterSample,
                                            onClearConsole = runnerVm::clearConsoleLogs,
                                            onCopyConsole = { shellVm.showMessage("Copied console output") },
                                            onBackClick = {
                                                shellVm.navigateBack(
                                                    hasOpenEditor = false,
                                                    closeEditor = {}
                                                )
                                            },
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        MainTab.PROXIES -> ProxiesScreen(
                                            state = proxiesState,
                                            onProtocolFilterChange = proxiesVm::setProtocolFilter,
                                            onStatusFilterChange = proxiesVm::setStatusFilter,
                                            onAddClick = { shellVm.openDialog(ActiveDialog.AddProxies) },
                                            onToggleCheckProxies = {
                                                if (proxiesState.isCheckingProxies) {
                                                    proxiesVm.toggleCheckProxies()
                                                } else {
                                                    runWithNotificationPermission { proxiesVm.toggleCheckProxies() }
                                                }
                                            },
                                            onRemoveBad = proxiesVm::removeBadAndBanned,
                                            onExportProxies = proxiesVm::exportProxies,
                                            onDeleteProxy = proxiesVm::deleteProxy,
                                            onClearAll = proxiesVm::clearAll,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        MainTab.WORDLISTS -> WordlistsScreen(
                                            state = wordlistsState,
                                            onAddClick = { shellVm.openDialog(ActiveDialog.AddWordlist) },
                                            onSelectForRunner = { list ->
                                                wordlistsVm.selectForRunner(list)
                                                shellVm.selectTab(MainTab.RUNNER)
                                            },
                                            onDeleteWordlist = wordlistsVm::deleteWordlist,
                                            onClearAll = wordlistsVm::clearAll,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        MainTab.CONFIGS -> ConfigsScreen(
                                            state = configsState,
                                            onSearchQueryChange = configsVm::updateSearchQuery,
                                            onCategoryFilterChange = configsVm::setCategoryFilter,
                                            onNewConfigClick = { configsVm.openEditor(null) },
                                            onImportConfigClick = { shellVm.openDialog(ActiveDialog.ImportConfig) },
                                            onDuplicateConfig = configsVm::duplicateConfig,
                                            onExportConfigLoli = { cfg ->
                                                configsVm.exportSingleConfig(cfg, com.example.network.ConfigExportFormat.LOLI)
                                            },
                                            onExportConfigJson = { cfg ->
                                                configsVm.exportSingleConfig(cfg, com.example.network.ConfigExportFormat.JSON)
                                            },
                                            onExportAllJson = configsVm::exportAllConfigsJson,
                                            onEditConfig = { configsVm.openEditor(it) },
                                            onSelectPreview = configsVm::selectPreview,
                                            onUseInRunner = { cfg ->
                                                configsVm.useInRunner(cfg)
                                                shellVm.selectTab(MainTab.RUNNER)
                                            },
                                            onDeleteConfig = configsVm::deleteConfig,
                                            onCopyScriptFeedback = { shellVm.showMessage("Copied LoliScript to clipboard") },
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        MainTab.HITS_DB -> HitsDbScreen(
                                            state = hitsState,
                                            onSearchChange = hitsVm::updateSearchQuery,
                                            onTypeFilterChange = hitsVm::setTypeFilter,
                                            onExportHits = hitsVm::exportHits,
                                            onInspectHit = { hit -> shellVm.openDialog(ActiveDialog.InspectHit(hit)) },
                                            onCopyHit = hitsVm::copyHit,
                                            onDeleteHit = hitsVm::deleteHit,
                                            onClearAll = hitsVm::clearAll,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        MainTab.TOOLS -> ToolsScreen(
                                            tools = toolsState,
                                            onSelectSection = toolsVm::selectSection,
                                            onCleanerInputChange = toolsVm::updateCleanerInput,
                                            onToggleCleanerDuplicates = toolsVm::toggleCleanerDuplicates,
                                            onToggleCleanerTrim = toolsVm::toggleCleanerTrim,
                                            onToggleCleanerEmpty = toolsVm::toggleCleanerEmpty,
                                            onRunCleaner = toolsVm::runListCleaner,
                                            onSaveCleanedAsWordlist = toolsVm::saveCleanedAsWordlist,
                                            onFormatterInputChange = toolsVm::updateFormatterInput,
                                            onFormatterSeparatorChange = toolsVm::updateFormatterSeparator,
                                            onRunFormatter = toolsVm::runComboFormatter,
                                            onSaveFormattedAsWordlist = toolsVm::saveFormattedAsWordlist,
                                            onRegexInputsChange = toolsVm::updateRegexInputs,
                                            onHashInputChange = toolsVm::updateHashInput,
                                            onNativeFieldsChange = toolsVm::updateNativeDiagnosticFields,
                                            onRunNativeScannerTest = toolsVm::runNativeScannerTest,
                                            onProbeUrlChange = toolsVm::updateProbeUrl,
                                            onRunNetworkProbe = toolsVm::runNetworkProbe,
                                            onCopyText = toolsVm::copyText,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        MainTab.SETTINGS -> SettingsScreen(
                                            settings = settingsState,
                                            onSelectSection = settingsVm::selectSection,
                                            onToggleKeepAwake = settingsVm::toggleKeepAwake,
                                            onToggleAutoSave = settingsVm::toggleAutoSave,
                                            onToggleIgnoreEmpty = settingsVm::toggleIgnoreEmpty,
                                            onToggleTimestamps = settingsVm::toggleTimestamps,
                                            onTimeoutDraft = settingsVm::updateTimeoutDraft,
                                            onTimeoutCommit = { settingsVm.commitTimeout() },
                                            onMaxRetriesDraft = settingsVm::updateMaxRetriesDraft,
                                            onMaxRetriesCommit = { settingsVm.commitMaxRetries() },
                                            onProxyJudgeUrlChange = settingsVm::updateProxyJudgeUrl,
                                            onToggleSoundOnHit = settingsVm::toggleSoundOnHit,
                                            onToggleSoundOnFinish = settingsVm::toggleSoundOnFinish,
                                            onTestHitSound = settingsVm::testHitSound,
                                            onTestFinishSound = settingsVm::testFinishSound,
                                            onSelectThemeMode = settingsVm::selectThemeMode,
                                            onToggleDynamicColor = settingsVm::toggleDynamicColor,
                                            onSelectAccentColor = settingsVm::selectAccentColor,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        MainTab.ABOUT -> AboutScreen(
                                            shellState = shellState,
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
                activeDialog = activeDialog,
                currentStartLine = runnerState.startLine,
                maxStartLine = (runnerState.selectedWordlist?.lineCount ?: 1000).coerceAtLeast(1),
                currentBotsCount = runnerState.botsCount,
                configs = configsState.configs,
                wordlists = wordlistsState.wordlists,
                onDismiss = shellVm::dismissDialog,
                onSelectConfig = runnerVm::selectConfig,
                onSelectWordlist = runnerVm::selectWordlist,
                onUpdateStartLine = { runnerVm.commitStartLine(it) },
                onUpdateBotsCount = { runnerVm.commitBotsCount(it) },
                onOpenNewConfigDialog = {
                    shellVm.dismissDialog()
                    shellVm.selectTab(MainTab.CONFIGS)
                    configsVm.openEditor(null)
                },
                onOpenAddWordlistDialog = { shellVm.openDialog(ActiveDialog.AddWordlist) },
                onCreateConfig = configsVm::addConfig,
                onImportConfigPayload = configsVm::importConfigPayload,
                onImportConfigFromUrl = configsVm::importConfigFromUrl,
                onCreateWordlist = wordlistsVm::addWordlist,
                onImportProxies = proxiesVm::addProxies,
                onImportProxiesFromUrl = proxiesVm::importProxiesFromUrl,
                onShowMessage = shellVm::showMessage
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 960, heightDp = 640)
@Composable
private fun OpenBulletWidePreview() {
    OpenBulletTheme {
        RunnerScreen(
            state = RunnerUiState(),
            onStartLineDraft = {},
            onStartLineCommit = {},
            onBotsDraft = {},
            onBotsCommit = {},
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
