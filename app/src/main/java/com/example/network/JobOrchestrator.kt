package com.example.network

import com.example.data.local.ProxyCheckUpdate
import com.example.model.ConfigItem
import com.example.model.HitDbItem
import com.example.model.ProxyItem
import com.example.model.ProxyMode
import com.example.model.ProxyStatus
import com.example.model.ResultTab
import com.example.model.RunnerSessionSnapshot
import com.example.util.DateTimeFormats
import com.example.viewmodel.RunnerPacketHelper
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers

/**
 * Thread-safe Proxy Rotation Pool coordinating round-robin selection, ban/timeout cooldown eviction,
 * per-proxy max-usage limits ([maxProxyUses]), and batched database status updates.
 */
class ProxyRotationPool(
    allProxies: List<ProxyItem>,
    private val maxProxyUses: Int = 0
) {
    val usableProxies: List<ProxyItem> = selectUsableProxies(allProxies)

    private val cursor = AtomicInteger(0)
    private val proxyFailures = ConcurrentHashMap<String, Int>()
    private val proxyUses = ConcurrentHashMap<String, Int>()
    private val proxyCooldownUntil = ConcurrentHashMap<String, Long>()
    private val pendingUpdates = ConcurrentHashMap<String, ProxyCheckUpdate>()

    val usableCount: Int get() = usableProxies.size

    fun acquireNext(enabled: Boolean, nowMs: Long = System.currentTimeMillis()): ProxyItem? {
        if (!enabled || usableProxies.isEmpty()) return null
        val size = usableProxies.size
        for (probe in 0 until size) {
            val idx = (cursor.getAndIncrement() and Int.MAX_VALUE) % size
            val candidate = usableProxies[idx]
            val cooldownExpiry = proxyCooldownUntil[candidate.id] ?: 0L
            val uses = proxyUses[candidate.id] ?: 0
            val underUseLimit = maxProxyUses <= 0 || uses < maxProxyUses
            if (nowMs >= cooldownExpiry && underUseLimit) {
                return candidate
            }
        }
        // All proxies exhausted or on cooldown — reset cycle so workers never deadlock
        proxyCooldownUntil.clear()
        proxyFailures.clear()
        proxyUses.clear()
        val idx = (cursor.getAndIncrement() and Int.MAX_VALUE) % size
        return usableProxies[idx]
    }

    fun recordFailure(proxy: ProxyItem, httpCode: Int, nowMs: Long = System.currentTimeMillis()) {
        val failTotal = (proxyFailures[proxy.id] ?: 0) + 1
        proxyFailures[proxy.id] = failTotal
        if (httpCode == 403 || httpCode == 429) {
            proxyCooldownUntil[proxy.id] = nowMs + BAN_COOLDOWN_MS
            pendingUpdates[proxy.id] = ProxyCheckUpdate(
                id = proxy.id,
                status = ProxyStatus.BANNED.name,
                pingMs = proxy.pingMs,
                country = "HTTP $httpCode"
            )
        } else if (failTotal >= MAX_CONSECUTIVE_FAILURES) {
            proxyCooldownUntil[proxy.id] = nowMs + TIMEOUT_COOLDOWN_MS
            pendingUpdates[proxy.id] = ProxyCheckUpdate(
                id = proxy.id,
                status = ProxyStatus.BAD.name,
                pingMs = null,
                country = "Timeout"
            )
        }
    }

    fun recordSuccess(proxy: ProxyItem, latencyMs: Long) {
        proxyFailures.remove(proxy.id)
        proxyCooldownUntil.remove(proxy.id)
        proxyUses.compute(proxy.id) { _, count -> (count ?: 0) + 1 }
        pendingUpdates[proxy.id] = ProxyCheckUpdate(
            id = proxy.id,
            status = ProxyStatus.AVAILABLE.name,
            pingMs = latencyMs.toInt().coerceAtLeast(1),
            country = proxy.country.ifBlank { "${proxy.protocol.label} Verified" }
        )
    }

    fun drainPendingUpdates(): List<ProxyCheckUpdate> {
        if (pendingUpdates.isEmpty()) return emptyList()
        val out = mutableListOf<ProxyCheckUpdate>()
        val keys = pendingUpdates.keys().toList()
        for (key in keys) {
            pendingUpdates.remove(key)?.let { out.add(it) }
        }
        return out
    }

    companion object {
        private const val BAN_COOLDOWN_MS = 30_000L
        private const val TIMEOUT_COOLDOWN_MS = 45_000L
        private const val MAX_CONSECUTIVE_FAILURES = 3

        fun selectUsableProxies(all: List<ProxyItem>): List<ProxyItem> {
            val alive = all.filter { it.status == ProxyStatus.AVAILABLE }
            if (alive.isNotEmpty()) return alive
            val untested = all.filter { it.status == ProxyStatus.UNTESTED }
            if (untested.isNotEmpty()) return untested
            return all
        }
    }
}

