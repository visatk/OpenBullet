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
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Save
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ConfigBlock
import com.example.model.ConfigDebugResult
import com.example.model.ConfigEditorState
import com.example.model.ConfigEditorSubTab
import com.example.network.ConfigTemplates
import com.example.ui.components.ObBorderedButton
import com.example.ui.components.ObPanel
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObColors

@Composable
fun ConfigEditorScreen(
    editorState: ConfigEditorState,
    usableProxyCount: Int,
    onBack: () -> Unit,
    onMetadataChange: (name: String?, author: String?, category: String?, wordlistType: String?, needsProxies: Boolean?) -> Unit,
    onApplyPreset: (ConfigTemplates.TemplatePreset) -> Unit,
    onSelectSubTab: (ConfigEditorSubTab) -> Unit,
    onAddBlock: (String) -> Unit,
    onUpdateBlock: (Int, ConfigBlock) -> Unit,
    onMoveBlock: (Int, Int) -> Unit,
    onDeleteBlock: (Int) -> Unit,
    onRawScriptChange: (String) -> Unit,
    onDebugInputChange: (testData: String?, useProxy: Boolean?) -> Unit,
    onRunDebug: () -> Unit,
    onSave: (loadInRunner: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scroll = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObColors.WorkspaceBackground)
    ) {
        // Editor Header Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObColors.ChromeBackground)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("config_editor_back")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Configs", tint = ObColors.TextPrimary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (editorState.isNew) "Config Maker" else "Config Editor",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = editorState.name.ifBlank { "Untitled Config" },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = ObColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            ObBorderedButton(
                onClick = { onSave(false) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("config_editor_save")
            ) {
                Icon(Icons.Outlined.Save, contentDescription = null, tint = ObColors.TextPrimary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Save", style = MaterialTheme.typography.labelMedium, color = ObColors.TextPrimary)
            }
            ObBorderedButton(
                onClick = { onSave(true) },
                isActive = true,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("config_editor_save_run")
            ) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = ObColors.StatusHitGreen, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Run", style = MaterialTheme.typography.labelMedium, color = ObColors.TextPrimary)
            }
        }
        HorizontalDivider(thickness = 1.dp, color = ObColors.DividerSubtle)

        // Sub-Tabs: Stacker | LoliScript | Debugger
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObColors.SurfaceTableHeader)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ConfigEditorSubTab.entries.forEach { tab ->
                val icon = when (tab) {
                    ConfigEditorSubTab.STACKER -> Icons.Outlined.Layers
                    ConfigEditorSubTab.SCRIPT -> Icons.Outlined.Code
                    ConfigEditorSubTab.DEBUGGER -> Icons.Outlined.BugReport
                }
                ObBorderedButton(
                    onClick = { onSelectSubTab(tab) },
                    isActive = editorState.activeSubTab == tab,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("editor_tab_${tab.name.lowercase()}")
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (editorState.activeSubTab == tab) MaterialTheme.colorScheme.primary else ObColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (editorState.activeSubTab == tab) ObColors.TextPrimary else ObColors.TextSecondary,
                        maxLines = 1
                    )
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = ObColors.DividerSubtle)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Metadata & Starter Presets Panel
            ObPanel(backgroundColor = ObColors.SurfacePanel, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = editorState.name,
                            onValueChange = { onMetadataChange(it, null, null, null, null) },
                            label = { Text("Config Name") },
                            singleLine = true,
                            colors = editorFieldColors(),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1.3f)
                        )
                        OutlinedTextField(
                            value = editorState.category,
                            onValueChange = { onMetadataChange(null, null, it, null, null) },
                            label = { Text("Category") },
                            singleLine = true,
                            colors = editorFieldColors(),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(0.9f)
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = editorState.author,
                            onValueChange = { onMetadataChange(null, it, null, null, null) },
                            label = { Text("Author") },
                            singleLine = true,
                            colors = editorFieldColors(),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = editorState.wordlistType,
                            onValueChange = { onMetadataChange(null, null, null, it, null) },
                            label = { Text("Wordlist Type") },
                            singleLine = true,
                            colors = editorFieldColors(),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onMetadataChange(null, null, null, null, !editorState.needsProxies) }
                        ) {
                            Checkbox(
                                checked = editorState.needsProxies,
                                onCheckedChange = { onMetadataChange(null, null, null, null, it) },
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                            )
                            Text("Requires Proxies (DEF mode)", style = MaterialTheme.typography.bodySmall, color = ObColors.TextPrimary)
                        }
                    }

                    Text("Starter Templates", style = MaterialTheme.typography.labelSmall, color = ObColors.TextSecondary)
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
                                Icon(Icons.Outlined.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(kind, style = MaterialTheme.typography.labelMedium, color = ObColors.TextPrimary)
                            }
                        }
                    }

                    if (editorState.blocks.isEmpty()) {
                        ObPanel(backgroundColor = ObColors.SurfaceRecessed, modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Stacker is empty", style = MaterialTheme.typography.titleSmall, color = ObColors.TextSecondary)
                                Text("Add a REQUEST, PARSE, KEYCHECK, or FUNCTION block above.", style = MaterialTheme.typography.bodySmall, color = ObColors.TextMuted)
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
                    Text("Quick Snippets", style = MaterialTheme.typography.labelSmall, color = ObColors.TextSecondary)
                    val snippets = listOf(
                        "REQUEST GET" to "\nREQUEST GET \"https://httpbin.org/get?user=<USER>\"\nHEADER \"Accept: application/json\"",
                        "REQUEST POST" to "\nREQUEST POST \"https://httpbin.org/post\"\nCONTENT_TYPE \"application/json\"\nCONTENT \"{\\\"user\\\":\\\"<USER>\\\",\\\"pass\\\":\\\"<PASS>\\\"}\"",
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
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("config_raw_script_field")
                    )
                }

                ConfigEditorSubTab.DEBUGGER -> {
                    ObPanel(backgroundColor = ObColors.SurfacePanel, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Live Config Debugger", style = MaterialTheme.typography.titleSmall, color = ObColors.TextPrimary)
                            OutlinedTextField(
                                value = editorState.testDataInput,
                                onValueChange = { onDebugInputChange(it, null) },
                                label = { Text("Test Combo Line (<USER>:<PASS>)") },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                colors = editorFieldColors(),
                                shape = RoundedCornerShape(6.dp),
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
                                        color = ObColors.TextPrimary
                                    )
                                }

                                ObBorderedButton(
                                    onClick = onRunDebug,
                                    isActive = true,
                                    modifier = Modifier.testTag("config_debug_run_button")
                                ) {
                                    if (editorState.isDebugging) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                                    } else {
                                        Icon(Icons.Outlined.BugReport, contentDescription = null, tint = ObColors.StatusHitGreen, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (editorState.isDebugging) "Testing..." else "Test Config", style = MaterialTheme.typography.labelLarge, color = ObColors.TextPrimary)
                                }
                            }
                        }
                    }

                    editorState.debugResult?.let { result ->
                        DebugResultPanel(result = result)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DebugResultPanel(result: ConfigDebugResult) {
    val outcomeColor = when (result.outcomeType) {
        "HIT" -> ObColors.StatusHitGreen
        "CUSTOM" -> ObColors.StatusCustomOrange
        "BAD" -> ObColors.StatusBadRed
        "RETRY" -> ObColors.StatusRetryYellow
        else -> ObColors.StatusToCheckAqua
    }

    ObPanel(
        backgroundColor = ObColors.SurfaceRecessed,
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
                    shape = RoundedCornerShape(4.dp),
                    color = outcomeColor.copy(alpha = 0.16f),
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
                    color = ObColors.TextSecondary
                )
            }

            if (result.captures.isNotEmpty()) {
                Text("Captures (CAP)", style = MaterialTheme.typography.labelSmall, color = ObColors.StatusHitGreen)
                result.captures.forEach { (k, v) ->
                    Text(
                        text = "$k = $v",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = ObColors.TextPrimary
                    )
                }
            }

            if (result.traceLogs.isNotEmpty()) {
                HorizontalDivider(thickness = 1.dp, color = ObColors.DividerSubtle)
                Text("Execution Trace", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                result.traceLogs.forEach { log ->
                    Text(
                        text = log,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = ObColors.TextSecondary
                    )
                }
            }

            if (result.responseBodyPreview.isNotBlank()) {
                HorizontalDivider(thickness = 1.dp, color = ObColors.DividerSubtle)
                Text("Response Body Preview", style = MaterialTheme.typography.labelSmall, color = ObColors.TextSecondary)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ObColors.ChromeBackground, RoundedCornerShape(6.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = result.responseBodyPreview.take(900),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = ObColors.TextPrimary
                    )
                }
            }
        }
    }
}
