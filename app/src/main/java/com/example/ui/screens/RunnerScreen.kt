package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.OpenBulletUiState
import com.example.model.ProxyMode
import com.example.model.ResultRow
import com.example.model.ResultTab
import com.example.model.RunnerStats
import com.example.model.WorkerRow
import com.example.ui.components.ObBorderedButton
import com.example.ui.components.ObMonoValueBox
import com.example.ui.components.ObPanel
import com.example.ui.components.ObProgressBar
import com.example.ui.components.ObRadioOption
import com.example.ui.components.ObScrollbarIndicator
import com.example.ui.components.ObSectionLabel
import com.example.ui.components.ObTrackSlider
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObColors
import java.util.Locale

private enum class CompactFeedTab(val label: String) {
    WORKERS("Workers"),
    RESULTS("Results")
}

@Composable
fun RunnerScreen(
    state: OpenBulletUiState,
    onStartLineChange: (Int) -> Unit,
    onBotsChange: (Int) -> Unit,
    onEditStartClick: () -> Unit,
    onEditBotsClick: () -> Unit,
    onProxyModeSelect: (ProxyMode) -> Unit,
    onStartStopClick: () -> Unit,
    onResultTabSelect: (ResultTab) -> Unit,
    onOpenConfigDialog: () -> Unit,
    onOpenWordlistDialog: () -> Unit,
    onClearConsole: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(ObColors.WorkspaceBackground)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        val isWideLayout = maxWidth >= 660.dp
        if (isWideLayout) {
            WideRunnerLayout(
                state = state,
                onStartLineChange = onStartLineChange,
                onBotsChange = onBotsChange,
                onEditStartClick = onEditStartClick,
                onEditBotsClick = onEditBotsClick,
                onProxyModeSelect = onProxyModeSelect,
                onStartStopClick = onStartStopClick,
                onResultTabSelect = onResultTabSelect,
                onOpenConfigDialog = onOpenConfigDialog,
                onOpenWordlistDialog = onOpenWordlistDialog,
                onClearConsole = onClearConsole,
                onBackClick = onBackClick
            )
        } else {
            CompactRunnerLayout(
                state = state,
                onStartLineChange = onStartLineChange,
                onBotsChange = onBotsChange,
                onEditStartClick = onEditStartClick,
                onEditBotsClick = onEditBotsClick,
                onProxyModeSelect = onProxyModeSelect,
                onStartStopClick = onStartStopClick,
                onResultTabSelect = onResultTabSelect,
                onOpenConfigDialog = onOpenConfigDialog,
                onOpenWordlistDialog = onOpenWordlistDialog,
                onClearConsole = onClearConsole
            )
        }
    }
}