/**
 * Drained telemetry and persistence batch produced by [JobOrchestrator.drainTelemetry].
 */
data class JobTelemetryBatch(
    val currentDone: Int,
    val totalCount: Int,
    val hits: Int,
    val custom: Int,
    val bad: Int,
    val retries: Int,
    val toCheck: Int,
    val cpm: Int,
    val activeDoP: Int,
    val targetDoP: Int,
    val totalElapsedSec: Long,
    val etaSec: Long,
    val workersPacked: String,
    val batchResultsPacked: String,
    val persistedResultsPacked: String,
    val newHitsToPersist: List<HitDbItem>,
    val proxyUpdatesToPersist: List<ProxyCheckUpdate>,
    val sessionSnapshot: RunnerSessionSnapshot,
    val dynamicLogLine: String?
)

/**
 * Central Job Orchestrator coordinating:
 * 1. **Wordlist / Input Loop** (cursor-based slicing, empty-line handling, progress & ETA)
 * 2. **Proxy Rotation** ([ProxyRotationPool] with round-robin, ban cooldowns, and max-uses rotation)
 * 3. **Dynamic Parallelization (DoP)** ([DynamicParallelTaskEngine] with live scaling and AIMD backpressure)
 * 4. **Multi-Part Config Block-Pipeline Execution** ([HttpExecutionEngine] with per-config settings & raw TCP/HTTP/SOCKS)
 * 5. **Progress & Monitoring UI Telemetry** (live worker slots, captured results, CPM, and session snapshots)
 */
