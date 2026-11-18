package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ConfigItem
import com.example.model.HitDbItem
import com.example.model.ProxyItem
import com.example.model.ProxyProtocol
import com.example.model.ResultTab
import com.example.model.WordlistItem
import com.example.ui.components.ObBorderedButton
import com.example.ui.components.ObPanel
import com.example.ui.components.ObScrollbarIndicator
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObColors

import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.example.model.ProxyStatus

@Composable
fun ProxiesScreen(
    proxies: List<ProxyItem>,
    activeFilter: ProxyProtocol?,
    isCheckingProxies: Boolean,
    proxyCheckStatusText: String,
    onFilterChange: (ProxyProtocol?) -> Unit,
    onAddClick: () -> Unit,
    onToggleCheckProxies: () -> Unit,
    onRemoveBad: () -> Unit,
    onExportProxies: () -> Unit,
    onDeleteProxy: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visible = if (activeFilter == null) proxies else proxies.filter { it.protocol == activeFilter }
    val badCount = proxies.count { it.status == ProxyStatus.BAD || it.status == ProxyStatus.BANNED }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObColors.WorkspaceBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ObBorderedButton(
                    onClick = { onFilterChange(null) },
                    isActive = activeFilter == null,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("All (${proxies.size})", style = MaterialTheme.typography.labelMedium, color = ObColors.TextPrimary)
                }
                ProxyProtocol.entries.forEach { proto ->
                    ObBorderedButton(
                        onClick = { onFilterChange(proto) },
                        isActive = activeFilter == proto,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(proto.label, style = MaterialTheme.typography.labelMedium, color = ObColors.TextPrimary)
                    }
                }
                ObBorderedButton(
                    onClick = onToggleCheckProxies,
                    isActive = isCheckingProxies,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("proxies_check_button")
                ) {
                    Icon(
                        Icons.Outlined.PlayArrow,
                        contentDescription = null,
                        tint = if (isCheckingProxies) ObColors.StatusBadRed else ObColors.StatusHitGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (isCheckingProxies) proxyCheckStatusText.ifEmpty { "Stop" } else "Check",
                        style = MaterialTheme.typography.labelMedium,
                        color = ObColors.TextPrimary
                    )
                }
                if (badCount > 0) {
                    ObBorderedButton(
                        onClick = onRemoveBad,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("proxies_clean_bad_button")
                    ) {
                        Icon(Icons.Outlined.CleaningServices, contentDescription = null, tint = ObColors.StatusCustomOrange, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Drop Bad ($badCount)", style = MaterialTheme.typography.labelMedium, color = ObColors.TextPrimary)
                    }
                }
                if (proxies.isNotEmpty()) {
                    IconButton(
                        onClick = onExportProxies,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("proxies_export_button")
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = "Export alive proxies", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(
                        onClick = onClearAll,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("proxies_clear_button")
                    ) {
                        Icon(Icons.Outlined.ClearAll, contentDescription = "Clear proxies", tint = ObColors.StatusBadRed)
                    }
                }
            }

            ObPanel(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ObColors.SurfaceTableHeader)
                                .padding(horizontal = 12.dp, vertical = 9.dp)
                        ) {
                            Text("Host", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = ObColors.TextPrimary, modifier = Modifier.weight(0.36f))
                            Text("Port", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = ObColors.TextPrimary, modifier = Modifier.weight(0.16f))
                            Text("Type", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = ObColors.TextPrimary, modifier = Modifier.weight(0.18f))
                            Text("Status", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = ObColors.TextPrimary, modifier = Modifier.weight(0.20f))
                            Spacer(Modifier.width(40.dp))
                        }
                        HorizontalDivider(thickness = 1.dp, color = ObColors.DividerSubtle)

                        if (visible.isEmpty()) {
                            EmptyTableHint(
                                icon = { Icon(Icons.Outlined.Dns, contentDescription = null, tint = ObColors.TextMuted, modifier = Modifier.size(34.dp)) },
                                title = "No proxies loaded",
                                subtitle = "Use Import Proxies to paste host:port entries."
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 84.dp)
                            ) {
                                items(visible, key = { it.id }) { proxy ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(proxy.host, style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily), color = ObColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.34f))
                                        Text(proxy.port.toString(), style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily), color = ObColors.TextSecondary, modifier = Modifier.weight(0.14f))
                                        Text(proxy.protocol.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(0.16f))
                                        Text(
                                            text = if (proxy.pingMs != null) "${proxy.status.label} (${proxy.pingMs}ms)" else proxy.status.label,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = proxy.status.color,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(0.26f)
                                        )
                                        IconButton(onClick = { onDeleteProxy(proxy.id) }, modifier = Modifier.minimumInteractiveComponentSize()) {
                                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete proxy", tint = ObColors.TextMuted, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    HorizontalDivider(thickness = 0.5.dp, color = ObColors.DividerSubtle)
                                }
                            }
                        }
                    }
                    ObScrollbarIndicator()
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onAddClick,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = ObColors.TextPrimary,
            icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
            text = { Text("Import Proxies", style = MaterialTheme.typography.labelLarge) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("proxies_import_button")
        )
    }
}

