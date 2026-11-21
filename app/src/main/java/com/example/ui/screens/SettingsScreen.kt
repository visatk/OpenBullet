package com.example.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.model.AppThemeMode
import com.example.model.SettingsSection
import com.example.model.SettingsState
import com.example.ui.components.ObBorderedButton
import com.example.ui.components.ObPanel
import com.example.ui.components.ObSegmentedControl
import com.example.ui.components.ObTrackSlider
import com.example.ui.components.obOutlinedTextFieldColors
import com.example.ui.theme.ObTheme
import com.example.viewmodel.RunnerPacketHelper

@Composable
fun SettingsScreen(
    settings: SettingsState,
    onSelectSection: (SettingsSection) -> Unit,
    onToggleKeepAwake: (Boolean) -> Unit,
    onToggleAutoSave: (Boolean) -> Unit,
    onToggleIgnoreEmpty: (Boolean) -> Unit,
    onToggleTimestamps: (Boolean) -> Unit,
    onTimeoutDraft: (Int) -> Unit,
    onTimeoutCommit: () -> Unit,
    onMaxRetriesDraft: (Int) -> Unit,
    onMaxRetriesCommit: () -> Unit,
    onProxyJudgeUrlChange: (String) -> Unit,
    onToggleSoundOnHit: (Boolean) -> Unit,
    onToggleSoundOnFinish: (Boolean) -> Unit,
    onTestHitSound: () -> Unit,
    onTestFinishSound: () -> Unit,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onSelectAccentColor: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val scroll = rememberScrollState()
    val subTabScroll = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp)
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(subTabScroll),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SettingsSection.entries.forEach { sec ->
                val icon = when (sec) {
                    SettingsSection.GENERAL -> Icons.Outlined.Settings
                    SettingsSection.RURILIB -> Icons.Outlined.Tune
                    SettingsSection.SOUNDS -> Icons.Outlined.GraphicEq
                    SettingsSection.THEMES -> Icons.Outlined.Palette
                }
                FilterChip(
                    selected = settings.activeSection == sec,
                    onClick = { onSelectSection(sec) },
                    label = { Text(sec.label) },
                    leadingIcon = {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("settings_subtab_${sec.name.lowercase()}")
                )
            }
        }

        ObPanel(backgroundColor = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (settings.activeSection) {
                    SettingsSection.GENERAL -> {
                        val context = LocalContext.current
                        var isBatteryUnrestricted by remember {
                            mutableStateOf(RunnerPacketHelper.isIgnoringBatteryOptimizations(context))
                        }
                        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                            isBatteryUnrestricted = RunnerPacketHelper.isIgnoringBatteryOptimizations(context)
                        }

                        Text("General & Background Execution", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        ObSwitchRow("Keep CPU & Wi-Fi awake (WakeLock during screen-off)", settings.keepAwakeEnabled, onToggleKeepAwake)
                        ObSwitchRow("Auto-save hits to Hits DB", settings.autoSaveHits, onToggleAutoSave)
                        ObSwitchRow("Ignore empty wordlist lines", settings.ignoreEmptyLines, onToggleIgnoreEmpty)
                        ObSwitchRow("Show timestamps in console log", settings.showTimestampsInConsole, onToggleTimestamps)

                        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = if (isBatteryUnrestricted) "Battery Optimization: Unrestricted" else "Battery Optimization: Restricted",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isBatteryUnrestricted) ObTheme.colors.hit else ObTheme.colors.custom
                                )
                                Text(
                                    text = "Disable battery restriction in Android Settings so OEMs never pause background runs.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            ObBorderedButton(
                                onClick = { RunnerPacketHelper.openBatteryOptimizationSettings(context) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Battery", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    SettingsSection.RURILIB -> {
                        Text("Engine & Network Parameters", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Request Timeout: ${settings.requestTimeoutSec}s", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            ObTrackSlider(
                                value = settings.requestTimeoutSec.toFloat(),
                                valueRange = 1f..60f,
                                onValueChange = { onTimeoutDraft(it.toInt()) },
                                onValueChangeFinished = onTimeoutCommit,
                                showTicks = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Max Retries: ${settings.maxRetries}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            ObTrackSlider(
                                value = settings.maxRetries.toFloat(),
                                valueRange = 0f..15f,
                                onValueChange = { onMaxRetriesDraft(it.toInt()) },
                                onValueChangeFinished = onMaxRetriesCommit,
                                showTicks = false,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        OutlinedTextField(
                            value = settings.proxyJudgeUrl,
                            onValueChange = onProxyJudgeUrlChange,
                            label = { Text("Proxy Checker Judge URL") },
                            singleLine = true,
                            colors = obOutlinedTextFieldColors(),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    SettingsSection.SOUNDS -> {
                        Text("Audio Notifications", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        ObSwitchRow("Play chime on Hit", settings.soundOnHit, onToggleSoundOnHit)
                        ObSwitchRow("Play alert when Runner finishes", settings.soundOnFinish, onToggleSoundOnFinish)
                        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ObBorderedButton(
                                onClick = onTestHitSound,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = ObTheme.colors.hit, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Test Hit Tone", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                            }
                            ObBorderedButton(
                                onClick = onTestFinishSound,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Test Finish Tone", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }

                    SettingsSection.THEMES -> {
                        Text("Theme Mode", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        ObSegmentedControl(
                            options = AppThemeMode.entries,
                            selectedOption = settings.themeMode,
                            labelProvider = { it.label },
                            onSelect = onSelectThemeMode,
                            modifier = Modifier.fillMaxWidth(),
                            testTagPrefix = "theme_mode"
                        )

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                            ObSwitchRow(
                                label = "Material You Dynamic Color (uses wallpaper palette)",
                                checked = settings.useDynamicColor,
                                onChange = onToggleDynamicColor
                            )
                        }

                        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        Text("Accent Color Swatch", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        val swatches = listOf(
                            0xFF2F94E8L to "OB Blue",
                            0xFF4CD964L to "Hit Green",
                            0xFFF59E35L to "Custom Orange",
                            0xFF26C6DAL to "Aqua Cyan",
                            0xFFBA68C8L to "Violet"
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            swatches.forEach { (hex, label) ->
                                val selected = !settings.useDynamicColor && settings.accentHex == hex
                                val swatchColor = Color(hex)
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(role = Role.RadioButton) { onSelectAccentColor(hex) }
                                        .padding(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .background(swatchColor, RoundedCornerShape(10.dp))
                                            .border(
                                                width = if (selected) 2.5.dp else 1.dp,
                                                color = if (selected) {
                                                    MaterialTheme.colorScheme.onSurface
                                                } else {
                                                    MaterialTheme.colorScheme.outlineVariant
                                                },
                                                shape = RoundedCornerShape(10.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (selected) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = "Selected $label",
                                                tint = if (swatchColor.luminance() > 0.55f) Color.Black else Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ObSwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerLowest
            )
        )
    }
}
