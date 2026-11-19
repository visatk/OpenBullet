package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.model.AppThemeMode
import com.example.model.ConfigItem
import com.example.model.HitDbItem
import com.example.model.PersistedSettingsSnapshot
import com.example.model.ProxyItem
import com.example.model.ProxyMode
import com.example.model.ProxyProtocol
import com.example.model.ProxyStatus
import com.example.model.ResultTab
import com.example.model.RunnerSessionSnapshot
import com.example.model.SettingsState
import com.example.model.WordlistItem

@Entity(
    tableName = "configs",
    indices = [Index(value = ["createdAt"])]
)
data class ConfigEntity(
    @PrimaryKey val id: String,
    val name: String,
    val author: String,
    val category: String,
    val wordlistType: String,
    val needsProxies: Boolean,
    val lastModified: String,
    val scriptPreview: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toModel(): ConfigItem = ConfigItem(
        id = id,
        name = name,
        author = author,
        category = category,
        wordlistType = wordlistType,
        needsProxies = needsProxies,
        lastModified = lastModified,
        scriptPreview = scriptPreview
    )
}

@Entity(tableName = "wordlists")
data class WordlistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val path: String,
    val type: String,
    val linesRaw: String,
    val lineCount: Int,
    val purpose: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toModel(): WordlistItem {
        val previewLines = if (linesRaw.isEmpty()) emptyList() else linesRaw.split("\n")
        return WordlistItem(
            id = id,
            name = name,
            path = path,
            type = type,
            lines = previewLines,
            purpose = purpose,
            storedLineCount = lineCount
        )
    }
}

@Entity(
    tableName = "proxies",
    indices = [
        Index(value = ["status"]),
        Index(value = ["protocol"]),
        Index(value = ["createdAt"])
    ]
)
data class ProxyEntity(
    @PrimaryKey val id: String,
    val host: String,
    val port: Int,
    val protocol: String,
    val pingMs: Int?,
    val country: String,
    val status: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toModel(): ProxyItem = ProxyItem(
        id = id,
        host = host,
        port = port,
        protocol = runCatching { ProxyProtocol.valueOf(protocol) }.getOrDefault(ProxyProtocol.HTTP),
        pingMs = pingMs,
        country = country,
        status = runCatching { ProxyStatus.valueOf(status) }.getOrDefault(ProxyStatus.UNTESTED)
    )
}

@Entity(
    tableName = "hits_db",
    indices = [
        Index(value = ["type"]),
        Index(value = ["createdAt"])
    ]
)
data class HitDbEntity(
    @PrimaryKey val id: String,
    val date: String,
    val data: String,
    val type: String,
    val configName: String,
    val wordlistName: String,
    val capturedData: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toModel(): HitDbItem = HitDbItem(
        id = id,
        date = date,
        data = data,
        type = runCatching { ResultTab.valueOf(type) }.getOrDefault(ResultTab.HITS),
        configName = configName,
        wordlistName = wordlistName,
        capturedData = capturedData
    )
}

