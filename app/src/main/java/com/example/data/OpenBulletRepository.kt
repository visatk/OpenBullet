package com.example.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
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
import com.example.model.ConfigItem
import com.example.model.HitDbItem
import com.example.model.ProxyItem
import com.example.model.ProxyMode
import com.example.model.ProxyProtocol
import com.example.model.ProxyStatus
import com.example.model.WordlistItem
import com.example.network.OpenBulletNetworkClient
import com.example.worker.ProxyCheckWorker
import com.example.worker.RunnerJobWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class OpenBulletRepository(context: Context) {

    private val appContext = context.applicationContext
    private val db = OpenBulletDatabase.getInstance(appContext)
    private val workManager = WorkManager.getInstance(appContext)

    val configsFlow: Flow<List<ConfigItem>> = db.configDao().observeAll().map { list ->
        list.map { it.toModel() }
    }

    val wordlistsFlow: Flow<List<WordlistItem>> = db.wordlistDao().observeAll().map { list ->
        list.map { it.toModel() }
    }

    val proxiesFlow: Flow<List<ProxyItem>> = db.proxyDao().observeAll().map { list ->
        list.map { it.toModel() }
    }

    val hitsDbFlow: Flow<List<HitDbItem>> = db.hitDbDao().observeAll().map { list ->
        list.map { it.toModel() }
    }

    val settingsFlow: Flow<SettingsEntity?> = db.settingsDao().observe()

    val runnerSessionFlow: Flow<com.example.data.local.RunnerSessionEntity?> = db.runnerSessionDao().observe()

    suspend fun saveConfig(config: ConfigItem) {
        db.configDao().upsert(
            ConfigEntity(
                id = config.id,
                name = config.name,
                author = config.author,
                category = config.category,
                wordlistType = config.wordlistType,
                needsProxies = config.needsProxies,
                lastModified = config.lastModified,
                scriptPreview = config.scriptPreview
            )
        )
    }

    suspend fun deleteConfig(id: String) = db.configDao().deleteById(id)

    suspend fun saveWordlist(wordlist: WordlistItem) {
        db.wordlistDao().upsert(
            WordlistEntity(
                id = wordlist.id,
                name = wordlist.name,
                path = wordlist.path,
                type = wordlist.type,
                linesRaw = wordlist.lines.joinToString("\n"),
                lineCount = wordlist.lineCount,
                purpose = wordlist.purpose
            )
        )
    }

    suspend fun deleteWordlist(id: String) = db.wordlistDao().deleteById(id)

    suspend fun clearWordlists() = db.wordlistDao().clearAll()

    suspend fun saveProxies(proxies: List<ProxyItem>) {
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

    suspend fun deleteProxy(id: String) = db.proxyDao().deleteById(id)

    suspend fun deleteBadAndBannedProxies() = db.proxyDao().deleteBadAndBanned()

    suspend fun clearProxies() = db.proxyDao().clearAll()

    suspend fun saveHit(hit: HitDbItem) {
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

    suspend fun deleteHit(id: String) = db.hitDbDao().deleteById(id)

    suspend fun clearHitsDb() = db.hitDbDao().clearAll()

    suspend fun saveSettings(update: (SettingsEntity) -> SettingsEntity) {
        val current = db.settingsDao().getSnapshot() ?: SettingsEntity()
        db.settingsDao().upsert(update(current))
    }

    // --- Networking (Retrofit + OkHttp) ---

    suspend fun fetchRemoteText(url: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            OpenBulletNetworkClient.apiService.fetchRemoteText(url).use { body ->
                body.string()
            }
        }
    }

    suspend fun runHttpProbe(url: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val start = System.currentTimeMillis()
            val response = OpenBulletNetworkClient.apiService.probeHttpGet(url)
            val elapsed = System.currentTimeMillis() - start
            "HTTP 200 (${elapsed}ms) | origin=${response.origin ?: "unknown"} | url=${response.url ?: url}"
        }
    }

    // --- WorkManager ---

    fun enqueueRunnerJob(
        configId: String,
        wordlistId: String,
        startLine: Int,
        botsCount: Int,
        proxyMode: ProxyMode
    ) {
        // Do not attach NetworkType.CONNECTED constraint here so transient Wi-Fi/cellular switches
        // while screen is off never cause WorkManager to kill and restart an active run.
        val request = OneTimeWorkRequestBuilder<RunnerJobWorker>()
            .addTag(RunnerJobWorker.WORK_TAG)
            .setInputData(
                workDataOf(
                    RunnerJobWorker.KEY_CONFIG_ID to configId,
                    RunnerJobWorker.KEY_WORDLIST_ID to wordlistId,
                    RunnerJobWorker.KEY_START_LINE to startLine,
                    RunnerJobWorker.KEY_BOTS_COUNT to botsCount,
                    RunnerJobWorker.KEY_PROXY_MODE to proxyMode.name
                )
            )
            .build()

        workManager.enqueueUniqueWork(
            RunnerJobWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelRunnerJob() {
        workManager.cancelUniqueWork(RunnerJobWorker.UNIQUE_WORK_NAME)
    }

    fun observeRunnerWork(): Flow<List<WorkInfo>> =
        workManager.getWorkInfosForUniqueWorkFlow(RunnerJobWorker.UNIQUE_WORK_NAME)

    fun enqueueProxyCheck(probeUrl: String = "https://httpbin.org/ip") {
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

    fun cancelProxyCheck() {
        workManager.cancelUniqueWork(ProxyCheckWorker.UNIQUE_WORK_NAME)
    }

    fun observeProxyCheckWork(): Flow<List<WorkInfo>> =
        workManager.getWorkInfosForUniqueWorkFlow(ProxyCheckWorker.UNIQUE_WORK_NAME)
}
