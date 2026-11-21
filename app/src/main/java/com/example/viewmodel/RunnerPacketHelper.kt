package com.example.viewmodel

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import com.example.model.ProxyItem
import com.example.model.ProxyStatus
import com.example.model.ResultRow
import com.example.model.ResultTab
import com.example.model.WorkerRow

object RunnerPacketHelper {

    private const val RECORD_SEP = "\u001E"
    private const val FIELD_SEP = "\u001F"

    fun packWithByteBudget(
        entries: Iterable<String>,
        maxBytes: Int,
        separator: String = RECORD_SEP
    ): String {
        if (maxBytes <= 0) return ""
        val sepBytes = separator.toByteArray(Charsets.UTF_8).size
        val out = StringBuilder()
        var currentBytes = 0
        for (entry in entries) {
            if (entry.isEmpty()) continue
            val entryBytes = entry.toByteArray(Charsets.UTF_8).size
            val needed = if (out.isEmpty()) entryBytes else sepBytes + entryBytes
            if (currentBytes + needed > maxBytes) break
            if (out.isNotEmpty()) out.append(separator)
            out.append(entry)
            currentBytes += needed
        }
        return out.toString()
    }

    fun unpackWorkers(packed: String): List<WorkerRow> {
        if (packed.isBlank()) return emptyList()
        return packed.split(RECORD_SEP).mapNotNull { entry ->
            val parts = entry.split(FIELD_SEP)
            if (parts.size < 5) return@mapNotNull null
            val id = parts[0].toIntOrNull() ?: return@mapNotNull null
            WorkerRow(
                id = id,
                data = parts[1],
                proxy = parts[2],
                status = parts[3],
                outcomeType = parts[4]
            )
        }
    }

    fun unpackResults(packed: String): List<ResultRow> {
        if (packed.isBlank()) return emptyList()
        return packed.split(RECORD_SEP).mapNotNull { entry ->
            val parts = entry.split(FIELD_SEP)
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

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun openBatteryOptimizationSettings(context: Context) {
        val generalSolved = runCatching {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)

        if (!generalSolved) {
            runCatching {
                val appDetails = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", context.packageName, null)
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(appDetails)
            }
        }
    }

    fun chooseDebugProxy(proxies: List<ProxyItem>): ProxyItem? {
        return proxies.firstOrNull { it.status == ProxyStatus.AVAILABLE }
            ?: proxies.firstOrNull { it.status == ProxyStatus.UNTESTED }
    }
}
