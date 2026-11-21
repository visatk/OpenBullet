package com.example.domain.usecase

import com.example.data.OpenBulletRepository
import com.example.model.ConfigItem
import com.example.model.ProxyMode
import com.example.model.RunnerStats
import com.example.model.WordlistItem

/**
 * Domain UseCase encapsulating Runner job lifecycle actions (start, pause with checkpoint persistence,
 * resume with accumulated counters, and stop) in accordance with Android Domain Layer guidelines.
 */
class ManageRunnerJobUseCase(
    private val repository: OpenBulletRepository
) {
    fun startJob(
        config: ConfigItem,
        wordlist: WordlistItem,
        startLine: Int,
        botsCount: Int,
        proxyMode: ProxyMode
    ) {
        val clampedStart = startLine.coerceIn(1, wordlist.lineCount.coerceAtLeast(1))
        val clampedBots = botsCount.coerceIn(1, 200)
        repository.enqueueRunnerJob(
            configId = config.id,
            wordlistId = wordlist.id,
            startLine = clampedStart,
            botsCount = clampedBots,
            proxyMode = proxyMode
        )
    }

    suspend fun pauseJobAndSaveCheckpoint(
        progressCurrent: Int,
        progressTotal: Int
    ): Int {
        val nextCheckpoint = (progressCurrent + 1).coerceIn(1, progressTotal.coerceAtLeast(1))
        repository.cancelRunnerJob()
        repository.saveSettings { it.copy(startLine = nextCheckpoint) }
        return nextCheckpoint
    }

    fun resumeJob(
        config: ConfigItem,
        wordlist: WordlistItem,
        checkpointLine: Int,
        botsCount: Int,
        proxyMode: ProxyMode,
        accumulatedStats: RunnerStats,
        elapsedSeconds: Long
    ) {
        val validLine = checkpointLine.coerceIn(1, wordlist.lineCount.coerceAtLeast(1))
        repository.enqueueRunnerJob(
            configId = config.id,
            wordlistId = wordlist.id,
            startLine = validLine,
            botsCount = botsCount.coerceIn(1, 200),
            proxyMode = proxyMode,
            initialHits = accumulatedStats.dataHits,
            initialCustom = accumulatedStats.dataCustom,
            initialBad = accumulatedStats.dataBad,
            initialRetries = accumulatedStats.dataRetries,
            initialToCheck = accumulatedStats.dataToCheck,
            initialElapsedSec = elapsedSeconds
        )
    }

    fun stopJob() {
        repository.cancelRunnerJob()
    }
}
