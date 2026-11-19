package com.example.data

import com.example.model.ConfigItem
import com.example.model.HitDbItem
import com.example.model.PersistedSettingsSnapshot
import com.example.model.ProxyCheckWorkSnapshot
import com.example.model.ProxyItem
import com.example.model.ProxyMode
import com.example.model.RunnerSessionSnapshot
import com.example.model.RunnerWorkSnapshot
import com.example.model.WordlistItem
import kotlinx.coroutines.flow.Flow

/**
 * Single Source of Truth repository contract decoupling ViewModels from Room, WorkManager, and Retrofit.
 */
interface OpenBulletRepository {
    val configsFlow: Flow<List<ConfigItem>>
    val wordlistsFlow: Flow<List<WordlistItem>>
    val proxiesFlow: Flow<List<ProxyItem>>
    val hitsDbFlow: Flow<List<HitDbItem>>
    val settingsFlow: Flow<PersistedSettingsSnapshot?>
    val runnerSessionFlow: Flow<RunnerSessionSnapshot?>

    suspend fun saveConfig(config: ConfigItem)
    suspend fun saveConfigs(configs: List<ConfigItem>)
    suspend fun deleteConfig(id: String)

    suspend fun saveWordlist(wordlist: WordlistItem)
    suspend fun loadWordlistLines(wordlistId: String): List<String>
    suspend fun deleteWordlist(id: String)
    suspend fun clearWordlists()

    suspend fun saveProxies(proxies: List<ProxyItem>)
    suspend fun deleteProxy(id: String)
    suspend fun deleteBadAndBannedProxies()
    suspend fun clearProxies()

    suspend fun saveHit(hit: HitDbItem)
    suspend fun deleteHit(id: String)
    suspend fun clearHitsDb()

    suspend fun saveSettings(update: (PersistedSettingsSnapshot) -> PersistedSettingsSnapshot)
    suspend fun clearRunnerSession()

    suspend fun seedStarterWorkspace(): Pair<ConfigItem, WordlistItem>

    suspend fun fetchRemoteText(url: String): Result<String>
    suspend fun runHttpProbe(url: String): Result<String>

    fun enqueueRunnerJob(
        configId: String,
        wordlistId: String,
        startLine: Int,
        botsCount: Int,
        proxyMode: ProxyMode,
        initialHits: Int = 0,
        initialCustom: Int = 0,
        initialBad: Int = 0,
        initialRetries: Int = 0,
        initialToCheck: Int = 0,
        initialElapsedSec: Long = 0L
    )

    fun cancelRunnerJob()
    fun observeRunnerWork(): Flow<RunnerWorkSnapshot?>

    fun enqueueProxyCheck(probeUrl: String = "https://httpbin.org/ip")
    fun cancelProxyCheck()
    fun observeProxyCheckWork(): Flow<ProxyCheckWorkSnapshot?>
}