@Composable
private fun WideRunnerLayout(
    state: OpenBulletUiState,
    onStartLineChange: (Int) -> Unit,
    onBotsChange: (Int) -> Unit,
    onEditStartClick: () -> Unit,
    onEditBotsClick: () -> Unit,
    onProxyModeSelect: (ProxyMode) -> Unit,
    onStartStopClick: () -> Unit,
    onResultTabSelect: (ResultTab) -> Unit,
    onOpenConfigDialog: () -> Unit,
    onOpenWordlistDialog: () -> Unit,
    onClearConsole: () -> Unit,
    onBackClick: () -> Unit
) {
    val progressRatio = if (state.progressTotal > 0) {
        state.progressCurrent.toFloat() / state.progressTotal.toFloat()
    } else 0f
    val progressPercent = (progressRatio * 100).toInt()
    val filteredResults = state.resultRows.filter { it.category == state.selectedResultTab }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Elevated Control Strip Card
        ObPanel(
            backgroundColor = ObColors.SurfacePanel,
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
                                .width(54.dp)
                                .testTag("start_value_box")
                        )
                        ObTrackSlider(
                            value = state.startLine.toFloat(),
                            valueRange = 1f..1000f,
                            onValueChange = { onStartLineChange(it.toInt()) },
                            showTicks = false,
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
                            onValueChange = { onBotsChange(it.toInt()) },
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
                            color = ObColors.TextPrimary,
                            maxLines = 1,
                            modifier = Modifier.width(172.dp)
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

                StartStopActionButton(
                    isRunning = state.isRunning,
                    onClick = onStartStopClick,
                    modifier = Modifier
                        .width(120.dp)
                        .height(84.dp)
                )
            }
        }

        // Main Supporting-Pane Split (Left Worker Table + Right Results Table)
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
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
                ResultFilterAndTimerRow(
                    selectedTab = state.selectedResultTab,
                    elapsedSeconds = state.elapsedSeconds,
                    onSelectTab = onResultTabSelect
                )
            }
        }

        // Bottom Strip (Selectors + Console + Back | Telemetry Stats)
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
    state: OpenBulletUiState,
    onStartLineChange: (Int) -> Unit,
    onBotsChange: (Int) -> Unit,
    onEditStartClick: () -> Unit,
    onEditBotsClick: () -> Unit,
    onProxyModeSelect: (ProxyMode) -> Unit,
    onStartStopClick: () -> Unit,
    onResultTabSelect: (ResultTab) -> Unit,
    onOpenConfigDialog: () -> Unit,
    onOpenWordlistDialog: () -> Unit,
    onClearConsole: () -> Unit
) {
    val scrollState = rememberScrollState()
    var activeFeedTab by remember { mutableStateOf(CompactFeedTab.WORKERS) }

    val progressRatio = if (state.progressTotal > 0) {
        state.progressCurrent.toFloat() / state.progressTotal.toFloat()
    } else 0f
    val progressPercent = (progressRatio * 100).toInt()
    val filteredResults = state.resultRows.filter { it.category == state.selectedResultTab }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Hero Execution & Timer Card
        ObPanel(
            backgroundColor = ObColors.SurfacePanel,
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
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = if (state.isRunning) "RUNNER ACTIVE" else "RUNNER STANDBY",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (state.isRunning) ObColors.StatusHitGreen else MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = formatElapsedTimer(state.elapsedSeconds),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontFeatureSettings = "tnum"
                            ),
                            color = ObColors.TextPrimary
                        )
                    }

                    StartStopActionButton(
                        isRunning = state.isRunning,
                        onClick = onStartStopClick,
                        modifier = Modifier
                            .width(124.dp)
                            .height(50.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Progress",
                            style = MaterialTheme.typography.labelMedium,
                            color = ObColors.TextSecondary
                        )
                        Text(
                            text = "${state.progressCurrent} / ${state.progressTotal} ($progressPercent%)",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontFeatureSettings = "tnum"
                            ),
                            color = ObColors.TextPrimary
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

        // 3. Parameters & Proxy Mode Card (Touch-friendly steppers + tap-to-edit + sliders)
        ObPanel(
            backgroundColor = ObColors.SurfacePanel,
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
                    range = 1..1000,
                    showTicks = false,
                    onValueChange = onStartLineChange,
                    onValueBoxClick = onEditStartClick,
                    sliderTestTag = "start_slider"
                )
                HorizontalDivider(thickness = 1.dp, color = ObColors.DividerSubtle)
                ParameterStepperRow(
                    label = stringResource(R.string.label_bots_colon),
                    value = state.botsCount,
                    range = 1..200,
                    showTicks = true,
                    onValueChange = onBotsChange,
                    onValueBoxClick = onEditBotsClick,
                    sliderTestTag = "bots_slider"
                )
                HorizontalDivider(thickness = 1.dp, color = ObColors.DividerSubtle)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ObSectionLabel(text = stringResource(R.string.label_prox_colon))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
        }

        // 4. Live Telemetry Stats Card (DATA & PROXIES)
        RunnerStatsPanel(
            stats = state.stats,
            modifier = Modifier.fillMaxWidth()
        )

        // 5. Segmented Live Monitor (Workers / Results)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompactFeedTab.entries.forEach { tab ->
                    val count = if (tab == CompactFeedTab.WORKERS) state.workerRows.size else filteredResults.size
                    ObBorderedButton(
                        onClick = { activeFeedTab = tab },
                        isActive = activeFeedTab == tab,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("compact_feed_${tab.name.lowercase()}")
                    ) {
                        Text(
                            text = "${tab.label} ($count)",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (activeFeedTab == tab) MaterialTheme.colorScheme.primary else ObColors.TextPrimary
                        )
                    }
                }
            }

            if (activeFeedTab == CompactFeedTab.WORKERS) {
                WorkerTablePanel(
                    workers = state.workerRows,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(188.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ResultFilterTabsOnly(
                        selectedTab = state.selectedResultTab,
                        onSelectTab = onResultTabSelect,
                        modifier = Modifier.fillMaxWidth()
                    )
                    ResultTablePanel(
                        results = filteredResults,
                        selectedTab = state.selectedResultTab,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(176.dp)
                    )
                }
            }
        }

        // 6. Console Output Box
        ConsoleLogBox(
            logs = state.consoleLogs,
            onClear = onClearConsole,
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp)
        )
    }
}

