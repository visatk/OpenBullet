package com.example.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.OpenBulletRepository
import com.example.domain.usecase.ManageRunnerJobUseCase
import com.example.model.ConfigItem
import com.example.model.PersistedSettingsSnapshot
import com.example.model.ProxyMode
import com.example.model.ProxyStatus
import com.example.model.ResultTab
import com.example.model.RunnerStats
import com.example.model.RunnerUiState
import com.example.model.WorkExecutionState
import com.example.model.WordlistItem
import com.example.nativeengine.NativeScannerBridge
import com.example.util.DateTimeFormats
import com.example.util.SoundNotifier
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RunnerViewModel(
    private val repository: OpenBulletRepository,
    private val soundNotifier: SoundNotifier,
    private val manageRunnerJobUseCase: ManageRunnerJobUseCase = ManageRunnerJobUseCase(repository),
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    private val initialResultTab = savedStateHandle.get<String>(KEY_SELECTED_RESULT_TAB)
        ?.let { name -> runCatching { ResultTab.valueOf(name) }.getOrNull() }
        ?: ResultTab.HITS

    private val _uiState = MutableStateFlow(
        RunnerUiState(
            selectedResultTab = initialResultTab,
            nativeEngineBanner = NativeScannerBridge.engineBanner()
        )
    )
    val uiState: StateFlow<RunnerUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private var savedSelectedConfigId: String? = null
    private var savedSelectedWordlistId: String? = null
    private var lastAppliedSettings: PersistedSettingsSnapshot? = null
    private var showTimestamps: Boolean = false
    private var soundOnHit: Boolean = true
    private var soundOnFinish: Boolean = false

    private var activeWorkId: String? = null
    private var pausedWorkId: String? = null
    private var lastObservedWorkLog: String? = null
    private var lastWorkerPacket: String? = null
    private var lastResultPacket: String? = null
    private var lastObservedHitsCount: Int = -1
    private var hasRestoredSession: Boolean = false

    init {
        observeDataStreams()
        observeRunnerWorkStream()
    }

    private fun observeDataStreams() {
        viewModelScope.launch {
            combine(
                repository.configsFlow,
                repository.wordlistsFlow,
                repository.proxiesFlow,
                repository.settingsFlow
            ) { configs, wordlists, proxies, settings ->
                Quadruple(configs, wordlists, proxies, settings)
            }.collect { (configs, wordlists, proxies, settings) ->
                val settingsChanged = settings != null && settings != lastAppliedSettings
                if (settingsChanged) {
                    lastAppliedSettings = settings
                    savedSelectedConfigId = settings.selectedConfigId
                    savedSelectedWordlistId = settings.selectedWordlistId
                    showTimestamps = settings.showTimestampsInConsole
                    soundOnHit = settings.soundOnHit
                    soundOnFinish = settings.soundOnFinish
                }

                _uiState.update { current ->
                    val targetCfgId = if (settingsChanged) {
                        savedSelectedConfigId
                    } else {
                        savedSelectedConfigId ?: current.selectedConfig?.id
                    }
                    val resolvedCfg = targetCfgId?.let { id -> configs.firstOrNull { it.id == id } }

                    val targetListId = if (settingsChanged) {
                        savedSelectedWordlistId
                    } else {
                        savedSelectedWordlistId ?: current.selectedWordlist?.id
                    }
                    val resolvedList = targetListId?.let { id -> wordlists.firstOrNull { it.id == id } }

                    val maxLine = (resolvedList?.lineCount ?: 1000).coerceAtLeast(1)
                    val baseStartLine = if (settingsChanged) settings.startLine else current.startLine
                    val clampedStart = baseStartLine.coerceIn(1, maxLine)
                    val botsCount = (if (settingsChanged) settings.botsCount else current.botsCount).coerceIn(1, 200)
                    val proxyMode = if (settingsChanged) settings.proxyMode else current.proxyMode

                    val listChanged = resolvedList?.id != current.selectedWordlist?.id
                    val nextProgressTotal = if (!current.isRunning && !current.isPaused && resolvedList != null && (current.progressTotal == 0 || listChanged)) {
                        resolvedList.lineCount
                    } else {
                        current.progressTotal
                    }
                    val nextDataTotal = if (!current.isRunning && !current.isPaused && resolvedList != null && (current.stats.dataTotal == 0 || listChanged)) {
                        resolvedList.lineCount
                    } else {
                        current.stats.dataTotal
                    }

                    current.copy(
                        configs = configs,
                        wordlists = wordlists,
                        selectedConfig = resolvedCfg,
                        selectedWordlist = resolvedList,
                        startLine = clampedStart,
                        botsCount = botsCount,
                        proxyMode = proxyMode,
                        progressTotal = nextProgressTotal,
                        stats = current.stats.copy(
                            dataTotal = nextDataTotal,
                            proxiesTotal = proxies.size,
                            proxiesAlive = proxies.count { it.status == ProxyStatus.AVAILABLE },
                            proxiesBanned = proxies.count { it.status == ProxyStatus.BANNED },
                            proxiesBad = proxies.count { it.status == ProxyStatus.BAD }
                        )
                    )
                }
            }
        }

        viewModelScope.launch {
            repository.runnerSessionFlow.collect { session ->
                if (session == null || hasRestoredSession) return@collect
                hasRestoredSession = true
                _uiState.update { current ->
                    val matchesSelection = current.selectedWordlist == null ||
                        current.selectedWordlist.id == session.wordlistId
                    if (!current.isRunning && current.progressCurrent == 0 && session.progressTotal > 0 && matchesSelection) {
                        val restoredWorkers = RunnerPacketHelper.unpackWorkers(session.workersPacked)
                        val restoredResults = RunnerPacketHelper.unpackResults(session.resultsPacked)
                        val nextResults = if (current.resultRows.isEmpty()) restoredResults else current.resultRows
                        current.copy(
                            progressCurrent = session.progressCurrent,
                            progressTotal = session.progressTotal,
                            elapsedSeconds = session.elapsedSeconds,
                            workerRows = if (current.workerRows.isEmpty()) restoredWorkers else current.workerRows,
                            resultRows = nextResults,
                            stats = current.stats.copy(
                                dataTotal = session.progressTotal,
                                dataHits = session.hits,
                                dataCustom = session.custom,
                                dataBad = session.bad,
                                dataRetries = session.retries,
                                dataToCheck = session.toCheck,
                                cpm = session.cpm
                            )
                        ).withDerivedResults()
                    } else {
                        current
                    }
                }
            }
        }
    }

    private fun observeRunnerWorkStream() {
        viewModelScope.launch {
            repository.observeRunnerWork().collect { work ->
                if (work == null) return@collect
                when (work.state) {
                    WorkExecutionState.IDLE -> Unit
                    WorkExecutionState.ENQUEUED -> {
                        if (work.workId.isNotEmpty() && work.workId != pausedWorkId) {
                            activeWorkId = work.workId
                            _uiState.update { it.copy(isRunning = true, isPaused = false) }
                        } else if (work.workId.isEmpty()) {
                            _uiState.update { it.copy(isRunning = true, isPaused = false) }
                        }
                    }

                    WorkExecutionState.RUNNING -> {
                        if (work.workId.isNotEmpty() && work.workId != activeWorkId) {
                            activeWorkId = work.workId
                            lastObservedHitsCount = work.hits
                        } else if (lastObservedHitsCount >= 0 && work.hits > lastObservedHitsCount && soundOnHit) {
                            soundNotifier.playAlertTone(isHit = true)
                        }
                        lastObservedHitsCount = work.hits

                        _uiState.update { state ->
                            val nextLogs = if (!work.logLine.isNullOrBlank() && work.logLine != lastObservedWorkLog) {
                                lastObservedWorkLog = work.logLine
                                appendLog(state, work.logLine)
                            } else state.consoleLogs

                            val nextWorkers = if (work.workersPacked.isNotEmpty() && work.workersPacked != lastWorkerPacket) {
                                lastWorkerPacket = work.workersPacked
                                RunnerPacketHelper.unpackWorkers(work.workersPacked)
                            } else if (work.workersPacked.isEmpty()) {
                                emptyList()
                            } else state.workerRows

                            val nextResults = if (work.resultsPacked.isNotEmpty() && work.resultsPacked != lastResultPacket) {
                                lastResultPacket = work.resultsPacked
                                (state.resultRows + RunnerPacketHelper.unpackResults(work.resultsPacked)).takeLast(300)
                            } else state.resultRows

                            state.copy(
                                isRunning = true,
                                isPaused = false,
                                progressCurrent = work.progressCurrent,
                                progressTotal = work.progressTotal,
                                elapsedSeconds = work.elapsedSeconds,
                                etaSeconds = work.etaSeconds,
                                workerRows = nextWorkers,
                                resultRows = nextResults,
                                consoleLogs = nextLogs,
                                stats = state.stats.copy(
                                    dataTotal = work.progressTotal,
                                    dataHits = work.hits,
                                    dataCustom = work.custom,
                                    dataBad = work.bad,
                                    dataRetries = work.retries,
                                    dataToCheck = work.toCheck,
                                    cpm = work.cpm
                                )
                            ).withDerivedResults()
                        }
                    }

                    WorkExecutionState.SUCCEEDED -> {
                        val wasRunning = _uiState.value.isRunning
                        if (wasRunning && soundOnFinish) {
                            soundNotifier.playAlertTone(isHit = false)
                        }
                        pausedWorkId = null
                        _uiState.update { state ->
                            val nextLogs = if (wasRunning && !work.logLine.isNullOrBlank() && work.logLine != lastObservedWorkLog) {
                                lastObservedWorkLog = work.logLine
                                appendLog(state, work.logLine)
                            } else state.consoleLogs

                            state.copy(
                                isRunning = false,
                                isPaused = false,
                                etaSeconds = 0L,
                                workerRows = emptyList(),
                                progressCurrent = if (wasRunning) work.progressCurrent else state.progressCurrent,
                                progressTotal = if (wasRunning) work.progressTotal else state.progressTotal,
                                elapsedSeconds = if (wasRunning) work.elapsedSeconds else state.elapsedSeconds,
                                consoleLogs = nextLogs,
                                stats = if (wasRunning) {
                                    state.stats.copy(
                                        dataTotal = work.progressTotal,
                                        dataHits = work.hits,
                                        dataCustom = work.custom,
                                        dataBad = work.bad,
                                        dataRetries = work.retries,
                                        dataToCheck = work.toCheck,
                                        cpm = work.cpm
                                    )
                                } else state.stats
                            )
                        }
                    }

                    WorkExecutionState.CANCELLED,
                    WorkExecutionState.FAILED -> {
                        if (work.workId.isNotEmpty() && work.workId == pausedWorkId) {
                            return@collect
                        }
                        _uiState.update { state ->
                            if (!state.isRunning || state.isPaused) return@update state
                            val reason = work.logLine ?: if (work.state == WorkExecutionState.CANCELLED) {
                                "Runner job stopped."
                            } else {
                                "Runner job failed."
                            }
                            state.copy(
                                isRunning = false,
                                etaSeconds = 0L,
                                workerRows = emptyList(),
                                consoleLogs = appendLog(state, reason)
                            )
                        }
                    }
                }
            }
        }
    }

    fun updateStartLineDraft(value: Int) {
        val maxLine = (_uiState.value.selectedWordlist?.lineCount ?: 100_000).coerceAtLeast(1)
        val clamped = value.coerceIn(1, maxLine)
        _uiState.update { it.copy(startLine = clamped, isPaused = false) }
    }

    fun commitStartLine(value: Int? = null) {
        val maxLine = (_uiState.value.selectedWordlist?.lineCount ?: 100_000).coerceAtLeast(1)
        val clamped = (value ?: _uiState.value.startLine).coerceIn(1, maxLine)
        _uiState.update { it.copy(startLine = clamped, isPaused = false) }
        viewModelScope.launch { repository.saveSettings { it.copy(startLine = clamped) } }
    }

    fun updateBotsCountDraft(value: Int) {
        val clamped = value.coerceIn(1, 200)
        _uiState.update { it.copy(botsCount = clamped) }
    }

    fun commitBotsCount(value: Int? = null) {
        val clamped = (value ?: _uiState.value.botsCount).coerceIn(1, 200)
        _uiState.update { it.copy(botsCount = clamped) }
        viewModelScope.launch { repository.saveSettings { it.copy(botsCount = clamped) } }
    }

    fun selectProxyMode(mode: ProxyMode) {
        _uiState.update { current ->
            current.copy(proxyMode = mode, consoleLogs = appendLog(current, "Proxy mode set to ${mode.label}"))
        }
        viewModelScope.launch { repository.saveSettings { it.copy(proxyMode = mode) } }
    }

    fun selectResultTab(tab: ResultTab) {
        savedStateHandle[KEY_SELECTED_RESULT_TAB] = tab.name
        _uiState.update { it.copy(selectedResultTab = tab).withDerivedResults() }
    }

    fun selectConfig(config: ConfigItem?) {
        savedSelectedConfigId = config?.id
        _uiState.update { current ->
            val msg = if (config != null) "Loaded config: ${config.name}" else "Cleared selected config"
            current.copy(
                selectedConfig = config,
                isPaused = false,
                consoleLogs = appendLog(current, msg)
            )
        }
        viewModelScope.launch { repository.saveSettings { it.copy(selectedConfigId = config?.id) } }
    }

    fun selectWordlist(wordlist: WordlistItem?) {
        savedSelectedWordlistId = wordlist?.id
        _uiState.update { current ->
            val total = wordlist?.lineCount ?: 0
            val msg = if (wordlist != null) "Loaded wordlist: ${wordlist.name} ($total lines)" else "Cleared selected wordlist"
            val clampedStart = if (total > 0) current.startLine.coerceIn(1, total) else 1
            current.copy(
                selectedWordlist = wordlist,
                startLine = clampedStart,
                isPaused = false,
                progressCurrent = 0,
                progressTotal = total,
                etaSeconds = 0L,
                stats = current.stats.copy(dataTotal = total),
                consoleLogs = appendLog(current, msg)
            )
        }
        viewModelScope.launch {
            repository.saveSettings {
                val total = wordlist?.lineCount ?: 0
                val clampedStart = if (total > 0) it.startLine.coerceIn(1, total) else 1
                it.copy(selectedWordlistId = wordlist?.id, startLine = clampedStart)
            }
        }
    }

    fun loadStarterSample() {
        viewModelScope.launch {
            val (cfg, list) = repository.seedStarterWorkspace()
            savedSelectedConfigId = cfg.id
            savedSelectedWordlistId = list.id
            _uiState.update { current ->
                current.copy(
                    selectedConfig = cfg,
                    selectedWordlist = list,
                    startLine = 1,
                    progressCurrent = 0,
                    progressTotal = list.lineCount,
                    isPaused = false,
                    stats = current.stats.copy(dataTotal = list.lineCount),
                    consoleLogs = appendLog(current, "Loaded starter config '${cfg.name}' and '${list.name}' (${list.lineCount} lines).")
                )
            }
            _events.tryEmit(UiEvent.ShowSnackbar("Starter workspace loaded — tap START to run!"))
        }
    }

    fun pauseOrResumeRunner() {
        val current = _uiState.value
        if (current.isRunning) {
            pausedWorkId = activeWorkId
            viewModelScope.launch {
                val nextCheckpoint = manageRunnerJobUseCase.pauseJobAndSaveCheckpoint(
                    progressCurrent = current.progressCurrent,
                    progressTotal = current.progressTotal
                )
                _uiState.update {
                    it.copy(
                        isRunning = false,
                        isPaused = true,
                        startLine = nextCheckpoint,
                        etaSeconds = 0L,
                        workerRows = emptyList(),
                        consoleLogs = appendLog(it, "Paused Runner at checkpoint line #$nextCheckpoint.")
                    )
                }
                _events.tryEmit(UiEvent.ShowSnackbar("Paused at line #$nextCheckpoint"))
            }
            return
        }

        if (current.isPaused) {
            val cfg = current.selectedConfig ?: return
            val list = current.selectedWordlist ?: return
            pausedWorkId = null
            activeWorkId = null
            lastObservedWorkLog = null
            lastWorkerPacket = null
            lastResultPacket = null

            val checkpointLine = manageRunnerJobUseCase.resumeJob(
                config = cfg,
                wordlist = list,
                checkpointLine = current.startLine,
                botsCount = current.botsCount,
                proxyMode = current.proxyMode,
                accumulatedStats = current.stats,
                elapsedSeconds = current.elapsedSeconds
            )

            _uiState.update {
                it.copy(
                    isRunning = true,
                    isPaused = false,
                    consoleLogs = appendLog(it, "Resuming Runner from line #$checkpointLine/${list.lineCount}...")
                )
            }
            _events.tryEmit(UiEvent.ShowSnackbar("Resuming from line #$checkpointLine"))
        }
    }

    fun toggleStartStop() {
        val current = _uiState.value
        if (current.isRunning || current.isPaused) {
            pausedWorkId = activeWorkId
            manageRunnerJobUseCase.stopJob()
            _uiState.update {
                it.copy(
                    isRunning = false,
                    isPaused = false,
                    etaSeconds = 0L,
                    workerRows = emptyList(),
                    consoleLogs = appendLog(it, "Stopped Runner job.")
                )
            }
            return
        }

        val cfg = current.selectedConfig
        val list = current.selectedWordlist
        if (cfg == null || list == null) {
            val missing = buildList {
                if (cfg == null) add("Config")
                if (list == null) add("Wordlist")
            }.joinToString(" and ")
            _uiState.update {
                it.copy(consoleLogs = appendLog(it, "Cannot start: Select $missing first."))
            }
            _events.tryEmit(UiEvent.ShowSnackbar("Select $missing before starting"))
            return
        }

        if (list.lineCount == 0) {
            _uiState.update {
                it.copy(consoleLogs = appendLog(it, "Selected wordlist '${list.name}' has 0 lines."))
            }
            _events.tryEmit(UiEvent.ShowSnackbar("Wordlist is empty"))
            return
        }

        pausedWorkId = null
        activeWorkId = null
        lastObservedWorkLog = null
        lastWorkerPacket = null
        lastResultPacket = null
        lastObservedHitsCount = 0

        _uiState.update {
            it.copy(
                isRunning = true,
                isPaused = false,
                elapsedSeconds = 0L,
                etaSeconds = 0L,
                progressCurrent = (current.startLine - 1).coerceIn(0, list.lineCount),
                progressTotal = list.lineCount,
                workerRows = emptyList(),
                resultRows = emptyList(),
                filteredResultRows = emptyList(),
                resultCounts = emptyMap(),
                stats = it.stats.copy(
                    dataTotal = list.lineCount,
                    dataHits = 0,
                    dataCustom = 0,
                    dataBad = 0,
                    dataRetries = 0,
                    dataToCheck = 0,
                    cpm = 0
                ),
                consoleLogs = appendLog(it, "Started Runner Pool for '${cfg.name}' (${current.botsCount} bots)...")
            )
        }

        manageRunnerJobUseCase.startJob(
            config = cfg,
            wordlist = list,
            startLine = current.startLine,
            botsCount = current.botsCount,
            proxyMode = current.proxyMode
        )
    }

    fun captureSnapshotSummary() {
        val state = _uiState.value
        val summary = "Snapshot -> Bots: ${state.botsCount}, Prog: ${state.progressCurrent}/${state.progressTotal}, Hits: ${state.stats.dataHits}, Engine: ${state.nativeEngineBanner}"
        _uiState.update { it.copy(consoleLogs = appendLog(it, summary)) }
        _events.tryEmit(UiEvent.ShowSnackbar("UI snapshot logged to console"))
    }

    fun clearConsoleLogs() {
        _uiState.update { it.copy(consoleLogs = listOf("Runner initialized successfully!")) }
    }

    private fun RunnerUiState.withDerivedResults(): RunnerUiState {
        val counts = ResultTab.entries.associateWith { tab ->
            resultRows.count { it.category == tab }
        }
        return copy(
            filteredResultRows = resultRows.filter { it.category == selectedResultTab },
            resultCounts = counts
        )
    }

    private fun appendLog(state: RunnerUiState, message: String): List<String> {
        val line = if (showTimestamps) "[${DateTimeFormats.formatClockNow()}] $message" else message
        return (state.consoleLogs + line).takeLast(120)
    }

    private data class Quadruple<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )

    companion object {
        private const val KEY_SELECTED_RESULT_TAB = "runner_selected_result_tab"
    }
}