@Composable
fun WordlistsScreen(
    wordlists: List<WordlistItem>,
    selectedWordlistId: String?,
    onAddClick: () -> Unit,
    onSelectForRunner: (WordlistItem) -> Unit,
    onDeleteWordlist: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObColors.WorkspaceBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Loaded Wordlists: ${wordlists.size}",
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = ObColors.TextSecondary
                )
                if (wordlists.isNotEmpty()) {
                    ObBorderedButton(
                        onClick = onClearAll,
                        modifier = Modifier.testTag("wordlists_clear_button")
                    ) {
                        Icon(Icons.Outlined.ClearAll, contentDescription = "Clear wordlists", tint = ObColors.StatusBadRed, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Clear All", style = MaterialTheme.typography.labelMedium, color = ObColors.TextPrimary)
                    }
                }
            }

            ObPanel(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ObColors.SurfaceTableHeader)
                                .padding(horizontal = 12.dp, vertical = 9.dp)
                        ) {
                            Text("Name", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = ObColors.TextPrimary, modifier = Modifier.weight(0.36f))
                            Text("Type", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = ObColors.TextPrimary, modifier = Modifier.weight(0.22f))
                            Text("Lines", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = ObColors.TextPrimary, modifier = Modifier.weight(0.18f))
                            Text("Use", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = ObColors.TextPrimary, modifier = Modifier.weight(0.24f))
                        }
                        HorizontalDivider(thickness = 1.dp, color = ObColors.DividerSubtle)

                        if (wordlists.isEmpty()) {
                            EmptyTableHint(
                                icon = { Icon(Icons.AutoMirrored.Outlined.ListAlt, contentDescription = null, tint = ObColors.TextMuted, modifier = Modifier.size(34.dp)) },
                                title = "No wordlists added",
                                subtitle = "Tap Add List to pick a .txt file or paste lines."
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 84.dp)
                            ) {
                                items(wordlists, key = { it.id }) { item ->
                                    val isSelected = item.id == selectedWordlistId
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(if (isSelected) ObColors.SurfaceSelected else ObColors.SurfaceTableBody)
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(item.name, style = MaterialTheme.typography.bodySmall, color = ObColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.36f))
                                        Text(item.type, style = MaterialTheme.typography.bodySmall, color = ObColors.TextSecondary, maxLines = 1, modifier = Modifier.weight(0.22f))
                                        Text(item.lineCount.toString(), style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily), color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(0.18f))
                                        Row(modifier = Modifier.weight(0.24f), horizontalArrangement = Arrangement.End) {
                                            IconButton(onClick = { onSelectForRunner(item) }, modifier = Modifier.minimumInteractiveComponentSize()) {
                                                Icon(Icons.Outlined.PlayArrow, contentDescription = "Use in Runner", tint = ObColors.StatusHitGreen, modifier = Modifier.size(20.dp))
                                            }
                                            IconButton(onClick = { onDeleteWordlist(item.id) }, modifier = Modifier.minimumInteractiveComponentSize()) {
                                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete wordlist", tint = ObColors.TextMuted, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                    HorizontalDivider(thickness = 0.5.dp, color = ObColors.DividerSubtle)
                                }
                            }
                        }
                    }
                    ObScrollbarIndicator()
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onAddClick,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = ObColors.TextPrimary,
            icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
            text = { Text("Add Wordlist", style = MaterialTheme.typography.labelLarge) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("wordlists_add_button")
        )
    }
}

