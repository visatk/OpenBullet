package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.ConfigItem
import com.example.model.HitDbItem
import com.example.model.ProxyItem
import com.example.model.ProxyMode
import com.example.model.ProxyProtocol
import com.example.model.ProxyStatus
import com.example.model.ResultTab
import com.example.model.SettingsState
import com.example.model.WordlistItem

@Entity(tableName = "configs")
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
        val parsedLines = if (linesRaw.isEmpty()) emptyList() else linesRaw.split("\n")
        return WordlistItem(
            id = id,
            name = name,
            path = path,
            type = type,
            lines = parsedLines,
            purpose = purpose
        )
    }
}

@Entity(tableName = "proxies")
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

@Entity(tableName = "hits_db")
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
    val accentHex: Long = 0xFF2F94E8L,
    val startLine: Int = 1,
    val botsCount: Int = 1,
    val proxyMode: String = ProxyMode.DEF.name
) {
    fun toSettingsState(current: SettingsState): SettingsState = current.copy(
        requestTimeoutSec = requestTimeoutSec,
        maxRetries = maxRetries,
        autoSaveHits = autoSaveHits,
        ignoreEmptyLines = ignoreEmptyLines,
        showTimestampsInConsole = showTimestampsInConsole,
        soundOnHit = soundOnHit,
        soundOnFinish = soundOnFinish,
        accentHex = accentHex
    )
}
