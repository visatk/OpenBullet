package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.model.ToolsSection
import com.example.model.ToolsState
import com.example.nativeengine.NativeScannerBridge
import com.example.ui.components.ObBorderedButton
import com.example.ui.components.ObPanel
import com.example.ui.components.ObStatusPill
import com.example.ui.components.obOutlinedTextFieldColors
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObTheme

@Composable
fun ToolsScreen(
    tools: ToolsState,
    onSelectSection: (ToolsSection) -> Unit,
    onCleanerInputChange: (String) -> Unit,
    onToggleCleanerDuplicates: (Boolean) -> Unit,
    onToggleCleanerTrim: (Boolean) -> Unit,
    onToggleCleanerEmpty: (Boolean) -> Unit,
    onRunCleaner: () -> Unit,
    onSaveCleanedAsWordlist: () -> Unit,
    onFormatterInputChange: (String) -> Unit,
    onFormatterSeparatorChange: (String) -> Unit,
    onRunFormatter: (swapOrder: Boolean) -> Unit,
    onSaveFormattedAsWordlist: () -> Unit,
    onRegexInputsChange: (pattern: String?, testInput: String?) -> Unit,
    onHashInputChange: (String) -> Unit,
    onNativeFieldsChange: (sampleBody: String?, successKey: String?, failureKey: String?) -> Unit,
    onRunNativeScannerTest: () -> Unit,
    onProbeUrlChange: (String) -> Unit,
    onRunNetworkProbe: () -> Unit,
    onCopyText: (label: String, text: String) -> Unit,
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
            ToolsSection.entries.forEach { sec ->
                val icon = when (sec) {
                    ToolsSection.CLEANER -> Icons.Outlined.CleaningServices
                    ToolsSection.FORMATTER -> Icons.Outlined.SwapHoriz
                    ToolsSection.REGEX_HASH -> Icons.Outlined.Code
                    ToolsSection.DIAGNOSTICS -> Icons.Outlined.Memory
                }
                val selected = tools.activeSection == sec
                FilterChip(
                    selected = selected,
                    onClick = { onSelectSection(sec) },
                    label = { Text(sec.label) },
                    leadingIcon = {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("tools_subtab_${sec.name.lowercase()}")
                )
            }
        }

        when (tools.activeSection) {
            ToolsSection.CLEANER -> {
                ObPanel(backgroundColor = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Wordlist Cleaner", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                            if (tools.cleanerStats.isNotEmpty()) {
                                ObStatusPill(
                                    text = tools.cleanerStats,
                                    tintColor = ObTheme.colors.hit,
                                    containerColor = ObTheme.colors.hitContainer
                                )
                            }
                        }
                        OutlinedTextField(
                            value = tools.cleanerInput,
                            onValueChange = onCleanerInputChange,
                            label = { Text("Input Lines") },
                            minLines = 4,
                            maxLines = 6,
                            colors = obOutlinedTextFieldColors(),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ToolCheckRow("Deduplicate", tools.cleanerRemoveDuplicates, onToggleCleanerDuplicates)
                            ToolCheckRow("Trim", tools.cleanerTrimWhitespace, onToggleCleanerTrim)
                            ToolCheckRow("Drop Empty", tools.cleanerRemoveEmpty, onToggleCleanerEmpty)
                        }
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ObBorderedButton(onClick = onRunCleaner, modifier = Modifier.testTag("tools_clean_button")) {
                                Icon(Icons.Outlined.CleaningServices, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Clean Lines", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                            }
                            if (tools.cleanerOutput.isNotBlank()) {
                                ObBorderedButton(onClick = onSaveCleanedAsWordlist, isActive = true) {
                                    Text("Save as Wordlist", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                                IconButton(
                                    onClick = { onCopyText("Cleaned Lines", tools.cleanerOutput) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy cleaned output", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        OutlinedTextField(
                            value = tools.cleanerOutput,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Cleaned Output") },
                            minLines = 4,
                            maxLines = 6,
                            colors = obOutlinedTextFieldColors(),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            ToolsSection.FORMATTER -> {
                ObPanel(backgroundColor = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Combo Pair Formatter", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                            if (tools.formatterStats.isNotEmpty()) {
                                ObStatusPill(
                                    text = tools.formatterStats,
                                    tintColor = MaterialTheme.colorScheme.primary,
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            }
                        }
                        OutlinedTextField(
                            value = tools.formatterInput,
                            onValueChange = onFormatterInputChange,
                            label = { Text("Input Pairs (e.g. a:b or a;b)") },
                            minLines = 4,
                            maxLines = 6,
                            colors = obOutlinedTextFieldColors(),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = tools.formatterSeparator,
                            onValueChange = onFormatterSeparatorChange,
                            label = { Text("Target Separator") },
                            singleLine = true,
                            colors = obOutlinedTextFieldColors(),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.width(160.dp)
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ObBorderedButton(onClick = { onRunFormatter(false) }) {
                                Text("Format", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                            }
                            ObBorderedButton(onClick = { onRunFormatter(true) }) {
                                Icon(Icons.Outlined.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Swap Sides", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                            }
                            if (tools.formatterOutput.isNotBlank()) {
                                ObBorderedButton(onClick = onSaveFormattedAsWordlist, isActive = true) {
                                    Text("Save as Wordlist", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                                IconButton(
                                    onClick = { onCopyText("Formatted Combos", tools.formatterOutput) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy formatted output", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        OutlinedTextField(
                            value = tools.formatterOutput,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Formatted Output") },
                            minLines = 4,
                            maxLines = 6,
                            colors = obOutlinedTextFieldColors(),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            ToolsSection.REGEX_HASH -> {
                ObPanel(backgroundColor = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Regex Extractor", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        OutlinedTextField(
                            value = tools.regexPattern,
                            onValueChange = { onRegexInputsChange(it, null) },
                            label = { Text("Regex Pattern") },
                            isError = tools.regexError != null,
                            supportingText = tools.regexError?.let { err -> { Text(err) } },
                            singleLine = true,
                            colors = obOutlinedTextFieldColors(),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = tools.regexTestInput,
                            onValueChange = { onRegexInputsChange(null, it) },
                            label = { Text("Test Input") },
                            minLines = 3,
                            colors = obOutlinedTextFieldColors(),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (tools.regexMatches.isEmpty()) {
                                    "Matches: 0"
                                } else {
                                    "Matches (${tools.regexMatches.size}): ${tools.regexMatches.joinToString(", ")}"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            if (tools.regexMatches.isNotEmpty()) {
                                IconButton(
                                    onClick = { onCopyText("Regex Matches", tools.regexMatches.joinToString("\n")) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy matches", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

                        Text("Base64, Hash & URL Encoder", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        OutlinedTextField(
                            value = tools.hashInput,
                            onValueChange = onHashInputChange,
                            label = { Text("Plaintext / Encoded String") },
                            singleLine = true,
                            colors = obOutlinedTextFieldColors(),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (tools.hashInput.isNotEmpty()) {
                            CopyableCryptoLine("Base64", tools.base64Output, ObTheme.colors.hit, onCopyText)
                            CopyableCryptoLine("MD5", tools.md5Output, MaterialTheme.colorScheme.primary, onCopyText)
                            CopyableCryptoLine("SHA-256", tools.sha256Output, MaterialTheme.colorScheme.onSurfaceVariant, onCopyText)
                            CopyableCryptoLine("URL Enc", tools.urlEncodedOutput, ObTheme.colors.toCheck, onCopyText)
                            CopyableCryptoLine("URL Dec", tools.urlDecodedOutput, ObTheme.colors.custom, onCopyText)
                        }
                    }
                }
            }

            ToolsSection.DIAGNOSTICS -> {
                ObPanel(backgroundColor = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Native Key Scanner (NDK C++17 / JNI)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            text = "Engine: ${NativeScannerBridge.engineBanner()}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedTextField(
                            value = tools.nativeSampleBody,
                            onValueChange = { onNativeFieldsChange(it, null, null) },
                            label = { Text("Sample HTTP Response / Line") },
                            minLines = 3,
                            colors = obOutlinedTextFieldColors(),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = tools.nativeSuccessKey,
                                onValueChange = { onNativeFieldsChange(null, it, null) },
                                label = { Text("Success Key") },
                                singleLine = true,
                                colors = obOutlinedTextFieldColors(),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = tools.nativeFailureKey,
                                onValueChange = { onNativeFieldsChange(null, null, it) },
                                label = { Text("Failure Key") },
                                singleLine = true,
                                colors = obOutlinedTextFieldColors(),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        ObBorderedButton(onClick = onRunNativeScannerTest, modifier = Modifier.testTag("tools_native_scan_btn")) {
                            Icon(Icons.Outlined.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Run Key Scan", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                        }
                        if (tools.nativeScanSummary.isNotEmpty()) {
                            Text(
                                text = tools.nativeScanSummary,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = ObTheme.colors.hit
                            )
                        }

                        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

                        Text("Network Probe (OkHttp + Retrofit)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        OutlinedTextField(
                            value = tools.probeUrl,
                            onValueChange = onProbeUrlChange,
                            label = { Text("Probe Endpoint URL") },
                            singleLine = true,
                            colors = obOutlinedTextFieldColors(),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        ObBorderedButton(
                            onClick = onRunNetworkProbe,
                            enabled = !tools.isProbing,
                            modifier = Modifier.testTag("tools_network_probe_btn")
                        ) {
                            Icon(Icons.Outlined.NetworkCheck, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (tools.isProbing) "Probing..." else "Probe Endpoint",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (tools.probeResult.isNotEmpty()) {
                            Text(
                                text = tools.probeResult,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = if (tools.probeResult.startsWith("Error")) ObTheme.colors.bad else ObTheme.colors.hit
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CopyableCryptoLine(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    onCopy: (String, String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(role = Role.Button) { onCopy(label, value) }
            .padding(vertical = 4.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label: $value",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
            color = color,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Outlined.ContentCopy,
            contentDescription = "Copy $label",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 8.dp)
                .size(16.dp)
        )
    }
}

@Composable
private fun ToolCheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(role = Role.Checkbox) { onChange(!checked) }
            .padding(horizontal = 8.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                uncheckedColor = MaterialTheme.colorScheme.outline
            )
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}