@Composable
private fun ParameterStepperRow(
    label: String,
    value: Int,
    range: IntRange,
    showTicks: Boolean,
    onValueChange: (Int) -> Unit,
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
                    onClick = { onValueChange((value - 1).coerceIn(range)) },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "Decrease $label", tint = ObColors.TextSecondary)
                }
                ObMonoValueBox(
                    text = value.toString(),
                    onClick = onValueBoxClick,
                    modifier = Modifier.width(64.dp)
                )
                IconButton(
                    onClick = { onValueChange((value + 1).coerceIn(range)) },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Increase $label", tint = ObColors.TextSecondary)
                }
            }
        }
        ObTrackSlider(
            value = value.toFloat(),
            valueRange = range.first.toFloat()..range.last.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            showTicks = showTicks,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(sliderTestTag)
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
        targetValue = if (isRunning) ObColors.StatusBadBg else MaterialTheme.colorScheme.primaryContainer,
        animationSpec = tween(200),
        label = "start_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isRunning) ObColors.StatusBadRed else MaterialTheme.colorScheme.primary,
        animationSpec = tween(200),
        label = "start_border"
    )
    val contentColor = if (isRunning) ObColors.StatusBadRed else ObColors.TextPrimary

    ObBorderedButton(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        containerColor = bgColor,
        borderColor = borderColor,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = modifier.testTag("runner_start_button")
    ) {
        Icon(
            imageVector = if (isRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (isRunning) stringResource(R.string.action_stop) else stringResource(R.string.action_start),
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            ),
            color = contentColor
        )
    }
}

@Composable
private fun WorkerTablePanel(
    workers: List<WorkerRow>,
    modifier: Modifier = Modifier
) {
    ObPanel(modifier = modifier.testTag("worker_table_panel")) {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ObColors.SurfaceTableHeader)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TableHeaderCell(stringResource(R.string.col_id), Modifier.weight(0.14f))
                    TableHeaderCell(stringResource(R.string.col_data), Modifier.weight(0.36f))
                    TableHeaderCell(stringResource(R.string.col_proxy), Modifier.weight(0.28f))
                    TableHeaderCell(stringResource(R.string.col_status), Modifier.weight(0.22f))
                }
                HorizontalDivider(thickness = 1.dp, color = ObColors.DividerSubtle)

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
                                imageVector = Icons.Outlined.ViewList,
                                contentDescription = null,
                                tint = ObColors.TextMuted,
                                modifier = Modifier.size(26.dp)
                            )
                            Text(
                                text = "Worker threads idle",
                                style = MaterialTheme.typography.labelMedium,
                                color = ObColors.TextSecondary
                            )
                            Text(
                                text = "Select a CFG & List, then tap START.",
                                style = MaterialTheme.typography.bodySmall,
                                color = ObColors.TextMuted
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
                                    text = row.id.toString(),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                    color = ObColors.TextSecondary,
                                    modifier = Modifier.weight(0.14f)
                                )
                                Text(
                                    text = row.data,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                    color = ObColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(0.36f)
                                )
                                Text(
                                    text = row.proxy,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                    color = ObColors.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(0.28f)
                                )
                                Text(
                                    text = row.status,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = row.statusColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(0.22f)
                                )
                            }
                            HorizontalDivider(thickness = 0.5.dp, color = ObColors.DividerSubtle.copy(alpha = 0.6f))
                        }
                    }
                }
            }
            ObScrollbarIndicator()
        }
    }
}

@Composable
private fun ResultTablePanel(
    results: List<ResultRow>,
    selectedTab: ResultTab,
    modifier: Modifier = Modifier
) {
    ObPanel(modifier = modifier.testTag("result_table_panel")) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObColors.SurfaceTableHeader)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TableHeaderCell(stringResource(R.string.col_time), Modifier.weight(0.18f))
                TableHeaderCell(stringResource(R.string.col_data), Modifier.weight(0.28f))
                TableHeaderCell(stringResource(R.string.col_proxy), Modifier.weight(0.22f))
                TableHeaderCell(stringResource(R.string.col_captured_data), Modifier.weight(0.32f))
            }
            HorizontalDivider(thickness = 1.dp, color = ObColors.DividerSubtle)

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
                        color = ObColors.TextMuted
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(results) { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = row.time,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = ObColors.TextSecondary,
                                maxLines = 1,
                                modifier = Modifier.weight(0.18f)
                            )
                            Text(
                                text = row.data,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = ObColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(0.28f)
                            )
                            Text(
                                text = row.proxy,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = ObColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(0.22f)
                            )
                            Text(
                                text = row.capturedData,
                                style = MaterialTheme.typography.bodySmall,
                                color = when (row.category) {
                                    ResultTab.HITS -> ObColors.StatusHitGreen
                                    ResultTab.CUSTOM -> ObColors.StatusCustomOrange
                                    ResultTab.TO_CHECK -> ObColors.StatusToCheckAqua
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(0.32f)
                            )
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = ObColors.DividerSubtle.copy(alpha = 0.6f))
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
        color = ObColors.TextPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(end = 4.dp)
    )
}

