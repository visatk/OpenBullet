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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.HistoryToggleOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.model.AppShellUiState
import com.example.model.MainTab
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObTheme

fun MainTab.icon(selected: Boolean): ImageVector = when (this) {
    MainTab.RUNNER -> if (selected) Icons.Filled.PlayArrow else Icons.Outlined.PlayArrow
    MainTab.PROXIES -> if (selected) Icons.Filled.Dns else Icons.Outlined.Dns
    MainTab.WORDLISTS -> if (selected) Icons.AutoMirrored.Filled.ListAlt else Icons.AutoMirrored.Outlined.ListAlt
    MainTab.CONFIGS -> if (selected) Icons.Filled.Code else Icons.Outlined.Code
    MainTab.HITS_DB -> if (selected) Icons.Filled.VerifiedUser else Icons.Outlined.VerifiedUser
    MainTab.TOOLS -> if (selected) Icons.Filled.Build else Icons.Outlined.Build
    MainTab.SETTINGS -> if (selected) Icons.Filled.Settings else Icons.Outlined.Settings
    MainTab.ABOUT -> if (selected) Icons.Filled.Info else Icons.Outlined.Info
}

@Composable
fun OpenBulletLogo(
    modifier: Modifier = Modifier,
    bulletColor: Color = MaterialTheme.colorScheme.onSurface,
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

@OptIn(ExperimentalMaterial3Api::class)
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

    Column(modifier = modifier.fillMaxWidth()) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            windowInsets = TopAppBarDefaults.windowInsets,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OpenBulletLogo()
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontFeatureSettings = "tnum"
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isRunning) {
                        RunningPulseBadge()
                    }
                }
            },
            actions = {
                if (isCompact) {
                    val utilityTabs = listOf(MainTab.TOOLS, MainTab.SETTINGS, MainTab.ABOUT)
                    utilityTabs.forEach { tab ->
                        val selected = activeTab == tab
                        IconButton(
                            onClick = { onSelectTab(tab) },
                            modifier = Modifier.testTag("top_action_${tab.name.lowercase()}")
                        ) {
                            Icon(
                                imageVector = tab.icon(selected),
                                contentDescription = tab.title,
                                tint = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                    Box {
                        IconButton(
                            onClick = { overflowExpanded = true },
                            modifier = Modifier.testTag("header_overflow_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "Runner utility actions",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        DropdownMenu(
                            expanded = overflowExpanded,
                            onDismissRequest = { overflowExpanded = false },
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (timestampsEnabled) {
                                            "Disable Log Timestamps"
                                        } else {
                                            "Enable Log Timestamps"
                                        },
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.HistoryToggleOff,
                                        contentDescription = null,
                                        tint = if (timestampsEnabled) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                },
                                onClick = {
                                    overflowExpanded = false
                                    onToggleTimestamps()
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text("Log UI Snapshot", color = MaterialTheme.colorScheme.onSurface)
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.PhotoCamera,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
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
                        modifier = Modifier.testTag("header_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.HistoryToggleOff,
                            contentDescription = stringResource(R.string.cd_history_timer),
                            tint = if (timestampsEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                    IconButton(
                        onClick = onSnapshotClick,
                        modifier = Modifier.testTag("header_snapshot_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoCamera,
                            contentDescription = stringResource(R.string.cd_snapshot),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        )
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
fun RunningPulseBadge(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "running_pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    val hitColor = ObTheme.colors.hit
    val hitContainer = ObTheme.colors.hitContainer
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = hitContainer,
        border = BorderStroke(1.dp, hitColor.copy(alpha = 0.55f)),
        modifier = modifier.semantics { contentDescription = "Runner status: Active" }
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
                    .background(hitColor, CircleShape)
            )
            Text(
                text = "ACTIVE",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = JetBrainsMonoFontFamily
                ),
                color = hitColor
            )
        }
    }
}

@Composable
fun ObBottomNavigationBar(
    shellState: AppShellUiState,
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

    Column(modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            modifier = Modifier.navigationBarsPadding()
        ) {
            primaryDestinations.forEach { tab ->
                val selected = shellState.activeTab == tab
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelectTab(tab) },
                    icon = {
                        DestinationBadgedIcon(
                            tab = tab,
                            selected = selected,
                            shellState = shellState
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
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.testTag("nav_${tab.name.lowercase()}")
                )
            }
        }
    }
}

@Composable
fun ObNavigationRail(
    shellState: AppShellUiState,
    onSelectTab: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val scroll = rememberScrollState()
    Row(modifier = modifier.fillMaxHeight()) {
        NavigationRail(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxHeight()
                .navigationBarsPadding()
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Start))
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(scroll)
                    .padding(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                MainTab.entries.forEach { tab ->
                    val selected = shellState.activeTab == tab
                    NavigationRailItem(
                        selected = selected,
                        onClick = { onSelectTab(tab) },
                        icon = {
                            DestinationBadgedIcon(
                                tab = tab,
                                selected = selected,
                                shellState = shellState
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
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
                    )
                }
            }
        }
        VerticalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
fun ObNavigationDrawerSheet(
    shellState: AppShellUiState,
    onSelectTab: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val scroll = rememberScrollState()
    val workstationTabs = listOf(
        MainTab.RUNNER,
        MainTab.PROXIES,
        MainTab.WORDLISTS,
        MainTab.CONFIGS,
        MainTab.HITS_DB
    )
    val utilityTabs = listOf(MainTab.TOOLS, MainTab.SETTINGS, MainTab.ABOUT)

    Row(modifier = modifier.fillMaxHeight()) {
        PermanentDrawerSheet(
            modifier = Modifier
                .width(228.dp)
                .fillMaxHeight(),
            drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            drawerContentColor = MaterialTheme.colorScheme.onSurface,
            windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Bottom)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(scroll)
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "WORKSTATION",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                workstationTabs.forEach { tab ->
                    val selected = shellState.activeTab == tab
                    val badgeText = when (tab) {
                        MainTab.RUNNER -> if (shellState.isRunnerActive) "LIVE" else null
                        MainTab.PROXIES -> shellState.totalProxiesCount.takeIf { it > 0 }?.toString()
                        MainTab.WORDLISTS -> shellState.wordlistsCount.takeIf { it > 0 }?.toString()
                        MainTab.CONFIGS -> shellState.configsCount.takeIf { it > 0 }?.toString()
                        MainTab.HITS_DB -> shellState.hitsCount.takeIf { it > 0 }?.toString()
                        else -> null
                    }
                    NavigationDrawerItem(
                        selected = selected,
                        onClick = { onSelectTab(tab) },
                        icon = { Icon(tab.icon(selected), contentDescription = null) },
                        label = { Text(tab.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        badge = badgeText?.let { text ->
                            {
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (tab == MainTab.RUNNER && shellState.isRunnerActive) {
                                        ObTheme.colors.hit
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    }
                                )
                            }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            unselectedContainerColor = Color.Transparent,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.testTag("drawer_${tab.name.lowercase()}")
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Text(
                    text = "UTILITIES",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                utilityTabs.forEach { tab ->
                    val selected = shellState.activeTab == tab
                    NavigationDrawerItem(
                        selected = selected,
                        onClick = { onSelectTab(tab) },
                        icon = { Icon(tab.icon(selected), contentDescription = null) },
                        label = { Text(tab.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            unselectedContainerColor = Color.Transparent,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.testTag("drawer_${tab.name.lowercase()}")
                    )
                }
            }
        }
        VerticalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun DestinationBadgedIcon(
    tab: MainTab,
    selected: Boolean,
    shellState: AppShellUiState
) {
    val badgeContent: (@Composable () -> Unit)? = when {
        tab == MainTab.RUNNER && shellState.isRunnerActive -> {
            { Badge(containerColor = ObTheme.colors.hit) }
        }
        tab == MainTab.HITS_DB && shellState.hitsCount > 0 -> {
            {
                Badge(
                    containerColor = ObTheme.colors.hit,
                    contentColor = Color(0xFF061B0D)
                ) {
                    val label = if (shellState.hitsCount > 99) "99+" else shellState.hitsCount.toString()
                    Text(label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        else -> null
    }

    if (badgeContent != null) {
        BadgedBox(badge = { badgeContent() }) {
            Icon(imageVector = tab.icon(selected), contentDescription = tab.title)
        }
    } else {
        Icon(imageVector = tab.icon(selected), contentDescription = tab.title)
    }
}

@Composable
fun ObBorderedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
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
        targetValue = if (isActive) MaterialTheme.colorScheme.primaryContainer else containerColor,
        animationSpec = tween(180),
        label = "btn_bg"
    )
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        color = animatedBg,
        border = BorderStroke(1.dp, animatedBorder),
        modifier = modifier.defaultMinSize(minHeight = 48.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(contentPadding)
                .alpha(if (enabled) 1f else 0.5f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content
        )
    }
}

@Composable
fun ObPanel(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    cornerRadius: Dp = 10.dp,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ObSegmentedControl(
    options: List<T>,
    selectedOption: T,
    labelProvider: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String = "seg"
) {
    val haptic = LocalHapticFeedback.current
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, item ->
            val isSelected = item == selectedOption
            SegmentedButton(
                selected = isSelected,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelect(item)
                },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    activeBorderColor = MaterialTheme.colorScheme.primary,
                    inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.testTag("${testTagPrefix}_${labelProvider(item).lowercase()}")
            ) {
                Text(
                    text = labelProvider(item),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ObStatusPill(
    text: String,
    tintColor: Color,
    containerColor: Color,
    modifier: Modifier = Modifier,
    showDot: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor,
        border = BorderStroke(1.dp, tintColor.copy(alpha = 0.45f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (showDot) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(tintColor, CircleShape)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontFeatureSettings = "tnum"
                ),
                color = tintColor,
                maxLines = 1
            )
        }
    }
}

@Composable
fun ObEmptyStateCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onActionClick != null) {
                Button(
                    onClick = onActionClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(actionLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
fun ObConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDestructive: Boolean = true
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDestructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    contentColor = if (isDestructive) {
                        MaterialTheme.colorScheme.onError
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    }
                )
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
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
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        animationSpec = tween(160),
        label = "radio_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) accent else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = tween(160),
        label = "radio_border"
    )

    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
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
                    color = if (selected) accent else MaterialTheme.colorScheme.outline,
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
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
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
    modifier: Modifier = Modifier,
    enabled: Boolean = valueRange.endInclusive > valueRange.start,
    onValueChangeFinished: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val span = valueRange.endInclusive - valueRange.start
    val safeEnd = if (span > 0f) valueRange.endInclusive else valueRange.start + 1f
    val safeRange = valueRange.start..safeEnd
    val clampedValue = value.coerceIn(safeRange)
    val fraction = if (span <= 0f) 0f else ((clampedValue - valueRange.start) / span).coerceIn(0f, 1f)
    val accent = MaterialTheme.colorScheme.primary
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val trackBg = MaterialTheme.colorScheme.surfaceContainerLowest
    val thumbColor = MaterialTheme.colorScheme.surface

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
                        color = tickColor,
                        start = Offset(x, 0f),
                        end = Offset(x, 5.dp.toPx()),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = tickColor,
                        start = Offset(x, size.height - 5.dp.toPx()),
                        end = Offset(x, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
        }

        Slider(
            value = clampedValue,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            enabled = enabled,
            valueRange = safeRange,
            interactionSource = interactionSource,
            colors = SliderDefaults.colors(
                thumbColor = thumbColor,
                activeTrackColor = accent,
                inactiveTrackColor = trackBg
            ),
            thumb = {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(thumbColor, CircleShape)
                        .border(2.dp, accent, CircleShape)
                )
            },
            track = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(trackBg)
                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(3.dp))
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
    val pct = (animatedProgress * 100).toInt()
    LinearProgressIndicator(
        progress = { animatedProgress },
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
        modifier = modifier
            .height(10.dp)
            .clip(RoundedCornerShape(4.dp))
            .semantics { contentDescription = "Runner progress $pct percent" }
    )
}

@Composable
fun ObSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
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
    val shape = RoundedCornerShape(8.dp)
    val baseModifier = modifier
        .heightIn(min = 40.dp)
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        .border(1.dp, MaterialTheme.colorScheme.outline, shape)
        .let { m -> if (onClick != null) m.clickable(role = Role.Button, onClick = onClick) else m }
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
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun obOutlinedTextFieldColors(labelAccent: Color? = null) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = labelAccent ?: MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedLabelColor = labelAccent ?: MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = labelAccent?.copy(alpha = 0.85f) ?: MaterialTheme.colorScheme.onSurfaceVariant
)
