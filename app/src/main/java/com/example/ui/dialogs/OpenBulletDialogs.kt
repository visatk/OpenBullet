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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.ui.components.ObBorderedButton
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObColors

@Composable
fun OpenBulletDialogHost(
    activeDialog: ActiveDialog,
    currentStartLine: Int,
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
    onCreateWordlist: (name: String, type: String, purpose: String, rawText: String) -> Unit,
    onImportProxies: (rawText: String, protocol: ProxyProtocol) -> Unit,
    onImportProxiesFromUrl: (url: String, protocol: ProxyProtocol) -> Unit
) {
    when (activeDialog) {
        ActiveDialog.None -> Unit
        ActiveDialog.EditStartLine -> {
            NumericParameterDialog(
                title = "Set Start Line (1 – 1000)",
                initialValue = currentStartLine,
                range = 1..1000,
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

        ActiveDialog.SelectConfig -> {
            ObDialogFrame(title = "Select Config (CFG)", onDismiss = onDismiss) {
                if (configs.isEmpty()) {
                    Text(
                        text = "No configs available yet. Create one to load it into the Runner.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ObColors.TextSecondary
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .border(1.dp, ObColors.BorderHairline, RoundedCornerShape(6.dp))
                            .background(ObColors.SurfaceRecessed, RoundedCornerShape(6.dp))
                    ) {
                        items(configs, key = { it.id }) { cfg ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectConfig(cfg) }
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(cfg.name, style = MaterialTheme.typography.bodyMedium, color = ObColors.TextPrimary)
                                Text(cfg.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                            HorizontalDivider(thickness = 0.5.dp, color = ObColors.DividerSubtle)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    ObBorderedButton(onClick = { onSelectConfig(null) }) {
                        Text("Clear", style = MaterialTheme.typography.labelMedium, color = ObColors.TextSecondary)
                    }
                    ObBorderedButton(onClick = onOpenNewConfigDialog) {
                        Text("Open Config Maker", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    ObBorderedButton(onClick = onDismiss) {
                        Text("Close", style = MaterialTheme.typography.labelMedium, color = ObColors.TextPrimary)
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
                        color = ObColors.TextSecondary
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .border(1.dp, ObColors.BorderHairline, RoundedCornerShape(6.dp))
                            .background(ObColors.SurfaceRecessed, RoundedCornerShape(6.dp))
                    ) {
                        items(wordlists, key = { it.id }) { list ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectWordlist(list) }
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(list.name, style = MaterialTheme.typography.bodyMedium, color = ObColors.TextPrimary)
                                Text("${list.lineCount} lines", style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily), color = MaterialTheme.colorScheme.primary)
                            }
                            HorizontalDivider(thickness = 0.5.dp, color = ObColors.DividerSubtle)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    ObBorderedButton(onClick = { onSelectWordlist(null) }) {
                        Text("Clear", style = MaterialTheme.typography.labelMedium, color = ObColors.TextSecondary)
                    }
                    ObBorderedButton(onClick = onOpenAddWordlistDialog) {
                        Text("Add List", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    ObBorderedButton(onClick = onDismiss) {
                        Text("Close", style = MaterialTheme.typography.labelMedium, color = ObColors.TextPrimary)
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
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Config Name") }, singleLine = true, colors = dialogFieldColors(), shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = author, onValueChange = { author = it }, label = { Text("Author") }, singleLine = true, colors = dialogFieldColors(), shape = RoundedCornerShape(6.dp), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, singleLine = true, colors = dialogFieldColors(), shape = RoundedCornerShape(6.dp), modifier = Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { needsProxies = !needsProxies }) {
                    Checkbox(checked = needsProxies, onCheckedChange = { needsProxies = it }, colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary))
                    Text("Needs Proxies (DEF mode)", style = MaterialTheme.typography.bodySmall, color = ObColors.TextPrimary)
                }
                OutlinedTextField(value = script, onValueChange = { script = it }, label = { Text("LoliScript Notes (optional)") }, minLines = 3, colors = dialogFieldColors(), shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth())
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    ObBorderedButton(onClick = onDismiss) { Text("Cancel", style = MaterialTheme.typography.labelMedium, color = ObColors.TextSecondary) }
                    ObBorderedButton(onClick = { onCreateConfig(name, author, category, "Default", needsProxies, script) }) {
                        Text("Save", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        ActiveDialog.AddWordlist -> {
            val context = LocalContext.current
            var name by remember { mutableStateOf("") }
            var type by remember { mutableStateOf("Default") }
            var linesText by remember { mutableStateOf("") }
            val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                if (uri != null) {
                    val content = runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    }.getOrNull().orEmpty()
                    if (content.isNotEmpty()) {
                        linesText = content
                        if (name.isBlank()) {
                            name = uri.lastPathSegment?.substringAfterLast('/') ?: "imported.txt"
                        }
                    }
                }
            }

            ObDialogFrame(title = "Add Wordlist", onDismiss = onDismiss) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Wordlist Name") }, singleLine = true, colors = dialogFieldColors(), shape = RoundedCornerShape(6.dp), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text("Type") }, singleLine = true, colors = dialogFieldColors(), shape = RoundedCornerShape(6.dp), modifier = Modifier.width(120.dp))
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
                    colors = dialogFieldColors(),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    ObBorderedButton(onClick = onDismiss) { Text("Cancel", style = MaterialTheme.typography.labelMedium, color = ObColors.TextSecondary) }
                    ObBorderedButton(onClick = { onCreateWordlist(name, type, "Runner", linesText) }) {
                        Text("Import", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        ActiveDialog.AddProxies -> {
            var protocol by remember { mutableStateOf(ProxyProtocol.HTTP) }
            var rawText by remember { mutableStateOf("") }
            var remoteUrl by remember { mutableStateOf("") }

            ObDialogFrame(title = "Import Proxies", onDismiss = onDismiss) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProxyProtocol.entries.forEach { p ->
                        ObBorderedButton(
                            onClick = { protocol = p },
                            isActive = protocol == p,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(p.label, style = MaterialTheme.typography.labelMedium, color = ObColors.TextPrimary)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = remoteUrl,
                        onValueChange = { remoteUrl = it },
                        label = { Text("Remote List URL (optional)") },
                        singleLine = true,
                        colors = dialogFieldColors(),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f)
                    )
                    ObBorderedButton(onClick = { onImportProxiesFromUrl(remoteUrl, protocol) }) {
                        Text("Fetch", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    label = { Text("host:port (one per line)") },
                    minLines = 4,
                    maxLines = 6,
                    colors = dialogFieldColors(),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    ObBorderedButton(onClick = onDismiss) { Text("Cancel", style = MaterialTheme.typography.labelMedium, color = ObColors.TextSecondary) }
                    ObBorderedButton(onClick = { onImportProxies(rawText, protocol) }) {
                        Text("Import", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
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
            colors = dialogFieldColors(),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            ObBorderedButton(onClick = onDismiss) {
                Text("Cancel", style = MaterialTheme.typography.labelMedium, color = ObColors.TextSecondary)
            }
            ObBorderedButton(onClick = {
                val parsed = text.toIntOrNull()?.coerceIn(range) ?: initialValue
                onConfirm(parsed)
            }) {
                Text("Apply", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
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
            shape = RoundedCornerShape(10.dp),
            color = ObColors.SurfacePanel,
            border = BorderStroke(1.dp, ObColors.BorderBright),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = ObColors.TextPrimary
                )
                HorizontalDivider(thickness = 1.dp, color = ObColors.DividerSubtle)
                content()
            }
        }
    }
}

@Composable
private fun dialogFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = ObColors.BorderHairline,
    focusedContainerColor = ObColors.SurfaceRecessed,
    unfocusedContainerColor = ObColors.SurfaceRecessed,
    focusedTextColor = ObColors.TextPrimary,
    unfocusedTextColor = ObColors.TextPrimary,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = ObColors.TextSecondary
)
