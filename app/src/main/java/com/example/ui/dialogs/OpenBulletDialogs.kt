package com.example.ui.dialogs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.model.ActiveDialog
import com.example.model.ConfigItem
import com.example.model.ProxyProtocol
import com.example.model.WordlistItem
import com.example.network.ConfigFileCodec
import com.example.ui.components.ObBorderedButton
import com.example.ui.components.ObStatusPill
import com.example.ui.components.obOutlinedTextFieldColors
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObTheme
import com.example.ui.theme.containerColor
import com.example.ui.theme.tintColor
import com.example.util.FileExportHelper
import kotlinx.coroutines.launch

@Composable
fun OpenBulletDialogHost(
    activeDialog: ActiveDialog,
    currentStartLine: Int,
    maxStartLine: Int,
    currentBotsCount: Int,
    configs: List<ConfigItem>,
    wordlists: List<WordlistItem>,
    onDismiss: () -> Unit,
    onSelectConfig: (ConfigItem?) -> Unit,
    onSelectWordlist: (WordlistItem?) -> Unit,
    onUpdateStartLine: (Int) -> Unit,
    onUpdateBotsCount: (Int) -> Unit,
    onOpenNewConfigDialog: () -> Unit,
    onOpenAddWordlistDialog: () -> Unit,
    onCreateConfig: (name: String, author: String, category: String, wordlistType: String, needsProxies: Boolean, script: String) -> Unit,
    onImportConfigPayload: (rawText: String, fallbackName: String, openInEditor: Boolean) -> Unit,
    onImportConfigFromUrl: (url: String) -> Unit,
    onCreateWordlist: (name: String, type: String, purpose: String, rawText: String) -> Unit,
    onImportProxies: (rawText: String, protocol: ProxyProtocol) -> Unit,
    onImportProxiesFromUrl: (url: String, protocol: ProxyProtocol) -> Unit,
    onShowMessage: (String) -> Unit = {}
) {
    when (activeDialog) {
        ActiveDialog.None -> Unit
        ActiveDialog.EditStartLine -> {
            val safeMax = maxStartLine.coerceAtLeast(1)
            NumericParameterDialog(
                title = "Set Start Line (1 – $safeMax)",
                initialValue = currentStartLine.coerceIn(1, safeMax),
                range = 1..safeMax,
                onDismiss = onDismiss,
                onConfirm = {
                    onUpdateStartLine(it)
                    onDismiss()
                }
            )
        }

        ActiveDialog.EditBotsCount -> {
            NumericParameterDialog(
                title = "Set Bots Count (1 – 200)",
                initialValue = currentBotsCount,
                range = 1..200,
                onDismiss = onDismiss,
                onConfirm = {
                    onUpdateBotsCount(it)
                    onDismiss()
                }
            )
        }

        is ActiveDialog.InspectResult -> {
            val context = LocalContext.current
            val row = activeDialog.row
            val accentColor = row.category.tintColor()
            ObDialogFrame(title = "Result Inspector (${row.category.label})", onDismiss = onDismiss) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Time: ${row.time}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Proxy: ${row.proxy}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    OutlinedTextField(
                        value = row.data,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Combo / Input Data") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        colors = obOutlinedTextFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = row.capturedData,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Captured Data (CAP)") },
                        minLines = 3,
                        maxLines = 6,
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            color = accentColor
                        ),
                        colors = obOutlinedTextFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    ObBorderedButton(
                        onClick = {
                            FileExportHelper.copyToClipboard(
                                context,
                                "OpenBullet Result",
                                "${row.data} | ${row.capturedData}"
                            )
                            onShowMessage("Copied result to clipboard")
                            onDismiss()
                        },
                        isActive = true
                    ) {
                        Text(
                            "Copy Combo & Captures",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    ObBorderedButton(onClick = onDismiss) {
                        Text("Close", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        is ActiveDialog.InspectHit -> {
            val context = LocalContext.current
            val hit = activeDialog.hit
            val accentColor = hit.type.tintColor()
            ObDialogFrame(title = "Hit Details", onDismiss = onDismiss) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ObStatusPill(
                        text = hit.type.label,
                        tintColor = accentColor,
                        containerColor = hit.type.containerColor(),
                        showDot = true
                    )
                    Text(
                        text = hit.date,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "Config: ${hit.configName} • List: ${hit.wordlistName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = hit.data,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Combo Data") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    colors = obOutlinedTextFieldColors(),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = hit.capturedData,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Captured Data") },
                    minLines = 3,
                    maxLines = 6,
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        color = accentColor
                    ),
                    colors = obOutlinedTextFieldColors(),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    ObBorderedButton(
                        onClick = {
                            FileExportHelper.copyToClipboard(
                                context,
                                "OpenBullet Hit",
                                "${hit.data} | ${hit.capturedData}"
                            )
                            onShowMessage("Copied ${hit.data}")
                            onDismiss()
                        },
                        isActive = true
                    ) {
                        Text("Copy Hit", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    ObBorderedButton(onClick = onDismiss) {
                        Text("Close", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        ActiveDialog.SelectConfig -> {
            ObDialogFrame(title = "Select Config (CFG)", onDismiss = onDismiss) {
                if (configs.isEmpty()) {
                    Text(
                        text = "No configs available yet. Create one to load it into the Runner.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(8.dp))
                    ) {
                        items(configs, key = { it.id }) { cfg ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectConfig(cfg)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(cfg.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                Text(cfg.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    ObBorderedButton(
                        onClick = {
                            onSelectConfig(null)
                            onDismiss()
                        }
                    ) {
                        Text("Clear", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ObBorderedButton(onClick = onOpenNewConfigDialog) {
                        Text("Config Maker", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    ObBorderedButton(onClick = onDismiss) {
                        Text("Close", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        ActiveDialog.SelectWordlist -> {
            ObDialogFrame(title = "Select Wordlist", onDismiss = onDismiss) {
                if (wordlists.isEmpty()) {
                    Text(
                        text = "No wordlists loaded yet. Import a .txt file or paste lines.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(8.dp))
                    ) {
                        items(wordlists, key = { it.id }) { list ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectWordlist(list)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(list.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    "${list.lineCount} lines",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    ObBorderedButton(
                        onClick = {
                            onSelectWordlist(null)
                            onDismiss()
                        }
                    ) {
                        Text("Clear", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ObBorderedButton(onClick = onOpenAddWordlistDialog) {
                        Text("Add List", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    ObBorderedButton(onClick = onDismiss) {
                        Text("Close", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        ActiveDialog.AddConfig -> {
            var name by remember { mutableStateOf("") }
            var author by remember { mutableStateOf("") }
            var category by remember { mutableStateOf("Default") }
            var needsProxies by remember { mutableStateOf(true) }
            var script by remember { mutableStateOf("") }

            ObDialogFrame(title = "Create Config", onDismiss = onDismiss) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Config Name") },
                    singleLine = true,
                    colors = obOutlinedTextFieldColors(),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = author,
                        onValueChange = { author = it },
                        label = { Text("Author") },
                        singleLine = true,
                        colors = obOutlinedTextFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category") },
                        singleLine = true,
                        colors = obOutlinedTextFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { needsProxies = !needsProxies }
                ) {
                    Checkbox(
                        checked = needsProxies,
                        onCheckedChange = { needsProxies = it },
                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                    )
                    Text("Needs Proxies (DEF mode)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                }
                OutlinedTextField(
                    value = script,
                    onValueChange = { script = it },
                    label = { Text("LoliScript Notes (optional)") },
                    minLines = 3,
                    colors = obOutlinedTextFieldColors(),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    ObBorderedButton(onClick = onDismiss) {
                        Text("Cancel", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ObBorderedButton(
                        onClick = {
                            onCreateConfig(name, author, category, "Default", needsProxies, script)
                            onDismiss()
                        },
                        isActive = true
                    ) {
                        Text("Save", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }

        ActiveDialog.ImportConfig -> {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            var fallbackName by remember { mutableStateOf("") }
            var remoteUrl by remember { mutableStateOf("") }
            var payloadText by remember { mutableStateOf("") }

            val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                if (uri != null) {
                    scope.launch {
                        val loaded = FileExportHelper.readTextFromUri(context, uri, "imported_config.loli")
                        if (loaded != null) {
                            val (fileName, content) = loaded
                            payloadText = content
                            if (fallbackName.isBlank()) fallbackName = fileName
                        }
                    }
                }
            }

            val detectedFormat = remember(payloadText) { ConfigFileCodec.detectFormatLabel(payloadText) }

            ObDialogFrame(title = "Import Config (.loli / JSON)", onDismiss = onDismiss) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ObBorderedButton(onClick = { filePicker.launch(arrayOf("*/*", "application/json", "text/plain")) }) {
                        Text("Pick .loli / .json File", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        text = "Format: $detectedFormat",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = ObTheme.colors.hit
                    )
                }
                OutlinedTextField(
                    value = fallbackName,
                    onValueChange = { fallbackName = it },
                    label = { Text("Fallback Name (if not in file)") },
                    singleLine = true,
                    colors = obOutlinedTextFieldColors(),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = remoteUrl,
                        onValueChange = { remoteUrl = it },
                        label = { Text("Remote .loli / .json URL (optional)") },
                        singleLine = true,
                        colors = obOutlinedTextFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                    ObBorderedButton(
                        onClick = {
                            onImportConfigFromUrl(remoteUrl)
                            onDismiss()
                        }
                    ) {
                        Text("Fetch", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
                OutlinedTextField(
                    value = payloadText,
                    onValueChange = { payloadText = it },
                    label = { Text("Paste .loli ([SETTINGS]/[SCRIPT]), JSON, or LoliScript") },
                    minLines = 5,
                    maxLines = 8,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    colors = obOutlinedTextFieldColors(),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    ObBorderedButton(onClick = onDismiss) {
                        Text("Cancel", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ObBorderedButton(
                        onClick = {
                            onImportConfigPayload(payloadText, fallbackName.ifBlank { "Imported Config" }, true)
                            onDismiss()
                        }
                    ) {
                        Text("Import & Edit", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                    ObBorderedButton(
                        onClick = {
                            onImportConfigPayload(payloadText, fallbackName.ifBlank { "Imported Config" }, false)
                            onDismiss()
                        },
                        isActive = true
                    ) {
                        Text("Import", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }

        ActiveDialog.AddWordlist -> {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            var name by remember { mutableStateOf("") }
            var type by remember { mutableStateOf("Default") }
            var linesText by remember { mutableStateOf("") }
            val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                if (uri != null) {
                    scope.launch {
                        val loaded = FileExportHelper.readTextFromUri(context, uri, "imported.txt")
                        if (loaded != null) {
                            val (fileName, content) = loaded
                            linesText = content
                            if (name.isBlank()) name = fileName
                        }
                    }
                }
            }

            ObDialogFrame(title = "Add Wordlist", onDismiss = onDismiss) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Wordlist Name") },
                        singleLine = true,
                        colors = obOutlinedTextFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = type,
                        onValueChange = { type = it },
                        label = { Text("Type") },
                        singleLine = true,
                        colors = obOutlinedTextFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.width(120.dp)
                    )
                }
                ObBorderedButton(onClick = { filePicker.launch(arrayOf("text/plain", "*/*")) }) {
                    Text("Pick .txt File", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                OutlinedTextField(
                    value = linesText,
                    onValueChange = { linesText = it },
                    label = { Text("Paste lines (one per line)") },
                    minLines = 4,
                    maxLines = 6,
                    colors = obOutlinedTextFieldColors(),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    ObBorderedButton(onClick = onDismiss) {
                        Text("Cancel", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ObBorderedButton(
                        onClick = {
                            onCreateWordlist(name, type, "Runner", linesText)
                            onDismiss()
                        },
                        isActive = true
                    ) {
                        Text("Import", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }

        ActiveDialog.AddProxies -> {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            var protocol by remember { mutableStateOf(ProxyProtocol.HTTP) }
            var rawText by remember { mutableStateOf("") }
            var remoteUrl by remember { mutableStateOf("") }

            val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                if (uri != null) {
                    scope.launch {
                        val loaded = FileExportHelper.readTextFromUri(context, uri, "proxies.txt")
                        if (loaded != null) {
                            rawText = loaded.second
                        }
                    }
                }
            }

            ObDialogFrame(title = "Import Proxies", onDismiss = onDismiss) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProxyProtocol.entries.forEach { p ->
                        ObBorderedButton(
                            onClick = { protocol = p },
                            isActive = protocol == p,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(p.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                ObBorderedButton(onClick = { filePicker.launch(arrayOf("text/plain", "*/*")) }) {
                    Text("Pick .txt Proxy File", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = remoteUrl,
                        onValueChange = { remoteUrl = it },
                        label = { Text("Remote List URL (optional)") },
                        singleLine = true,
                        colors = obOutlinedTextFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                    ObBorderedButton(
                        onClick = {
                            onImportProxiesFromUrl(remoteUrl, protocol)
                            onDismiss()
                        }
                    ) {
                        Text("Fetch", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    label = { Text("host:port or socks5://host:port (one per line)") },
                    minLines = 4,
                    maxLines = 6,
                    colors = obOutlinedTextFieldColors(),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    ObBorderedButton(onClick = onDismiss) {
                        Text("Cancel", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ObBorderedButton(
                        onClick = {
                            onImportProxies(rawText, protocol)
                            onDismiss()
                        },
                        isActive = true
                    ) {
                        Text("Import", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }
    }
}

@Composable
private fun NumericParameterDialog(
    title: String,
    initialValue: Int,
    range: IntRange,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var text by remember { mutableStateOf(initialValue.toString()) }
    ObDialogFrame(title = title, onDismiss = onDismiss) {
        OutlinedTextField(
            value = text,
            onValueChange = { input -> text = input.filter { it.isDigit() } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = obOutlinedTextFieldColors(),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            ObBorderedButton(onClick = onDismiss) {
                Text("Cancel", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ObBorderedButton(
                onClick = {
                    val parsed = text.toIntOrNull()?.coerceIn(range) ?: initialValue
                    onConfirm(parsed)
                },
                isActive = true
            ) {
                Text("Apply", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun ObDialogFrame(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            tonalElevation = 6.dp,
            modifier = Modifier.imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                content()
            }
        }
    }
}
