package com.example.network

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Snapshot of live concurrency and throughput telemetry from [DynamicParallelTaskEngine].
 */
data class DoPTelemetrySnapshot(
    val targetDoP: Int,
    val effectiveDoP: Int,
    val activeWorkers: Int,
    val inFlightTasks: Int,
    val completedTasks: Int,
    val totalTasks: Int,
    val avgLatencyMs: Long,
    val throttleRatioPermille: Int
)

/**
 * High-throughput asynchronous task execution engine with a dynamically adjustable
 * Degree of Parallelism (DoP) and AIMD (Additive Increase / Multiplicative Decrease)
 * adaptive backpressure controller.
 *
 * Key capabilities:
 * - **Live Scale-Up & Scale-Down**: Calling [setTargetDoP] while tasks are running immediately
 *   spawns additional worker coroutines when scaling up, or gracefully retires excess worker
 *   coroutines after their current task completes when scaling down.
 * - **Adaptive Backpressure (AIMD)**: Automatically dampens effective DoP during severe timeout
 *   or HTTP 429 rate-limit bursts to prevent socket starvation, and smoothly recovers back to
 *   [targetDoP] as healthy completions arrive.
 * - **Compact Worker Slot IDs**: Assigns lowest available worker slot IDs (`1..N`) so UI telemetry
 *   tables always display compact worker rows.
 */
