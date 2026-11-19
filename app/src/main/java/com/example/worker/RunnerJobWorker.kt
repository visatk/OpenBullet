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
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
        val botsCount = inputData.getInt(KEY_BOTS_COUNT, 1).coerceIn(1, 200)
        val proxyModeStr = inputData.getString(KEY_PROXY_MODE) ?: ProxyMode.DEF.name
        val proxyMode = runCatching { ProxyMode.valueOf(proxyModeStr) }.getOrDefault(ProxyMode.DEF)

        val initialHits = inputData.getInt(KEY_INITIAL_HITS, 0)
        val initialCustom = inputData.getInt(KEY_INITIAL_CUSTOM, 0)
        val initialBad = inputData.getInt(KEY_INITIAL_BAD, 0)
        val initialRetries = inputData.getInt(KEY_INITIAL_RETRIES, 0)
        val initialToCheck = inputData.getInt(KEY_INITIAL_TO_CHECK, 0)
        val initialElapsedSec = inputData.getLong(KEY_INITIAL_ELAPSED_SEC, 0L)

        val config = db.configDao().getById(configId)?.toModel() ?: return@withContext Result.failure()
        val wordlist = db.wordlistDao().getById(wordlistId)?.toModel() ?: return@withContext Result.failure()
        val settings = db.settingsDao().getSnapshot()
        val timeoutSec = settings?.requestTimeoutSec ?: 10
        val maxRetries = (settings?.maxRetries ?: 3).coerceIn(0, 15)
        val autoSaveHits = settings?.autoSaveHits ?: true
        val keepAwake = settings?.keepAwakeEnabled ?: true

        val lines = wordlist.lines
        if (lines.isEmpty()) {
            return@withContext Result.success(
                workDataOf(KEY_LOG_LINE to "Wordlist '${wordlist.name}' has 0 lines.")
            )
        }

        val totalCount = lines.size
        val startIdx = (startLine - 1).coerceIn(0, lines.lastIndex)

        runCatching {
            setForeground(
                BackgroundKeepAliveHelper.buildForegroundInfo(
                    context = applicationContext,
                    workId = id,
                    notificationId = BackgroundKeepAliveHelper.RUNNER_NOTIFICATION_ID,
                    title = "OpenBullet • ${config.name}",
                    contentText = "Launching $botsCount bot(s) on ${wordlist.name}...",
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

            // Lock-free atomic cursor for zero-stall continuous worker pool
            val nextLineCursor = AtomicInteger(startIdx)
            val processedCount = AtomicInteger(startIdx)
            val hitsCount = AtomicInteger(initialHits)
            val customCount = AtomicInteger(initialCustom)
            val badCount = AtomicInteger(initialBad)
            val retriesCount = AtomicInteger(initialRetries)
            val toCheckCount = AtomicInteger(initialToCheck)

            // Smart proxy pool with cooldown & auto-unban on starvation
            val proxyCursor = AtomicInteger(0)
            val proxyFailures = ConcurrentHashMap<String, Int>()
            val proxyCooldownUntil = ConcurrentHashMap<String, Long>()

            // Live bot slot table & result queue
            val activeBotSlots = ConcurrentHashMap<Int, String>()
            val pendingResultRows = ConcurrentLinkedQueue<String>()
            val recentPersistedResults = ConcurrentLinkedQueue<String>()
            val pendingHitEntities = ConcurrentLinkedQueue<HitDbEntity>()

            // Sliding-window completion timestamps (ms) for accurate real-time CPM & ETA
            val completionTimestamps = ConcurrentLinkedQueue<Long>()

            val runStartMs = System.currentTimeMillis()
            val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

            val modeDesc = if (initialElapsedSec > 0L) "Resumed" else "Started"
            val startLog = "$modeDesc continuous pool ($botsCount bots, line ${startIdx + 1}/$totalCount, proxies=${if (useProxies) usableProxies.size else 0})."

            setProgress(
                buildProgressData(
                    current = startIdx,
                    total = totalCount,
                    hits = hitsCount.get(),
                    custom = customCount.get(),
                    bad = badCount.get(),
                    retries = retriesCount.get(),
                    toCheck = toCheckCount.get(),
                    cpm = 0,
                    elapsedSec = initialElapsedSec,
                    etaSec = 0L,
                    workersPacked = "",
                    resultsPacked = "",
                    logLine = startLog
                )
            )
            db.runnerSessionDao().upsert(
                RunnerSessionEntity(
                    configId = configId,
                    wordlistId = wordlistId,
                    isRunning = true,
                    progressCurrent = startIdx,
                    progressTotal = totalCount,
                    hits = hitsCount.get(),
                    custom = customCount.get(),
                    bad = badCount.get(),
                    retries = retriesCount.get(),
                    toCheck = toCheckCount.get(),
                    elapsedSeconds = initialElapsedSec,
                    lastStatusLog = startLog
                )
            )

            fun acquireNextProxy(): ProxyItem? {
                if (!useProxies || usableProxies.isEmpty()) return null
                val now = System.currentTimeMillis()
                val size = usableProxies.size
                for (probe in 0 until size) {
                    val idx = (proxyCursor.getAndIncrement() and Int.MAX_VALUE) % size
                    val candidate = usableProxies[idx]
                    val cooldownExpiry = proxyCooldownUntil[candidate.id] ?: 0L
                    if (now >= cooldownExpiry) {
                        return candidate
                    }
                }
                // Auto-unban: all proxies are in cooldown/banned -> reset cooldowns so run never stalls
                proxyCooldownUntil.clear()
                proxyFailures.clear()
                val idx = (proxyCursor.getAndIncrement() and Int.MAX_VALUE) % size
                return usableProxies[idx]
            }

            suspend fun flushTelemetry(forceNotification: Boolean, lastNotifTimeRef: LongArray) {
                val toInsert = mutableListOf<HitDbEntity>()
                while (true) {
                    val entity = pendingHitEntities.poll() ?: break
                    toInsert.add(entity)
                }
                if (toInsert.isNotEmpty()) {
                    db.hitDbDao().insertAll(toInsert)
                }

                val batchResults = mutableListOf<String>()
                while (batchResults.size < 25) {
                    val r = pendingResultRows.poll() ?: break
                    batchResults.add(r)
                    recentPersistedResults.add(r)
                    while (recentPersistedResults.size > 40) {
                        recentPersistedResults.poll()
                    }
                }

                val nowMs = System.currentTimeMillis()
                val windowCutoff = nowMs - 60_000L
                while (true) {
                    val head = completionTimestamps.peek() ?: break
                    if (head < windowCutoff) {
                        completionTimestamps.poll()
                    } else {
                        break
                    }
                }

                val currentDone = processedCount.get()
                val sessionElapsedSec = ((nowMs - runStartMs) / 1000L).coerceAtLeast(1L)
                val totalElapsedSec = initialElapsedSec + sessionElapsedSec
                val windowCount = completionTimestamps.size
                val cpm = if (sessionElapsedSec < 60L) {
                    ((windowCount * 60L) / sessionElapsedSec).toInt()
                } else {
                    windowCount
                }
                val remainingLines = (totalCount - currentDone).coerceAtLeast(0)
                val etaSec = if (cpm > 0) ((remainingLines * 60L) / cpm.toLong()) else 0L

                val workersPacked = activeBotSlots.entries
                    .sortedBy { it.key }
                    .take(16)
                    .joinToString("\u001E") { it.value }
                val batchResultsPacked = batchResults.joinToString("\u001E")
                val persistedResultsPacked = recentPersistedResults.toList().takeLast(30).joinToString("\u001E")

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
                        elapsedSec = totalElapsedSec,
                        etaSec = etaSec,
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
                        elapsedSeconds = totalElapsedSec,
                        workersPacked = workersPacked,
                        resultsPacked = persistedResultsPacked,
                        lastStatusLog = startLog
                    )
                )

                if (forceNotification || nowMs - lastNotifTimeRef[0] >= 1000L || currentDone >= totalCount) {
                    lastNotifTimeRef[0] = nowMs
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

            val lastNotifRef = longArrayOf(0L)

            coroutineScope {
                val workersFinished = AtomicInteger(0)

                // Dedicated 250ms telemetry ticker for smooth 60fps UI & batched Room writes
                val tickerJob = launch {
                    while (isActive && !isStopped && workersFinished.get() < botsCount) {
                        delay(250L)
                        flushTelemetry(forceNotification = false, lastNotifTimeRef = lastNotifRef)
                    }
                }

                // Launch N persistent bot workers pulling from the lock-free atomic line cursor
                val botJobs = (1..botsCount).map { botId ->
                    launch {
                        try {
                            while (isActive && !isStopped) {
                                val lineIndex = nextLineCursor.getAndIncrement()
                                if (lineIndex >= totalCount) break

                                val rawLine = lines[lineIndex].trim()
                                if (rawLine.isEmpty()) {
                                    badCount.incrementAndGet()
                                    processedCount.incrementAndGet()
                                    completionTimestamps.add(System.currentTimeMillis())
                                    continue
                                }

                                var attempt = 0
                                var usedProxy: ProxyItem? = null
                                var finalResult: HttpCheckResult
                                do {
                                    usedProxy = acquireNextProxy()
                                    val liveProxyLabel = if (usedProxy != null) "${usedProxy.host}:${usedProxy.port}" else "Direct"
                                    val phaseLabel = if (attempt == 0) "CHECKING" else "RETRY #$attempt"
                                    activeBotSlots[botId] = "$botId\u001F${rawLine.take(40)}\u001F$liveProxyLabel\u001F$phaseLabel\u001FRUNNING"

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
                                                // Place proxy in 30-second cooldown and mark BANNED in DB
                                                proxyCooldownUntil[usedProxy.id] = System.currentTimeMillis() + 30_000L
                                                db.proxyDao().updateCheckResult(
                                                    usedProxy.id,
                                                    ProxyStatus.BANNED.name,
                                                    usedProxy.pingMs,
                                                    "HTTP ${finalResult.httpCode}"
                                                )
                                            } else if (failTotal >= 3) {
                                                proxyCooldownUntil[usedProxy.id] = System.currentTimeMillis() + 45_000L
                                                db.proxyDao().updateCheckResult(
                                                    usedProxy.id,
                                                    ProxyStatus.BAD.name,
                                                    null,
                                                    "Timeout"
                                                )
                                            }
                                        }
                                        attempt++
                                    } else {
                                        break
                                    }
                                } while (attempt <= maxRetries)

                                if (usedProxy != null && finalResult.outcomeType != "RETRY" && finalResult.httpCode > 0) {
                                    proxyFailures.remove(usedProxy.id)
                                    proxyCooldownUntil.remove(usedProxy.id)
                                    db.proxyDao().updateCheckResult(
                                        id = usedProxy.id,
                                        status = ProxyStatus.AVAILABLE.name,
                                        pingMs = finalResult.latencyMs.toInt(),
                                        country = usedProxy.country.ifBlank { "Verified" }
                                    )
                                }

                                val proxyLabel = if (usedProxy != null) "${usedProxy.host}:${usedProxy.port}" else "Direct"
                                activeBotSlots[botId] = "$botId\u001F${rawLine.take(40)}\u001F$proxyLabel\u001F${finalResult.status}\u001F${finalResult.outcomeType}"

                                val nowClock = synchronized(timeFmt) { timeFmt.format(Date()) }
                                when (finalResult.outcomeType) {
                                    "HIT" -> {
                                        hitsCount.incrementAndGet()
                                        pendingResultRows.add(
                                            packResultRow(nowClock, rawLine, proxyLabel, finalResult.capturedData, ResultTab.HITS.name)
                                        )
                                        if (autoSaveHits) {
                                            pendingHitEntities.add(
                                                HitDbEntity(
                                                    id = UUID.randomUUID().toString(),
                                                    date = nowClock,
                                                    data = rawLine,
                                                    type = ResultTab.HITS.name,
                                                    configName = config.name,
                                                    wordlistName = wordlist.name,
                                                    capturedData = finalResult.capturedData
                                                )
                                            )
                                        }
                                    }
                                    "CUSTOM" -> {
                                        customCount.incrementAndGet()
                                        pendingResultRows.add(
                                            packResultRow(nowClock, rawLine, proxyLabel, finalResult.capturedData, ResultTab.CUSTOM.name)
                                        )
                                        if (autoSaveHits) {
                                            pendingHitEntities.add(
                                                HitDbEntity(
                                                    id = UUID.randomUUID().toString(),
                                                    date = nowClock,
                                                    data = rawLine,
                                                    type = ResultTab.CUSTOM.name,
                                                    configName = config.name,
                                                    wordlistName = wordlist.name,
                                                    capturedData = finalResult.capturedData
                                                )
                                            )
                                        }
                                    }
                                    "BAD" -> badCount.incrementAndGet()
                                    "RETRY" -> retriesCount.incrementAndGet()
                                    else -> {
                                        toCheckCount.incrementAndGet()
                                        pendingResultRows.add(
                                            packResultRow(nowClock, rawLine, proxyLabel, finalResult.capturedData, ResultTab.TO_CHECK.name)
                                        )
                                    }
                                }

                                processedCount.incrementAndGet()
                                completionTimestamps.add(System.currentTimeMillis())
                            }
                        } finally {
                            activeBotSlots.remove(botId)
                            workersFinished.incrementAndGet()
                        }
                    }
                }

                botJobs.forEach { it.join() }
                tickerJob.cancel()
            }

            // Final flush after all bots complete or stop
            flushTelemetry(forceNotification = true, lastNotifTimeRef = lastNotifRef)

            val sessionElapsedSec = ((System.currentTimeMillis() - runStartMs) / 1000L).coerceAtLeast(0L)
            val totalElapsedSec = initialElapsedSec + sessionElapsedSec
            val checkedInThisSession = (processedCount.get() - startIdx).coerceAtLeast(0)
            val finalCpm = if (sessionElapsedSec > 0) ((checkedInThisSession * 60L) / sessionElapsedSec).toInt() else 0
            val finalLog = if (isStopped) {
                "Runner stopped at line ${processedCount.get()}/$totalCount. Hits: ${hitsCount.get()}."
            } else {
                "Runner finished ${processedCount.get()}/$totalCount. Hits: ${hitsCount.get()}, Custom: ${customCount.get()}."
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
                    elapsedSeconds = totalElapsedSec,
                    workersPacked = "",
                    resultsPacked = recentPersistedResults.toList().takeLast(30).joinToString("\u001E"),
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
                    elapsedSec = totalElapsedSec,
                    etaSec = 0L,
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
        val untested = all.filter { it.status == ProxyStatus.UNTESTED }
        if (untested.isNotEmpty()) return untested
        return all
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
            data.replace("\u001F", " ").take(80),
            proxy.replace("\u001F", " ").take(36),
            captured.replace("\u001F", " ").take(160),
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
        etaSec: Long,
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
            KEY_ETA_SEC to etaSec,
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

        const val KEY_INITIAL_HITS = "initial_hits"
        const val KEY_INITIAL_CUSTOM = "initial_custom"
        const val KEY_INITIAL_BAD = "initial_bad"
        const val KEY_INITIAL_RETRIES = "initial_retries"
        const val KEY_INITIAL_TO_CHECK = "initial_to_check"
        const val KEY_INITIAL_ELAPSED_SEC = "initial_elapsed_sec"

        const val KEY_CURRENT = "current"
        const val KEY_TOTAL = "total"
        const val KEY_HITS = "hits"
        const val KEY_CUSTOM = "custom"
        const val KEY_BAD = "bad"
        const val KEY_RETRIES = "retries"
        const val KEY_TO_CHECK = "to_check"
        const val KEY_CPM = "cpm"
        const val KEY_ELAPSED_SEC = "elapsed_sec"
        const val KEY_ETA_SEC = "eta_sec"
        const val KEY_WORKERS_PACKED = "workers_packed"
        const val KEY_RESULTS_PACKED = "results_packed"
        const val KEY_LOG_LINE = "log_line"
    }
}
