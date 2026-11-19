package com.example.data

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.data.local.ConfigEntity
import com.example.data.local.HitDbEntity
import com.example.data.local.OpenBulletDatabase
import com.example.data.local.ProxyEntity
import com.example.data.local.SettingsEntity
import com.example.data.local.WordlistEntity
import com.example.data.local.WordlistFileStore
import com.example.di.DispatcherProvider
import com.example.model.ConfigItem
import com.example.model.HitDbItem
import com.example.model.PersistedSettingsSnapshot
import com.example.model.ProxyCheckWorkSnapshot
import com.example.model.ProxyItem
import com.example.model.ProxyMode
import com.example.model.RunnerSessionSnapshot
import com.example.model.RunnerWorkSnapshot
import com.example.model.WorkExecutionState
import com.example.model.WordlistItem
import com.example.network.ConfigTemplates
import com.example.network.OpenBulletApiService
import com.example.util.DateTimeFormats
import com.example.worker.ProxyCheckWorker
import com.example.worker.RunnerJobWorker
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class DefaultOpenBulletRepository(
    context: Context,
    private val db: OpenBulletDatabase,
    private val wordlistFileStore: WordlistFileStore,
    private val workManager: WorkManager,
    private val apiService: OpenBulletApiService,
    private val dispatchers: DispatcherProvider
) : OpenBulletRepository {

    private val appContext = context.applicationContext
    private val settingsMutex = Mutex()

    override val configsFlow: Flow<List<ConfigItem>> = db.configDao().observeAll().map { list ->
        list.map { it.toModel() }
    }

    override val wordlistsFlow: Flow<List<WordlistItem>> = db.wordlistDao().observeAll().map { list ->
        list.map { it.toModel() }
    }

    override val proxiesFlow: Flow<List<ProxyItem>> = db.proxyDao().observeAll().map { list ->
        list.map { it.toModel() }
    }

    override val hitsDbFlow: Flow<List<HitDbItem>> = db.hitDbDao().observeAll().map { list ->
        list.map { it.toModel() }
    }

    override val settingsFlow: Flow<PersistedSettingsSnapshot?> = db.settingsDao().observe().map { entity ->
        entity?.toSnapshot()
    }

    override val runnerSessionFlow: Flow<RunnerSessionSnapshot?> = db.runnerSessionDao().observe().map { entity ->
        entity?.toSnapshot()
    }

    override suspend fun saveConfig(config: ConfigItem) = withContext(dispatchers.io) {
        db.configDao().upsert(config.toEntity())
    }

    override suspend fun saveConfigs(configs: List<ConfigItem>) = withContext(dispatchers.io) {
        if (configs.isNotEmpty()) {
            db.configDao().insertAll(configs.map { it.toEntity() })
        }
    }

    override suspend fun deleteConfig(id: String) = withContext(dispatchers.io) {
        db.configDao().deleteById(id)
    }

    override suspend fun saveWordlist(wordlist: WordlistItem) = withContext(dispatchers.io) {
        wordlistFileStore.writeLines(wordlist.id, wordlist.lines)
        val previewRaw = wordlist.lines.take(PREVIEW_LINE_CAP).joinToString("\n")
        db.wordlistDao().upsert(
            WordlistEntity(
                id = wordlist.id,
                name = wordlist.name,
                path = "files://wordlists/${wordlist.id}.txt",
                type = wordlist.type,
                linesRaw = previewRaw,
                lineCount = wordlist.lineCount,
                purpose = wordlist.purpose
            )
        )
    }

    override suspend fun loadWordlistLines(wordlistId: String): List<String> = withContext(dispatchers.io) {
        val entity = db.wordlistDao().getById(wordlistId) ?: return@withContext emptyList()
        wordlistFileStore.readLines(wordlistId, entity.linesRaw)
    }

    override suspend fun deleteWordlist(id: String) = withContext(dispatchers.io) {
        db.wordlistDao().deleteById(id)
        wordlistFileStore.delete(id)
    }

    override suspend fun clearWordlists() = withContext(dispatchers.io) {
        db.wordlistDao().clearAll()
        wordlistFileStore.clearAll()
    }

    override suspend fun saveProxies(proxies: List<ProxyItem>) = withContext(dispatchers.io) {
        if (proxies.isEmpty()) return@withContext
        db.proxyDao().insertAll(
            proxies.map {
                ProxyEntity(
                    id = it.id,
                    host = it.host,
                    port = it.port,
                    protocol = it.protocol.name,
                    pingMs = it.pingMs,
                    country = it.country,
                    status = it.status.name
                )
            }
        )
    }

    override suspend fun deleteProxy(id: String) = withContext(dispatchers.io) {
        db.proxyDao().deleteById(id)
    }

    override suspend fun deleteBadAndBannedProxies() = withContext(dispatchers.io) {
        db.proxyDao().deleteBadAndBanned()
    }

    override suspend fun clearProxies() = withContext(dispatchers.io) {
        db.proxyDao().clearAll()
    }

    override suspend fun saveHit(hit: HitDbItem) = withContext(dispatchers.io) {
        db.hitDbDao().insert(
            HitDbEntity(
                id = hit.id,
                date = hit.date,
                data = hit.data,
                type = hit.type.name,
                configName = hit.configName,
                wordlistName = hit.wordlistName,
                capturedData = hit.capturedData
            )
        )
    }

    override suspend fun deleteHit(id: String) = withContext(dispatchers.io) {
        db.hitDbDao().deleteById(id)
    }

    override suspend fun clearHitsDb() = withContext(dispatchers.io) {
        db.hitDbDao().clearAll()
    }

    override suspend fun saveSettings(
        update: (PersistedSettingsSnapshot) -> PersistedSettingsSnapshot
    ) = withContext(dispatchers.io) {
        settingsMutex.withLock {
            val current = db.settingsDao().getSnapshot()?.toSnapshot() ?: PersistedSettingsSnapshot()
            val updated = update(current)
            db.settingsDao().upsert(SettingsEntity.fromSnapshot(updated))
        }
    }

    override suspend fun clearRunnerSession() = withContext(dispatchers.io) {
        db.runnerSessionDao().clearSession()
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

    override suspend fun fetchRemoteText(url: String): Result<String> = withContext(dispatchers.io) {
        runCatching {
            apiService.fetchRemoteText(url).use { body ->
                body.string()
            }
        }
    }

    override suspend fun runHttpProbe(url: String): Result<String> = withContext(dispatchers.io) {
        runCatching {
            val start = System.currentTimeMillis()
            val response = apiService.probeHttpGet(url)
            val elapsed = System.currentTimeMillis() - start
            "HTTP 200 (${elapsed}ms) | origin=${response.origin ?: "unknown"} | url=${response.url ?: url}"
        }
    }

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
        val request = OneTimeWorkRequestBuilder<RunnerJobWorker>()
            .addTag(RunnerJobWorker.WORK_TAG)
            .setInputData(
                workDataOf(
                    RunnerJobWorker.KEY_CONFIG_ID to configId,
                    RunnerJobWorker.KEY_WORDLIST_ID to wordlistId,
                    RunnerJobWorker.KEY_START_LINE to startLine,
                    RunnerJobWorker.KEY_BOTS_COUNT to botsCount,
                    RunnerJobWorker.KEY_PROXY_MODE to proxyMode.name,
                    RunnerJobWorker.KEY_INITIAL_HITS to initialHits,
                    RunnerJobWorker.KEY_INITIAL_CUSTOM to initialCustom,
                    RunnerJobWorker.KEY_INITIAL_BAD to initialBad,
                    RunnerJobWorker.KEY_INITIAL_RETRIES to initialRetries,
                    RunnerJobWorker.KEY_INITIAL_TO_CHECK to initialToCheck,
                    RunnerJobWorker.KEY_INITIAL_ELAPSED_SEC to initialElapsedSec
                )
            )
            .build()

        workManager.enqueueUniqueWork(
            RunnerJobWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    override fun cancelRunnerJob() {
        workManager.cancelUniqueWork(RunnerJobWorker.UNIQUE_WORK_NAME)
    }

    override fun observeRunnerWork(): Flow<RunnerWorkSnapshot?> =
        workManager.getWorkInfosForUniqueWorkFlow(RunnerJobWorker.UNIQUE_WORK_NAME)
            .map { infos -> infos.firstOrNull()?.toRunnerSnapshot() }

    override fun enqueueProxyCheck(probeUrl: String) {
        val request = OneTimeWorkRequestBuilder<ProxyCheckWorker>()
            .addTag(ProxyCheckWorker.WORK_TAG)
            .setInputData(workDataOf(ProxyCheckWorker.KEY_PROBE_URL to probeUrl))
            .build()

        workManager.enqueueUniqueWork(
            ProxyCheckWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    override fun cancelProxyCheck() {
        workManager.cancelUniqueWork(ProxyCheckWorker.UNIQUE_WORK_NAME)
    }

    override fun observeProxyCheckWork(): Flow<ProxyCheckWorkSnapshot?> =
        workManager.getWorkInfosForUniqueWorkFlow(ProxyCheckWorker.UNIQUE_WORK_NAME)
            .map { infos -> infos.firstOrNull()?.toProxyCheckSnapshot() }

    private fun ConfigItem.toEntity(): ConfigEntity = ConfigEntity(
        id = id,
        name = name,
        author = author,
        category = category,
        wordlistType = wordlistType,
        needsProxies = needsProxies,
        lastModified = lastModified,
        scriptPreview = scriptPreview
    )

    private fun WorkInfo.toRunnerSnapshot(): RunnerWorkSnapshot {
        val mappedState = state.toExecutionState()
        val data = if (mappedState == WorkExecutionState.RUNNING) progress else outputData
        return RunnerWorkSnapshot(
            workId = id.toString(),
            state = mappedState,
            progressCurrent = data.getInt(RunnerJobWorker.KEY_CURRENT, 0),
            progressTotal = data.getInt(RunnerJobWorker.KEY_TOTAL, 0),
            hits = data.getInt(RunnerJobWorker.KEY_HITS, 0),
            custom = data.getInt(RunnerJobWorker.KEY_CUSTOM, 0),
            bad = data.getInt(RunnerJobWorker.KEY_BAD, 0),
            retries = data.getInt(RunnerJobWorker.KEY_RETRIES, 0),
            toCheck = data.getInt(RunnerJobWorker.KEY_TO_CHECK, 0),
            cpm = data.getInt(RunnerJobWorker.KEY_CPM, 0),
            elapsedSeconds = data.getLong(RunnerJobWorker.KEY_ELAPSED_SEC, 0L),
            etaSeconds = data.getLong(RunnerJobWorker.KEY_ETA_SEC, 0L),
            workersPacked = data.getString(RunnerJobWorker.KEY_WORKERS_PACKED).orEmpty(),
            resultsPacked = data.getString(RunnerJobWorker.KEY_RESULTS_PACKED).orEmpty(),
            logLine = data.getString(RunnerJobWorker.KEY_LOG_LINE)
        )
    }

    private fun WorkInfo.toProxyCheckSnapshot(): ProxyCheckWorkSnapshot {
        val mappedState = state.toExecutionState()
        val data = if (mappedState == WorkExecutionState.RUNNING) progress else outputData
        return ProxyCheckWorkSnapshot(
            state = mappedState,
            checked = data.getInt(ProxyCheckWorker.KEY_CHECKED, 0),
            total = data.getInt(ProxyCheckWorker.KEY_TOTAL, 0)
        )
    }

    private fun WorkInfo.State.toExecutionState(): WorkExecutionState = when (this) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> WorkExecutionState.ENQUEUED
        WorkInfo.State.RUNNING -> WorkExecutionState.RUNNING
        WorkInfo.State.SUCCEEDED -> WorkExecutionState.SUCCEEDED
        WorkInfo.State.FAILED -> WorkExecutionState.FAILED
        WorkInfo.State.CANCELLED -> WorkExecutionState.CANCELLED
    }

    companion object {
        private const val PREVIEW_LINE_CAP = 20
    }
}