@Composable
fun ConfigsScreen(
    configs: List<ConfigItem>,
    selectedPreviewId: String?,
    activeRunnerConfigId: String?,
    onNewConfigClick: () -> Unit,
    onImportConfigClick: () -> Unit,
    onExportConfigLoli: (ConfigItem) -> Unit,
    onExportConfigJson: (ConfigItem) -> Unit,
    onExportAllJson: () -> Unit,
    onEditConfig: (ConfigItem) -> Unit,
    onSelectPreview: (String) -> Unit,
    onUseInRunner: (ConfigItem) -> Unit,
    onDeleteConfig: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val previewConfig = configs.firstOrNull { it.id == selectedPreviewId } ?: configs.firstOrNull()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObColors.WorkspaceBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Configs: ${configs.size}",
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = ObColors.TextSecondary,
                    modifier = Modifier.padding(end = 4.dp)
                )
                ObBorderedButton(
                    onClick = onImportConfigClick,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("configs_import_button")
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Import .loli or JSON", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Import (.loli/JSON)", style = MaterialTheme.typography.labelMedium, color = ObColors.TextPrimary)
                }
                if (previewConfig != null) {
                    ObBorderedButton(
                        onClick = { onEditConfig(previewConfig) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("configs_edit_button")
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit Config", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Edit", style = MaterialTheme.typography.labelMedium, color = ObColors.TextPrimary)
                    }
                    ObBorderedButton(
                        onClick = { onExportConfigLoli(previewConfig) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("configs_export_loli_button")
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = "Export .loli", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(".loli", style = MaterialTheme.typography.labelMedium, color = ObColors.TextPrimary)
                    }
                    ObBorderedButton(
                        onClick = { onExportConfigJson(previewConfig) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("configs_export_json_button")
                    ) {
                        Icon(Icons.Outlined.Code, contentDescription = "Export JSON", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(".json", style = MaterialTheme.typography.labelMedium, color = ObColors.TextPrimary)
                    }
                    ObBorderedButton(
                        onClick = { onUseInRunner(previewConfig) },
                        isActive = previewConfig.id == activeRunnerConfigId,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("configs_load_runner_button")
                    ) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = "Load in Runner", tint = ObColors.StatusHitGreen, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Load in Runner", style = MaterialTheme.typography.labelMedium, color = ObColors.TextPrimary)
                    }
                }
                if (configs.size > 1) {
                    ObBorderedButton(
                        onClick = onExportAllJson,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("configs_export_all_button")
                    ) {
                        Text("Backup All", style = MaterialTheme.typography.labelMedium, color = ObColors.TextSecondary)
                    }
                }
            }

            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val isWide = maxWidth >= 660.dp
                if (isWide) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ConfigTablePanel(
                            configs = configs,
                            selectedId = previewConfig?.id,
                            onSelect = onSelectPreview,
                            onEdit = onEditConfig,
                            onDelete = onDeleteConfig,
                            modifier = Modifier.weight(0.52f).fillMaxHeight()
                        )
                        ConfigScriptPreviewPanel(
                            config = previewConfig,
                            modifier = Modifier.weight(0.48f).fillMaxHeight()
                        )
                    }
                } else {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ConfigTablePanel(
                            configs = configs,
                            selectedId = previewConfig?.id,
                            onSelect = onSelectPreview,
                            onEdit = onEditConfig,
                            onDelete = onDeleteConfig,
                            modifier = Modifier.weight(0.55f).fillMaxWidth()
                        )
                        ConfigScriptPreviewPanel(
                            config = previewConfig,
                            modifier = Modifier.weight(0.45f).fillMaxWidth()
                        )
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onNewConfigClick,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = ObColors.TextPrimary,
            icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
            text = { Text("Config Maker", style = MaterialTheme.typography.labelLarge) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("configs_new_button")
        )
    }
}

