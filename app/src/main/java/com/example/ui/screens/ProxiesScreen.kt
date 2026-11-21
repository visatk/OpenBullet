package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ProxiesUiState
import com.example.model.ProxyProtocol
import com.example.model.ProxyStatus
import com.example.ui.components.ObBorderedButton
import com.example.ui.components.ObConfirmDialog
import com.example.ui.components.ObEmptyStateCard
import com.example.ui.components.ObPanel
import com.example.ui.components.ObStatusPill
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObTheme
import com.example.ui.theme.containerColor
import com.example.ui.theme.tintColor

@Composable
fun ProxiesScreen(
    state: ProxiesUiState,
    onProtocolFilterChange: (ProxyProtocol?) -> Unit,
    onStatusFilterChange: (ProxyStatus?) -> Unit,
    onAddClick: () -> Unit,
    onToggleCheckProxies: () -> Unit,
    onRemoveBad: () -> Unit,
    onExportProxies: () -> Unit,
    onDeleteProxy: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmClear by remember { mutableStateOf(false) }

    val visible = state.filteredProxies
    val aliveCount = state.aliveCount
    val bannedCount = state.bannedCount
    val badCount = state.badCount
    val avgPingMs = state.avgPingMs

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
            // Summary strip & primary actions
            ObPanel(
                backgroundColor = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ObStatusPill(
                            text = "Total ${state.proxies.size}",
                            tintColor = MaterialTheme.colorScheme.onSurface,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                        ObStatusPill(
                            text = "Alive $aliveCount",
                            tintColor = ObTheme.colors.hit,
                            containerColor = ObTheme.colors.hitContainer,
                            showDot = true
                        )
                        if (bannedCount > 0) {
                            ObStatusPill(
                                text = "Banned $bannedCount",
                                tintColor = ObTheme.colors.banned,
                                containerColor = ObTheme.colors.bannedContainer
                            )
                        }
                        if (avgPingMs != null) {
                            ObStatusPill(
                                text = "Avg ${avgPingMs}ms",
                                tintColor = MaterialTheme.colorScheme.primary,
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ObBorderedButton(
                            onClick = onToggleCheckProxies,
                            isActive = state.isCheckingProxies,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("proxies_check_button")
                        ) {
                            Icon(
                                imageVector = if (state.isCheckingProxies) Icons.Filled.Stop else Icons.Outlined.PlayArrow,
                                contentDescription = null,
                                tint = if (state.isCheckingProxies) ObTheme.colors.bad else ObTheme.colors.hit,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (state.isCheckingProxies) {
                                    state.proxyCheckStatusText.ifEmpty { "Stop" }
                                } else {
                                    "Check All"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (badCount > 0) {
                            ObBorderedButton(
                                onClick = onRemoveBad,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("proxies_clean_bad_button")
                            ) {
                                Icon(
                                    Icons.Outlined.CleaningServices,
                                    contentDescription = null,
                                    tint = ObTheme.colors.custom,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "Drop Bad ($badCount)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        if (state.proxies.isNotEmpty()) {
                            IconButton(
                                onClick = onExportProxies,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("proxies_export_button")
                            ) {
                                Icon(
                                    Icons.Outlined.Share,
                                    contentDescription = "Export alive proxies",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = { confirmClear = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("proxies_clear_button")
                            ) {
                                Icon(
                                    Icons.Outlined.ClearAll,
                                    contentDescription = "Clear proxies",
                                    tint = ObTheme.colors.bad
                                )
                            }
                        }
                    }

                    if (state.isCheckingProxies) {
                        val progress = if (state.checkingTotal > 0) {
                            state.checkedCount.toFloat() / state.checkingTotal.toFloat()
                        } else 0f
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Protocol & Status Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = state.protocolFilter == null && state.statusFilter == null,
                    onClick = {
                        onProtocolFilterChange(null)
                        onStatusFilterChange(null)
                    },
                    label = { Text("All (${state.proxies.size})") },
                    leadingIcon = if (state.protocolFilter == null && state.statusFilter == null) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
                ProxyProtocol.entries.forEach { proto ->
                    val selected = state.protocolFilter == proto
                    FilterChip(
                        selected = selected,
                        onClick = { onProtocolFilterChange(if (selected) null else proto) },
                        label = { Text(proto.label) },
                        leadingIcon = if (selected) {
                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
                ProxyStatus.entries.forEach { status ->
                    val selected = state.statusFilter == status
                    FilterChip(
                        selected = selected,
                        onClick = { onStatusFilterChange(if (selected) null else status) },
                        label = { Text(status.label) },
                        leadingIcon = if (selected) {
                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }

            // Proxy Table
            ObPanel(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        Text(
                            "Host",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(0.36f)
                        )
                        Text(
                            "Port",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(0.16f)
                        )
                        Text(
                            "Type",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(0.16f)
                        )
                        Text(
                            "Status",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(0.24f)
                        )
                        Spacer(Modifier.width(40.dp))
                    }
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

                    if (visible.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            ObEmptyStateCard(
                                icon = Icons.Outlined.Dns,
                                title = if (state.proxies.isEmpty()) "No proxies loaded" else "No matching proxies",
                                subtitle = if (state.proxies.isEmpty()) {
                                    "Import host:port entries via paste, file, or URL."
                                } else {
                                    "Adjust protocol or status filter chips above."
                                },
                                actionLabel = if (state.proxies.isEmpty()) "Import Proxies" else null,
                                onActionClick = if (state.proxies.isEmpty()) onAddClick else null
                            )
                        }
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
                                    Text(
                                        text = proxy.host,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(0.36f)
                                    )
                                    Text(
                                        text = proxy.port.toString(),
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(0.16f)
                                    )
                                    Text(
                                        text = proxy.protocol.label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(0.16f)
                                    )
                                    Box(modifier = Modifier.weight(0.24f)) {
                                        ObStatusPill(
                                            text = if (proxy.pingMs != null) {
                                                "${proxy.status.label} ${proxy.pingMs}ms"
                                            } else {
                                                proxy.status.label
                                            },
                                            tintColor = proxy.status.tintColor(),
                                            containerColor = proxy.status.containerColor()
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeleteProxy(proxy.id) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            Icons.Outlined.DeleteOutline,
                                            contentDescription = "Delete proxy",
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

        ExtendedFloatingActionButton(
            onClick = onAddClick,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
            text = { Text("Import Proxies", style = MaterialTheme.typography.labelLarge) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("proxies_import_button")
        )
    }

    if (confirmClear) {
        ObConfirmDialog(
            title = "Clear All Proxies?",
            message = "This will permanently remove all ${state.proxies.size} proxies from the database.",
            confirmLabel = "Clear All",
            onConfirm = {
                confirmClear = false
                onClearAll()
            },
            onDismiss = { confirmClear = false }
        )
    }
}

@Composable
private fun ProxyTelemetryMetric(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = "tnum"
            ),
            color = valueColor
        )
    }
}

