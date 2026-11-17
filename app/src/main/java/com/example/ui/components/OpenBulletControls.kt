package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.HistoryToggleOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.MainTab
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObColors

fun MainTab.icon(selected: Boolean): ImageVector = when (this) {
    MainTab.RUNNER -> if (selected) Icons.Filled.PlayArrow else Icons.Outlined.PlayArrow
    MainTab.PROXIES -> if (selected) Icons.Filled.Dns else Icons.Outlined.Dns
    MainTab.WORDLISTS -> if (selected) Icons.Filled.ListAlt else Icons.Outlined.ListAlt
    MainTab.CONFIGS -> if (selected) Icons.Filled.Code else Icons.Outlined.Code
    MainTab.HITS_DB -> if (selected) Icons.Filled.VerifiedUser else Icons.Outlined.VerifiedUser
    MainTab.TOOLS -> if (selected) Icons.Filled.Build else Icons.Outlined.Build
    MainTab.SETTINGS -> if (selected) Icons.Filled.Settings else Icons.Outlined.Settings
    MainTab.ABOUT -> if (selected) Icons.Filled.Info else Icons.Outlined.Info
}

@Composable
fun OpenBulletLogo(
    modifier: Modifier = Modifier,
    bulletColor: Color = ObColors.TextPrimary,
    centerBulletColor: Color = MaterialTheme.colorScheme.primary
) {
    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val positions = listOf(0.20f, 0.50f, 0.80f)
        val bw = w * 0.16f
        positions.forEachIndexed { index, cxFrac ->
            val cx = w * cxFrac
            val topY = if (index == 1) h * 0.10f else h * 0.17f
            val shoulderY = topY + h * 0.22f
            val bodyBottom = h * 0.78f
            val color = if (index == 1) centerBulletColor else bulletColor
            val path = Path().apply {
                moveTo(cx - bw / 2f, bodyBottom)
                lineTo(cx - bw / 2f, shoulderY)
                quadraticTo(cx - bw / 2f, topY + (shoulderY - topY) * 0.35f, cx, topY)
                quadraticTo(cx + bw / 2f, topY + (shoulderY - topY) * 0.35f, cx + bw / 2f, shoulderY)
                lineTo(cx + bw / 2f, bodyBottom)
                close()
            }
            drawPath(path = path, color = color)
            drawRoundRect(
                color = color,
                topLeft = Offset(cx - bw * 0.62f, h * 0.82f),
                size = Size(bw * 1.24f, h * 0.10f),
                cornerRadius = CornerRadius(2f, 2f)
            )
        }
    }
}

@Composable
fun ObWindowHeader(
    isCompact: Boolean,
    isRunning: Boolean,
    activeTab: MainTab,
    timestampsEnabled: Boolean,
    onSelectTab: (MainTab) -> Unit,
    onToggleTimestamps: () -> Unit,
    onSnapshotClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var overflowExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ObColors.ChromeBackground)
            .statusBarsPadding()
            .displayCutoutPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OpenBulletLogo(modifier = Modifier.padding(end = 10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = ObColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = ObColors.SurfaceTableHeader,
                        border = BorderStroke(1.dp, ObColors.BorderHairline)
                    ) {
                        Text(
                            text = "1.0.0",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (isRunning) {
                        RunningPulseBadge()
                    }
                }
            }

            if (isCompact) {
                val utilityTabs = listOf(MainTab.TOOLS, MainTab.SETTINGS, MainTab.ABOUT)
                utilityTabs.forEach { tab ->
                    val selected = activeTab == tab
                    IconButton(
                        onClick = { onSelectTab(tab) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("top_action_${tab.name.lowercase()}")
                    ) {
                        Icon(
                            imageVector = tab.icon(selected),
                            contentDescription = tab.title,
                            tint = if (selected) MaterialTheme.colorScheme.primary else ObColors.TextSecondary
                        )
                    }
                }
                Box {
                    IconButton(
                        onClick = { overflowExpanded = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("header_overflow_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Runner utility actions",
                            tint = ObColors.TextPrimary
                        )
                    }
                    DropdownMenu(
                        expanded = overflowExpanded,
                        onDismissRequest = { overflowExpanded = false },
                        containerColor = ObColors.SurfacePanel
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (timestampsEnabled) "Disable Log Timestamps" else "Enable Log Timestamps",
                                    color = ObColors.TextPrimary
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.HistoryToggleOff,
                                    contentDescription = null,
                                    tint = if (timestampsEnabled) MaterialTheme.colorScheme.primary else ObColors.TextSecondary
                                )
                            },
                            onClick = {
                                overflowExpanded = false
                                onToggleTimestamps()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Log UI Snapshot", color = ObColors.TextPrimary) },
                            leadingIcon = {
                                Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = ObColors.TextSecondary)
                            },
                            onClick = {
                                overflowExpanded = false
                                onSnapshotClick()
                            }
                        )
                    }
                }
            } else {
                IconButton(
                    onClick = onToggleTimestamps,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("header_history_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.HistoryToggleOff,
                        contentDescription = stringResource(R.string.cd_history_timer),
                        tint = if (timestampsEnabled) MaterialTheme.colorScheme.primary else ObColors.TextPrimary
                    )
                }
                IconButton(
                    onClick = onSnapshotClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("header_snapshot_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PhotoCamera,
                        contentDescription = stringResource(R.string.cd_snapshot),
                        tint = ObColors.TextPrimary
                    )
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = ObColors.DividerSubtle)
    }
}