@Composable
private fun ConfigTablePanel(
    configs: List<ConfigItem>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onEdit: (ConfigItem) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    ObPanel(modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObColors.SurfaceTableHeader)
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Text("Name", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = ObColors.TextPrimary, modifier = Modifier.weight(0.36f))
                Text("Author", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = ObColors.TextPrimary, modifier = Modifier.weight(0.22f))
                Text("Category", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = ObColors.TextPrimary, modifier = Modifier.weight(0.22f))
                Spacer(Modifier.width(80.dp))
            }
            HorizontalDivider(thickness = 1.dp, color = ObColors.DividerSubtle)

            if (configs.isEmpty()) {
                EmptyTableHint(
                    icon = { Icon(Icons.Outlined.Code, contentDescription = null, tint = ObColors.TextMuted, modifier = Modifier.size(34.dp)) },
                    title = "No configs found",
                    subtitle = "Tap Config Maker to build and test a config."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 84.dp)
                ) {
                    items(configs, key = { it.id }) { cfg ->
                        val active = cfg.id == selectedId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (active) ObColors.SurfaceSelected else ObColors.SurfaceTableBody)
                                .clickable { onSelect(cfg.id) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(cfg.name, style = MaterialTheme.typography.bodySmall, color = ObColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.36f))
                            Text(cfg.author, style = MaterialTheme.typography.bodySmall, color = ObColors.TextSecondary, maxLines = 1, modifier = Modifier.weight(0.22f))
                            Text(cfg.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, modifier = Modifier.weight(0.22f))
                            IconButton(onClick = { onEdit(cfg) }, modifier = Modifier.minimumInteractiveComponentSize()) {
                                Icon(Icons.Outlined.Edit, contentDescription = "Edit config", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { onDelete(cfg.id) }, modifier = Modifier.minimumInteractiveComponentSize()) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete config", tint = ObColors.TextMuted, modifier = Modifier.size(18.dp))
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = ObColors.DividerSubtle)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigScriptPreviewPanel(
    config: ConfigItem?,
    modifier: Modifier = Modifier
) {
    ObPanel(modifier = modifier, backgroundColor = ObColors.SurfaceRecessed) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObColors.SurfaceTableHeader)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("LoliScript Preview", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = ObColors.TextPrimary)
                Text(config?.lastModified ?: "", style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily), color = ObColors.TextMuted)
            }
            HorizontalDivider(thickness = 1.dp, color = ObColors.DividerSubtle)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                Text(
                    text = config?.scriptPreview ?: "# Select or create a config in Config Maker.",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = if (config != null) ObColors.TextPrimary else ObColors.TextMuted
                )
            }
        }
    }
}

