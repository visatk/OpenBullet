package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.ProxyMode
import com.example.model.ResultRow
import com.example.model.ResultTab
import com.example.model.RunnerStats
import com.example.model.RunnerUiState
import com.example.model.WorkerRow
import com.example.ui.components.ObBorderedButton
import com.example.ui.components.ObMonoValueBox
import com.example.ui.components.ObPanel
import com.example.ui.components.ObProgressBar
import com.example.ui.components.ObRadioOption
import com.example.ui.components.ObSectionLabel
import com.example.ui.components.ObSegmentedControl
import com.example.ui.components.ObStatusPill
import com.example.ui.components.ObTrackSlider
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObTheme
import com.example.ui.theme.outcomeTintColor
import com.example.ui.theme.tintColor
import com.example.util.FileExportHelper
import java.util.Locale

private enum class CompactFeedTab(val label: String) {
    WORKERS("Workers"),
    RESULTS("Results")
}

@Composable
fun RunnerScreen(
    state: RunnerUiState,
    onStartLineDraft: (Int) -> Unit,
    onStartLineCommit: (Int?) -> Unit,
    onBotsDraft: (Int) -> Unit,
    onBotsCommit: (Int?) -> Unit,
    onEditStartClick: () -> Unit,
    onEditBotsClick: () -> Unit,
    onProxyModeSelect: (ProxyMode) -> Unit,
    onStartStopClick: () -> Unit,
    onPauseResumeClick: () -> Unit = {},
    onResultTabSelect: (ResultTab) -> Unit,
    onInspectResultRow: (ResultRow) -> Unit = {},
    onOpenConfigDialog: () -> Unit,
    onOpenWordlistDialog: () -> Unit,
    onLoadStarterSample: () -> Unit = {},
    onClearConsole: () -> Unit,
    onCopyConsole: (String) -> Unit = {},
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        val isWideLayout = maxWidth >= 660.dp
        if (isWideLayout) {
            WideRunnerLayout(
                state = state,
                onStartLineDraft = onStartLineDraft,
                onStartLineCommit = onStartLineCommit,
                onBotsDraft = onBotsDraft,
                onBotsCommit = onBotsCommit,
                onEditStartClick = onEditStartClick,
                onEditBotsClick = onEditBotsClick,
                onProxyModeSelect = onProxyModeSelect,
                onStartStopClick = onStartStopClick,
                onPauseResumeClick = onPauseResumeClick,
                onResultTabSelect = onResultTabSelect,
                onInspectResultRow = onInspectResultRow,
                onOpenConfigDialog = onOpenConfigDialog,
                onOpenWordlistDialog = onOpenWordlistDialog,
                onLoadStarterSample = onLoadStarterSample,
                onClearConsole = onClearConsole,
                onCopyConsole = onCopyConsole,
                onBackClick = onBackClick
            )
        } else {
            CompactRunnerLayout(
                state = state,
                onStartLineDraft = onStartLineDraft,
                onStartLineCommit = onStartLineCommit,
                onBotsDraft = onBotsDraft,
                onBotsCommit = onBotsCommit,
                onEditStartClick = onEditStartClick,
                onEditBotsClick = onEditBotsClick,
                onProxyModeSelect = onProxyModeSelect,
                onStartStopClick = onStartStopClick,
                onPauseResumeClick = onPauseResumeClick,
                onResultTabSelect = onResultTabSelect,
                onInspectResultRow = onInspectResultRow,
                onOpenConfigDialog = onOpenConfigDialog,
                onOpenWordlistDialog = onOpenWordlistDialog,
                onLoadStarterSample = onLoadStarterSample,
                onClearConsole = onClearConsole,
                onCopyConsole = onCopyConsole
            )
        }
    }
}