class JobOrchestrator(
    private val config: ConfigItem,
    private val wordlistId: String,
    private val wordlistName: String,
    private val lines: List<String>,
    startLine: Int,
    initialBotsCount: Int,
    initialProxyMode: ProxyMode,
    allProxies: List<ProxyItem>,
    globalTimeoutSec: Int = 10,
    globalMaxRetries: Int = 3,
    private val autoSaveHits: Boolean = true,
    private val ignoreEmptyLines: Boolean = true,
    initialHits: Int = 0,
    initialCustom: Int = 0,
    initialBad: Int = 0,
    initialRetries: Int = 0,
    initialToCheck: Int = 0,
    private val initialElapsedSec: Long = 0L,
    dispatcher: CoroutineContext = Dispatchers.IO,
    private val blockExecutor: (
        rawLine: String,
        script: ParsedLoliScript,
        timeoutSec: Int,
        proxyItem: ProxyItem?,
        customUserAgent: String
    ) -> HttpCheckResult = { rawLine, script, timeout, proxy, ua ->
        HttpExecutionEngine.executeCheck(
            rawLine = rawLine,
            script = script,
            timeoutSec = timeout,
            proxyItem = proxy,
            customUserAgent = ua
        )
    }
) {
    val totalCount: Int = lines.size
    val startIdx: Int = if (lines.isEmpty()) 0 else (startLine - 1).coerceIn(0, lines.lastIndex)

    // Multi-part Config settings resolution (config-specific settings override or refine global defaults)
    private val execSettings = config.executionSettings
    val effectiveTimeoutSec: Int = (if (execSettings.timeoutSec > 0) execSettings.timeoutSec else globalTimeoutSec)
        .coerceIn(1, 120)
    val effectiveMaxRetries: Int = (if (config.maxRetries >= 0) execSettings.maxRetries else globalMaxRetries)
        .coerceIn(0, 15)
    val customUserAgent: String = execSettings.customUserAgent

    val parsedScript: ParsedLoliScript = LoliScriptParser.parse(config.scriptPreview)

    val proxyPool = ProxyRotationPool(
        allProxies = allProxies,
        maxProxyUses = execSettings.maxProxyUses
    )

    val taskEngine = DynamicParallelTaskEngine<String>(
        initialDoP = initialBotsCount.coerceIn(1, DynamicParallelTaskEngine.MAX_DOP_LIMIT),
        minDoP = 1,
        maxDoP = DynamicParallelTaskEngine.MAX_DOP_LIMIT,
        adaptiveScalingEnabled = true,
        dispatcher = dispatcher
    )

    private val liveProxyMode = AtomicReference(initialProxyMode)
    private val processedCount = AtomicInteger(startIdx)
    private val hitsCount = AtomicInteger(initialHits)
    private val customCount = AtomicInteger(initialCustom)
    private val badCount = AtomicInteger(initialBad)
    private val retriesCount = AtomicInteger(initialRetries)
    private val toCheckCount = AtomicInteger(initialToCheck)

    private val activeBotSlots = ConcurrentHashMap<Int, String>()
    private val pendingResultRows = ConcurrentLinkedQueue<String>()
    private val recentPersistedResults = ConcurrentLinkedQueue<String>()
    private val pendingHitItems = ConcurrentLinkedQueue<HitDbItem>()
    private val completionTimestamps = ConcurrentLinkedQueue<Long>()
    private val dynamicLogQueue = ConcurrentLinkedQueue<String>()

    private var runStartMs: Long = System.currentTimeMillis()

    val currentProcessedCount: Int get() = processedCount.get()
    val currentHitsCount: Int get() = hitsCount.get()
    val currentCustomCount: Int get() = customCount.get()
    val currentBadCount: Int get() = badCount.get()
    val currentRetriesCount: Int get() = retriesCount.get()
    val currentToCheckCount: Int get() = toCheckCount.get()

    fun shouldUseProxies(): Boolean {
        val mode = liveProxyMode.get()
        return (mode == ProxyMode.ON || (mode == ProxyMode.DEF && execSettings.needsProxies)) &&
            proxyPool.usableCount > 0
    }

    fun updateTargetDoP(newBotsCount: Int) {
        val clamped = newBotsCount.coerceIn(1, DynamicParallelTaskEngine.MAX_DOP_LIMIT)
        val prev = taskEngine.currentTargetDoP
        if (clamped != prev) {
            taskEngine.setTargetDoP(clamped)
            dynamicLogQueue.offer("Dynamic DoP scaled from $prev -> $clamped active bots.")
        }
    }

    fun updateProxyMode(newMode: ProxyMode) {
        val prev = liveProxyMode.getAndSet(newMode)
        if (prev != newMode) {
            dynamicLogQueue.offer("Live proxy mode switched to ${newMode.label}.")
        }
    }

    /**
     * Runs the complete orchestrated job across the wordlist input loop, proxy rotation pool,
     * dynamic DoP engine, and multi-block script pipeline.
     */
    suspend fun execute(shouldContinue: () -> Boolean = { true }) {
        runStartMs = System.currentTimeMillis()
        taskEngine.executeAll(
            items = lines,
            startIndex = startIdx,
            shouldContinue = shouldContinue,
            onWorkerExit = { slotId -> activeBotSlots.remove(slotId) }
        ) { botId, _, rawEntry ->
            val rawLine = rawEntry.trim()
            if (rawLine.isEmpty()) {
                if (!ignoreEmptyLines) {
                    badCount.incrementAndGet()
                }
                processedCount.incrementAndGet()
                completionTimestamps.add(System.currentTimeMillis())
                return@executeAll
            }

            var attempt = 0
            var usedProxy: ProxyItem?
            var finalResult: HttpCheckResult
            do {
                usedProxy = proxyPool.acquireNext(enabled = shouldUseProxies())
                val liveProxyLabel = if (usedProxy != null) {
                    "${usedProxy.protocol.label}://${usedProxy.host}:${usedProxy.port}"
                } else "Direct"
                val phaseLabel = if (attempt == 0) "CHECKING" else "RETRY #$attempt"
                activeBotSlots[botId] = "$botId\u001F${rawLine.take(40)}\u001F$liveProxyLabel\u001F$phaseLabel\u001FRUNNING"

                finalResult = blockExecutor(
                    rawLine,
                    parsedScript,
                    effectiveTimeoutSec,
                    usedProxy,
                    customUserAgent
                )

                val isCongested = finalResult.outcomeType == "RETRY" ||
                    finalResult.httpCode == 429 ||
                    finalResult.httpCode == 0
                taskEngine.recordTaskOutcome(
                    latencyMs = finalResult.latencyMs,
                    isCongestedOrRateLimited = isCongested
                )

                if (finalResult.outcomeType == "RETRY" && attempt < effectiveMaxRetries && shouldContinue()) {
                    retriesCount.incrementAndGet()
                    if (usedProxy != null) {
                        proxyPool.recordFailure(usedProxy, finalResult.httpCode)
                    }
                    attempt++
                } else {
                    break
                }
            } while (attempt <= effectiveMaxRetries)

            if (usedProxy != null && finalResult.outcomeType != "RETRY" && finalResult.httpCode > 0) {
                proxyPool.recordSuccess(usedProxy, finalResult.latencyMs)
            }

            val proxyLabel = if (usedProxy != null) {
                "${usedProxy.protocol.label}://${usedProxy.host}:${usedProxy.port}"
            } else "Direct"
            activeBotSlots[botId] = "$botId\u001F${rawLine.take(40)}\u001F$proxyLabel\u001F${finalResult.status}\u001F${finalResult.outcomeType}"

            val nowClock = DateTimeFormats.formatClockNow()
            when (finalResult.outcomeType) {
                "HIT" -> {
                    hitsCount.incrementAndGet()
                    pendingResultRows.add(
                        packResultRow(nowClock, rawLine, proxyLabel, finalResult.capturedData, ResultTab.HITS.name)
                    )
                    if (autoSaveHits) {
                        pendingHitItems.add(
                            HitDbItem(
                                id = UUID.randomUUID().toString(),
                                date = nowClock,
                                data = rawLine,
                                type = ResultTab.HITS,
                                configName = config.name,
                                wordlistName = wordlistName,
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
                        pendingHitItems.add(
                            HitDbItem(
                                id = UUID.randomUUID().toString(),
                                date = nowClock,
                                data = rawLine,
                                type = ResultTab.CUSTOM,
                                configName = config.name,
                                wordlistName = wordlistName,
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
    }

    /**
     * Drains buffered Hits, Proxy status updates, and Result rows, and computes current
     * throughput (CPM), ETA, active worker slots, and a [RunnerSessionSnapshot].
     */
    fun drainTelemetry(
        markRunning: Boolean,
        fallbackStatusLog: String
    ): JobTelemetryBatch {
        val dynamicLog = dynamicLogQueue.poll()
        val effectiveLog = dynamicLog ?: fallbackStatusLog

        val toInsertHits = mutableListOf<HitDbItem>()
        while (true) {
            val item = pendingHitItems.poll() ?: break
            toInsertHits.add(item)
        }

        val proxyUpdates = proxyPool.drainPendingUpdates()

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

        val workersPacked = if (markRunning) {
            RunnerPacketHelper.packWithByteBudget(
                entries = activeBotSlots.entries.sortedBy { it.key }.take(16).map { it.value },
                maxBytes = MAX_WORKERS_BYTES
            )
        } else ""

        val batchResultsPacked = RunnerPacketHelper.packWithByteBudget(
            entries = batchResults,
            maxBytes = MAX_RESULTS_BYTES
        )
        val persistedResultsPacked = RunnerPacketHelper.packWithByteBudget(
            entries = recentPersistedResults.toList().takeLast(30),
            maxBytes = MAX_RESULTS_BYTES
        )

        val activeDoP = if (markRunning) taskEngine.currentEffectiveDoP else 0
        val targetDoP = taskEngine.currentTargetDoP

        val sessionSnapshot = RunnerSessionSnapshot(
            configId = config.id,
            wordlistId = wordlistId,
            isRunning = markRunning,
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
            lastStatusLog = effectiveLog
        )

        return JobTelemetryBatch(
            currentDone = currentDone,
            totalCount = totalCount,
            hits = hitsCount.get(),
            custom = customCount.get(),
            bad = badCount.get(),
            retries = retriesCount.get(),
            toCheck = toCheckCount.get(),
            cpm = cpm,
            activeDoP = activeDoP,
            targetDoP = targetDoP,
            totalElapsedSec = totalElapsedSec,
            etaSec = etaSec,
            workersPacked = workersPacked,
            batchResultsPacked = batchResultsPacked,
            persistedResultsPacked = persistedResultsPacked,
            newHitsToPersist = toInsertHits,
            proxyUpdatesToPersist = proxyUpdates,
            sessionSnapshot = sessionSnapshot,
            dynamicLogLine = dynamicLog
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
            data.replace("\u001F", " ").take(80),
            proxy.replace("\u001F", " ").take(44),
            captured.replace("\u001F", " ").take(160),
            category
        ).joinToString("\u001F")
    }

    companion object {
        private const val MAX_WORKERS_BYTES = 2500
        private const val MAX_RESULTS_BYTES = 5000
    }
}
