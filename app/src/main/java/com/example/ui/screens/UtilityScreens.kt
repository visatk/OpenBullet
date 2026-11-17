package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.SettingsSection
import com.example.model.SettingsState
import com.example.model.ToolsSection
import com.example.model.ToolsState
import com.example.ui.components.ObBorderedButton
import com.example.ui.components.ObPanel
import com.example.ui.components.ObTrackSlider
import com.example.ui.components.OpenBulletLogo
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObColors
import com.example.viewmodel.OpenBulletViewModel

@Composable
fun ToolsScreen(
    tools: ToolsState,
    viewModel: OpenBulletViewModel,
    modifier: Modifier = Modifier
) {
    val scroll = rememberScrollState()
    val subTabScroll = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObColors.WorkspaceBackground)
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
            ToolsSection.entries.forEach { sec ->
                val icon = when (sec) {
                    ToolsSection.CLEANER -> Icons.Outlined.CleaningServices
                    ToolsSection.FORMATTER -> Icons.Outlined.SwapHoriz
                    ToolsSection.REGEX_HASH -> Icons.Outlined.Code
                    ToolsSection.DIAGNOSTICS -> Icons.Outlined.Memory
                }
                ObBorderedButton(
                    onClick = { viewModel.selectToolsSection(sec) },
                    isActive = tools.activeSection == sec,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("tools_subtab_${sec.name.lowercase()}")
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (tools.activeSection == sec) MaterialTheme.colorScheme.primary else ObColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = sec.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (tools.activeSection == sec) MaterialTheme.colorScheme.primary else ObColors.TextPrimary
                    )
                }
            }
        }

        when (tools.activeSection) {
            ToolsSection.CLEANER -> {
                ObPanel(backgroundColor = ObColors.SurfacePanel, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Wordlist Cleaner", style = MaterialTheme.typography.titleSmall, color = ObColors.TextPrimary)
                        OutlinedTextField(
                            value = tools.cleanerInput,
                            onValueChange = viewModel::updateCleanerInput,
                            label = { Text("Input Lines") },
                            minLines = 4,
                            maxLines = 6,
                            colors = obFieldColors(),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ObCheckRow("Deduplicate", tools.cleanerRemoveDuplicates, viewModel::toggleCleanerDuplicates)
                            ObCheckRow("Trim", tools.cleanerTrimWhitespace, viewModel::toggleCleanerTrim)
                            ObCheckRow("Drop Empty", tools.cleanerRemoveEmpty, viewModel::toggleCleanerEmpty)
                        }
                        ObBorderedButton(onClick = viewModel::runListCleaner, modifier = Modifier.testTag("tools_clean_button")) {
                            Icon(Icons.Outlined.CleaningServices, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Clean Lines", style = MaterialTheme.typography.labelLarge, color = ObColors.TextPrimary)
                        }
                        OutlinedTextField(
                            value = tools.cleanerOutput,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Cleaned Output") },
                            minLines = 4,
                            maxLines = 6,
                            colors = obFieldColors(),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            ToolsSection.FORMATTER -> {
                ObPanel(backgroundColor = ObColors.SurfacePanel, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Combo Pair Formatter", style = MaterialTheme.typography.titleSmall, color = ObColors.TextPrimary)
                        OutlinedTextField(
                            value = tools.formatterInput,
                            onValueChange = viewModel::updateFormatterInput,
                            label = { Text("Input Pairs (e.g. a:b or a;b)") },
                            minLines = 4,
                            maxLines = 6,
                            colors = obFieldColors(),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = tools.formatterSeparator,
                            onValueChange = viewModel::updateFormatterSeparator,
                            label = { Text("Target Separator") },
                            singleLine = true,
                            colors = obFieldColors(),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.width(160.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ObBorderedButton(onClick = { viewModel.runComboFormatter(swapOrder = false) }) {
                                Text("Format", style = MaterialTheme.typography.labelLarge, color = ObColors.TextPrimary)
                            }
                            ObBorderedButton(onClick = { viewModel.runComboFormatter(swapOrder = true) }) {
                                Icon(Icons.Outlined.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Swap Sides", style = MaterialTheme.typography.labelLarge, color = ObColors.TextPrimary)
                            }
                        }
                        OutlinedTextField(
                            value = tools.formatterOutput,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Formatted Output") },
                            minLines = 4,
                            maxLines = 6,
                            colors = obFieldColors(),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            ToolsSection.REGEX_HASH -> {
                ObPanel(backgroundColor = ObColors.SurfacePanel, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Regex Extractor", style = MaterialTheme.typography.titleSmall, color = ObColors.TextPrimary)
                        OutlinedTextField(
                            value = tools.regexPattern,
                            onValueChange = { viewModel.updateRegexInputs(pattern = it) },
                            label = { Text("Regex Pattern") },
                            singleLine = true,
                            colors = obFieldColors(),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = tools.regexTestInput,
                            onValueChange = { viewModel.updateRegexInputs(testInput = it) },
                            label = { Text("Test Input") },
                            minLines = 3,
                            colors = obFieldColors(),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = if (tools.regexMatches.isEmpty()) "Matches: 0" else "Matches (${tools.regexMatches.size}): ${tools.regexMatches.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.primary
                        )

                        HorizontalDivider(thickness = 1.dp, color = ObColors.DividerSubtle)

                        Text("Base64 & SHA-256 Encoder", style = MaterialTheme.typography.titleSmall, color = ObColors.TextPrimary)
                        OutlinedTextField(
                            value = tools.hashInput,
                            onValueChange = viewModel::updateHashInput,
                            label = { Text("Plaintext") },
                            singleLine = true,
                            colors = obFieldColors(),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (tools.base64Output.isNotEmpty()) {
                            Text("Base64: ${tools.base64Output}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily), color = ObColors.StatusHitGreen)
                            Text("SHA-256: ${tools.sha256Output}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily), color = ObColors.TextSecondary)
                        }
                    }
                }
            }

            ToolsSection.DIAGNOSTICS -> {
                ObPanel(backgroundColor = ObColors.SurfacePanel, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Native Key Scanner (NDK C++17 / JNI)", style = MaterialTheme.typography.titleSmall, color = ObColors.TextPrimary)
                        Text(
                            text = "Engine: ${com.example.nativeengine.NativeScannerBridge.engineBanner()}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedTextField(
                            value = tools.nativeSampleBody,
                            onValueChange = { viewModel.updateNativeDiagnosticFields(sampleBody = it) },
                            label = { Text("Sample HTTP Response / Line") },
                            minLines = 3,
                            colors = obFieldColors(),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = tools.nativeSuccessKey,
                                onValueChange = { viewModel.updateNativeDiagnosticFields(successKey = it) },
                                label = { Text("Success Key") },
                                singleLine = true,
                                colors = obFieldColors(),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = tools.nativeFailureKey,
                                onValueChange = { viewModel.updateNativeDiagnosticFields(failureKey = it) },
                                label = { Text("Failure Key") },
                                singleLine = true,
                                colors = obFieldColors(),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        ObBorderedButton(onClick = viewModel::runNativeScannerTest, modifier = Modifier.testTag("tools_native_scan_btn")) {
                            Icon(Icons.Outlined.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Run Key Scan", style = MaterialTheme.typography.labelLarge, color = ObColors.TextPrimary)
                        }
                        if (tools.nativeScanSummary.isNotEmpty()) {
                            Text(
                                text = tools.nativeScanSummary,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = ObColors.StatusHitGreen
                            )
                        }

                        HorizontalDivider(thickness = 1.dp, color = ObColors.DividerSubtle)

                        Text("Network Probe (OkHttp + Retrofit)", style = MaterialTheme.typography.titleSmall, color = ObColors.TextPrimary)
                        OutlinedTextField(
                            value = tools.probeUrl,
                            onValueChange = viewModel::updateProbeUrl,
                            label = { Text("Probe Endpoint URL") },
                            singleLine = true,
                            colors = obFieldColors(),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        ObBorderedButton(onClick = viewModel::runNetworkProbe, modifier = Modifier.testTag("tools_network_probe_btn")) {
                            Icon(Icons.Outlined.NetworkCheck, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (tools.isProbing) "Probing..." else "Probe Endpoint", style = MaterialTheme.typography.labelLarge, color = ObColors.TextPrimary)
                        }
                        if (tools.probeResult.isNotEmpty()) {
                            Text(
                                text = tools.probeResult,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = if (tools.probeResult.startsWith("Error")) ObColors.StatusBadRed else ObColors.StatusHitGreen
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    settings: SettingsState,
    viewModel: OpenBulletViewModel,
    modifier: Modifier = Modifier
) {
    val scroll = rememberScrollState()
    val subTabScroll = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObColors.WorkspaceBackground)
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
                ObBorderedButton(
                    onClick = { viewModel.selectSettingsSection(sec) },
                    isActive = settings.activeSection == sec,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("settings_subtab_${sec.name.lowercase()}")
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (settings.activeSection == sec) MaterialTheme.colorScheme.primary else ObColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = sec.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (settings.activeSection == sec) MaterialTheme.colorScheme.primary else ObColors.TextPrimary
                    )
                }
            }
        }

        ObPanel(backgroundColor = ObColors.SurfacePanel, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (settings.activeSection) {
                    SettingsSection.GENERAL -> {
                        Text("General Settings", style = MaterialTheme.typography.titleSmall, color = ObColors.TextPrimary)
                        ObSwitchRow("Auto-save hits to Hits DB", settings.autoSaveHits, viewModel::toggleSettingAutoSave)
                        ObSwitchRow("Ignore empty wordlist lines", settings.ignoreEmptyLines, viewModel::toggleSettingIgnoreEmpty)
                        ObSwitchRow("Show timestamps in console log", settings.showTimestampsInConsole) {
                            viewModel.toggleTimestampLogging()
                        }
                    }

                    SettingsSection.RURILIB -> {
                        Text("RuriLib Engine Parameters", style = MaterialTheme.typography.titleSmall, color = ObColors.TextPrimary)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Request Timeout: ${settings.requestTimeoutSec}s", style = MaterialTheme.typography.bodyMedium, color = ObColors.TextPrimary)
                            ObTrackSlider(
                                value = settings.requestTimeoutSec.toFloat(),
                                valueRange = 1f..60f,
                                onValueChange = { viewModel.updateTimeout(it.toInt()) },
                                showTicks = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Max Retries: ${settings.maxRetries}", style = MaterialTheme.typography.bodyMedium, color = ObColors.TextPrimary)
                            ObTrackSlider(
                                value = settings.maxRetries.toFloat(),
                                valueRange = 0f..15f,
                                onValueChange = { viewModel.updateMaxRetries(it.toInt()) },
                                showTicks = false,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    SettingsSection.SOUNDS -> {
                        Text("Audio Notifications", style = MaterialTheme.typography.titleSmall, color = ObColors.TextPrimary)
                        ObSwitchRow("Play chime on Hit", settings.soundOnHit, viewModel::toggleSettingSoundOnHit)
                        ObSwitchRow("Play alert when Runner finishes", settings.soundOnFinish, viewModel::toggleSettingSoundOnFinish)
                    }

                    SettingsSection.THEMES -> {
                        Text("Accent Color Swatch", style = MaterialTheme.typography.titleSmall, color = ObColors.TextPrimary)
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
                                val selected = settings.accentHex == hex
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { viewModel.selectAccentColor(hex) }
                                        .padding(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(Color(hex), RoundedCornerShape(8.dp))
                                            .border(
                                                width = if (selected) 2.5.dp else 1.dp,
                                                color = if (selected) ObColors.TextPrimary else ObColors.BorderHairline,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) ObColors.TextPrimary else ObColors.TextSecondary)
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
fun AboutScreen(
    nativeEngineBanner: String = "",
    modifier: Modifier = Modifier
) {
    val scroll = rememberScrollState()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObColors.WorkspaceBackground)
            .verticalScroll(scroll)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        ObPanel(
            backgroundColor = ObColors.SurfacePanel,
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OpenBulletLogo(modifier = Modifier.size(56.dp), centerBulletColor = MaterialTheme.colorScheme.primary)
                Text(
                    text = "OpenBullet 1.0.0",
                    style = MaterialTheme.typography.titleLarge,
                    color = ObColors.TextPrimary
                )
                Text(
                    text = "Adaptive Android Workstation",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                HorizontalDivider(thickness = 1.dp, color = ObColors.DividerSubtle)
                ArchitectureRow("UI", "Jetpack Compose / Material 3 / Kotlin")
                ArchitectureRow("DB", "Room (SQLite) + KSP Reactive Flows")
                ArchitectureRow("Background", "WorkManager + Bounded Coroutines")
                ArchitectureRow("Networking", "OkHttp + Retrofit + Moshi")
                ArchitectureRow("Scanning", nativeEngineBanner.ifEmpty { "NDK C++17 / JNI Bridge" })
            }
        }
    }
}

@Composable
private fun ArchitectureRow(layer: String, tech: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(layer, style = MaterialTheme.typography.labelMedium, color = ObColors.TextSecondary)
        Text(tech, style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily), color = ObColors.TextPrimary)
    }
}

@Composable
private fun ObCheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { onChange(!checked) }
            .padding(end = 8.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onChange,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                uncheckedColor = ObColors.BorderBright
            )
        )
        Text(label, style = MaterialTheme.typography.bodySmall, color = ObColors.TextPrimary)
    }
}

@Composable
private fun ObSwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable { onChange(!checked) }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = ObColors.TextPrimary, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ObColors.TextPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = ObColors.TextSecondary,
                uncheckedTrackColor = ObColors.SurfaceRecessed
            )
        )
    }
}

@Composable
private fun obFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = ObColors.BorderHairline,
    focusedContainerColor = ObColors.SurfaceRecessed,
    unfocusedContainerColor = ObColors.SurfaceRecessed,
    focusedTextColor = ObColors.TextPrimary,
    unfocusedTextColor = ObColors.TextPrimary,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = ObColors.TextSecondary
)