@Composable
private fun WideRunnerLayout(
    state: RunnerUiState,
    onStartLineDraft: (Int) -> Unit,
    onStartLineCommit: (Int?) -> Unit,
    onBotsDraft: (Int) -> Unit,
    onBotsCommit: (Int?) -> Unit,
    onEditStartClick: () -> Unit,
    onEditBotsClick: () -> Unit,
    onProxyModeSelect: (ProxyMode) -> Unit,
    onStartStopClick: () -> Unit,
    onPauseResumeClick: () -> Unit,
    onResultTabSelect: (ResultTab) -> Unit,
    onInspectResultRow: (ResultRow) -> Unit,
    onOpenConfigDialog: () -> Unit,
    onOpenWordlistDialog: () -> Unit,
    onLoadStarterSample: () -> Unit,
    onClearConsole: () -> Unit,
    onCopyConsole: (String) -> Unit,
    onBackClick: () -> Unit
) {
    val progressRatio = if (state.progressTotal > 0) {
        state.progressCurrent.toFloat() / state.progressTotal.toFloat()
    } else 0f
    val progressPercent = (progressRatio * 100).toInt()
    val filteredResults = remember(state.resultRows, state.selectedResultTab) {
        state.resultRows.filter { it.category == state.selectedResultTab }
    }
    val maxStartLine = (state.selectedWordlist?.lineCount ?: 1000).coerceAtLeast(1)

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (state.configs.isEmpty() && state.wordlists.isEmpty()) {
            QuickStartSampleBanner(onLoadStarterSample = onLoadStarterSample)
        }

        ObPanel(
            backgroundColor = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ObSectionLabel(text = stringResource(R.string.label_start_colon))
                        ObMonoValueBox(
                            text = state.startLine.toString(),
                            onClick = onEditStartClick,
                            modifier = Modifier
                                .width(56.dp)
                                .testTag("start_value_box")
                        )
                        ObTrackSlider(
                            value = state.startLine.toFloat(),
                            valueRange = 1f..maxStartLine.toFloat(),
                            onValueChange = { onStartLineDraft(it.toInt()) },
                            onValueChangeFinished = { onStartLineCommit(null) },
                            showTicks = false,
                            enabled = maxStartLine > 1,
                            modifier = Modifier
                                .weight(1.35f)
                                .testTag("start_slider")
                        )
                        ObSectionLabel(text = stringResource(R.string.label_bots_colon))
                        ObMonoValueBox(
                            text = state.botsCount.toString(),
                            onClick = onEditBotsClick,
                            modifier = Modifier
                                .width(48.dp)
                                .testTag("bots_value_box")
                        )
                        ObTrackSlider(
                            value = state.botsCount.toFloat(),
                            valueRange = 1f..200f,
                            onValueChange = { onBotsDraft(it.toInt()) },
                            onValueChangeFinished = { onBotsCommit(null) },
                            showTicks = true,
                            modifier = Modifier
                                .weight(0.75f)
                                .testTag("bots_slider")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Prog: ${state.progressCurrent} / ${state.progressTotal} ($progressPercent%)",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontFeatureSettings = "tnum"
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            modifier = Modifier.width(176.dp)
                        )
                        ObProgressBar(
                            progress = progressRatio,
                            modifier = Modifier.weight(1.1f)
                        )
                        ObSectionLabel(text = stringResource(R.string.label_prox_colon))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ProxyMode.entries.forEach { mode ->
                                ObRadioOption(
                                    label = mode.label,
                                    selected = state.proxyMode == mode,
                                    onClick = { onProxyModeSelect(mode) },
                                    modifier = Modifier.testTag("proxy_mode_${mode.name.lowercase()}")
                                )
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (state.isRunning || state.isPaused) {
                        PauseResumeActionButton(
                            isPaused = state.isPaused,
                            onClick = onPauseResumeClick,
                            modifier = Modifier
                                .width(106.dp)
                                .height(84.dp)
                        )
                    }
                    StartStopActionButton(
                        isRunning = state.isRunning || state.isPaused,
                        onClick = onStartStopClick,
                        modifier = Modifier
                            .width(114.dp)
                            .height(84.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WorkerTablePanel(
                workers = state.workerRows,
                modifier = Modifier
                    .weight(0.58f)
                    .fillMaxHeight()
            )
            Column(
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ResultTablePanel(
                    results = filteredResults,
                    selectedTab = state.selectedResultTab,
                    onInspectRow = onInspectResultRow,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
                ResultFilterAndTimerRow(
                    resultRows = state.resultRows,
                    selectedTab = state.selectedResultTab,
                    elapsedSeconds = state.elapsedSeconds,
                    etaSeconds = state.etaSeconds,
                    onSelectTab = onResultTabSelect
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(0.58f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SelectorCardsRow(
                    selectedConfigName = state.selectedConfig?.name,
                    selectedWordlistName = state.selectedWordlist?.name,
                    selectedWordlistCount = state.selectedWordlist?.lineCount,
                    onOpenConfigDialog = onOpenConfigDialog,
                    onOpenWordlistDialog = onOpenWordlistDialog
                )
                ConsoleLogBox(
                    logs = state.consoleLogs,
                    onClear = onClearConsole,
                    onCopy = onCopyConsole,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(104.dp)
                )
                BackButton(onClick = onBackClick)
            }

            RunnerStatsPanel(
                stats = state.stats,
                modifier = Modifier.weight(0.42f)
            )
        }
    }
}

@Composable
private fun CompactRunnerLayout(
    state: RunnerUiState,
    onStartLineDraft: (Int) -> Unit,
    onStartLineCommit: (Int?) -> Unit,
    onBotsDraft: (Int) -> Unit,
    onBotsCommit: (Int?) -> Unit,
    onEditStartClick: () -> Unit,
    onEditBotsClick: () -> Unit,
    onProxyModeSelect: (ProxyMode) -> Unit,
    onStartStopClick: () -> Unit,
    onPauseResumeClick: () -> Unit,
    onResultTabSelect: (ResultTab) -> Unit,
    onInspectResultRow: (ResultRow) -> Unit,
    onOpenConfigDialog: () -> Unit,
    onOpenWordlistDialog: () -> Unit,
    onLoadStarterSample: () -> Unit,
    onClearConsole: () -> Unit,
    onCopyConsole: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    var activeFeedTab by remember { mutableStateOf(CompactFeedTab.WORKERS) }

    val progressRatio = if (state.progressTotal > 0) {
        state.progressCurrent.toFloat() / state.progressTotal.toFloat()
    } else 0f
    val progressPercent = (progressRatio * 100).toInt()
    val filteredResults = remember(state.resultRows, state.selectedResultTab) {
        state.resultRows.filter { it.category == state.selectedResultTab }
    }
    val maxStartLine = (state.selectedWordlist?.lineCount ?: 1000).coerceAtLeast(1)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (state.configs.isEmpty() && state.wordlists.isEmpty()) {
            QuickStartSampleBanner(onLoadStarterSample = onLoadStarterSample)
        }

        // 1. Hero Execution & Timer Card
        ObPanel(
            backgroundColor = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val statusText = when {
                            state.isRunning -> "ACTIVE • ${state.stats.cpm} CPM"
                            state.isPaused -> "PAUSED • LINE #${state.startLine}"
                            else -> "STANDBY"
                        }
                        val statusTint = when {
                            state.isRunning -> ObTheme.colors.hit
                            state.isPaused -> ObTheme.colors.custom
                            else -> MaterialTheme.colorScheme.primary
                        }
                        val statusBg = when {
                            state.isRunning -> ObTheme.colors.hitContainer
                            state.isPaused -> ObTheme.colors.customContainer
                            else -> MaterialTheme.colorScheme.primaryContainer
                        }
                        ObStatusPill(
                            text = statusText,
                            tintColor = statusTint,
                            containerColor = statusBg,
                            showDot = state.isRunning
                        )
                        Text(
                            text = buildString {
                                append(formatElapsedTimer(state.elapsedSeconds))
                                if (state.isRunning && state.etaSeconds > 0L) {
                                    append(" • ETA ")
                                    append(formatShortEta(state.etaSeconds))
                                }
                            },
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontFeatureSettings = "tnum"
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (state.isRunning || state.isPaused) {
                            PauseResumeActionButton(
                                isPaused = state.isPaused,
                                onClick = onPauseResumeClick,
                                modifier = Modifier.height(48.dp)
                            )
                        }
                        StartStopActionButton(
                            isRunning = state.isRunning || state.isPaused,
                            onClick = onStartStopClick,
                            modifier = Modifier.height(48.dp)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Progress",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${state.progressCurrent} / ${state.progressTotal} ($progressPercent%)",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontFeatureSettings = "tnum"
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    ObProgressBar(
                        progress = progressRatio,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 2. Config & Wordlist Selector Cards
        SelectorCardsRow(
            selectedConfigName = state.selectedConfig?.name,
            selectedWordlistName = state.selectedWordlist?.name,
            selectedWordlistCount = state.selectedWordlist?.lineCount,
            onOpenConfigDialog = onOpenConfigDialog,
            onOpenWordlistDialog = onOpenWordlistDialog
        )

        // 3. Parameters & Proxy Mode Card
        ObPanel(
            backgroundColor = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ParameterStepperRow(
                    label = stringResource(R.string.label_start_colon),
                    value = state.startLine,
                    range = 1..maxStartLine,
                    showTicks = false,
                    onValueDraft = onStartLineDraft,
                    onValueCommit = { onStartLineCommit(it) },
                    onValueBoxClick = onEditStartClick,
                    sliderTestTag = "start_slider"
                )
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                ParameterStepperRow(
                    label = stringResource(R.string.label_bots_colon),
                    value = state.botsCount,
                    range = 1..200,
                    showTicks = true,
                    onValueDraft = onBotsDraft,
                    onValueCommit = { onBotsCommit(it) },
                    onValueBoxClick = onEditBotsClick,
                    sliderTestTag = "bots_slider"
                )
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ObSectionLabel(text = stringResource(R.string.label_prox_colon))
                    ObSegmentedControl(
                        options = ProxyMode.entries,
                        selectedOption = state.proxyMode,
                        labelProvider = { it.label },
                        onSelect = onProxyModeSelect,
                        testTagPrefix = "proxy_mode"
                    )
                }
            }
        }

        // 4. Live Telemetry Stats Card
        RunnerStatsPanel(
            stats = state.stats,
            modifier = Modifier.fillMaxWidth()
        )

        // 5. Segmented Live Monitor (Workers / Results)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ObSegmentedControl(
                options = CompactFeedTab.entries,
                selectedOption = activeFeedTab,
                labelProvider = { tab ->
                    val count = if (tab == CompactFeedTab.WORKERS) {
                        state.workerRows.size
                    } else {
                        filteredResults.size
                    }
                    "${tab.label} ($count)"
                },
                onSelect = { activeFeedTab = it },
                modifier = Modifier.fillMaxWidth(),
                testTagPrefix = "compact_feed"
            )

            if (activeFeedTab == CompactFeedTab.WORKERS) {
                WorkerTablePanel(
                    workers = state.workerRows,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(192.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ResultFilterTabsOnly(
                        resultRows = state.resultRows,
                        selectedTab = state.selectedResultTab,
                        onSelectTab = onResultTabSelect,
                        modifier = Modifier.fillMaxWidth()
                    )
                    ResultTablePanel(
                        results = filteredResults,
                        selectedTab = state.selectedResultTab,
                        onInspectRow = onInspectResultRow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )
                }
            }
        }

        // 6. Console Output Box
        ConsoleLogBox(
            logs = state.consoleLogs,
            onClear = onClearConsole,
            onCopy = onCopyConsole,
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
        )
    }
}

@Composable
private fun QuickStartSampleBanner(onLoadStarterSample: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.RocketLaunch,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "New Workstation Ready",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Load an offline validator config + 5 demo combos in one tap.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onLoadStarterSample,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.testTag("runner_quickstart_button")
            ) {
                Text("Load Sample", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ParameterStepperRow(
    label: String,
    value: Int,
    range: IntRange,
    showTicks: Boolean,
    onValueDraft: (Int) -> Unit,
    onValueCommit: (Int?) -> Unit,
    onValueBoxClick: () -> Unit,
    sliderTestTag: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ObSectionLabel(text = label)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = { onValueCommit((value - 1).coerceIn(range)) },
                    enabled = value > range.first,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        Icons.Filled.Remove,
                        contentDescription = "Decrease $label",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ObMonoValueBox(
                    text = value.toString(),
                    onClick = onValueBoxClick,
                    modifier = Modifier.width(64.dp)
                )
                IconButton(
                    onClick = { onValueCommit((value + 1).coerceIn(range)) },
                    enabled = value < range.last,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Increase $label",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        ObTrackSlider(
            value = value.toFloat(),
            valueRange = range.first.toFloat()..range.last.toFloat(),
            onValueChange = { onValueDraft(it.toInt()) },
            onValueChangeFinished = { onValueCommit(null) },
            showTicks = showTicks,
            enabled = range.last > range.first,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(sliderTestTag)
        )
    }
}

@Composable
private fun PauseResumeActionButton(
    isPaused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val accent = if (isPaused) ObTheme.colors.hit else ObTheme.colors.custom
    val bg = if (isPaused) ObTheme.colors.hitContainer else ObTheme.colors.customContainer
    FilledTonalButton(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = bg,
            contentColor = accent
        ),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.65f)),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = modifier.testTag("runner_pause_resume_button")
    ) {
        Icon(
            imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = if (isPaused) "RESUME" else "PAUSE",
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )
        )
    }
}

@Composable
private fun StartStopActionButton(
    isRunning: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val bgColor by animateColorAsState(
        targetValue = if (isRunning) ObTheme.colors.badContainer else MaterialTheme.colorScheme.primary,
        animationSpec = tween(200),
        label = "start_bg"
    )
    val contentColor = if (isRunning) ObTheme.colors.bad else MaterialTheme.colorScheme.onPrimary

    Button(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = bgColor,
            contentColor = contentColor
        ),
        border = if (isRunning) BorderStroke(1.dp, ObTheme.colors.bad) else null,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        modifier = modifier.testTag("runner_start_button")
    ) {
        Icon(
            imageVector = if (isRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (isRunning) stringResource(R.string.action_stop) else stringResource(R.string.action_start),
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
        )
    }
}

@Composable
private fun WorkerTablePanel(
    workers: List<WorkerRow>,
    modifier: Modifier = Modifier
) {
    ObPanel(modifier = modifier.testTag("worker_table_panel")) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TableHeaderCell(stringResource(R.string.col_id), Modifier.weight(0.14f))
                TableHeaderCell(stringResource(R.string.col_data), Modifier.weight(0.36f))
                TableHeaderCell(stringResource(R.string.col_proxy), Modifier.weight(0.28f))
                TableHeaderCell(stringResource(R.string.col_status), Modifier.weight(0.22f))
            }
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

            if (workers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ViewList,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(26.dp)
                        )
                        Text(
                            text = "Worker threads idle",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Select a CFG & List, then tap START.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(workers, key = { it.id }) { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "#${row.id}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(0.14f)
                            )
                            Text(
                                text = row.data,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(0.36f)
                            )
                            Text(
                                text = row.proxy,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(0.28f)
                            )
                            Text(
                                text = row.status,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = outcomeTintColor(row.outcomeType),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(0.22f)
                            )
                        }
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultTablePanel(
    results: List<ResultRow>,
    selectedTab: ResultTab,
    onInspectRow: (ResultRow) -> Unit,
    modifier: Modifier = Modifier
) {
    ObPanel(modifier = modifier.testTag("result_table_panel")) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TableHeaderCell(stringResource(R.string.col_time), Modifier.weight(0.18f))
                TableHeaderCell(stringResource(R.string.col_data), Modifier.weight(0.28f))
                TableHeaderCell(stringResource(R.string.col_proxy), Modifier.weight(0.22f))
                TableHeaderCell(stringResource(R.string.col_captured_data), Modifier.weight(0.32f))
            }
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

            if (results.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No ${selectedTab.label.lowercase()} captured yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(results, key = { it.id }) { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onInspectRow(row) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = row.time,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.weight(0.18f)
                            )
                            Text(
                                text = row.data,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(0.28f)
                            )
                            Text(
                                text = row.proxy,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(0.22f)
                            )
                            Text(
                                text = row.capturedData,
                                style = MaterialTheme.typography.bodySmall,
                                color = row.category.tintColor(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(0.32f)
                            )
                        }
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TableHeaderCell(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(end = 4.dp)
    )
}

@Composable
private fun ResultFilterAndTimerRow(
    resultRows: List<ResultRow>,
    selectedTab: ResultTab,
    elapsedSeconds: Long,
    etaSeconds: Long,
    onSelectTab: (ResultTab) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        ResultFilterTabsOnly(
            resultRows = resultRows,
            selectedTab = selectedTab,
            onSelectTab = onSelectTab
        )
        Text(
            text = buildString {
                append(formatElapsedTimer(elapsedSeconds))
                if (etaSeconds > 0L) {
                    append(" • ETA ")
                    append(formatShortEta(etaSeconds))
                }
            },
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontFeatureSettings = "tnum"
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

@Composable
private fun ResultFilterTabsOnly(
    resultRows: List<ResultRow>,
    selectedTab: ResultTab,
    onSelectTab: (ResultTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ResultTab.entries.forEach { tab ->
            val accentColor = tab.tintColor()
            val count = resultRows.count { it.category == tab }
            ObBorderedButton(
                onClick = { onSelectTab(tab) },
                isActive = selectedTab == tab,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("result_tab_${tab.name.lowercase()}")
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(accentColor, CircleShape)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (count > 0) "${tab.label} ($count)" else tab.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selectedTab == tab) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SelectorCardsRow(
    selectedConfigName: String?,
    selectedWordlistName: String?,
    selectedWordlistCount: Int?,
    onOpenConfigDialog: () -> Unit,
    onOpenWordlistDialog: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SelectorCard(
            actionLabel = stringResource(R.string.btn_select_cfg),
            valueLabel = selectedConfigName ?: stringResource(R.string.value_none),
            isLoaded = selectedConfigName != null,
            icon = Icons.Outlined.Code,
            onClick = onOpenConfigDialog,
            modifier = Modifier
                .weight(1f)
                .testTag("select_cfg_button")
        )
        SelectorCard(
            actionLabel = stringResource(R.string.btn_select_list),
            valueLabel = if (selectedWordlistName != null) {
                "$selectedWordlistName (${selectedWordlistCount ?: 0})"
            } else {
                stringResource(R.string.value_none)
            },
            isLoaded = selectedWordlistName != null,
            icon = Icons.AutoMirrored.Outlined.ListAlt,
            onClick = onOpenWordlistDialog,
            modifier = Modifier
                .weight(1f)
                .testTag("select_list_button")
        )
    }
}

@Composable
private fun SelectorCard(
    actionLabel: String,
    valueLabel: String,
    isLoaded: Boolean,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        shape = shape,
        color = if (isLoaded) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (isLoaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier.heightIn(min = 56.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isLoaded) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isLoaded) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = if (isLoaded) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ConsoleLogBox(
    logs: List<String>,
    onClear: () -> Unit,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.lastIndex)
        }
    }

    ObPanel(
        modifier = modifier.testTag("runner_console_box"),
        backgroundColor = ObTheme.colors.terminalBackground
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(start = 10.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Terminal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Console Output",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    IconButton(
                        onClick = {
                            val text = logs.joinToString("\n")
                            FileExportHelper.copyToClipboard(context, "OpenBullet Console", text)
                            onCopy(text)
                        },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "Copy console output",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteSweep,
                            contentDescription = "Reset console output",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(logs) { line ->
                    val lineColor = when {
                        line.contains("error", ignoreCase = true) ||
                            line.contains("failed", ignoreCase = true) ||
                            line.contains("cannot start", ignoreCase = true) -> ObTheme.colors.bad
                        line.contains("finished", ignoreCase = true) ||
                            line.contains("loaded", ignoreCase = true) -> ObTheme.colors.hit
                        line.contains("paused", ignoreCase = true) -> ObTheme.colors.custom
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 12.sp
                        ),
                        color = lineColor
                    )
                }
            }
        }
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    ObBorderedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        modifier = Modifier.testTag("runner_back_button")
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.cd_back_arrow),
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.btn_back),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun RunnerStatsPanel(
    stats: RunnerStats,
    modifier: Modifier = Modifier
) {
    ObPanel(
        backgroundColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = stringResource(R.string.stats_data_header),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                StatLine("Total:", stats.dataTotal.toString(), MaterialTheme.colorScheme.onSurface)
                StatLine("Hits:", stats.dataHits.toString(), ObTheme.colors.hit, showDot = true)
                StatLine("Custom:", stats.dataCustom.toString(), ObTheme.colors.custom, showDot = true)
                StatLine("Bad:", stats.dataBad.toString(), ObTheme.colors.bad, showDot = true)
                StatLine("Retries:", stats.dataRetries.toString(), ObTheme.colors.retry, showDot = true)
                StatLine("To Check:", stats.dataToCheck.toString(), ObTheme.colors.toCheck, showDot = true)
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = stringResource(R.string.stats_proxies_header),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                StatLine("Total:", stats.proxiesTotal.toString(), MaterialTheme.colorScheme.onSurface)
                StatLine("Alive:", stats.proxiesAlive.toString(), ObTheme.colors.hit, showDot = true)
                StatLine("Banned:", stats.proxiesBanned.toString(), ObTheme.colors.banned, showDot = true)
                StatLine("Bad:", stats.proxiesBad.toString(), ObTheme.colors.bad, showDot = true)
                StatLine("CPM:", stats.cpm.toString(), MaterialTheme.colorScheme.primary, bold = true)
                StatLine("Credit:", stats.credit, MaterialTheme.colorScheme.onSurface, bold = true)
            }
        }
    }
}

@Composable
private fun StatLine(
    label: String,
    value: String,
    color: Color,
    bold: Boolean = false,
    showDot: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (showDot) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(color, CircleShape)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium
                ),
                color = color
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
                fontFamily = JetBrainsMonoFontFamily,
                fontFeatureSettings = "tnum"
            ),
            color = color
        )
    }
}

private fun formatElapsedTimer(totalSeconds: Long): String {
    val days = totalSeconds / 86400
    val hours = (totalSeconds % 86400) / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d days %02d : %02d : %02d", days, hours, minutes, seconds)
}

private fun formatShortEta(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
