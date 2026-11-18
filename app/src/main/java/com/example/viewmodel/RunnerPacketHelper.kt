package com.example.viewmodel

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.PowerManager
import android.provider.Settings
import com.example.model.ProxyStatus
import com.example.model.ResultRow
import com.example.model.ResultTab
import com.example.model.WorkerRow
import com.example.ui.theme.ObColors

object RunnerPacketHelper {

    fun unpackWorkers(packed: String): List<WorkerRow> {
        if (packed.isBlank()) return emptyList()
        return packed.split("\u001E").mapNotNull { entry ->
            val parts = entry.split("\u001F")
            if (parts.size < 5) return@mapNotNull null
            val id = parts[0].toIntOrNull() ?: return@mapNotNull null
            val color = when (parts[4]) {
                "HIT" -> ObColors.StatusHitGreen
                "CUSTOM" -> ObColors.StatusCustomOrange
                "BAD" -> ObColors.StatusBadRed
                "RETRY" -> ObColors.StatusRetryYellow
                else -> ObColors.StatusToCheckAqua
            }
            WorkerRow(
                id = id,
                data = parts[1],
                proxy = parts[2],
                status = parts[3],
                statusColor = color
            )
        }
    }

    fun unpackResults(packed: String): List<ResultRow> {
        if (packed.isBlank()) return emptyList()
        return packed.split("\u001E").mapNotNull { entry ->
            val parts = entry.split("\u001F")
            if (parts.size < 5) return@mapNotNull null
            val cat = runCatching { ResultTab.valueOf(parts[4]) }.getOrDefault(ResultTab.TO_CHECK)
            ResultRow(
                time = parts[0],
                data = parts[1],
                proxy = parts[2],
                capturedData = parts[3],
                category = cat
            )
        }
    }

    fun playAlertTone(isHit: Boolean) {
        runCatching {
            val tone = if (isHit) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_BEEP2
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
            tg.startTone(tone, if (isHit) 140 else 220)
        }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun openBatteryOptimizationSettings(context: Context) {
        runCatching {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun chooseDebugProxy(proxies: List<com.example.model.ProxyItem>): com.example.model.ProxyItem? {
        return proxies.firstOrNull { it.status == ProxyStatus.AVAILABLE }
            ?: proxies.firstOrNull { it.status == ProxyStatus.UNTESTED }
    }
}
