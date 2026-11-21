package com.example.data.source

import com.example.data.local.ConfigEntity
import com.example.data.local.HitDbEntity
import com.example.data.local.OpenBulletDatabase
import com.example.data.local.ProxyCheckUpdate
import com.example.data.local.ProxyEntity
import com.example.data.local.RunnerSessionEntity
import com.example.data.local.SettingsEntity
import com.example.data.local.WordlistEntity
import com.example.data.local.WordlistFileStore
import com.example.di.DispatcherProvider
import com.example.model.ConfigItem
import com.example.model.HitDbItem
import com.example.model.PersistedSettingsSnapshot
import com.example.model.ProxyItem
import com.example.model.RunnerSessionSnapshot
import com.example.model.WordlistItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Local Data Source contract isolating Room database DAOs and WordlistFileStore I/O
 * in accordance with Android Data Layer architecture guidelines.
 */
interface LocalWorkspaceDataSource {
    val configsFlow: Flow<List<ConfigItem>>
    val wordlistsFlow: Flow<List<WordlistItem>>
    val proxiesFlow: Flow<List<ProxyItem>>
    val hitsDbFlow: Flow<List<HitDbItem>>
    val settingsFlow: Flow<PersistedSettingsSnapshot?>
    val runnerSessionFlow: Flow<RunnerSessionSnapshot?>

    suspend fun getConfigById(id: String): ConfigItem?
    suspend fun upsertConfig(config: ConfigItem)
    suspend fun insertConfigs(configs: List<ConfigItem>)
    suspend fun deleteConfig(id: String)

    suspend fun getWordlistById(id: String): WordlistItem?
    suspend fun upsertWordlist(wordlist: WordlistItem)
    suspend fun readWordlistLines(wordlistId: String): List<String>
    suspend fun deleteWordlist(id: String)
    suspend fun clearWordlists()

    suspend fun getAllProxiesSnapshot(): List<ProxyItem>
    suspend fun insertProxies(proxies: List<ProxyItem>)
    suspend fun updateProxyCheckResult(id: String, status: String, pingMs: Int?, country: String)
    suspend fun updateProxyCheckResultsBatch(updates: List<ProxyCheckUpdate>)
    suspend fun deleteProxy(id: String)
    suspend fun deleteBadAndBannedProxies()
    suspend fun clearProxies()

    suspend fun insertHit(hit: HitDbItem)
    suspend fun insertHitsBatch(hits: List<HitDbItem>)
    suspend fun deleteHit(id: String)
    suspend fun clearHitsDb()

    suspend fun getSettingsSnapshot(): PersistedSettingsSnapshot?
    suspend fun updateSettings(transform: (PersistedSettingsSnapshot) -> PersistedSettingsSnapshot)

    suspend fun upsertRunnerSession(session: RunnerSessionSnapshot)
    suspend fun clearRunnerSession()
}