@Composable
fun RunningPulseBadge(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "running_pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = ObColors.StatusHitBg,
        border = BorderStroke(1.dp, ObColors.StatusHitGreen.copy(alpha = 0.55f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .alpha(alpha)
                    .background(ObColors.StatusHitGreen, CircleShape)
            )
            Text(
                text = "ACTIVE",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = ObColors.StatusHitGreen
            )
        }
    }
}

@Composable
fun ObBottomNavigationBar(
    activeTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryDestinations = listOf(
        MainTab.RUNNER,
        MainTab.PROXIES,
        MainTab.WORDLISTS,
        MainTab.CONFIGS,
        MainTab.HITS_DB
    )

    Column(modifier = modifier.background(ObColors.ChromeBackground)) {
        HorizontalDivider(thickness = 1.dp, color = ObColors.DividerSubtle)
        NavigationBar(
            containerColor = ObColors.ChromeBackground,
            contentColor = ObColors.TextPrimary,
            tonalElevation = 0.dp,
            modifier = Modifier.navigationBarsPadding()
        ) {
            primaryDestinations.forEach { tab ->
                val selected = activeTab == tab
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelectTab(tab) },
                    icon = {
                        Icon(
                            imageVector = tab.icon(selected),
                            contentDescription = tab.title
                        )
                    },
                    label = {
                        Text(
                            text = if (tab == MainTab.HITS_DB) "Hits" else tab.title,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = ObColors.TextSecondary,
                        unselectedTextColor = ObColors.TextSecondary
                    ),
                    modifier = Modifier.testTag("nav_${tab.name.lowercase()}")
                )
            }
        }
    }
}

@Composable
fun ObNavigationRail(
    activeTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val scroll = rememberScrollState()
    Row(modifier = modifier.fillMaxHeight()) {
        NavigationRail(
            containerColor = ObColors.ChromeBackground,
            contentColor = ObColors.TextPrimary,
            modifier = Modifier
                .fillMaxHeight()
                .navigationBarsPadding()
                .displayCutoutPadding()
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(scroll)
                    .padding(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                MainTab.entries.forEach { tab ->
                    val selected = activeTab == tab
                    NavigationRailItem(
                        selected = selected,
                        onClick = { onSelectTab(tab) },
                        icon = {
                            Icon(
                                imageVector = tab.icon(selected),
                                contentDescription = tab.title
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                ),
                                maxLines = 1
                            )
                        },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = ObColors.TextSecondary,
                            unselectedTextColor = ObColors.TextSecondary
                        ),
                        modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
                    )
                }
            }
        }
        VerticalDivider(thickness = 1.dp, color = ObColors.DividerSubtle)
    }
}

