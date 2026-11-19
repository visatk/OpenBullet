package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ConfigItem
import com.example.model.ConfigsUiState
import com.example.ui.components.ObBorderedButton
import com.example.ui.components.ObEmptyStateCard
import com.example.ui.components.ObPanel
import com.example.ui.components.obOutlinedTextFieldColors
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObTheme
import com.example.util.FileExportHelper

@Composable
fun ConfigsScreen(
    state: ConfigsUiState,
    onSearchQueryChange: (String) -> Unit,
    onCategoryFilterChange: (String?) -> Unit,
    onNewConfigClick: () -> Unit,
    onImportConfigClick: () -> Unit,
    onDuplicateConfig: (ConfigItem) -> Unit,
    onExportConfigLoli: (ConfigItem) -> Unit,
    onExportConfigJson: (ConfigItem) -> Unit,
    onExportAllJson: () -> Unit,
    onEditConfig: (ConfigItem) -> Unit,
    onSelectPreview: (String) -> Unit,
    onUseInRunner: (ConfigItem) -> Unit,
    onDeleteConfig: (String) -> Unit,
    onCopyScriptFeedback: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = remember(state.configs) {
        state.configs.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val filteredConfigs = remember(state.configs, state.searchQuery, state.categoryFilter) {
        val q = state.searchQuery.trim()
        state.configs.filter { cfg ->
            val matchCat = state.categoryFilter == null || cfg.category.equals(state.categoryFilter, ignoreCase = true)
            val matchQuery = q.isEmpty() ||
                cfg.name.contains(q, ignoreCase = true) ||
                cfg.author.contains(q, ignoreCase = true) ||
                cfg.category.contains(q, ignoreCase = true)
            matchCat && matchQuery
        }
    }
    val previewConfig = filteredConfigs.firstOrNull { it.id == state.selectedPreviewId }
        ?: filteredConfigs.firstOrNull()
        ?: state.configs.firstOrNull { it.id == state.selectedPreviewId }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.configs.isNotEmpty()) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchQueryChange,
                    singleLine = true,
                    placeholder = { Text("Search configs by name, author, or category...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = if (state.searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search")
                            }
                        }
                    } else null,
                    colors = obOutlinedTextFieldColors(),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (categories.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = state.categoryFilter == null,
                        onClick = { onCategoryFilterChange(null) },
                        label = { Text("All (${state.configs.size})") },
                        leadingIcon = if (state.categoryFilter == null) {
                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                    categories.forEach { cat ->
                        val selected = state.categoryFilter == cat
                        FilterChip(
                            selected = selected,
                            onClick = { onCategoryFilterChange(if (selected) null else cat) },
                            label = { Text(cat) },
                            leadingIcon = if (selected) {
                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Configs: ${state.configs.size}",
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp)
                )
                ObBorderedButton(
                    onClick = onImportConfigClick,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("configs_import_button")
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = "Import .loli or JSON",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Import (.loli/JSON)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (previewConfig != null) {
                    ObBorderedButton(
                        onClick = { onEditConfig(previewConfig) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("configs_edit_button")
                    ) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = "Edit Config",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Edit", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                    ObBorderedButton(
                        onClick = { onDuplicateConfig(previewConfig) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("configs_duplicate_button")
                    ) {
                        Icon(
                            Icons.Outlined.ContentCopy,
                            contentDescription = "Duplicate Config",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Clone", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                    ObBorderedButton(
                        onClick = { onExportConfigLoli(previewConfig) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("configs_export_loli_button")
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
                        onClick = { onExportConfigJson(previewConfig) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("configs_export_json_button")
                    ) {
                        Icon(
                            Icons.Outlined.Code,
                            contentDescription = "Export JSON",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(".json", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                    ObBorderedButton(
                        onClick = { onUseInRunner(previewConfig) },
                        isActive = previewConfig.id == state.activeRunnerConfigId,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("configs_load_runner_button")
                    ) {
                        Icon(
                            Icons.Outlined.PlayArrow,
                            contentDescription = "Load in Runner",
                            tint = ObTheme.colors.hit,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Load in Runner", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                if (state.configs.size > 1) {
                    ObBorderedButton(
                        onClick = onExportAllJson,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("configs_export_all_button")
                    ) {
                        Text("Backup All", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val isWide = maxWidth >= 660.dp
                if (isWide) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ConfigTablePanel(
                            configs = filteredConfigs,
                            selectedId = previewConfig?.id,
                            onSelect = onSelectPreview,
                            onEdit = onEditConfig,
                            onDelete = onDeleteConfig,
                            onNewConfigClick = onNewConfigClick,
                            modifier = Modifier.weight(0.52f).fillMaxHeight()
                        )
                        ConfigScriptPreviewPanel(
                            config = previewConfig,
                            onCopyScript = onCopyScriptFeedback,
                            modifier = Modifier.weight(0.48f).fillMaxHeight()
                        )
                    }
                } else {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ConfigTablePanel(
                            configs = filteredConfigs,
                            selectedId = previewConfig?.id,
                            onSelect = onSelectPreview,
                            onEdit = onEditConfig,
                            onDelete = onDeleteConfig,
                            onNewConfigClick = onNewConfigClick,
                            modifier = Modifier.weight(0.55f).fillMaxWidth()
                        )
                        ConfigScriptPreviewPanel(
                            config = previewConfig,
                            onCopyScript = onCopyScriptFeedback,
                            modifier = Modifier.weight(0.45f).fillMaxWidth()
                        )
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onNewConfigClick,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
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
    onNewConfigClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ObPanel(modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Text(
                    "Name",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(0.36f)
                )
                Text(
                    "Author",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(0.22f)
                )
                Text(
                    "Category",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(0.22f)
                )
                Spacer(Modifier.width(80.dp))
            }
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

            if (configs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ObEmptyStateCard(
                        icon = Icons.Outlined.Code,
                        title = "No configs found",
                        subtitle = "Create a new config visually with Stacker blocks or import a .loli file.",
                        actionLabel = "Open Config Maker",
                        onActionClick = onNewConfigClick
                    )
                }
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
                                .background(
                                    if (active) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerLow
                                    }
                                )
                                .clickable { onSelect(cfg.id) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                cfg.name,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(0.36f)
                            )
                            Text(
                                cfg.author,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.weight(0.22f)
                            )
                            Text(
                                cfg.category,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                modifier = Modifier.weight(0.22f)
                            )
                            IconButton(
                                onClick = { onEdit(cfg) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    Icons.Outlined.Edit,
                                    contentDescription = "Edit config",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { onDelete(cfg.id) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    Icons.Outlined.DeleteOutline,
                                    contentDescription = "Delete config",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigScriptPreviewPanel(
    config: ConfigItem?,
    onCopyScript: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scroll = rememberScrollState()

    ObPanel(modifier = modifier, backgroundColor = ObTheme.colors.codeBackground) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "LoliScript Preview",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        config?.lastModified ?: "",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (config != null) {
                        IconButton(
                            onClick = {
                                FileExportHelper.copyToClipboard(context, config.name, config.scriptPreview)
                                onCopyScript()
                            },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                Icons.Outlined.ContentCopy,
                                contentDescription = "Copy script",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(12.dp)
            ) {
                Text(
                    text = config?.scriptPreview ?: "# Select or create a config in Config Maker.",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = if (config != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}
