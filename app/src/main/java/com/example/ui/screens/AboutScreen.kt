package com.example.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.model.AppShellUiState
import com.example.ui.components.ObPanel
import com.example.ui.components.ObStatusPill
import com.example.ui.components.OpenBulletLogo
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObTheme

@Composable
fun AboutScreen(
    shellState: AppShellUiState,
    modifier: Modifier = Modifier
) {
    val scroll = rememberScrollState()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scroll)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        ObPanel(
            backgroundColor = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .widthIn(max = 580.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OpenBulletLogo(
                    modifier = Modifier.size(56.dp),
                    centerBulletColor = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "OpenBullet ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Modern Adaptive Android Workstation",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ObStatusPill(
                        text = "API ${Build.VERSION.SDK_INT}",
                        tintColor = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                    ObStatusPill(
                        text = "${shellState.configsCount} CFGs • ${shellState.hitsCount} Hits",
                        tintColor = ObTheme.colors.hit,
                        containerColor = ObTheme.colors.hitContainer
                    )
                }

                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                ArchitectureRow("UI", "Jetpack Compose / Material 3 Adaptive")
                ArchitectureRow("Architecture", "UDF + Domain UseCases + DI Container")
                ArchitectureRow("Database", "Room (SQLite) + File-Backed Wordlists")
                ArchitectureRow("Background", "WorkManager Foreground DataSync")
                ArchitectureRow("Networking", "OkHttp + Retrofit + Moshi")
                ArchitectureRow("Scanning", shellState.nativeEngineBanner.ifEmpty { "NDK C++17 / JVM Fallback" })
                ArchitectureRow("Package", BuildConfig.APPLICATION_ID)
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
        Text(layer, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            tech,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
