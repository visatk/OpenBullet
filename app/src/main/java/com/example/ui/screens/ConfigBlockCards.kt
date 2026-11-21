package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.ConfigBlock
import com.example.model.ParseMode
import com.example.model.ScriptFunctionType
import com.example.ui.components.ObBorderedButton
import com.example.ui.components.ObPanel
import com.example.ui.components.obOutlinedTextFieldColors
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObTheme

@Composable
fun ConfigBlockCard(
    index: Int,
    totalCount: Int,
    block: ConfigBlock,
    onUpdate: (ConfigBlock) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    val (badgeText, accentColor) = when (block) {
        is ConfigBlock.RequestBlock -> "REQUEST ${block.method}" to MaterialTheme.colorScheme.primary
        is ConfigBlock.ParseBlock -> "PARSE ${block.mode.label}" to ObTheme.colors.custom
        is ConfigBlock.KeyCheckBlock -> "KEYCHECK" to ObTheme.colors.hit
        is ConfigBlock.FunctionBlock -> "FUNCTION ${block.functionType.loliKeyword}" to ObTheme.colors.banned
    }

    ObPanel(
        backgroundColor = MaterialTheme.colorScheme.surfaceContainer,
        borderColor = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = accentColor.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.55f))
                ) {
                    Text(
                        text = "#${index + 1} $badgeText",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                if (index > 0) {
                    IconButton(onClick = onMoveUp, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(
                            Icons.Outlined.ArrowUpward,
                            contentDescription = "Move block up",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (index < totalCount - 1) {
                    IconButton(onClick = onMoveDown, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(
                            Icons.Outlined.ArrowDownward,
                            contentDescription = "Move block down",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete block",
                        tint = ObTheme.colors.bad,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (block) {
                    is ConfigBlock.RequestBlock -> RequestBlockFields(block, onUpdate)
                    is ConfigBlock.ParseBlock -> ParseBlockFields(block, onUpdate)
                    is ConfigBlock.KeyCheckBlock -> KeyCheckBlockFields(block, onUpdate)
                    is ConfigBlock.FunctionBlock -> FunctionBlockFields(block, onUpdate)
                }
            }
        }
    }
}

@Composable
private fun RequestBlockFields(
    block: ConfigBlock.RequestBlock,
    onUpdate: (ConfigBlock) -> Unit
) {
    val methods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "TCP")
    val isTcp = block.method.equals("TCP", ignoreCase = true) || block.url.startsWith("tcp://", ignoreCase = true)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        methods.forEach { m ->
            ObBorderedButton(
                onClick = {
                    val updatedUrl = if (m == "TCP" && !block.url.startsWith("tcp://", ignoreCase = true)) {
                        "tcp://example.com:80"
                    } else block.url
                    onUpdate(block.copy(method = m, url = updatedUrl))
                },
                isActive = block.method.equals(m, ignoreCase = true),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(m, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }

    OutlinedTextField(
        value = block.url,
        onValueChange = { onUpdate(block.copy(url = it)) },
        label = {
            Text(
                if (isTcp) "Target TCP Endpoint (tcp://host:port)"
                else "Target URL (supports <USER>, <PASS>, <VAR>)"
            )
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
        colors = editorFieldColors(),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    )

    if (!isTcp) {
        OutlinedTextField(
            value = block.headersText,
            onValueChange = { onUpdate(block.copy(headersText = it)) },
            label = { Text("Headers (Key: Value per line)") },
            minLines = 2,
            maxLines = 4,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
            colors = editorFieldColors(),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (block.method.uppercase() in setOf("POST", "PUT", "PATCH", "DELETE", "TCP") || isTcp) {
        if (!isTcp) {
            OutlinedTextField(
                value = block.contentType,
                onValueChange = { onUpdate(block.copy(contentType = it)) },
                label = { Text("Content-Type") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                colors = editorFieldColors(),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        OutlinedTextField(
            value = block.postBody,
            onValueChange = { onUpdate(block.copy(postBody = it)) },
            label = {
                Text(
                    if (isTcp) "Raw TCP Payload (supports <USER>, <PASS>, \\r\\n)"
                    else "Request Body"
                )
            },
            minLines = 2,
            maxLines = 4,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
            colors = editorFieldColors(),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (!isTcp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onUpdate(block.copy(followRedirects = !block.followRedirects)) }
        ) {
            Checkbox(
                checked = block.followRedirects,
                onCheckedChange = { onUpdate(block.copy(followRedirects = it)) },
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
            )
            Text("Follow HTTP Redirects", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun ParseBlockFields(
    block: ConfigBlock.ParseBlock,
    onUpdate: (ConfigBlock) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ParseMode.entries.forEach { mode ->
            ObBorderedButton(
                onClick = { onUpdate(block.copy(mode = mode)) },
                isActive = block.mode == mode,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(mode.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        Spacer(Modifier.weight(1f))
        ObBorderedButton(
            onClick = { onUpdate(block.copy(isCapture = !block.isCapture)) },
            isActive = block.isCapture,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = if (block.isCapture) "CAP (Save)" else "VAR (Internal)",
                style = MaterialTheme.typography.labelMedium,
                color = if (block.isCapture) ObTheme.colors.hit else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = block.outputName,
            onValueChange = { onUpdate(block.copy(outputName = it)) },
            label = { Text("Variable / Capture Name") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
            colors = editorFieldColors(),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = block.sourceVar,
            onValueChange = { onUpdate(block.copy(sourceVar = it)) },
            label = { Text("Source") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
            colors = editorFieldColors(),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.weight(0.55f)
        )
    }

    OutlinedTextField(
        value = block.leftOrPattern,
        onValueChange = { onUpdate(block.copy(leftOrPattern = it)) },
        label = {
            Text(
                when (block.mode) {
                    ParseMode.LR -> "Left Delimiter"
                    ParseMode.REGEX -> "Regex Pattern"
                    ParseMode.JSON -> "JSON Path (e.g. data.plan or items[0].id)"
                }
            )
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
        colors = editorFieldColors(),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    )

    if (block.mode == ParseMode.LR) {
        OutlinedTextField(
            value = block.right,
            onValueChange = { onUpdate(block.copy(right = it)) },
            label = { Text("Right Delimiter") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
            colors = editorFieldColors(),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun KeyCheckBlockFields(
    block: ConfigBlock.KeyCheckBlock,
    onUpdate: (ConfigBlock) -> Unit
) {
    KeyChainFieldEditor(
        blockId = block.id,
        label = "Success Keys (comma-separated -> HIT)",
        keys = block.successKeys,
        accentColor = ObTheme.colors.hit,
        onKeysChanged = { onUpdate(block.copy(successKeys = it)) }
    )
    KeyChainFieldEditor(
        blockId = block.id,
        label = "Custom Keys (comma-separated -> CUSTOM)",
        keys = block.customKeys,
        accentColor = ObTheme.colors.custom,
        onKeysChanged = { onUpdate(block.copy(customKeys = it)) }
    )
    KeyChainFieldEditor(
        blockId = block.id,
        label = "Failure Keys (comma-separated -> BAD)",
        keys = block.failureKeys,
        accentColor = ObTheme.colors.bad,
        onKeysChanged = { onUpdate(block.copy(failureKeys = it)) }
    )
    KeyChainFieldEditor(
        blockId = block.id,
        label = "Retry / Ban Keys (comma-separated -> RETRY & Rotate Proxy)",
        keys = block.retryKeys,
        accentColor = ObTheme.colors.retry,
        onKeysChanged = { onUpdate(block.copy(retryKeys = it)) }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeyChainFieldEditor(
    blockId: String,
    label: String,
    keys: List<String>,
    accentColor: Color,
    onKeysChanged: (List<String>) -> Unit
) {
    fun splitKeys(raw: String): List<String> =
        raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    // Keep raw text buffer so typing a trailing comma never gets stripped before the next key is typed
    var textBuffer by remember(blockId) { mutableStateOf(keys.joinToString(", ")) }
    LaunchedEffect(keys) {
        if (splitKeys(textBuffer) != keys) {
            textBuffer = keys.joinToString(", ")
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = textBuffer,
            onValueChange = { raw ->
                textBuffer = raw
                onKeysChanged(splitKeys(raw))
            },
            label = { Text(label) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
            colors = editorFieldColors(accentColor),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        )
        if (keys.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                keys.forEachIndexed { idx, key ->
                    InputChip(
                        selected = true,
                        onClick = {
                            val next = keys.toMutableList().apply { removeAt(idx) }
                            textBuffer = next.joinToString(", ")
                            onKeysChanged(next)
                        },
                        label = {
                            Text(
                                text = key,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily)
                            )
                        },
                        trailingIcon = {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Remove key",
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = InputChipDefaults.inputChipColors(
                            selectedContainerColor = accentColor.copy(alpha = 0.14f),
                            selectedLabelColor = accentColor,
                            selectedTrailingIconColor = accentColor
                        ),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
                    )
                }
            }
        }
    }
}

@Composable
private fun FunctionBlockFields(
    block: ConfigBlock.FunctionBlock,
    onUpdate: (ConfigBlock) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ScriptFunctionType.entries.forEach { fn ->
            ObBorderedButton(
                onClick = { onUpdate(block.copy(functionType = fn)) },
                isActive = block.functionType == fn,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(fn.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
    OutlinedTextField(
        value = block.inputTemplate,
        onValueChange = { onUpdate(block.copy(inputTemplate = it)) },
        label = { Text("Input Template (e.g. <USER>:<PASS>)") },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
        colors = editorFieldColors(),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = block.outputVar,
        onValueChange = { onUpdate(block.copy(outputVar = it)) },
        label = { Text("Output Variable Name (used as <NAME>)") },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
        colors = editorFieldColors(),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun editorFieldColors(labelAccent: Color? = null) = obOutlinedTextFieldColors(labelAccent)