class DynamicParallelTaskEngine<T>(
    initialDoP: Int,
    private val minDoP: Int = 1,
    private val maxDoP: Int = MAX_DOP_LIMIT,
    private val adaptiveScalingEnabled: Boolean = true,
    private val dispatcher: CoroutineContext = Dispatchers.IO
) {
    private val clampedInitial = initialDoP.coerceIn(minDoP, maxDoP)

    private val _targetDoP = MutableStateFlow(clampedInitial)
    val targetDoPFlow: StateFlow<Int> = _targetDoP.asStateFlow()

    private val _effectiveDoP = MutableStateFlow(clampedInitial)
    val effectiveDoPFlow: StateFlow<Int> = _effectiveDoP.asStateFlow()

    private val _activeWorkers = MutableStateFlow(0)
    val activeWorkersFlow: StateFlow<Int> = _activeWorkers.asStateFlow()

    private val activeWorkerCount = AtomicInteger(0)
    private val inFlightCount = AtomicInteger(0)
    private val completedCount = AtomicInteger(0)
    private val nextTaskCursor = AtomicInteger(0)
    private val isExhausted = AtomicBoolean(false)

    // AIMD adaptive scaling state (1000 = 100% of targetDoP)
    private val adaptiveScalePermille = AtomicInteger(1000)
    private val ewmaLatencyMs = AtomicLong(0L)
    private val ewmaThrottlePermille = AtomicInteger(0)

    private val availableSlotIds = PriorityBlockingQueue<Int>(maxDoP).apply {
        for (id in 1..maxDoP) add(id)
    }
    private val activeWorkerJobs = ConcurrentHashMap<Int, Job>()
    private val scaleSignal = Channel<Unit>(Channel.CONFLATED)

    val currentTargetDoP: Int get() = _targetDoP.value
    val currentEffectiveDoP: Int get() = _effectiveDoP.value
    val currentActiveWorkers: Int get() = _activeWorkers.value

    /**
     * Dynamically updates the target Degree of Parallelism at runtime.
     * Can be invoked from any thread while [executeAll] is running.
     */
    fun setTargetDoP(newDoP: Int) {
        val clamped = newDoP.coerceIn(minDoP, maxDoP)
        val previous = _targetDoP.value
        _targetDoP.value = clamped
        // Reset adaptive penalty when user explicitly adjusts DoP
        adaptiveScalePermille.set(1000)
        recomputeEffectiveDoP()
        if (clamped != previous || activeWorkerCount.get() < _effectiveDoP.value) {
            scaleSignal.trySend(Unit)
        }
    }

    /**
     * Records task execution telemetry so the AIMD adaptive controller can adjust
     * [effectiveDoPFlow] under heavy congestion or rate limiting.
     */
    fun recordTaskOutcome(latencyMs: Long, isCongestedOrRateLimited: Boolean) {
        val safeLatency = latencyMs.coerceAtLeast(0L)
        while (true) {
            val prev = ewmaLatencyMs.get()
            val next = if (prev == 0L) safeLatency else ((prev * 7L + safeLatency * 3L) / 10L)
            if (ewmaLatencyMs.compareAndSet(prev, next)) break
        }

        val samplePermille = if (isCongestedOrRateLimited) 1000 else 0
        var updatedThrottle: Int
        while (true) {
            val prev = ewmaThrottlePermille.get()
            updatedThrottle = (prev * 8 + samplePermille * 2) / 10
            if (ewmaThrottlePermille.compareAndSet(prev, updatedThrottle)) break
        }

        if (!adaptiveScalingEnabled) return

        val currentScale = adaptiveScalePermille.get()
        val nextScale = when {
            // Multiplicative decrease when throttle/timeout ratio exceeds 55%
            isCongestedOrRateLimited && updatedThrottle >= 550 -> {
                (currentScale * 85 / 100).coerceAtLeast(MIN_ADAPTIVE_SCALE_PERMILLE)
            }
            // Additive increase when healthy responses keep throttle ratio below 25%
            !isCongestedOrRateLimited && updatedThrottle <= 250 && currentScale < 1000 -> {
                (currentScale + 50).coerceAtMost(1000)
            }
            else -> currentScale
        }

        if (nextScale != currentScale && adaptiveScalePermille.compareAndSet(currentScale, nextScale)) {
            val oldEffective = _effectiveDoP.value
            val newEffective = recomputeEffectiveDoP()
            if (newEffective > oldEffective) {
                scaleSignal.trySend(Unit)
            }
        }
    }

    fun snapshotTelemetry(totalTasks: Int): DoPTelemetrySnapshot = DoPTelemetrySnapshot(
        targetDoP = _targetDoP.value,
        effectiveDoP = _effectiveDoP.value,
        activeWorkers = activeWorkerCount.get(),
        inFlightTasks = inFlightCount.get(),
        completedTasks = completedCount.get(),
        totalTasks = totalTasks,
        avgLatencyMs = ewmaLatencyMs.get(),
        throttleRatioPermille = ewmaThrottlePermille.get()
    )

    /**
     * Executes [items] starting at [startIndex] using a dynamically scaled pool of coroutines.
     *
     * @param items Random-access list of tasks to process.
     * @param startIndex 0-based index to start processing from.
     * @param shouldContinue Optional predicate checked before claiming each task.
     * @param onWorkerExit Optional callback invoked whenever a worker slot retires.
     * @param processor Suspending lambda receiving `(workerSlotId, taskIndex, item)`.
     */
    suspend fun executeAll(
        items: List<T>,
        startIndex: Int = 0,
        shouldContinue: () -> Boolean = { true },
        onWorkerExit: (workerSlotId: Int) -> Unit = {},
        processor: suspend (workerSlotId: Int, taskIndex: Int, item: T) -> Unit
    ) {
        val total = items.size
        val clampedStart = startIndex.coerceIn(0, total)
        nextTaskCursor.set(clampedStart)
        completedCount.set(clampedStart)
        isExhausted.set(clampedStart >= total)

        if (clampedStart >= total) return

        coroutineScope {
            val supervisorScope = this

            fun spawnWorkersIfNeeded() {
                while (
                    supervisorScope.isActive &&
                    !isExhausted.get() &&
                    shouldContinue()
                ) {
                    val desired = _effectiveDoP.value.coerceIn(minDoP, maxDoP)
                    val current = activeWorkerCount.get()
                    if (current >= desired) break
                    if (nextTaskCursor.get() >= total) {
                        isExhausted.set(true)
                        break
                    }
                    if (!activeWorkerCount.compareAndSet(current, current + 1)) continue

                    _activeWorkers.value = activeWorkerCount.get()
                    val slotId = availableSlotIds.poll() ?: (current + 1)

                    val job = supervisorScope.launch(dispatcher) {
                        try {
                            while (isActive && shouldContinue()) {
                                // Check if DoP was scaled down and this worker should retire
                                val effectiveLimit = _effectiveDoP.value.coerceIn(minDoP, maxDoP)
                                val activeNow = activeWorkerCount.get()
                                if (activeNow > effectiveLimit && slotId > effectiveLimit) {
                                    break
                                }

                                val taskIndex = nextTaskCursor.getAndIncrement()
                                if (taskIndex >= total) {
                                    isExhausted.set(true)
                                    break
                                }

                                inFlightCount.incrementAndGet()
                                try {
                                    processor(slotId, taskIndex, items[taskIndex])
                                } finally {
                                    inFlightCount.decrementAndGet()
                                    completedCount.incrementAndGet()
                                }
                            }
                        } finally {
                            activeWorkerJobs.remove(slotId)
                            onWorkerExit(slotId)
                            availableSlotIds.offer(slotId)
                            val remainingWorkers = activeWorkerCount.decrementAndGet()
                            _activeWorkers.value = remainingWorkers
                            // Notify scaler in case tasks remain and all workers retired due to a race
                            if (!isExhausted.get() && nextTaskCursor.get() < total && shouldContinue()) {
                                scaleSignal.trySend(Unit)
                            }
                        }
                    }
                    activeWorkerJobs[slotId] = job
                }
            }

            // Initial worker wave
            recomputeEffectiveDoP()
            spawnWorkersIfNeeded()

            // Dynamic scaler loop: listens for runtime DoP adjustments until all tasks complete
            val scalerJob = launch(dispatcher) {
                for (signal in scaleSignal) {
                    if (!isActive || !shouldContinue()) break
                    if (isExhausted.get() && activeWorkerCount.get() == 0) break
                    spawnWorkersIfNeeded()
                }
            }

            // Wait for all tasks to be claimed and all active workers to finish
            while (isActive && shouldContinue()) {
                if (nextTaskCursor.get() >= total && activeWorkerCount.get() == 0 && inFlightCount.get() == 0) {
                    break
                }
                spawnWorkersIfNeeded()
                val snapshotJobs = activeWorkerJobs.values.toList()
                if (snapshotJobs.isEmpty()) {
                    if (nextTaskCursor.get() >= total) break
                    kotlinx.coroutines.delay(15L)
                } else {
                    // Wait briefly or until any worker finishes / scale signal arrives
                    kotlinx.coroutines.withTimeoutOrNull(40L) {
                        snapshotJobs.firstOrNull()?.join()
                    }
                }
            }

            scalerJob.cancel()
        }
    }

    private fun recomputeEffectiveDoP(): Int {
        val target = _targetDoP.value.coerceIn(minDoP, maxDoP)
        val computed = if (adaptiveScalingEnabled) {
            val scale = adaptiveScalePermille.get().coerceIn(MIN_ADAPTIVE_SCALE_PERMILLE, 1000)
            ((target * scale) / 1000.0).roundToInt().coerceIn(minDoP, target)
        } else {
            target
        }
        _effectiveDoP.value = computed
        return computed
    }

    companion object {
        const val MAX_DOP_LIMIT = 200
        private const val MIN_ADAPTIVE_SCALE_PERMILLE = 350
    }
}