class RoomWorkspaceDataSource(
    private val db: OpenBulletDatabase,
    private val wordlistFileStore: WordlistFileStore,
    private val dispatchers: DispatcherProvider
) : LocalWorkspaceDataSource {

    private val settingsMutex = Mutex()

    override val configsFlow: Flow<List<ConfigItem>> =
        db.configDao().observeAll().map { entities -> entities.map { it.toModel() } }

    override val wordlistsFlow: Flow<List<WordlistItem>> =
        db.wordlistDao().observeAll().map { entities -> entities.map { it.toModel() } }

    override val proxiesFlow: Flow<List<ProxyItem>> =
        db.proxyDao().observeAll().map { entities -> entities.map { it.toModel() } }

    override val hitsDbFlow: Flow<List<HitDbItem>> =
        db.hitDbDao().observeAll().map { entities -> entities.map { it.toModel() } }

    override val settingsFlow: Flow<PersistedSettingsSnapshot?> =
        db.settingsDao().observe().map { it?.toSnapshot() }

    override val runnerSessionFlow: Flow<RunnerSessionSnapshot?> =
        db.runnerSessionDao().observe().map { it?.toSnapshot() }

    override suspend fun getConfigById(id: String): ConfigItem? = withContext(dispatchers.io) {
        db.configDao().getById(id)?.toModel()
    }

    override suspend fun upsertConfig(config: ConfigItem) = withContext(dispatchers.io) {
        db.configDao().upsert(config.toEntity())
    }

    override suspend fun insertConfigs(configs: List<ConfigItem>) = withContext(dispatchers.io) {
        if (configs.isNotEmpty()) {
            db.configDao().insertAll(configs.map { it.toEntity() })
        }
    }

    override suspend fun deleteConfig(id: String) = withContext(dispatchers.io) {
        db.configDao().deleteById(id)
    }

    override suspend fun getWordlistById(id: String): WordlistItem? = withContext(dispatchers.io) {
        db.wordlistDao().getById(id)?.toModel()
    }

    override suspend fun upsertWordlist(wordlist: WordlistItem) = withContext(dispatchers.io) {
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

    override suspend fun readWordlistLines(wordlistId: String): List<String> = withContext(dispatchers.io) {
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

    override suspend fun getAllProxiesSnapshot(): List<ProxyItem> = withContext(dispatchers.io) {
        db.proxyDao().getAllSnapshot().map { it.toModel() }
    }

    override suspend fun insertProxies(proxies: List<ProxyItem>) = withContext(dispatchers.io) {
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

    override suspend fun updateProxyCheckResult(
        id: String,
        status: String,
        pingMs: Int?,
        country: String
    ) = withContext(dispatchers.io) {
        db.proxyDao().updateCheckResult(id = id, status = status, pingMs = pingMs, country = country)
    }

    override suspend fun updateProxyCheckResultsBatch(
        updates: List<ProxyCheckUpdate>
    ) = withContext(dispatchers.io) {
        if (updates.isNotEmpty()) {
            db.proxyDao().updateCheckResultsBatch(updates)
        }
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

    override suspend fun insertHit(hit: HitDbItem) = withContext(dispatchers.io) {
        db.hitDbDao().insert(hit.toEntity())
    }

    override suspend fun insertHitsBatch(hits: List<HitDbItem>) = withContext(dispatchers.io) {
        if (hits.isNotEmpty()) {
            db.hitDbDao().insertAll(hits.map { it.toEntity() })
        }
    }

    override suspend fun deleteHit(id: String) = withContext(dispatchers.io) {
        db.hitDbDao().deleteById(id)
    }

    override suspend fun clearHitsDb() = withContext(dispatchers.io) {
        db.hitDbDao().clearAll()
    }

    override suspend fun getSettingsSnapshot(): PersistedSettingsSnapshot? = withContext(dispatchers.io) {
        db.settingsDao().getSnapshot()?.toSnapshot()
    }

    override suspend fun updateSettings(
        transform: (PersistedSettingsSnapshot) -> PersistedSettingsSnapshot
    ) = withContext(dispatchers.io) {
        settingsMutex.withLock {
            val current = db.settingsDao().getSnapshot()?.toSnapshot() ?: PersistedSettingsSnapshot()
            val updated = transform(current)
            db.settingsDao().upsert(SettingsEntity.fromSnapshot(updated))
        }
    }

    override suspend fun upsertRunnerSession(session: RunnerSessionSnapshot) = withContext(dispatchers.io) {
        db.runnerSessionDao().upsert(
            RunnerSessionEntity(
                configId = session.configId,
                wordlistId = session.wordlistId,
                isRunning = session.isRunning,
                progressCurrent = session.progressCurrent,
                progressTotal = session.progressTotal,
                hits = session.hits,
                custom = session.custom,
                bad = session.bad,
                retries = session.retries,
                toCheck = session.toCheck,
                cpm = session.cpm,
                elapsedSeconds = session.elapsedSeconds,
                workersPacked = session.workersPacked,
                resultsPacked = session.resultsPacked,
                lastStatusLog = session.lastStatusLog
            )
        )
    }

    override suspend fun clearRunnerSession() = withContext(dispatchers.io) {
        db.runnerSessionDao().clearSession()
    }

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

    private fun HitDbItem.toEntity(): HitDbEntity = HitDbEntity(
        id = id,
        date = date,
        data = data,
        type = type.name,
        configName = configName,
        wordlistName = wordlistName,
        capturedData = capturedData
    )

    companion object {
        private const val PREVIEW_LINE_CAP = 20
    }
}