@Composable
private fun ResultFilterAndTimerRow(
    selectedTab: ResultTab,
    elapsedSeconds: Long,
    onSelectTab: (ResultTab) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        ResultFilterTabsOnly(selectedTab = selectedTab, onSelectTab = onSelectTab)
        Text(
            text = formatElapsedTimer(elapsedSeconds),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontFeatureSettings = "tnum"
            ),
            color = ObColors.TextPrimary,
            maxLines = 1
        )
    }
}

@Composable
private fun ResultFilterTabsOnly(
    selectedTab: ResultTab,
    onSelectTab: (ResultTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ResultTab.entries.forEach { tab ->
            val accentColor = when (tab) {
                ResultTab.HITS -> ObColors.StatusHitGreen
                ResultTab.CUSTOM -> ObColors.StatusCustomOrange
                ResultTab.TO_CHECK -> ObColors.StatusToCheckAqua
            }
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
                    text = tab.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selectedTab == tab) ObColors.TextPrimary else ObColors.TextSecondary,
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
            icon = Icons.Outlined.ListAlt,
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
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick,
        shape = shape,
        color = if (isLoaded) ObColors.SurfaceSelected else ObColors.SurfacePanel,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isLoaded) MaterialTheme.colorScheme.primary else ObColors.BorderHairline
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
                tint = if (isLoaded) MaterialTheme.colorScheme.primary else ObColors.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = ObColors.TextSecondary,
                    maxLines = 1
                )
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isLoaded) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = if (isLoaded) ObColors.TextPrimary else ObColors.TextMuted,
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
    modifier: Modifier = Modifier
) {
    ObPanel(
        modifier = modifier.testTag("runner_console_box"),
        backgroundColor = ObColors.SurfaceRecessed
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObColors.SurfaceTableHeader)
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
                        color = ObColors.TextSecondary
                    )
                }
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteSweep,
                        contentDescription = "Reset console output",
                        tint = ObColors.TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            HorizontalDivider(thickness = 1.dp, color = ObColors.DividerSubtle)
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(logs) { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 12.sp
                            ),
                            color = ObColors.TextPrimary
                        )
                    }
                }
                ObScrollbarIndicator()
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
            tint = ObColors.TextPrimary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.btn_back),
            style = MaterialTheme.typography.labelLarge,
            color = ObColors.TextPrimary
        )
    }
}

@Composable
private fun RunnerStatsPanel(
    stats: RunnerStats,
    modifier: Modifier = Modifier
) {
    ObPanel(
        backgroundColor = ObColors.SurfacePanel,
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
                    color = ObColors.TextPrimary
                )
                HorizontalDivider(thickness = 1.dp, color = ObColors.DividerSubtle)
                StatLine("Total:", stats.dataTotal.toString(), ObColors.TextPrimary)
                StatLine("Hits:", stats.dataHits.toString(), ObColors.StatusHitGreen, showDot = true)
                StatLine("Custom:", stats.dataCustom.toString(), ObColors.StatusCustomOrange, showDot = true)
                StatLine("Bad:", stats.dataBad.toString(), ObColors.StatusBadRed, showDot = true)
                StatLine("Retries:", stats.dataRetries.toString(), ObColors.StatusRetryYellow, showDot = true)
                StatLine("To Check:", stats.dataToCheck.toString(), ObColors.StatusToCheckAqua, showDot = true)
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = stringResource(R.string.stats_proxies_header),
                    style = MaterialTheme.typography.titleSmall,
                    color = ObColors.TextPrimary
                )
                HorizontalDivider(thickness = 1.dp, color = ObColors.DividerSubtle)
                StatLine("Total:", stats.proxiesTotal.toString(), ObColors.TextPrimary)
                StatLine("Alive:", stats.proxiesAlive.toString(), ObColors.TextPrimary)
                StatLine("Banned:", stats.proxiesBanned.toString(), ObColors.TextPrimary)
                StatLine("Bad:", stats.proxiesBad.toString(), ObColors.TextPrimary)
                StatLine("CPM:", stats.cpm.toString(), MaterialTheme.colorScheme.primary, bold = true)
                StatLine("Credit:", stats.credit, ObColors.TextPrimary, bold = true)
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
