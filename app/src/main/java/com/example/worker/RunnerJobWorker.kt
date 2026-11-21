package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.local.OpenBulletDatabase
import com.example.data.local.WordlistFileStore
import com.example.data.source.LocalWorkspaceDataSource
import com.example.data.source.RoomWorkspaceDataSource
import com.example.di.DefaultDispatcherProvider
import com.example.model.ProxyMode
import com.example.model.RunnerSessionSnapshot
import com.example.network.JobOrchestrator
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RunnerJobWorker(
    appContext: Context,
    params: WorkerParameters,
    private val localDataSource: LocalWorkspaceDataSource
) : CoroutineWorker(appContext, params) {

    constructor(appContext: Context, params: WorkerParameters) : this(
        appContext = appContext,
        params = params,
        localDataSource = RoomWorkspaceDataSource(
            db = OpenBulletDatabase.getInstance(appContext),
            wordlistFileStore = WordlistFileStore(appContext),
            dispatchers = DefaultDispatcherProvider()
        )
    )

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return BackgroundKeepAliveHelper.buildForegroundInfo(
            context = applicationContext,
            workId = id,
            notificationId = BackgroundKeepAliveHelper.RUNNER_NOTIFICATION_ID,
            title = "OpenBullet • Runner",
            contentText = "Running Job Orchestrator...",
            progressCurrent = 0,
            progressTotal = 0
        )
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val configId = inputData.getString(KEY_CONFIG_ID)
            ?: return@withContext Result.failure(workDataOf(KEY_LOG_LINE to "Missing config ID."))
        val wordlistId = inputData.getString(KEY_WORDLIST_ID)
            ?: return@withContext Result.failure(workDataOf(KEY_LOG_LINE to "Missing wordlist ID."))
        val startLine = inputData.getInt(KEY_START_LINE, 1)
        val initialBotsCount = inputData.getInt(KEY_BOTS_COUNT, 1).coerceIn(1, 200)
        val proxyModeStr = inputData.getString(KEY_PROXY_MODE) ?: ProxyMode.DEF.name
        val initialProxyMode = runCatching { ProxyMode.valueOf(proxyModeStr) }.getOrDefault(ProxyMode.DEF)

        val initialHits = inputData.getInt(KEY_INITIAL_HITS, 0)
        val initialCustom = inputData.getInt(KEY_INITIAL_CUSTOM, 0)
        val initialBad = inputData.getInt(KEY_INITIAL_BAD, 0)
        val initialRetries = inputData.getInt(KEY_INITIAL_RETRIES, 0)
        val initialToCheck = inputData.getInt(KEY_INITIAL_TO_CHECK, 0)
        val initialElapsedSec = inputData.getLong(KEY_INITIAL_ELAPSED_SEC, 0L)

        val config = localDataSource.getConfigById(configId)
            ?: return@withContext Result.failure(workDataOf(KEY_LOG_LINE to "Selected config no longer exists."))
        val wordlist = localDataSource.getWordlistById(wordlistId)
            ?: return@withContext Result.failure(workDataOf(KEY_LOG_LINE to "Selected wordlist no longer exists."))

        val settings = localDataSource.getSettingsSnapshot()
        val globalTimeoutSec = settings?.requestTimeoutSec ?: 10
        val globalMaxRetries = (settings?.maxRetries ?: 3).coerceIn(0, 15)
        val autoSaveHits = settings?.autoSaveHits ?: true
        val ignoreEmptyLines = settings?.ignoreEmptyLines ?: true
        val keepAwake = settings?.keepAwakeEnabled ?: true

        val lines = localDataSource.readWordlistLines(wordlist.id)
        if (lines.isEmpty()) {
            return@withContext Result.success(
                workDataOf(KEY_LOG_LINE to "Wordlist '${wordlist.name}' has 0 lines.")
            )
        }

        val totalCount = lines.size
        val startIdx = (startLine - 1).coerceIn(0, lines.lastIndex)

        try {
            setForeground(
                BackgroundKeepAliveHelper.buildForegroundInfo(
                    context = applicationContext,
                    workId = id,
                    notificationId = BackgroundKeepAliveHelper.RUNNER_NOTIFICATION_ID,
                    title = "OpenBullet • ${config.name}",
                    contentText = "Orchestrating DoP=$initialBotsCount on ${wordlist.name}...",
                    progressCurrent = startIdx,
                    progressTotal = totalCount
                )
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            // Continue in case foreground promotion is restricted
        }

        BackgroundKeepAliveHelper.withKeepAwakeLocks(applicationContext, enabled = keepAwake) {
            val allProxies = localDataSource.getAllProxiesSnapshot()
            val orchestrator = JobOrchestrator(
                config = config,
                wordlistId = wordlist.id,
                wordlistName = wordlist.name,
                lines = lines,
                startLine = startLine,
                initialBotsCount = initialBotsCount,
                initialProxyMode = initialProxyMode,
                allProxies = allProxies,
                globalTimeoutSec = globalTimeoutSec,
                globalMaxRetries = globalMaxRetries,
                autoSaveHits = autoSaveHits,
                ignoreEmptyLines = ignoreEmptyLines,
                initialHits = initialHits,
                initialCustom = initialCustom,
                initialBad = initialBad,
                initialRetries = initialRetries,
                initialToCheck = initialToCheck,
                initialElapsedSec = initialElapsedSec,
                dispatcher = Dispatchers.IO
            )

            val runStartMs = System.currentTimeMillis()
            val modeDesc = if (initialElapsedSec > 0L) "Resumed" else "Started"
            val startLog = "$modeDesc JobOrchestrator (${config.name} v${config.version}, DoP=$initialBotsCount, line ${startIdx + 1}/$totalCount, proxies=${if (orchestrator.shouldUseProxies()) orchestrator.proxyPool.usableCount else 0})."

            setProgress(
                buildProgressData(
                    current = startIdx,
                    total = totalCount,
                    hits = orchestrator.currentHitsCount,
                    custom = orchestrator.currentCustomCount,
                    bad = orchestrator.currentBadCount,
                    retries = orchestrator.currentRetriesCount,
                    toCheck = orchestrator.currentToCheckCount,
                    cpm = 0,
                    activeDoP = orchestrator.taskEngine.currentEffectiveDoP,
                    targetDoP = orchestrator.taskEngine.currentTargetDoP,
                    elapsedSec = initialElapsedSec,
                    etaSec = 0L,
                    workersPacked = "",
                    resultsPacked = "",
                    logLine = startLog
                )
            )
            localDataSource.upsertRunnerSession(
                RunnerSessionSnapshot(
                    configId = configId,
                    wordlistId = wordlistId,
                    isRunning = true,
                    progressCurrent = startIdx,
                    progressTotal = totalCount,
                    hits = orchestrator.currentHitsCount,
                    custom = orchestrator.currentCustomCount,
                    bad = orchestrator.currentBadCount,
                    retries = orchestrator.currentRetriesCount,
                    toCheck = orchestrator.currentToCheckCount,
                    elapsedSeconds = initialElapsedSec,
                    lastStatusLog = startLog
                )
            )

            suspend fun persistAndPublishTelemetry(
                markRunning: Boolean,
                fallbackLog: String,
                forceNotification: Boolean,
                lastNotifTimeRef: LongArray
            ) {
                val batch = orchestrator.drainTelemetry(
                    markRunning = markRunning,
                    fallbackStatusLog = fallbackLog
                )
                if (batch.newHitsToPersist.isNotEmpty()) {
                    localDataSource.insertHitsBatch(batch.newHitsToPersist)
                }
                if (batch.proxyUpdatesToPersist.isNotEmpty()) {
                    localDataSource.updateProxyCheckResultsBatch(batch.proxyUpdatesToPersist)
                }
                localDataSource.upsertRunnerSession(batch.sessionSnapshot)

                if (markRunning) {
                    setProgress(
                        buildProgressData(
                            current = batch.currentDone,
                            total = batch.totalCount,
                            hits = batch.hits,
                            custom = batch.custom,
                            bad = batch.bad,
                            retries = batch.retries,
                            toCheck = batch.toCheck,
                            cpm = batch.cpm,
                            activeDoP = batch.activeDoP,
                            targetDoP = batch.targetDoP,
                            elapsedSec = batch.totalElapsedSec,
                            etaSec = batch.etaSec,
                            workersPacked = batch.workersPacked,
                            resultsPacked = batch.batchResultsPacked,
                            logLine = batch.dynamicLogLine
                        )
                    )

                    val nowMs = System.currentTimeMillis()
                    if (forceNotification || nowMs - lastNotifTimeRef[0] >= 1000L || batch.currentDone >= totalCount) {
                        lastNotifTimeRef[0] = nowMs
                        val pct = if (totalCount > 0) (batch.currentDone * 100) / totalCount else 0
                        try {
                            setForeground(
                                BackgroundKeepAliveHelper.buildForegroundInfo(
                                    context = applicationContext,
                                    workId = id,
                                    notificationId = BackgroundKeepAliveHelper.RUNNER_NOTIFICATION_ID,
                                    title = "OpenBullet • ${config.name} ($pct%)",
                                    contentText = "${batch.currentDone}/$totalCount • DoP: ${batch.activeDoP}/${batch.targetDoP} • Hits: ${batch.hits} • CPM: ${batch.cpm}",
                                    progressCurrent = batch.currentDone,
                                    progressTotal = totalCount
                                )
                            )
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (_: Throwable) {
                            // Non-fatal notification update failure
                        }
                    }
                }
            }

            val lastNotifRef = longArrayOf(0L)

            try {
                coroutineScope {
                    val poolCompleted = AtomicInteger(0)

                    val settingsWatcherJob = launch {
                        localDataSource.settingsFlow.collect { latestSettings ->
                            if (latestSettings == null) return@collect
                            orchestrator.updateTargetDoP(latestSettings.botsCount)
                            orchestrator.updateProxyMode(latestSettings.proxyMode)
                        }
                    }

                    val tickerJob = launch {
                        while (isActive && !isStopped && poolCompleted.get() == 0) {
                            delay(250L)
                            persistAndPublishTelemetry(
                                markRunning = true,
                                fallbackLog = startLog,
                                forceNotification = false,
                                lastNotifTimeRef = lastNotifRef
                            )
                        }
                    }

                    orchestrator.execute(shouldContinue = { !isStopped })

                    poolCompleted.set(1)
                    settingsWatcherJob.cancel()
                    tickerJob.cancel()
                }
            } finally {
                withContext(NonCancellable) {
                    val sessionElapsedSec = ((System.currentTimeMillis() - runStartMs) / 1000L).coerceAtLeast(0L)
                    val totalElapsedSec = initialElapsedSec + sessionElapsedSec
                    val checkedInThisSession = (orchestrator.currentProcessedCount - startIdx).coerceAtLeast(0)
                    val finalCpm = if (sessionElapsedSec > 0) ((checkedInThisSession * 60L) / sessionElapsedSec).toInt() else 0
                    val finalLog = if (isStopped) {
                        "Runner stopped at line ${orchestrator.currentProcessedCount}/$totalCount. Hits: ${orchestrator.currentHitsCount}."
                    } else {
                        "Runner finished ${orchestrator.currentProcessedCount}/$totalCount. Hits: ${orchestrator.currentHitsCount}, Custom: ${orchestrator.currentCustomCount}."
                    }
                    val drained = orchestrator.drainTelemetry(
                        markRunning = false,
                        fallbackStatusLog = finalLog
                    )
                    if (drained.newHitsToPersist.isNotEmpty()) {
                        localDataSource.insertHitsBatch(drained.newHitsToPersist)
                    }
                    if (drained.proxyUpdatesToPersist.isNotEmpty()) {
                        localDataSource.updateProxyCheckResultsBatch(drained.proxyUpdatesToPersist)
                    }
                    localDataSource.upsertRunnerSession(
                        drained.sessionSnapshot.copy(
                            isRunning = false,
                            cpm = finalCpm,
                            elapsedSeconds = totalElapsedSec,
                            workersPacked = "",
                            lastStatusLog = finalLog
                        )
                    )
                }
            }

            val sessionElapsedSec = ((System.currentTimeMillis() - runStartMs) / 1000L).coerceAtLeast(0L)
            val totalElapsedSec = initialElapsedSec + sessionElapsedSec
            val checkedInThisSession = (orchestrator.currentProcessedCount - startIdx).coerceAtLeast(0)
            val finalCpm = if (sessionElapsedSec > 0) ((checkedInThisSession * 60L) / sessionElapsedSec).toInt() else 0
            val finalLog = if (isStopped) {
                "Runner stopped at line ${orchestrator.currentProcessedCount}/$totalCount. Hits: ${orchestrator.currentHitsCount}."
            } else {
                "Runner finished ${orchestrator.currentProcessedCount}/$totalCount. Hits: ${orchestrator.currentHitsCount}, Custom: ${orchestrator.currentCustomCount}."
            }

            Result.success(
                buildProgressData(
                    current = orchestrator.currentProcessedCount,
                    total = totalCount,
                    hits = orchestrator.currentHitsCount,
                    custom = orchestrator.currentCustomCount,
                    bad = orchestrator.currentBadCount,
                    retries = orchestrator.currentRetriesCount,
                    toCheck = orchestrator.currentToCheckCount,
                    cpm = finalCpm,
                    activeDoP = 0,
                    targetDoP = orchestrator.taskEngine.currentTargetDoP,
                    elapsedSec = totalElapsedSec,
                    etaSec = 0L,
                    workersPacked = "",
                    resultsPacked = "",
                    logLine = finalLog
                )
            )
        }
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
        activeDoP: Int,
        targetDoP: Int,
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
            KEY_ACTIVE_DOP to activeDoP,
            KEY_TARGET_DOP to targetDoP,
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
        const val KEY_ACTIVE_DOP = "active_dop"
        const val KEY_TARGET_DOP = "target_dop"
        const val KEY_ELAPSED_SEC = "elapsed_sec"
        const val KEY_ETA_SEC = "eta_sec"
        const val KEY_WORKERS_PACKED = "workers_packed"
        const val KEY_RESULTS_PACKED = "results_packed"
        const val KEY_LOG_LINE = "log_line"
    }
}
