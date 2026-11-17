package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.local.HitDbEntity
import com.example.data.local.OpenBulletDatabase
import com.example.model.ProxyMode
import com.example.model.ResultTab
import com.example.network.HttpExecutionEngine
import com.example.network.LoliScriptParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
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
        val botsCount = inputData.getInt(KEY_BOTS_COUNT, 1).coerceIn(1, 32)
        val proxyModeStr = inputData.getString(KEY_PROXY_MODE) ?: ProxyMode.DEF.name
        val proxyMode = runCatching { ProxyMode.valueOf(proxyModeStr) }.getOrDefault(ProxyMode.DEF)

        val config = db.configDao().getById(configId)?.toModel() ?: return@withContext Result.failure()
        val wordlist = db.wordlistDao().getById(wordlistId)?.toModel() ?: return@withContext Result.failure()
        val settings = db.settingsDao().getSnapshot()
        val timeoutSec = settings?.requestTimeoutSec ?: 10
        val autoSaveHits = settings?.autoSaveHits ?: true

        val lines = wordlist.lines
        if (lines.isEmpty()) {
            return@withContext Result.success(
                workDataOf(KEY_LOG_LINE to "Wordlist '${wordlist.name}' has 0 lines.")
            )
        }

        val proxies = db.proxyDao().getAllSnapshot().map { it.toModel() }
        val useProxies = (proxyMode == ProxyMode.ON || (proxyMode == ProxyMode.DEF && config.needsProxies)) &&
            proxies.isNotEmpty()

        val parsedScript = LoliScriptParser.parse(config.scriptPreview)
        val startIdx = (startLine - 1).coerceIn(0, lines.lastIndex)
        val totalCount = lines.size

        val processedCount = AtomicInteger(startIdx)
        val hitsCount = AtomicInteger(0)
        val customCount = AtomicInteger(0)
        val badCount = AtomicInteger(0)
        val retriesCount = AtomicInteger(0)
        val toCheckCount = AtomicInteger(0)

        val startTimeMs = System.currentTimeMillis()
        val semaphore = Semaphore(botsCount)
        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

        // Emit initial progress
        setProgress(
            buildProgressData(
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
                logLine = "WorkManager started '${config.name}' on '${wordlist.name}' ($botsCount bots)."
            )
        )

        val chunkSize = botsCount.coerceIn(1, 8)
        for (chunkStart in startIdx until totalCount step chunkSize) {
            if (!isActive || isStopped) break
            val chunkEnd = (chunkStart + chunkSize).coerceAtMost(totalCount)

            val workerStrings = mutableListOf<String>()
            val resultStrings = mutableListOf<String>()
            val hitEntitiesToSave = mutableListOf<HitDbEntity>()

            val batchResults = coroutineScope {
                (chunkStart until chunkEnd).mapIndexed { offset, lineIndex ->
                    async {
                        semaphore.withPermit {
                            val rawLine = lines[lineIndex].trim()
                            val chosenProxy = if (useProxies) proxies[lineIndex % proxies.size] else null
                            val proxyLabel = if (chosenProxy != null) "${chosenProxy.host}:${chosenProxy.port}" else "Direct"

                            val checkResult = if (rawLine.isEmpty()) {
                                com.example.network.HttpCheckResult(
                                    status = "EMPTY",
                                    outcomeType = "BAD",
                                    capturedData = "Empty line",
                                    latencyMs = 0L,
                                    httpCode = 0
                                )
                            } else {
                                HttpExecutionEngine.executeCheck(
                                    rawLine = rawLine,
                                    script = parsedScript,
                                    timeoutSec = timeoutSec,
                                    proxyItem = chosenProxy
                                )
                            }

                            Triple(offset + 1, rawLine.ifEmpty { "<empty>" } to proxyLabel, checkResult)
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
                        resultStrings.add(packResultRow(nowClock, dataVal, proxyLabel, outcome.capturedData, ResultTab.HITS.name))
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
                        resultStrings.add(packResultRow(nowClock, dataVal, proxyLabel, outcome.capturedData, ResultTab.CUSTOM.name))
                    }
                    "BAD" -> badCount.incrementAndGet()
                    "RETRY" -> retriesCount.incrementAndGet()
                    else -> {
                        toCheckCount.incrementAndGet()
                        resultStrings.add(packResultRow(nowClock, dataVal, proxyLabel, outcome.capturedData, ResultTab.TO_CHECK.name))
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
                    workersPacked = workerStrings.take(12).joinToString("\u001E"),
                    resultsPacked = resultStrings.take(12).joinToString("\u001E"),
                    logLine = null
                )
            )
        }

        val elapsedSec = ((System.currentTimeMillis() - startTimeMs) / 1000L).coerceAtLeast(0L)
        Result.success(
            buildProgressData(
                current = processedCount.get(),
                total = totalCount,
                hits = hitsCount.get(),
                custom = customCount.get(),
                bad = badCount.get(),
                retries = retriesCount.get(),
                toCheck = toCheckCount.get(),
                cpm = if (elapsedSec > 0) (((processedCount.get() - startIdx) * 60L) / elapsedSec).toInt() else 0,
                elapsedSec = elapsedSec,
                workersPacked = "",
                resultsPacked = "",
                logLine = "Runner completed ${processedCount.get() - startIdx} item(s). Hits: ${hitsCount.get()}."
            )
        )
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