@Composable
fun HitsDbScreen(
    hits: List<HitDbItem>,
    searchQuery: String,
    typeFilter: ResultTab?,
    onSearchChange: (String) -> Unit,
    onTypeFilterChange: (ResultTab?) -> Unit,
    onExportHits: () -> Unit,
    onCopyHit: (HitDbItem) -> Unit,
    onDeleteHit: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val filtered = hits.filter { item ->
        val matchesType = typeFilter == null || item.type == typeFilter
        val matchesQuery = searchQuery.isBlank() ||
            item.data.contains(searchQuery, ignoreCase = true) ||
            item.configName.contains(searchQuery, ignoreCase = true)
        matchesType && matchesQuery
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObColors.WorkspaceBackground)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                singleLine = true,
                placeholder = { Text("Search hits...", style = MaterialTheme.typography.bodySmall, color = ObColors.TextMuted) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = ObColors.TextMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = ObColors.BorderHairline,
                    focusedContainerColor = ObColors.SurfaceRecessed,
                    unfocusedContainerColor = ObColors.SurfaceRecessed,
                    focusedTextColor = ObColors.TextPrimary,
                    unfocusedTextColor = ObColors.TextPrimary
                ),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.weight(1f)
            )
            if (hits.isNotEmpty()) {
                IconButton(onClick = onExportHits, modifier = Modifier.minimumInteractiveComponentSize().testTag("hits_export_button")) {
                    Icon(Icons.Outlined.Share, contentDescription = "Export Hits", tint = MaterialTheme.colorScheme.primary)
                }
            }
            ObBorderedButton(onClick = onClearAll, modifier = Modifier.testTag("hits_clear_button")) {
                Icon(Icons.Outlined.ClearAll, contentDescription = "Clear Hits DB", tint = ObColors.StatusBadRed, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Clear", style = MaterialTheme.typography.labelMedium, color = ObColors.TextPrimary)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ObBorderedButton(
                onClick = { onTypeFilterChange(null) },
                isActive = typeFilter == null,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("All (${hits.size})", style = MaterialTheme.typography.labelMedium, color = ObColors.TextPrimary)
            }
            ResultTab.entries.forEach { tab ->
                ObBorderedButton(
                    onClick = { onTypeFilterChange(tab) },
                    isActive = typeFilter == tab,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(tab.label, style = MaterialTheme.typography.labelMedium, color = ObColors.TextPrimary)
                }
            }
        }

        ObPanel(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ObColors.SurfaceTableHeader)
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        Text("Data", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = ObColors.TextPrimary, modifier = Modifier.weight(0.32f))
                        Text("Config", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = ObColors.TextPrimary, modifier = Modifier.weight(0.22f))
                        Text("Captured", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = ObColors.TextPrimary, modifier = Modifier.weight(0.26f))
                        Text("Actions", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = ObColors.TextPrimary, modifier = Modifier.weight(0.20f))
                    }
                    HorizontalDivider(thickness = 1.dp, color = ObColors.DividerSubtle)

                    if (filtered.isEmpty()) {
                        EmptyTableHint(
                            icon = { Icon(Icons.Outlined.VerifiedUser, contentDescription = null, tint = ObColors.TextMuted, modifier = Modifier.size(34.dp)) },
                            title = "Hits DB is empty",
                            subtitle = "Captured hits from Runner sessions appear here."
                        )
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(filtered, key = { it.id }) { hit ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                            cm?.setPrimaryClip(ClipData.newPlainText("OpenBullet Hit", "${hit.data} | ${hit.capturedData}"))
                                            onCopyHit(hit)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(hit.data, style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily), color = ObColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.32f))
                                    Text(hit.configName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.22f))
                                    Text(hit.capturedData, style = MaterialTheme.typography.bodySmall, color = ObColors.StatusHitGreen, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.26f))
                                    Row(modifier = Modifier.weight(0.20f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                                        IconButton(
                                            onClick = {
                                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                cm?.setPrimaryClip(ClipData.newPlainText("OpenBullet Hit", "${hit.data} | ${hit.capturedData}"))
                                                onCopyHit(hit)
                                            },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy hit", tint = ObColors.TextSecondary, modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(onClick = { onDeleteHit(hit.id) }, modifier = Modifier.minimumInteractiveComponentSize()) {
                                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete hit", tint = ObColors.TextMuted, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                                HorizontalDivider(thickness = 0.5.dp, color = ObColors.DividerSubtle)
                            }
                        }
                    }
                }
                ObScrollbarIndicator()
            }
        }
    }
}

@Composable
private fun EmptyTableHint(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            icon()
            Text(title, style = MaterialTheme.typography.titleSmall, color = ObColors.TextSecondary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = ObColors.TextMuted)
        }
    }
}
