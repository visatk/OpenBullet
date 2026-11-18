package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.local.HitDbEntity
import com.example.data.local.OpenBulletDatabase
import com.example.data.local.RunnerSessionEntity
import com.example.model.ProxyItem
import com.example.model.ProxyMode
import com.example.model.ProxyStatus
import com.example.model.ResultTab
import com.example.network.HttpCheckResult
import com.example.network.HttpExecutionEngine
import com.example.network.LoliScriptParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class RunnerJobWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = OpenBulletDatabase.getInstance(applicationContext)
        val configId = inputData.getString(KEY_CONFIG_ID) ?: return@withContext Result.failure()
        val wordlistId = inputData.getString(KEY_WORDLIST_ID) ?: return@withContext Result.failure()
        val startLine = inputData.getInt(KEY_START_LINE, 1)
        val botsCount = inputData.getInt(KEY_BOTS_COUNT, 1).coerceIn(1, 50)
        val proxyModeStr = inputData.getString(KEY_PROXY_MODE) ?: ProxyMode.DEF.name
        val proxyMode = runCatching { ProxyMode.valueOf(proxyModeStr) }.getOrDefault(ProxyMode.DEF)

        val config = db.configDao().getById(configId)?.toModel() ?: return@withContext Result.failure()
        val wordlist = db.wordlistDao().getById(wordlistId)?.toModel() ?: return@withContext Result.failure()
        val settings = db.settingsDao().getSnapshot()
        val timeoutSec = settings?.requestTimeoutSec ?: 10
        val maxRetries = (settings?.maxRetries ?: 3).coerceIn(0, 10)
        val autoSaveHits = settings?.autoSaveHits ?: true
        val keepAwake = settings?.keepAwakeEnabled ?: true

        val lines = wordlist.lines
        if (lines.isEmpty()) {
            return@withContext Result.success(
                workDataOf(KEY_LOG_LINE to "Wordlist '${wordlist.name}' has 0 lines.")
            )
        }

        // Immediately promote to Android Foreground Service so execution continues when
        // backgrounded, switching apps, or when the screen turns off.
        val totalCount = lines.size
        val startIdx = (startLine - 1).coerceIn(0, lines.lastIndex)

        runCatching {
            setForeground(
                BackgroundKeepAliveHelper.buildForegroundInfo(
                    context = applicationContext,
                    workId = id,
                    notificationId = BackgroundKeepAliveHelper.RUNNER_NOTIFICATION_ID,
                    title = "OpenBullet • ${config.name}",
                    contentText = "Starting $botsCount bot(s) on ${wordlist.name}...",
                    progressCurrent = startIdx,
                    progressTotal = totalCount
                )
            )
        }

        BackgroundKeepAliveHelper.withKeepAwakeLocks(applicationContext, enabled = keepAwake) {
            val allProxies = db.proxyDao().getAllSnapshot().map { it.toModel() }
            val usableProxies = selectUsableProxies(allProxies)
            val useProxies = (proxyMode == ProxyMode.ON || (proxyMode == ProxyMode.DEF && config.needsProxies)) &&
                usableProxies.isNotEmpty()

            val parsedScript = LoliScriptParser.parse(config.scriptPreview)
            val processedCount = AtomicInteger(startIdx)
            val hitsCount = AtomicInteger(0)
            val customCount = AtomicInteger(0)
            val badCount = AtomicInteger(0)
            val retriesCount = AtomicInteger(0)
            val toCheckCount = AtomicInteger(0)
            val proxyCursor = AtomicInteger(0)
            val proxyFailures = ConcurrentHashMap<String, Int>()

            val startTimeMs = System.currentTimeMillis()
            val semaphore = Semaphore(botsCount)
            val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)
            var lastNotifUpdateMs = 0L

            val startLog = "Foreground Runner started '${config.name}' on '${wordlist.name}' ($botsCount bots, proxies=${if (useProxies) usableProxies.size else 0})."
            val initialProgress = buildProgressData(
                current = startIdx,
                total = totalCount,
                hits = 0,
                custom = 0,
                bad = 0,
                retries = 0,
                toCheck = 0,
                cpm = 0,
                elapsedSec = 0L,
                workersPacked = "",
                resultsPacked = "",
                logLine = startLog
            )
            setProgress(initialProgress)
            db.runnerSessionDao().upsert(
                RunnerSessionEntity(
                    configId = configId,
                    wordlistId = wordlistId,
                    isRunning = true,
                    progressCurrent = startIdx,
                    progressTotal = totalCount,
                    lastStatusLog = startLog
                )
            )

            val recentResults = mutableListOf<String>()
            val chunkSize = botsCount.coerceIn(1, 12)
            for (chunkStart in startIdx until totalCount step chunkSize) {
                if (!isActive || isStopped) break
                val chunkEnd = (chunkStart + chunkSize).coerceAtMost(totalCount)

                val workerStrings = mutableListOf<String>()
                val batchResultStrings = mutableListOf<String>()
                val hitEntitiesToSave = mutableListOf<HitDbEntity>()

                val batchResults = coroutineScope {
                    (chunkStart until chunkEnd).mapIndexed { offset, lineIndex ->
                        async {
                            semaphore.withPermit {
                                val rawLine = lines[lineIndex].trim()
                                if (rawLine.isEmpty()) {
                                    Triple(
                                        offset + 1,
                                        "<empty>" to "Direct",
                                        HttpCheckResult("EMPTY", "BAD", "Empty line", 0L, 0)
                                    )
                                } else {
                                    var attempt = 0
                                    var usedProxy: ProxyItem? = null
                                    var finalResult: HttpCheckResult
                                    do {
                                        usedProxy = if (useProxies) {
                                            val idx = proxyCursor.getAndIncrement() and Int.MAX_VALUE
                                            usableProxies[idx % usableProxies.size]
                                        } else null

                                        finalResult = HttpExecutionEngine.executeCheck(
                                            rawLine = rawLine,
                                            script = parsedScript,
                                            timeoutSec = timeoutSec,
                                            proxyItem = usedProxy
                                        )

                                        if (finalResult.outcomeType == "RETRY" && attempt < maxRetries && isActive && !isStopped) {
                                            retriesCount.incrementAndGet()
                                            if (usedProxy != null) {
                                                val failTotal = (proxyFailures[usedProxy.id] ?: 0) + 1
                                                proxyFailures[usedProxy.id] = failTotal
                                                if (finalResult.httpCode == 403 || finalResult.httpCode == 429) {
                                                    db.proxyDao().updateCheckResult(usedProxy.id, ProxyStatus.BANNED.name, usedProxy.pingMs, "HTTP ${finalResult.httpCode}")
                                                } else if (failTotal >= 3) {
                                                    db.proxyDao().updateCheckResult(usedProxy.id, ProxyStatus.BAD.name, null, "Timeout")
                                                }
                                            }
                                            attempt++
                                        } else {
                                            break
                                        }
                                    } while (attempt <= maxRetries)

                                    if (usedProxy != null && finalResult.outcomeType != "RETRY" && finalResult.httpCode > 0) {
                                        db.proxyDao().updateCheckResult(
                                            id = usedProxy.id,
                                            status = ProxyStatus.AVAILABLE.name,
                                            pingMs = finalResult.latencyMs.toInt(),
                                            country = usedProxy.country.ifBlank { "Verified" }
                                        )
                                    }

                                    val proxyLabel = if (usedProxy != null) "${usedProxy.host}:${usedProxy.port}" else "Direct"
                                    Triple(offset + 1, rawLine to proxyLabel, finalResult)
                                }
                            }
                        }
                    }.awaitAll()
                }

                val nowClock = timeFmt.format(Date())
                batchResults.forEach { (workerId, lineAndProxy, outcome) ->
                    val (dataVal, proxyLabel) = lineAndProxy
                    when (outcome.outcomeType) {
                        "HIT" -> {
                            hitsCount.incrementAndGet()
                            val packed = packResultRow(nowClock, dataVal, proxyLabel, outcome.capturedData, ResultTab.HITS.name)
                            batchResultStrings.add(packed)
                            recentResults.add(packed)
                            if (autoSaveHits) {
                                hitEntitiesToSave.add(
                                    HitDbEntity(
                                        id = UUID.randomUUID().toString(),
                                        date = nowClock,
                                        data = dataVal,
                                        type = ResultTab.HITS.name,
                                        configName = config.name,
                                        wordlistName = wordlist.name,
                                        capturedData = outcome.capturedData
                                    )
                                )
                            }
                        }
                        "CUSTOM" -> {
                            customCount.incrementAndGet()
                            val packed = packResultRow(nowClock, dataVal, proxyLabel, outcome.capturedData, ResultTab.CUSTOM.name)
                            batchResultStrings.add(packed)
                            recentResults.add(packed)
                            if (autoSaveHits) {
                                hitEntitiesToSave.add(
                                    HitDbEntity(
                                        id = UUID.randomUUID().toString(),
                                        date = nowClock,
                                        data = dataVal,
                                        type = ResultTab.CUSTOM.name,
                                        configName = config.name,
                                        wordlistName = wordlist.name,
                                        capturedData = outcome.capturedData
                                    )
                                )
                            }
                        }
                        "BAD" -> badCount.incrementAndGet()
                        "RETRY" -> retriesCount.incrementAndGet()
                        else -> {
                            toCheckCount.incrementAndGet()
                            val packed = packResultRow(nowClock, dataVal, proxyLabel, outcome.capturedData, ResultTab.TO_CHECK.name)
                            batchResultStrings.add(packed)
                            recentResults.add(packed)
                        }
                    }
                    workerStrings.add("$workerId\u001F${dataVal.take(40)}\u001F$proxyLabel\u001F${outcome.status}\u001F${outcome.outcomeType}")
                }

                if (hitEntitiesToSave.isNotEmpty()) {
                    db.hitDbDao().insertAll(hitEntitiesToSave)
                }

                val currentDone = processedCount.addAndGet(batchResults.size)
                val elapsedSec = ((System.currentTimeMillis() - startTimeMs) / 1000L).coerceAtLeast(1L)
                val cpm = (((currentDone - startIdx) * 60L) / elapsedSec).toInt()
                val workersPacked = workerStrings.take(12).joinToString("\u001E")
                val batchResultsPacked = batchResultStrings.take(12).joinToString("\u001E")
                val persistedResultsPacked = recentResults.takeLast(30).joinToString("\u001E")

                setProgress(
                    buildProgressData(
                        current = currentDone,
                        total = totalCount,
                        hits = hitsCount.get(),
                        custom = customCount.get(),
                        bad = badCount.get(),
                        retries = retriesCount.get(),
                        toCheck = toCheckCount.get(),
                        cpm = cpm,
                        elapsedSec = elapsedSec,
                        workersPacked = workersPacked,
                        resultsPacked = batchResultsPacked,
                        logLine = null
                    )
                )

                db.runnerSessionDao().upsert(
                    RunnerSessionEntity(
                        configId = configId,
                        wordlistId = wordlistId,
                        isRunning = true,
                        progressCurrent = currentDone,
                        progressTotal = totalCount,
                        hits = hitsCount.get(),
                        custom = customCount.get(),
                        bad = badCount.get(),
                        retries = retriesCount.get(),
                        toCheck = toCheckCount.get(),
                        cpm = cpm,
                        elapsedSeconds = elapsedSec,
                        workersPacked = workersPacked,
                        resultsPacked = persistedResultsPacked,
                        lastStatusLog = startLog
                    )
                )

                val nowMs = System.currentTimeMillis()
                if (nowMs - lastNotifUpdateMs >= 900L || currentDone >= totalCount) {
                    lastNotifUpdateMs = nowMs
                    val pct = if (totalCount > 0) (currentDone * 100) / totalCount else 0
                    runCatching {
                        setForeground(
                            BackgroundKeepAliveHelper.buildForegroundInfo(
                                context = applicationContext,
                                workId = id,
                                notificationId = BackgroundKeepAliveHelper.RUNNER_NOTIFICATION_ID,
                                title = "OpenBullet • ${config.name} ($pct%)",
                                contentText = "$currentDone/$totalCount • Hits: ${hitsCount.get()} • Custom: ${customCount.get()} • CPM: $cpm",
                                progressCurrent = currentDone,
                                progressTotal = totalCount
                            )
                        )
                    }
                }
            }

            val elapsedSec = ((System.currentTimeMillis() - startTimeMs) / 1000L).coerceAtLeast(0L)
            val checkedTotal = (processedCount.get() - startIdx).coerceAtLeast(0)
            val finalCpm = if (elapsedSec > 0) ((checkedTotal * 60L) / elapsedSec).toInt() else 0
            val finalLog = if (isStopped) {
                "Runner stopped at ${processedCount.get()}/$totalCount. Hits: ${hitsCount.get()}."
            } else {
                "Runner completed $checkedTotal item(s). Hits: ${hitsCount.get()}, Custom: ${customCount.get()}."
            }

            db.runnerSessionDao().upsert(
                RunnerSessionEntity(
                    configId = configId,
                    wordlistId = wordlistId,
                    isRunning = false,
                    progressCurrent = processedCount.get(),
                    progressTotal = totalCount,
                    hits = hitsCount.get(),
                    custom = customCount.get(),
                    bad = badCount.get(),
                    retries = retriesCount.get(),
                    toCheck = toCheckCount.get(),
                    cpm = finalCpm,
                    elapsedSeconds = elapsedSec,
                    workersPacked = "",
                    resultsPacked = recentResults.takeLast(30).joinToString("\u001E"),
                    lastStatusLog = finalLog
                )
            )

            Result.success(
                buildProgressData(
                    current = processedCount.get(),
                    total = totalCount,
                    hits = hitsCount.get(),
                    custom = customCount.get(),
                    bad = badCount.get(),
                    retries = retriesCount.get(),
                    toCheck = toCheckCount.get(),
                    cpm = finalCpm,
                    elapsedSec = elapsedSec,
                    workersPacked = "",
                    resultsPacked = "",
                    logLine = finalLog
                )
            )
        }
    }

    private fun selectUsableProxies(all: List<ProxyItem>): List<ProxyItem> {
        val alive = all.filter { it.status == ProxyStatus.AVAILABLE }
        if (alive.isNotEmpty()) return alive
        return all.filter { it.status == ProxyStatus.UNTESTED }
    }

    private fun packResultRow(
        time: String,
        data: String,
        proxy: String,
        captured: String,
        category: String
    ): String {
        return listOf(
            time,
            data.replace("\u001F", " ").take(48),
            proxy.replace("\u001F", " ").take(28),
            captured.replace("\u001F", " ").take(64),
            category
        ).joinToString("\u001F")
    }

    private fun buildProgressData(
        current: Int,
        total: Int,
        hits: Int,
        custom: Int,
        bad: Int,
        retries: Int,
        toCheck: Int,
        cpm: Int,
        elapsedSec: Long,
        workersPacked: String,
        resultsPacked: String,
        logLine: String?
    ): Data {
        return workDataOf(
            KEY_CURRENT to current,
            KEY_TOTAL to total,
            KEY_HITS to hits,
            KEY_CUSTOM to custom,
            KEY_BAD to bad,
            KEY_RETRIES to retries,
            KEY_TO_CHECK to toCheck,
            KEY_CPM to cpm,
            KEY_ELAPSED_SEC to elapsedSec,
            KEY_WORKERS_PACKED to workersPacked,
            KEY_RESULTS_PACKED to resultsPacked,
            KEY_LOG_LINE to logLine
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "openbullet_runner_job"
        const val WORK_TAG = "runner_job_tag"

        const val KEY_CONFIG_ID = "config_id"
        const val KEY_WORDLIST_ID = "wordlist_id"
        const val KEY_START_LINE = "start_line"
        const val KEY_BOTS_COUNT = "bots_count"
        const val KEY_PROXY_MODE = "proxy_mode"

        const val KEY_CURRENT = "current"
        const val KEY_TOTAL = "total"
        const val KEY_HITS = "hits"
        const val KEY_CUSTOM = "custom"
        const val KEY_BAD = "bad"
        const val KEY_RETRIES = "retries"
        const val KEY_TO_CHECK = "to_check"
        const val KEY_CPM = "cpm"
        const val KEY_ELAPSED_SEC = "elapsed_sec"
        const val KEY_WORKERS_PACKED = "workers_packed"
        const val KEY_RESULTS_PACKED = "results_packed"
        const val KEY_LOG_LINE = "log_line"
    }
}