@Composable
fun ObBorderedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    containerColor: Color = ObColors.SurfaceButton,
    borderColor: Color = ObColors.BorderHairline,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val animatedBorder by animateColorAsState(
        targetValue = if (isActive) accent else borderColor,
        animationSpec = tween(180),
        label = "btn_border"
    )
    val animatedBg by animateColorAsState(
        targetValue = if (isActive) ObColors.SurfaceSelected else containerColor,
        animationSpec = tween(180),
        label = "btn_bg"
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = animatedBg,
        border = BorderStroke(1.dp, animatedBorder),
        modifier = modifier.defaultMinSize(minHeight = 48.dp)
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content
        )
    }
}

@Composable
fun ObPanel(
    modifier: Modifier = Modifier,
    backgroundColor: Color = ObColors.SurfaceTableBody,
    borderColor: Color = ObColors.BorderHairline,
    cornerRadius: Dp = 8.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(backgroundColor, RoundedCornerShape(cornerRadius))
            .border(1.dp, borderColor, RoundedCornerShape(cornerRadius))
    ) {
        content()
    }
}

@Composable
fun ObRadioOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    val haptic = LocalHapticFeedback.current
    val bgColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else ObColors.SurfaceRecessed,
        animationSpec = tween(160),
        label = "radio_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) accent else ObColors.BorderHairline,
        animationSpec = tween(160),
        label = "radio_border"
    )

    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .clickable(role = Role.RadioButton) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(15.dp)
                .border(
                    width = 1.5.dp,
                    color = if (selected) accent else ObColors.BorderBright,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(accent, CircleShape)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (selected) ObColors.TextPrimary else ObColors.TextSecondary,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObTrackSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    showTicks: Boolean,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start))
        .coerceIn(0f, 1f)
    val accent = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier.height(40.dp),
        contentAlignment = Alignment.Center
    ) {
        if (showTicks) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .padding(horizontal = 9.dp)
            ) {
                val tickCount = 11
                for (i in 0 until tickCount) {
                    val x = (size.width / (tickCount - 1)) * i
                    drawLine(
                        color = ObColors.TextMuted,
                        start = Offset(x, 0f),
                        end = Offset(x, 5.dp.toPx()),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = ObColors.TextMuted,
                        start = Offset(x, size.height - 5.dp.toPx()),
                        end = Offset(x, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            interactionSource = interactionSource,
            colors = SliderDefaults.colors(
                thumbColor = ObColors.SliderThumb,
                activeTrackColor = accent,
                inactiveTrackColor = ObColors.SliderTrack
            ),
            thumb = {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(ObColors.SliderThumb, CircleShape)
                        .border(2.dp, accent, CircleShape)
                )
            },
            track = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(ObColors.SliderTrack)
                        .border(0.5.dp, ObColors.BorderHairline, RoundedCornerShape(3.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .background(accent)
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ObProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "runner_progress"
    )
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(ObColors.SurfaceRecessed)
            .border(1.dp, ObColors.BorderHairline, RoundedCornerShape(7.dp))
    ) {
        if (animatedProgress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .background(accent, RoundedCornerShape(7.dp))
            )
        }
    }
}

@Composable
fun ObScrollbarIndicator(
    modifier: Modifier = Modifier,
    width: Dp = 12.dp
) {
    Column(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .background(ObColors.SurfaceRecessed.copy(alpha = 0.7f)),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "▲",
            fontSize = 7.sp,
            color = ObColors.TextMuted,
            modifier = Modifier.padding(top = 3.dp)
        )
        Box(
            modifier = Modifier
                .width(4.dp)
                .weight(1f)
                .padding(vertical = 4.dp)
        )
        Text(
            text = "▼",
            fontSize = 7.sp,
            color = ObColors.TextMuted,
            modifier = Modifier.padding(bottom = 3.dp)
        )
    }
}

@Composable
fun ObSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = ObColors.TextSecondary
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        maxLines = 1,
        modifier = modifier
    )
}

@Composable
fun ObMonoValueBox(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(6.dp)
    val baseModifier = modifier
        .heightIn(min = 36.dp)
        .clip(shape)
        .background(ObColors.SurfaceRecessed)
        .border(1.dp, ObColors.BorderBright, shape)
        .let { m -> if (onClick != null) m.clickable(onClick = onClick) else m }
        .padding(horizontal = 10.dp, vertical = 6.dp)

    Box(
        modifier = baseModifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum"
            ),
            color = ObColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