@Entity(tableName = "app_settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 0,
    val requestTimeoutSec: Int = 10,
    val maxRetries: Int = 3,
    val autoSaveHits: Boolean = true,
    val ignoreEmptyLines: Boolean = true,
    val showTimestampsInConsole: Boolean = false,
    val soundOnHit: Boolean = true,
    val soundOnFinish: Boolean = false,
    val keepAwakeEnabled: Boolean = true,
    val proxyJudgeUrl: String = "https://httpbin.org/ip",
    val accentHex: Long = 0xFF2F94E8L,
    val startLine: Int = 1,
    val botsCount: Int = 1,
    val proxyMode: String = ProxyMode.DEF.name,
    val selectedConfigId: String? = null,
    val selectedWordlistId: String? = null,
    val themeMode: String = AppThemeMode.DARK.name,
    val useDynamicColor: Boolean = false
) {
    fun toSettingsState(current: SettingsState = SettingsState()): SettingsState = current.copy(
        requestTimeoutSec = requestTimeoutSec,
        maxRetries = maxRetries,
        autoSaveHits = autoSaveHits,
        ignoreEmptyLines = ignoreEmptyLines,
        showTimestampsInConsole = showTimestampsInConsole,
        soundOnHit = soundOnHit,
        soundOnFinish = soundOnFinish,
        keepAwakeEnabled = keepAwakeEnabled,
        proxyJudgeUrl = proxyJudgeUrl,
        accentHex = accentHex,
        themeMode = runCatching { AppThemeMode.valueOf(themeMode) }.getOrDefault(AppThemeMode.DARK),
        useDynamicColor = useDynamicColor
    )

    fun toSnapshot(): PersistedSettingsSnapshot = PersistedSettingsSnapshot(
        requestTimeoutSec = requestTimeoutSec,
        maxRetries = maxRetries,
        autoSaveHits = autoSaveHits,
        ignoreEmptyLines = ignoreEmptyLines,
        showTimestampsInConsole = showTimestampsInConsole,
        soundOnHit = soundOnHit,
        soundOnFinish = soundOnFinish,
        keepAwakeEnabled = keepAwakeEnabled,
        proxyJudgeUrl = proxyJudgeUrl,
        accentHex = accentHex,
        themeMode = runCatching { AppThemeMode.valueOf(themeMode) }.getOrDefault(AppThemeMode.DARK),
        useDynamicColor = useDynamicColor,
        startLine = startLine,
        botsCount = botsCount,
        proxyMode = runCatching { ProxyMode.valueOf(proxyMode) }.getOrDefault(ProxyMode.DEF),
        selectedConfigId = selectedConfigId,
        selectedWordlistId = selectedWordlistId
    )

    companion object {
        fun fromSnapshot(snapshot: PersistedSettingsSnapshot): SettingsEntity = SettingsEntity(
            id = 0,
            requestTimeoutSec = snapshot.requestTimeoutSec,
            maxRetries = snapshot.maxRetries,
            autoSaveHits = snapshot.autoSaveHits,
            ignoreEmptyLines = snapshot.ignoreEmptyLines,
            showTimestampsInConsole = snapshot.showTimestampsInConsole,
            soundOnHit = snapshot.soundOnHit,
            soundOnFinish = snapshot.soundOnFinish,
            keepAwakeEnabled = snapshot.keepAwakeEnabled,
            proxyJudgeUrl = snapshot.proxyJudgeUrl,
            accentHex = snapshot.accentHex,
            startLine = snapshot.startLine,
            botsCount = snapshot.botsCount,
            proxyMode = snapshot.proxyMode.name,
            selectedConfigId = snapshot.selectedConfigId,
            selectedWordlistId = snapshot.selectedWordlistId,
            themeMode = snapshot.themeMode.name,
            useDynamicColor = snapshot.useDynamicColor
        )
    }
}

@Entity(tableName = "runner_session")
data class RunnerSessionEntity(
    @PrimaryKey val id: Int = 0,
    val configId: String = "",
    val wordlistId: String = "",
    val isRunning: Boolean = false,
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,
    val hits: Int = 0,
    val custom: Int = 0,
    val bad: Int = 0,
    val retries: Int = 0,
    val toCheck: Int = 0,
    val cpm: Int = 0,
    val elapsedSeconds: Long = 0L,
    val workersPacked: String = "",
    val resultsPacked: String = "",
    val lastStatusLog: String = ""
) {
    fun toSnapshot(): RunnerSessionSnapshot = RunnerSessionSnapshot(
        configId = configId,
        wordlistId = wordlistId,
        isRunning = isRunning,
        progressCurrent = progressCurrent,
        progressTotal = progressTotal,
        hits = hits,
        custom = custom,
        bad = bad,
        retries = retries,
        toCheck = toCheck,
        cpm = cpm,
        elapsedSeconds = elapsedSeconds,
        workersPacked = workersPacked,
        resultsPacked = resultsPacked,
        lastStatusLog = lastStatusLog
    )
}
