package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.model.ProxyStatus
import com.example.model.ResultTab

@Composable
fun ProxyStatus.tintColor(): Color = when (this) {
    ProxyStatus.UNTESTED -> MaterialTheme.colorScheme.onSurfaceVariant
    ProxyStatus.AVAILABLE -> ObTheme.colors.hit
    ProxyStatus.BANNED -> ObTheme.colors.banned
    ProxyStatus.BAD -> ObTheme.colors.bad
}

@Composable
fun ProxyStatus.containerColor(): Color = when (this) {
    ProxyStatus.UNTESTED -> MaterialTheme.colorScheme.surfaceContainerHigh
    ProxyStatus.AVAILABLE -> ObTheme.colors.hitContainer
    ProxyStatus.BANNED -> ObTheme.colors.bannedContainer
    ProxyStatus.BAD -> ObTheme.colors.badContainer
}

@Composable
fun ResultTab.tintColor(): Color = when (this) {
    ResultTab.HITS -> ObTheme.colors.hit
    ResultTab.CUSTOM -> ObTheme.colors.custom
    ResultTab.TO_CHECK -> ObTheme.colors.toCheck
}

@Composable
fun ResultTab.containerColor(): Color = when (this) {
    ResultTab.HITS -> ObTheme.colors.hitContainer
    ResultTab.CUSTOM -> ObTheme.colors.customContainer
    ResultTab.TO_CHECK -> ObTheme.colors.toCheckContainer
}

@Composable
fun outcomeTintColor(outcomeType: String): Color = when (outcomeType.uppercase()) {
    "HIT" -> ObTheme.colors.hit
    "CUSTOM" -> ObTheme.colors.custom
    "BAD" -> ObTheme.colors.bad
    "RETRY" -> ObTheme.colors.retry
    "RUNNING" -> MaterialTheme.colorScheme.primary
    else -> ObTheme.colors.toCheck
}

@Composable
fun outcomeContainerColor(outcomeType: String): Color = when (outcomeType.uppercase()) {
    "HIT" -> ObTheme.colors.hitContainer
    "CUSTOM" -> ObTheme.colors.customContainer
    "BAD" -> ObTheme.colors.badContainer
    "RETRY" -> ObTheme.colors.retryContainer
    "RUNNING" -> MaterialTheme.colorScheme.primaryContainer
    else -> ObTheme.colors.toCheckContainer
}
