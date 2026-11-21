package com.example.data

import com.example.data.source.BackgroundWorkDataSource
import com.example.data.source.LocalWorkspaceDataSource
import com.example.data.source.RemoteTextDataSource
import com.example.di.DispatcherProvider
import com.example.model.ConfigItem
import com.example.model.HitDbItem
import com.example.model.PersistedSettingsSnapshot
import com.example.model.ProxyCheckWorkSnapshot
import com.example.model.ProxyItem
import com.example.model.ProxyMode
import com.example.model.RunnerSessionSnapshot
import com.example.model.RunnerWorkSnapshot
import com.example.model.WordlistItem
import com.example.network.ConfigTemplates
import com.example.util.DateTimeFormats
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Single Source of Truth repository coordinating [LocalWorkspaceDataSource],
 * [RemoteTextDataSource], and [BackgroundWorkDataSource] in accordance with the
 * official Android App Architecture Data Layer guidelines.
 */
class DefaultOpenBulletRepository(
    private val localDataSource: LocalWorkspaceDataSource,
    private val remoteDataSource: RemoteTextDataSource,
    private val backgroundWorkDataSource: BackgroundWorkDataSource,
    private val dispatchers: DispatcherProvider
) : OpenBulletRepository {

    override val configsFlow: Flow<List<ConfigItem>> = localDataSource.configsFlow

    override val wordlistsFlow: Flow<List<WordlistItem>> = localDataSource.wordlistsFlow

    override val proxiesFlow: Flow<List<ProxyItem>> = localDataSource.proxiesFlow

    override val hitsDbFlow: Flow<List<HitDbItem>> = localDataSource.hitsDbFlow

    override val settingsFlow: Flow<PersistedSettingsSnapshot?> = localDataSource.settingsFlow

    override val runnerSessionFlow: Flow<RunnerSessionSnapshot?> = localDataSource.runnerSessionFlow

    override suspend fun saveConfig(config: ConfigItem) {
        localDataSource.upsertConfig(config)
    }

    override suspend fun saveConfigs(configs: List<ConfigItem>) {
        localDataSource.insertConfigs(configs)
    }

    override suspend fun deleteConfig(id: String) {
        localDataSource.deleteConfig(id)
    }

    override suspend fun saveWordlist(wordlist: WordlistItem) {
        localDataSource.upsertWordlist(wordlist)
    }

    override suspend fun loadWordlistLines(wordlistId: String): List<String> =
        localDataSource.readWordlistLines(wordlistId)

    override suspend fun deleteWordlist(id: String) {
        localDataSource.deleteWordlist(id)
    }

    override suspend fun clearWordlists() {
        localDataSource.clearWordlists()
    }

    override suspend fun saveProxies(proxies: List<ProxyItem>) {
        localDataSource.insertProxies(proxies)
    }

    override suspend fun deleteProxy(id: String) {
        localDataSource.deleteProxy(id)
    }

    override suspend fun deleteBadAndBannedProxies() {
        localDataSource.deleteBadAndBannedProxies()
    }

    override suspend fun clearProxies() {
        localDataSource.clearProxies()
    }

    override suspend fun saveHit(hit: HitDbItem) {
        localDataSource.insertHit(hit)
    }

    override suspend fun deleteHit(id: String) {
        localDataSource.deleteHit(id)
    }

    override suspend fun clearHitsDb() {
        localDataSource.clearHitsDb()
    }

    override suspend fun saveSettings(
        update: (PersistedSettingsSnapshot) -> PersistedSettingsSnapshot
    ) {
        localDataSource.updateSettings(update)
    }

    override suspend fun clearRunnerSession() {
        localDataSource.clearRunnerSession()
    }

    override suspend fun seedStarterWorkspace(): Pair<ConfigItem, WordlistItem> = withContext(dispatchers.io) {
        val localPreset = ConfigTemplates.presets.firstOrNull { it.id == "local_validator" }
            ?: ConfigTemplates.presets.first()
        val httpPreset = ConfigTemplates.presets.firstOrNull { it.id == "httpbin_get" }

        val starterConfig = ConfigItem(
            id = UUID.randomUUID().toString(),
            name = localPreset.title,
            author = "OpenBullet",
            category = localPreset.category,
            wordlistType = localPreset.wordlistType,
            needsProxies = localPreset.needsProxies,
            lastModified = DateTimeFormats.formatDateNow(),
            scriptPreview = localPreset.script
        )
        saveConfig(starterConfig)

        if (httpPreset != null && httpPreset.id != localPreset.id) {
            saveConfig(
                ConfigItem(
                    id = UUID.randomUUID().toString(),
                    name = httpPreset.title,
                    author = "OpenBullet",
                    category = httpPreset.category,
                    wordlistType = httpPreset.wordlistType,
                    needsProxies = httpPreset.needsProxies,
                    lastModified = DateTimeFormats.formatDateNow(),
                    scriptPreview = httpPreset.script
                )
            )
        }

        val sampleLines = listOf(
            "alice@example.com:DemoPass101",
            "bob@example.com:DemoPass202",
            "charlie@test.org:DemoPass303",
            "invalid_token_without_pair",
            "dana@company.net:DemoPass404"
        )
        val starterWordlist = WordlistItem(
            id = UUID.randomUUID().toString(),
            name = "starter_combos.txt",
            path = "files://wordlists/starter_combos.txt",
            type = "Mail:Pass",
            lines = sampleLines,
            purpose = "Quick Start Sample"
        )
        saveWordlist(starterWordlist)

        saveSettings {
            it.copy(
                selectedConfigId = starterConfig.id,
                selectedWordlistId = starterWordlist.id,
                startLine = 1
            )
        }

        starterConfig to starterWordlist
    }

    override suspend fun fetchRemoteText(url: String): Result<String> =
        remoteDataSource.fetchRemoteText(url)

    override suspend fun runHttpProbe(url: String): Result<String> =
        remoteDataSource.runHttpProbe(url)

    override fun enqueueRunnerJob(
        configId: String,
        wordlistId: String,
        startLine: Int,
        botsCount: Int,
        proxyMode: ProxyMode,
        initialHits: Int,
        initialCustom: Int,
        initialBad: Int,
        initialRetries: Int,
        initialToCheck: Int,
        initialElapsedSec: Long
    ) {
        backgroundWorkDataSource.enqueueRunnerJob(
            configId = configId,
            wordlistId = wordlistId,
            startLine = startLine,
            botsCount = botsCount,
            proxyMode = proxyMode,
            initialHits = initialHits,
            initialCustom = initialCustom,
            initialBad = initialBad,
            initialRetries = initialRetries,
            initialToCheck = initialToCheck,
            initialElapsedSec = initialElapsedSec
        )
    }

    override fun cancelRunnerJob() {
        backgroundWorkDataSource.cancelRunnerJob()
    }

    override fun observeRunnerWork(): Flow<RunnerWorkSnapshot?> =
        backgroundWorkDataSource.observeRunnerWork()

    override fun enqueueProxyCheck(probeUrl: String) {
        backgroundWorkDataSource.enqueueProxyCheck(probeUrl)
    }

    override fun cancelProxyCheck() {
        backgroundWorkDataSource.cancelProxyCheck()
    }

    override fun observeProxyCheckWork(): Flow<ProxyCheckWorkSnapshot?> =
        backgroundWorkDataSource.observeProxyCheckWork()
}
