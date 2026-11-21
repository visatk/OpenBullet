package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ConfigBlock
import com.example.model.ConfigDebugResult
import com.example.model.ConfigEditorState
import com.example.model.ConfigEditorSubTab
import com.example.network.ConfigTemplates
import com.example.network.LoliScriptParser
import com.example.ui.components.ObBorderedButton
import com.example.ui.components.ObConfirmDialog
import com.example.ui.components.ObPanel
import com.example.ui.components.ObSegmentedControl
import com.example.ui.components.ObStatusPill
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObTheme
import com.example.ui.theme.outcomeContainerColor
import com.example.ui.theme.outcomeTintColor
import com.example.util.FileExportHelper

@Composable
fun ConfigEditorScreen(
    editorState: ConfigEditorState,
    usableProxyCount: Int,
    onBack: () -> Unit,
    onMetadataChange: (
        name: String?,
        author: String?,
        category: String?,
        wordlistType: String?,
        needsProxies: Boolean?,
        version: String?,
        description: String?
    ) -> Unit,
    onExecutionSettingsChange: (
        suggestedBots: Int?,
        maxRetries: Int?,
        timeoutSec: Int?,
        customUserAgent: String?,
        maxProxyUses: Int?
    ) -> Unit = { _, _, _, _, _ -> },
    onApplyPreset: (ConfigTemplates.TemplatePreset) -> Unit,
    onSelectSubTab: (ConfigEditorSubTab) -> Unit,
    onAddBlock: (String) -> Unit,
    onUpdateBlock: (Int, ConfigBlock) -> Unit,
    onMoveBlock: (Int, Int) -> Unit,
    onDeleteBlock: (Int) -> Unit,
    onRawScriptChange: (String) -> Unit,
    onDebugInputChange: (testData: String?, useProxy: Boolean?) -> Unit,
    onRunDebug: () -> Unit,
    onExportLoli: () -> Unit,
    onSave: (loadInRunner: Boolean) -> Unit,
    onCopyFeedback: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scroll = rememberScrollState()
    var confirmDiscard by remember { mutableStateOf(false) }

    val handleBack = {
        if (editorState.hasUnsavedChanges) {
            confirmDiscard = true
        } else {
            onBack()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Editor Header Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = handleBack,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("config_editor_back")
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Configs",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = buildString {
                        append(if (editorState.isNew) "Multi-Part Config Maker" else "Multi-Part Config Editor")
                        append(" • v${editorState.version.ifBlank { "1.0" }}")
                        if (editorState.hasUnsavedChanges) append(" • Unsaved")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (editorState.hasUnsavedChanges) ObTheme.colors.custom else MaterialTheme.colorScheme.primary
                )
                Text(
                    text = editorState.name.ifBlank { "Untitled Config" },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            ObBorderedButton(
                onClick = onExportLoli,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.testTag("config_editor_export_loli")
            ) {
                Icon(
                    Icons.Outlined.Share,
                    contentDescription = "Export .loli",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(".loli", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            ObBorderedButton(
                onClick = { onSave(false) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("config_editor_save")
            ) {
                Icon(
                    Icons.Outlined.Save,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Save", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            ObBorderedButton(
                onClick = { onSave(true) },
                isActive = true,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("config_editor_save_run")
            ) {
                Icon(
                    Icons.Outlined.PlayArrow,
                    contentDescription = null,
                    tint = ObTheme.colors.hit,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Run", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

        // Sub-Tabs: Stacker | LoliScript | Debugger
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            ObSegmentedControl(
                options = ConfigEditorSubTab.entries,
                selectedOption = editorState.activeSubTab,
                labelProvider = { it.label },
                onSelect = onSelectSubTab,
                modifier = Modifier.fillMaxWidth(),
                testTagPrefix = "editor_tab"
            )
        }
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Multi-Part Config Panel: Part 1 (Metadata) + Part 2 (Execution Settings)
            ObPanel(backgroundColor = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Part 1 • Config Metadata",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        ObStatusPill(
                            text = "DoP: ${editorState.suggestedBots} • ${editorState.timeoutSec}s • ${editorState.maxRetries} retries",
                            tintColor = MaterialTheme.colorScheme.primary,
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = editorState.name,
                            onValueChange = { onMetadataChange(it, null, null, null, null, null, null) },
                            label = { Text("Config Name") },
                            singleLine = true,
                            colors = editorFieldColors(),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1.3f)
                        )
                        OutlinedTextField(
                            value = editorState.category,
                            onValueChange = { onMetadataChange(null, null, it, null, null, null, null) },
                            label = { Text("Category") },
                            singleLine = true,
                            colors = editorFieldColors(),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(0.85f)
                        )
                        OutlinedTextField(
                            value = editorState.version,
                            onValueChange = { onMetadataChange(null, null, null, null, null, it, null) },
                            label = { Text("Version") },
                            singleLine = true,
                            colors = editorFieldColors(),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(0.55f)
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = editorState.author,
                            onValueChange = { onMetadataChange(null, it, null, null, null, null, null) },
                            label = { Text("Author") },
                            singleLine = true,
                            colors = editorFieldColors(),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = editorState.wordlistType,
                            onValueChange = { onMetadataChange(null, null, null, it, null, null, null) },
                            label = { Text("Wordlist Type") },
                            singleLine = true,
                            colors = editorFieldColors(),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = editorState.description,
                        onValueChange = { onMetadataChange(null, null, null, null, null, null, it) },
                        label = { Text("Description / Notes") },
                        singleLine = true,
                        colors = editorFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        "Part 2 • Execution Settings & Parallelization (DoP)",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = editorState.suggestedBots.toString(),
                            onValueChange = { raw ->
                                val parsed = raw.filter { it.isDigit() }.toIntOrNull()
                                if (parsed != null) onExecutionSettingsChange(parsed, null, null, null, null)
                            },
                            label = { Text("Suggested DoP") },
                            singleLine = true,
                            colors = editorFieldColors(),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = editorState.timeoutSec.toString(),
                            onValueChange = { raw ->
                                val parsed = raw.filter { it.isDigit() }.toIntOrNull()
                                if (parsed != null) onExecutionSettingsChange(null, null, parsed, null, null)
                            },
                            label = { Text("Timeout (s)") },
                            singleLine = true,
                            colors = editorFieldColors(),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = editorState.maxRetries.toString(),
                            onValueChange = { raw ->
                                val parsed = raw.filter { it.isDigit() }.toIntOrNull()
                                if (parsed != null) onExecutionSettingsChange(null, parsed, null, null, null)
                            },
                            label = { Text("Max Retries") },
                            singleLine = true,
                            colors = editorFieldColors(),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = editorState.maxProxyUses.toString(),
                            onValueChange = { raw ->
                                val parsed = raw.filter { it.isDigit() }.toIntOrNull()
                                if (parsed != null) onExecutionSettingsChange(null, null, null, null, parsed)
                            },
                            label = { Text("Max Proxy Uses") },
                            singleLine = true,
                            colors = editorFieldColors(),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = editorState.customUserAgent,
                            onValueChange = { onExecutionSettingsChange(null, null, null, it, null) },
                            label = { Text("Custom User-Agent (optional)") },
                            singleLine = true,
                            colors = editorFieldColors(),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                onMetadataChange(null, null, null, null, !editorState.needsProxies, null, null)
                            }
                        ) {
                            Checkbox(
                                checked = editorState.needsProxies,
                                onCheckedChange = { onMetadataChange(null, null, null, null, it, null, null) },
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                            )
                            Text(
                                "Needs Proxies",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Text("Starter Templates", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ConfigTemplates.presets.forEach { preset ->
                            ObBorderedButton(
                                onClick = { onApplyPreset(preset) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(preset.title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            when (editorState.activeSubTab) {
                ConfigEditorSubTab.STACKER -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("REQUEST", "PARSE", "KEYCHECK", "FUNCTION").forEach { kind ->
                            ObBorderedButton(
                                onClick = { onAddBlock(kind) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("add_block_${kind.lowercase()}")
                            ) {
                                Icon(
                                    Icons.Outlined.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(kind, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }

                    if (editorState.blocks.isEmpty()) {
                        ObPanel(backgroundColor = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Stacker is empty", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    "Add a REQUEST, PARSE, KEYCHECK, or FUNCTION block above.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        editorState.blocks.forEachIndexed { idx, block ->
                            ConfigBlockCard(
                                index = idx,
                                totalCount = editorState.blocks.size,
                                block = block,
                                onUpdate = { onUpdateBlock(idx, it) },
                                onMoveUp = { onMoveBlock(idx, idx - 1) },
                                onMoveDown = { onMoveBlock(idx, idx + 1) },
                                onDelete = { onDeleteBlock(idx) }
                            )
                        }
                    }
                }

                ConfigEditorSubTab.SCRIPT -> {
                    val parsedBlockCount = remember(editorState.rawScript) {
                        LoliScriptParser.parseToBlocks(editorState.rawScript).size
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Part 3 • Script Logic & Quick Snippets", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ObStatusPill(
                            text = "$parsedBlockCount parsed blocks",
                            tintColor = if (parsedBlockCount > 0) ObTheme.colors.hit else ObTheme.colors.custom,
                            containerColor = if (parsedBlockCount > 0) ObTheme.colors.hitContainer else ObTheme.colors.customContainer
                        )
                    }
                    val snippets = listOf(
                        "REQUEST GET" to "\nREQUEST GET \"https://httpbin.org/get?user=<USER>\"\nHEADER \"Accept: application/json\"",
                        "REQUEST POST" to "\nREQUEST POST \"https://httpbin.org/post\"\nCONTENT_TYPE \"application/json\"\nCONTENT \"{\\\"user\\\":\\\"<USER>\\\",\\\"pass\\\":\\\"<PASS>\\\"}\"",
                        "REQUEST TCP" to "\nREQUEST TCP \"tcp://httpbin.org:80\"\nCONTENT \"HEAD / HTTP/1.1\\r\\nHost: httpbin.org\\r\\nConnection: close\\r\\n\\r\\n\"",
                        "KEYCHECK" to "\nKEYCHECK\n  KEYCHAIN Success OR\n    KEY \"HTTP/200\"\n  KEYCHAIN Failure OR\n    KEY \"Unauthorized\"",
                        "PARSE LR" to "\nPARSE \"<SOURCE>\" LR \"\\\"origin\\\": \\\"\" \"\\\"\" -> CAP \"Origin\"",
                        "PARSE JSON" to "\nPARSE \"<SOURCE>\" JSON \"origin\" -> CAP \"Origin\"",
                        "FUNCTION" to "\nFUNCTION BASE64ENCODE \"<USER>:<PASS>\" -> VAR \"TOKEN\""
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        snippets.forEach { (label, code) ->
                            ObBorderedButton(
                                onClick = { onRawScriptChange(editorState.rawScript.trimEnd() + code) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("+ $label", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = editorState.rawScript,
                        onValueChange = onRawScriptChange,
                        label = { Text("LoliScript Code") },
                        minLines = 12,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        colors = editorFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("config_raw_script_field")
                    )
                }

                ConfigEditorSubTab.DEBUGGER -> {
                    ObPanel(backgroundColor = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Live Config Debugger", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                            OutlinedTextField(
                                value = editorState.testDataInput,
                                onValueChange = { onDebugInputChange(it, null) },
                                label = { Text("Test Combo Line (<USER>:<PASS>)") },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                colors = editorFieldColors(),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { onDebugInputChange(null, !editorState.debugUseProxy) }
                                ) {
                                    Checkbox(
                                        checked = editorState.debugUseProxy,
                                        onCheckedChange = { onDebugInputChange(null, it) },
                                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                    )
                                    Text(
                                        text = "Use Proxy ($usableProxyCount usable)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                ObBorderedButton(
                                    onClick = onRunDebug,
                                    isActive = true,
                                    enabled = !editorState.isDebugging,
                                    modifier = Modifier.testTag("config_debug_run_button")
                                ) {
                                    if (editorState.isDebugging) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        Icon(
                                            Icons.Outlined.BugReport,
                                            contentDescription = null,
                                            tint = ObTheme.colors.hit,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        if (editorState.isDebugging) "Testing..." else "Test Config",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }

                    editorState.debugResult?.let { result ->
                        DebugResultPanel(result = result, onCopyFeedback = onCopyFeedback)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirmDiscard) {
        ObConfirmDialog(
            title = "Discard Unsaved Changes?",
            message = "You have modified '${editorState.name.ifBlank { "Untitled Config" }}'. Leave without saving?",
            confirmLabel = "Discard",
            onConfirm = {
                confirmDiscard = false
                onBack()
            },
            onDismiss = { confirmDiscard = false }
        )
    }
}

@Composable
private fun DebugResultPanel(
    result: ConfigDebugResult,
    onCopyFeedback: (String) -> Unit
) {
    val context = LocalContext.current
    val outcomeColor = outcomeTintColor(result.outcomeType)
    val outcomeBg = outcomeContainerColor(result.outcomeType)

    ObPanel(
        backgroundColor = MaterialTheme.colorScheme.surfaceContainerLow,
        borderColor = outcomeColor.copy(alpha = 0.65f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = outcomeBg,
                    border = BorderStroke(1.dp, outcomeColor)
                ) {
                    Text(
                        text = result.statusLabel,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = outcomeColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = "${result.latencyMs}ms • Proxy: ${result.proxyUsed}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (result.captures.isNotEmpty()) {
                Text("Captures (CAP)", style = MaterialTheme.typography.labelSmall, color = ObTheme.colors.hit)
                result.captures.forEach { (k, v) ->
                    Text(
                        text = "$k = $v",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (result.variables.isNotEmpty()) {
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                Text("Variables (VAR)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                result.variables.entries
                    .filter { it.key != "SOURCE" || it.value.isNotEmpty() }
                    .forEach { (k, v) ->
                        val displayVal = if (k == "SOURCE") "${v.take(60)} (${v.length} chars)" else v.take(120)
                        Text(
                            text = "<$k> = $displayVal",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
            }

            if (result.traceLogs.isNotEmpty()) {
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                Text("Execution Trace", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                result.traceLogs.forEach { log ->
                    Text(
                        text = log,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (result.responseBodyPreview.isNotBlank()) {
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Response Body Preview",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        onClick = {
                            FileExportHelper.copyToClipboard(context, "Debug Response", result.responseBodyPreview)
                            onCopyFeedback("Copied response preview")
                        },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            Icons.Outlined.ContentCopy,
                            contentDescription = "Copy response preview",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ObTheme.colors.codeBackground, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = result.responseBodyPreview.take(900),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
