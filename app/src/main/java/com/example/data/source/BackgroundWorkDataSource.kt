package com.example.data.source

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.model.ProxyCheckWorkSnapshot
import com.example.model.ProxyMode
import com.example.model.RunnerWorkSnapshot
import com.example.model.WorkExecutionState
import com.example.worker.ProxyCheckWorker
import com.example.worker.RunnerJobWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Data source encapsulating WorkManager scheduling and telemetry observation for long-running background jobs.
 */
interface BackgroundWorkDataSource {
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

    fun enqueueProxyCheck(probeUrl: String)
    fun cancelProxyCheck()
    fun observeProxyCheckWork(): Flow<ProxyCheckWorkSnapshot?>
}

class WorkManagerBackgroundWorkDataSource(
    private val workManager: WorkManager
) : BackgroundWorkDataSource {

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
}
