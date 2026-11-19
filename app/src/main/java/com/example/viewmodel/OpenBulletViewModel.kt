package com.example.viewmodel

import android.app.Application
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.example.data.OpenBulletRepository
import com.example.model.ActiveDialog
import com.example.model.ConfigBlock
import com.example.model.ConfigEditorSubTab
import com.example.model.ConfigItem
import com.example.model.MainTab
import com.example.model.OpenBulletUiState
import com.example.model.ProxyItem
import com.example.model.ProxyMode
import com.example.model.ProxyProtocol
import com.example.model.ProxyStatus
import com.example.model.ResultTab
import com.example.model.SettingsSection
import com.example.model.ToolsSection
import com.example.model.WordlistItem
import com.example.nativeengine.NativeScannerBridge
import com.example.network.ConfigTemplates
import com.example.network.HttpExecutionEngine
import com.example.worker.ProxyCheckWorker
import com.example.worker.RunnerJobWorker
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OpenBulletViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = OpenBulletRepository(application)

    private val _uiState = MutableStateFlow(
        OpenBulletUiState(
            nativeEngineBanner = NativeScannerBridge.engineBanner()
        )
    )
    val uiState: StateFlow<OpenBulletUiState> = _uiState.asStateFlow()

    private var lastObservedWorkLog: String? = null
    private var lastWorkerPacket: String? = null
    private var lastResultPacket: String? = null
    private var lastObservedHitsCount: Int = 0
    private var savedSelectedConfigId: String? = null
    private var savedSelectedWordlistId: String? = null

    init {
        observeRoomStreams()
        observeWorkManagerStreams()
    }

    private fun observeRoomStreams() {
        viewModelScope.launch {
            repository.configsFlow.collect { configs ->
                _uiState.update { current ->
                    val targetCfgId = current.selectedConfig?.id ?: savedSelectedConfigId
                    val updatedSelected = targetCfgId?.let { id -> configs.firstOrNull { it.id == id } }
                    val updatedPreviewId = current.selectedConfigPreviewId?.takeIf { id ->
                        configs.any { it.id == id }
                    } ?: configs.firstOrNull()?.id
                    current.copy(
                        configs = configs,
                        selectedConfig = updatedSelected,
                        selectedConfigPreviewId = updatedPreviewId
                    )
                }
            }
        }

        viewModelScope.launch {
            repository.wordlistsFlow.collect { wordlists ->
                _uiState.update { current ->
                    val targetListId = current.selectedWordlist?.id ?: savedSelectedWordlistId
                    val updatedSelected = targetListId?.let { id -> wordlists.firstOrNull { it.id == id } }
                    current.copy(
                        wordlists = wordlists,
                        selectedWordlist = updatedSelected,
                        progressTotal = if (current.progressTotal == 0 && updatedSelected != null) updatedSelected.lineCount else current.progressTotal,
                        stats = if (current.stats.dataTotal == 0 && updatedSelected != null) current.stats.copy(dataTotal = updatedSelected.lineCount) else current.stats
                    )
                }
            }
        }

        viewModelScope.launch {
            repository.proxiesFlow.collect { proxies ->
                _uiState.update { current ->
                    current.copy(
                        proxies = proxies,
                        stats = recalculateProxyStats(proxies, current.stats)
                    )
                }
            }
        }

        viewModelScope.launch {
            repository.hitsDbFlow.collect { hits ->
                _uiState.update { it.copy(hitsDb = hits) }
            }
        }

        viewModelScope.launch {
            repository.settingsFlow.collect { entity ->
                if (entity != null) {
                    savedSelectedConfigId = entity.selectedConfigId
                    savedSelectedWordlistId = entity.selectedWordlistId
                    _uiState.update { current ->
                        val resolvedCfg = current.selectedConfig
                            ?: entity.selectedConfigId?.let { id -> current.configs.firstOrNull { it.id == id } }
                        val resolvedList = current.selectedWordlist
                            ?: entity.selectedWordlistId?.let { id -> current.wordlists.firstOrNull { it.id == id } }
                        current.copy(
                            settings = entity.toSettingsState(current.settings),
                            startLine = entity.startLine,
                            botsCount = entity.botsCount,
                            proxyMode = runCatching { ProxyMode.valueOf(entity.proxyMode) }.getOrDefault(ProxyMode.DEF),
                            selectedConfig = resolvedCfg,
                            selectedWordlist = resolvedList
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            repository.runnerSessionFlow.collect { session ->
                if (session != null) {
                    _uiState.update { current ->
                        // Restore telemetry if UI just reopened or was recreated in background
                        if (current.progressCurrent == 0 && session.progressTotal > 0) {
                            val restoredWorkers = RunnerPacketHelper.unpackWorkers(session.workersPacked)
                            val restoredResults = RunnerPacketHelper.unpackResults(session.resultsPacked)
                            current.copy(
                                progressCurrent = session.progressCurrent,
                                progressTotal = session.progressTotal,
                                elapsedSeconds = session.elapsedSeconds,
                                workerRows = if (current.workerRows.isEmpty()) restoredWorkers else current.workerRows,
                                resultRows = if (current.resultRows.isEmpty()) restoredResults else current.resultRows,
                                stats = current.stats.copy(
                                    dataTotal = session.progressTotal,
                                    dataHits = session.hits,
                                    dataCustom = session.custom,
                                    dataBad = session.bad,
                                    dataRetries = session.retries,
                                    dataToCheck = session.toCheck,
                                    cpm = session.cpm
                                )
                            )
                        } else current
                    }
                }
            }
        }
    }

    private fun observeWorkManagerStreams() {
        viewModelScope.launch {
            repository.observeRunnerWork().collect { infos ->
                val info = infos.firstOrNull() ?: return@collect
                when (info.state) {
                    WorkInfo.State.ENQUEUED,
                    WorkInfo.State.BLOCKED -> {
                        _uiState.update { it.copy(isRunning = true, isPaused = false) }
                    }
                    WorkInfo.State.RUNNING -> {
                        val p = info.progress
                        val currentCount = p.getInt(RunnerJobWorker.KEY_CURRENT, _uiState.value.progressCurrent)
                        val totalCount = p.getInt(RunnerJobWorker.KEY_TOTAL, _uiState.value.progressTotal)
                        val hits = p.getInt(RunnerJobWorker.KEY_HITS, _uiState.value.stats.dataHits)
                        val custom = p.getInt(RunnerJobWorker.KEY_CUSTOM, _uiState.value.stats.dataCustom)
                        val bad = p.getInt(RunnerJobWorker.KEY_BAD, _uiState.value.stats.dataBad)
                        val retries = p.getInt(RunnerJobWorker.KEY_RETRIES, _uiState.value.stats.dataRetries)
                        val toCheck = p.getInt(RunnerJobWorker.KEY_TO_CHECK, _uiState.value.stats.dataToCheck)
                        val cpm = p.getInt(RunnerJobWorker.KEY_CPM, _uiState.value.stats.cpm)
                        val elapsed = p.getLong(RunnerJobWorker.KEY_ELAPSED_SEC, _uiState.value.elapsedSeconds)
                        val eta = p.getLong(RunnerJobWorker.KEY_ETA_SEC, _uiState.value.etaSeconds)
                        val packedWorkers = p.getString(RunnerJobWorker.KEY_WORKERS_PACKED).orEmpty()
                        val packedResults = p.getString(RunnerJobWorker.KEY_RESULTS_PACKED).orEmpty()
                        val logLine = p.getString(RunnerJobWorker.KEY_LOG_LINE)

                        if (hits > lastObservedHitsCount && _uiState.value.settings.soundOnHit) {
                            RunnerPacketHelper.playAlertTone(isHit = true)
                        }
                        lastObservedHitsCount = hits

                        _uiState.update { state ->
                            val nextLogs = if (!logLine.isNullOrBlank() && logLine != lastObservedWorkLog) {
                                lastObservedWorkLog = logLine
                                appendLog(state, logLine)
                            } else state.consoleLogs

                            val nextWorkers = if (packedWorkers.isNotEmpty() && packedWorkers != lastWorkerPacket) {
                                lastWorkerPacket = packedWorkers
                                RunnerPacketHelper.unpackWorkers(packedWorkers)
                            } else if (packedWorkers.isEmpty()) {
                                emptyList()
                            } else state.workerRows

                            val nextResults = if (packedResults.isNotEmpty() && packedResults != lastResultPacket) {
                                lastResultPacket = packedResults
                                (state.resultRows + RunnerPacketHelper.unpackResults(packedResults)).takeLast(300)
                            } else state.resultRows

                            state.copy(
                                isRunning = true,
                                isPaused = false,
                                progressCurrent = currentCount,
                                progressTotal = totalCount,
                                elapsedSeconds = elapsed,
                                etaSeconds = eta,
                                workerRows = nextWorkers,
                                resultRows = nextResults,
                                consoleLogs = nextLogs,
                                stats = state.stats.copy(
                                    dataTotal = totalCount,
                                    dataHits = hits,
                                    dataCustom = custom,
                                    dataBad = bad,
                                    dataRetries = retries,
                                    dataToCheck = toCheck,
                                    cpm = cpm
                                )
                            )
                        }
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        val out = info.outputData
                        val logLine = out.getString(RunnerJobWorker.KEY_LOG_LINE)
                        val currentCount = out.getInt(RunnerJobWorker.KEY_CURRENT, _uiState.value.progressCurrent)
                        val totalCount = out.getInt(RunnerJobWorker.KEY_TOTAL, _uiState.value.progressTotal)
                        val hits = out.getInt(RunnerJobWorker.KEY_HITS, _uiState.value.stats.dataHits)
                        val custom = out.getInt(RunnerJobWorker.KEY_CUSTOM, _uiState.value.stats.dataCustom)
                        val bad = out.getInt(RunnerJobWorker.KEY_BAD, _uiState.value.stats.dataBad)
                        val retries = out.getInt(RunnerJobWorker.KEY_RETRIES, _uiState.value.stats.dataRetries)
                        val toCheck = out.getInt(RunnerJobWorker.KEY_TO_CHECK, _uiState.value.stats.dataToCheck)
                        val cpm = out.getInt(RunnerJobWorker.KEY_CPM, _uiState.value.stats.cpm)
                        val elapsed = out.getLong(RunnerJobWorker.KEY_ELAPSED_SEC, _uiState.value.elapsedSeconds)

                        val wasRunning = _uiState.value.isRunning
                        if (wasRunning && _uiState.value.settings.soundOnFinish) {
                            RunnerPacketHelper.playAlertTone(isHit = false)
                        }

                        _uiState.update { state ->
                            val nextLogs = if (wasRunning && !logLine.isNullOrBlank() && logLine != lastObservedWorkLog) {
                                lastObservedWorkLog = logLine
                                appendLog(state, logLine)
                            } else state.consoleLogs

                            state.copy(
                                isRunning = false,
                                isPaused = false,
                                etaSeconds = 0L,
                                workerRows = emptyList(),
                                progressCurrent = if (wasRunning) currentCount else state.progressCurrent,
                                progressTotal = if (wasRunning) totalCount else state.progressTotal,
                                elapsedSeconds = if (wasRunning) elapsed else state.elapsedSeconds,
                                consoleLogs = nextLogs,
                                stats = if (wasRunning) {
                                    state.stats.copy(
                                        dataTotal = totalCount,
                                        dataHits = hits,
                                        dataCustom = custom,
                                        dataBad = bad,
                                        dataRetries = retries,
                                        dataToCheck = toCheck,
                                        cpm = cpm
                                    )
                                } else state.stats
                            )
                        }
                    }
                    WorkInfo.State.CANCELLED,
                    WorkInfo.State.FAILED -> {
                        _uiState.update { state ->
                            if (!state.isRunning) return@update state
                            val reason = when {
                                state.isPaused -> "Runner paused at line ${state.startLine}."
                                info.state == WorkInfo.State.CANCELLED -> "Runner job stopped."
                                else -> "Runner job failed."
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

        viewModelScope.launch {
            repository.observeProxyCheckWork().collect { infos ->
                val info = infos.firstOrNull() ?: return@collect
                when (info.state) {
                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                        _uiState.update { it.copy(isCheckingProxies = true, proxyCheckStatusText = "Queued...") }
                    }
                    WorkInfo.State.RUNNING -> {
                        val checked = info.progress.getInt(ProxyCheckWorker.KEY_CHECKED, 0)
                        val total = info.progress.getInt(ProxyCheckWorker.KEY_TOTAL, 0)
                        _uiState.update { it.copy(isCheckingProxies = true, proxyCheckStatusText = "Checking $checked / $total") }
                    }
                    WorkInfo.State.SUCCEEDED, WorkInfo.State.CANCELLED, WorkInfo.State.FAILED -> {
                        _uiState.update { it.copy(isCheckingProxies = false, proxyCheckStatusText = "") }
                    }
                }
            }
        }
    }

    fun selectTab(tab: MainTab) {
        _uiState.update { current ->
            if (current.activeTab == tab && current.configEditor == null) return@update current
            current.copy(
                activeTab = tab,
                configEditor = null,
                tabHistory = if (current.activeTab != tab) {
                    (current.tabHistory + current.activeTab).takeLast(12)
                } else current.tabHistory
            )
        }
    }

    fun navigateBack(): Boolean {
        val current = _uiState.value
        return when {
            current.activeDialog != ActiveDialog.None -> {
                dismissDialog()
                true
            }
            current.configEditor != null -> {
                closeConfigEditor()
                true
            }
            current.tabHistory.isNotEmpty() -> {
                val previous = current.tabHistory.last()
                _uiState.update { it.copy(activeTab = previous, tabHistory = it.tabHistory.dropLast(1)) }
                true
            }
            current.activeTab != MainTab.RUNNER -> {
                _uiState.update { it.copy(activeTab = MainTab.RUNNER) }
                true
            }
            else -> false
        }
    }

    fun updateStartLine(value: Int) {
        val clamped = value.coerceIn(1, 100000)
        _uiState.update { it.copy(startLine = clamped, isPaused = false) }
        viewModelScope.launch { repository.saveSettings { it.copy(startLine = clamped) } }
    }

    fun updateBotsCount(value: Int) {
        val clamped = value.coerceIn(1, 200)
        _uiState.update { it.copy(botsCount = clamped) }
        viewModelScope.launch { repository.saveSettings { it.copy(botsCount = clamped) } }
    }

    fun selectProxyMode(mode: ProxyMode) {
        _uiState.update { current ->
            current.copy(proxyMode = mode, consoleLogs = appendLog(current, "Proxy mode set to ${mode.label}"))
        }
        viewModelScope.launch { repository.saveSettings { it.copy(proxyMode = mode.name) } }
    }

    fun selectResultTab(tab: ResultTab) {
        _uiState.update { it.copy(selectedResultTab = tab) }
    }

    fun openDialog(dialog: ActiveDialog) {
        _uiState.update { it.copy(activeDialog = dialog) }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(activeDialog = ActiveDialog.None) }
    }

    fun selectConfig(config: ConfigItem?) {
        savedSelectedConfigId = config?.id
        _uiState.update { current ->
            val msg = if (config != null) "Loaded config: ${config.name}" else "Cleared selected config"
            current.copy(
                selectedConfig = config,
                isPaused = false,
                activeDialog = ActiveDialog.None,
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
            current.copy(
                selectedWordlist = wordlist,
                isPaused = false,
                progressCurrent = 0,
                progressTotal = total,
                etaSeconds = 0L,
                stats = current.stats.copy(dataTotal = total),
                activeDialog = ActiveDialog.None,
                consoleLogs = appendLog(current, msg)
            )
        }
        viewModelScope.launch { repository.saveSettings { it.copy(selectedWordlistId = wordlist?.id) } }
    }

    fun pauseOrResumeRunner() {
        val current = _uiState.value
        if (current.isRunning) {
            val nextCheckpoint = (current.progressCurrent + 1).coerceIn(1, current.progressTotal.coerceAtLeast(1))
            repository.cancelRunnerJob()
            _uiState.update {
                it.copy(
                    isRunning = false,
                    isPaused = true,
                    startLine = nextCheckpoint,
                    etaSeconds = 0L,
                    workerRows = emptyList(),
                    statusBannerMessage = "Paused at line #$nextCheckpoint",
                    consoleLogs = appendLog(it, "Paused Runner at checkpoint line #$nextCheckpoint.")
                )
            }
            viewModelScope.launch { repository.saveSettings { it.copy(startLine = nextCheckpoint) } }
            return
        }

        if (current.isPaused) {
            val cfg = current.selectedConfig ?: return
            val list = current.selectedWordlist ?: return
            val checkpointLine = current.startLine.coerceIn(1, list.lineCount.coerceAtLeast(1))
            lastObservedWorkLog = null
            lastWorkerPacket = null
            lastResultPacket = null

            _uiState.update {
                it.copy(
                    isRunning = true,
                    isPaused = false,
                    statusBannerMessage = "Resuming from line #$checkpointLine",
                    consoleLogs = appendLog(it, "Resuming Runner from line #$checkpointLine/${list.lineCount}...")
                )
            }

            repository.enqueueRunnerJob(
                configId = cfg.id,
                wordlistId = list.id,
                startLine = checkpointLine,
                botsCount = current.botsCount,
                proxyMode = current.proxyMode,
                initialHits = current.stats.dataHits,
                initialCustom = current.stats.dataCustom,
                initialBad = current.stats.dataBad,
                initialRetries = current.stats.dataRetries,
                initialToCheck = current.stats.dataToCheck,
                initialElapsedSec = current.elapsedSeconds
            )
        }
    }

    fun toggleStartStop() {
        val current = _uiState.value
        if (current.isRunning || current.isPaused) {
            repository.cancelRunnerJob()
            _uiState.update {
                it.copy(
                    isRunning = false,
                    isPaused = false,
                    etaSeconds = 0L,
                    workerRows = emptyList(),
                    consoleLogs = appendLog(it, "Stopped Runner Foreground Service.")
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
                it.copy(
                    consoleLogs = appendLog(it, "Cannot start: Select $missing first."),
                    statusBannerMessage = "Select $missing before starting"
                )
            }
            return
        }

        if (list.lines.isEmpty()) {
            _uiState.update {
                it.copy(
                    consoleLogs = appendLog(it, "Selected wordlist '${list.name}' has 0 lines."),
                    statusBannerMessage = "Wordlist is empty"
                )
            }
            return
        }

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
                stats = it.stats.copy(
                    dataTotal = list.lineCount,
                    dataHits = 0,
                    dataCustom = 0,
                    dataBad = 0,
                    dataRetries = 0,
                    dataToCheck = 0,
                    cpm = 0
                ),
                consoleLogs = appendLog(it, "Started Continuous Runner Pool for '${cfg.name}' (${current.botsCount} bots)...")
            )
        }

        repository.enqueueRunnerJob(
            configId = cfg.id,
            wordlistId = list.id,
            startLine = current.startLine,
            botsCount = current.botsCount,
            proxyMode = current.proxyMode
        )
    }

    fun toggleTimestampLogging() {
        val next = !_uiState.value.settings.showTimestampsInConsole
        _uiState.update { current ->
            val msg = if (next) "Console timestamps enabled." else "Console timestamps disabled."
            val updated = current.copy(settings = current.settings.copy(showTimestampsInConsole = next))
            updated.copy(consoleLogs = appendLog(updated, msg), statusBannerMessage = msg)
        }
        viewModelScope.launch { repository.saveSettings { it.copy(showTimestampsInConsole = next) } }
    }

    fun captureSnapshotSummary() {
        val state = _uiState.value
        val summary = "Snapshot -> Bots: ${state.botsCount}, Prog: ${state.progressCurrent}/${state.progressTotal}, Hits: ${state.stats.dataHits}, Engine: ${state.nativeEngineBanner}"
        _uiState.update {
            it.copy(consoleLogs = appendLog(it, summary), statusBannerMessage = "UI snapshot logged to console")
        }
    }

    fun clearBannerMessage() {
        _uiState.update { it.copy(statusBannerMessage = null) }
    }

    fun showBannerMessage(message: String) {
        _uiState.update { it.copy(statusBannerMessage = message) }
    }

    fun clearConsoleLogs() {
        _uiState.update { it.copy(consoleLogs = listOf("Runner initialized succesfully!")) }
    }

    // --- Config Editor / Maker ---
    fun openConfigEditor(existing: ConfigItem? = null) {
        _uiState.update {
            it.copy(
                activeTab = MainTab.CONFIGS,
                activeDialog = ActiveDialog.None,
                configEditor = ConfigEditorHelper.createInitialState(existing)
            )
        }
    }

    fun closeConfigEditor() {
        _uiState.update { it.copy(configEditor = null) }
    }

    fun applyTemplateToEditor(preset: ConfigTemplates.TemplatePreset) {
        _uiState.update { current ->
            val ed = current.configEditor ?: return@update current
            current.copy(
                configEditor = ConfigEditorHelper.applyPreset(ed, preset),
                statusBannerMessage = "Loaded template: ${preset.title}"
            )
        }
    }

    fun updateEditorMetadata(
        name: String? = null,
        author: String? = null,
        category: String? = null,
        wordlistType: String? = null,
        needsProxies: Boolean? = null
    ) {
        _uiState.update { current ->
            val ed = current.configEditor ?: return@update current
            current.copy(
                configEditor = ed.copy(
                    name = name ?: ed.name,
                    author = author ?: ed.author,
                    category = category ?: ed.category,
                    wordlistType = wordlistType ?: ed.wordlistType,
                    needsProxies = needsProxies ?: ed.needsProxies
                )
            )
        }
    }

    fun selectEditorSubTab(subTab: ConfigEditorSubTab) {
        _uiState.update { current ->
            val ed = current.configEditor ?: return@update current
            current.copy(configEditor = ConfigEditorHelper.switchSubTab(ed, subTab))
        }
    }

    fun updateEditorRawScript(script: String) {
        _uiState.update { current ->
            val ed = current.configEditor ?: return@update current
            current.copy(configEditor = ed.copy(rawScript = script))
        }
    }

    fun addBlockToEditor(kind: String) {
        _uiState.update { current ->
            val ed = current.configEditor ?: return@update current
            val newBlock = ConfigEditorHelper.createDefaultBlock(kind)
            current.copy(configEditor = ed.copy(blocks = ed.blocks + newBlock))
        }
    }

    fun updateBlockInEditor(index: Int, updated: ConfigBlock) {
        _uiState.update { current ->
            val ed = current.configEditor ?: return@update current
            if (index !in ed.blocks.indices) return@update current
            val nextBlocks = ed.blocks.toMutableList().apply { set(index, updated) }
            current.copy(configEditor = ed.copy(blocks = nextBlocks))
        }
    }

    fun moveBlockInEditor(fromIndex: Int, toIndex: Int) {
        _uiState.update { current ->
            val ed = current.configEditor ?: return@update current
            if (fromIndex !in ed.blocks.indices || toIndex !in ed.blocks.indices) return@update current
            val list = ed.blocks.toMutableList()
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            current.copy(configEditor = ed.copy(blocks = list))
        }
    }

    fun deleteBlockFromEditor(index: Int) {
        _uiState.update { current ->
            val ed = current.configEditor ?: return@update current
            if (index !in ed.blocks.indices) return@update current
            val list = ed.blocks.toMutableList().apply { removeAt(index) }
            current.copy(configEditor = ed.copy(blocks = list))
        }
    }

    fun updateEditorDebugInputs(testData: String? = null, useProxy: Boolean? = null) {
        _uiState.update { current ->
            val ed = current.configEditor ?: return@update current
            current.copy(
                configEditor = ed.copy(
                    testDataInput = testData ?: ed.testDataInput,
                    debugUseProxy = useProxy ?: ed.debugUseProxy
                )
            )
        }
    }

    fun runConfigDebug() {
        val state = _uiState.value
        val ed = state.configEditor ?: return
        val synced = ConfigEditorHelper.switchSubTab(ed, ConfigEditorSubTab.DEBUGGER)
        val scriptText = ConfigEditorHelper.syncScriptForSave(ed)
        val chosenProxy = if (ed.debugUseProxy) RunnerPacketHelper.chooseDebugProxy(state.proxies) else null
        val timeoutSec = state.settings.requestTimeoutSec

        _uiState.update {
            it.copy(configEditor = synced.copy(isDebugging = true, debugResult = null))
        }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                HttpExecutionEngine.executeDebug(
                    rawLine = ed.testDataInput.ifBlank { "test@example.com:password123" },
                    scriptText = scriptText,
                    timeoutSec = timeoutSec,
                    proxyItem = chosenProxy
                )
            }
            _uiState.update { cur ->
                val curEd = cur.configEditor ?: return@update cur
                cur.copy(configEditor = curEd.copy(isDebugging = false, debugResult = result))
            }
        }
    }

    fun saveEditedConfig(loadInRunner: Boolean) {
        val ed = _uiState.value.configEditor ?: return
        val finalScript = ConfigEditorHelper.syncScriptForSave(ed)
        val cleanName = ed.name.trim().ifEmpty { "Untitled Config" }
        val config = ConfigItem(
            id = ed.configId,
            name = cleanName,
            author = ed.author.trim().ifEmpty { "LocalUser" },
            category = ed.category.trim().ifEmpty { "Default" },
            wordlistType = ed.wordlistType.trim().ifEmpty { "Default" },
            needsProxies = ed.needsProxies,
            lastModified = currentDateString(),
            scriptPreview = finalScript
        )

        viewModelScope.launch { repository.saveConfig(config) }
        if (loadInRunner) {
            savedSelectedConfigId = config.id
            viewModelScope.launch { repository.saveSettings { it.copy(selectedConfigId = config.id) } }
        }

        _uiState.update { current ->
            current.copy(
                selectedConfigPreviewId = config.id,
                selectedConfig = if (loadInRunner) config else current.selectedConfig,
                configEditor = null,
                activeTab = if (loadInRunner) MainTab.RUNNER else current.activeTab,
                consoleLogs = appendLog(current, "Saved config '$cleanName' (${if (loadInRunner) "loaded in Runner" else "Room DB"})."),
                statusBannerMessage = if (loadInRunner) "Saved & loaded '$cleanName'" else "Saved config '$cleanName'"
            )
        }
    }

    // --- Configs CRUD (Room) ---
    fun addConfig(
        name: String,
        author: String,
        category: String,
        wordlistType: String,
        needsProxies: Boolean,
        scriptPreview: String
    ) {
        val cleanName = name.trim().ifEmpty { "Untitled Config" }
        val config = ConfigItem(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            author = author.trim().ifEmpty { "LocalUser" },
            category = category.trim().ifEmpty { "Default" },
            wordlistType = wordlistType.trim().ifEmpty { "Default" },
            needsProxies = needsProxies,
            lastModified = currentDateString(),
            scriptPreview = scriptPreview.ifBlank { ConfigTemplates.presets.first().script }
        )
        viewModelScope.launch { repository.saveConfig(config) }
        _uiState.update { current ->
            current.copy(
                selectedConfigPreviewId = config.id,
                activeDialog = ActiveDialog.None,
                consoleLogs = appendLog(current, "Saved config '$cleanName' to Room DB.")
            )
        }
    }

    fun selectConfigForPreview(id: String) {
        _uiState.update { it.copy(selectedConfigPreviewId = id) }
    }

    fun deleteConfig(id: String) {
        viewModelScope.launch { repository.deleteConfig(id) }
    }

    fun importConfigPayload(
        rawText: String,
        fallbackName: String = "Imported Config",
        openInEditorAfterImport: Boolean = false
    ) {
        val parsedList = com.example.network.ConfigFileCodec.parseImportPayload(rawText, fallbackName)
        if (parsedList.isEmpty()) {
            _uiState.update {
                it.copy(statusBannerMessage = "Nothing to import: empty or invalid payload")
            }
            return
        }
        viewModelScope.launch {
            parsedList.forEach { cfg -> repository.saveConfig(cfg) }
        }
        val firstCfg = parsedList.first()
        _uiState.update { current ->
            current.copy(
                selectedConfigPreviewId = firstCfg.id,
                activeDialog = ActiveDialog.None,
                configEditor = if (openInEditorAfterImport) ConfigEditorHelper.createInitialState(firstCfg) else current.configEditor,
                statusBannerMessage = "Imported ${parsedList.size} config(s) (${firstCfg.name})",
                consoleLogs = appendLog(
                    current,
                    "Imported ${parsedList.size} config(s) via ConfigFileCodec (${com.example.network.ConfigFileCodec.detectFormatLabel(rawText)})."
                )
            )
        }
    }

    fun importConfigFromUrl(url: String) {
        if (url.isBlank()) return
        val fallbackName = url.trim().substringAfterLast('/').ifBlank { "Remote Config" }
        viewModelScope.launch {
            _uiState.update { it.copy(statusBannerMessage = "Fetching config from URL...") }
            repository.fetchRemoteText(url.trim())
                .onSuccess { text ->
                    importConfigPayload(text, fallbackName = fallbackName, openInEditorAfterImport = false)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            statusBannerMessage = "Config fetch failed: ${err.message ?: "Network error"}",
                            consoleLogs = appendLog(it, "Config URL fetch error: ${err.message}")
                        )
                    }
                }
        }
    }

    fun buildSingleConfigExport(
        config: ConfigItem,
        format: com.example.network.ConfigExportFormat
    ): String {
        return when (format) {
            com.example.network.ConfigExportFormat.LOLI ->
                com.example.network.ConfigFileCodec.serializeToLoli(config)
            com.example.network.ConfigExportFormat.JSON ->
                com.example.network.ConfigFileCodec.serializeToJson(config)
        }
    }

    fun buildActiveEditorConfigExport(
        format: com.example.network.ConfigExportFormat
    ): Pair<String, String>? {
        val ed = _uiState.value.configEditor ?: return null
        val finalScript = ConfigEditorHelper.syncScriptForSave(ed)
        val cleanName = ed.name.trim().ifEmpty { "Untitled_Config" }
        val tempConfig = ConfigItem(
            id = ed.configId,
            name = cleanName,
            author = ed.author.trim().ifEmpty { "LocalUser" },
            category = ed.category.trim().ifEmpty { "Default" },
            wordlistType = ed.wordlistType.trim().ifEmpty { "Default" },
            needsProxies = ed.needsProxies,
            lastModified = currentDateString(),
            scriptPreview = finalScript
        )
        val safeFileName = cleanName.replace(Regex("""[^a-zA-Z0-9._-]"""), "_") + ".${format.extension}"
        return safeFileName to buildSingleConfigExport(tempConfig, format)
    }

    fun buildAllConfigsJsonExport(): String {
        val all = _uiState.value.configs
        if (all.isEmpty()) return ""
        return com.example.network.ConfigFileCodec.serializeListToJson(all)
    }

    // --- Wordlists CRUD (Room + SAF + Tools export) ---
    fun addWordlist(name: String, type: String, purpose: String, rawText: String) {
        val cleanLines = rawText
            .lineSequence()
            .map { it.trim() }
            .filter { !(_uiState.value.settings.ignoreEmptyLines && it.isEmpty()) }
            .toList()
        val cleanName = name.trim().ifEmpty { "wordlist_${_uiState.value.wordlists.size + 1}.txt" }
        val item = WordlistItem(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            path = "room://$cleanName",
            type = type.trim().ifEmpty { "Default" },
            lines = cleanLines,
            purpose = purpose.trim().ifEmpty { "Runner Input" }
        )
        viewModelScope.launch { repository.saveWordlist(item) }
        _uiState.update { current ->
            current.copy(
                activeDialog = ActiveDialog.None,
                statusBannerMessage = "Saved wordlist '$cleanName' (${cleanLines.size} lines)",
                consoleLogs = appendLog(current, "Saved wordlist '$cleanName' (${cleanLines.size} lines) to Room DB.")
            )
        }
    }

    fun deleteWordlist(id: String) {
        viewModelScope.launch { repository.deleteWordlist(id) }
    }

    fun clearWordlists() {
        viewModelScope.launch { repository.clearWordlists() }
    }

    // --- Proxies CRUD (Room + WorkManager Proxy Checker + Retrofit URL Import) ---
    fun addProxies(rawText: String, protocol: ProxyProtocol) {
        val parsed = rawText
            .lineSequence()
            .map { it.trim() }
            .filter { it.contains(":") }
            .mapNotNull { line ->
                val parts = line.split(":")
                val host = parts.getOrNull(0)?.trim().orEmpty()
                val port = parts.getOrNull(1)?.trim()?.toIntOrNull()
                if (host.isNotEmpty() && port != null && port in 1..65535) {
                    ProxyItem(
                        id = UUID.randomUUID().toString(),
                        host = host,
                        port = port,
                        protocol = protocol,
                        pingMs = null,
                        country = "Pending",
                        status = ProxyStatus.UNTESTED
                    )
                } else null
            }
            .toList()

        viewModelScope.launch { repository.saveProxies(parsed) }
        _uiState.update { current ->
            current.copy(
                activeDialog = ActiveDialog.None,
                statusBannerMessage = "Imported ${parsed.size} ${protocol.label} proxies",
                consoleLogs = appendLog(current, "Saved ${parsed.size} ${protocol.label} proxy entries to Room DB.")
            )
        }
    }

    fun importProxiesFromUrl(url: String, protocol: ProxyProtocol) {
        if (url.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(statusBannerMessage = "Fetching proxies via Retrofit...") }
            repository.fetchRemoteText(url.trim())
                .onSuccess { text -> addProxies(text, protocol) }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            statusBannerMessage = "Import failed: ${err.message ?: "Network error"}",
                            consoleLogs = appendLog(it, "Proxy URL fetch error: ${err.message}")
                        )
                    }
                }
        }
    }

    fun toggleProxyCheckWork() {
        if (_uiState.value.isCheckingProxies) {
            repository.cancelProxyCheck()
            _uiState.update { it.copy(isCheckingProxies = false, proxyCheckStatusText = "") }
            return
        }
        if (_uiState.value.proxies.isEmpty()) {
            _uiState.update { it.copy(statusBannerMessage = "Import proxies before checking") }
            return
        }
        val judgeUrl = _uiState.value.settings.proxyJudgeUrl.ifBlank { "https://httpbin.org/ip" }
        repository.enqueueProxyCheck(probeUrl = judgeUrl)
        _uiState.update {
            it.copy(
                isCheckingProxies = true,
                consoleLogs = appendLog(it, "Started Foreground Proxy Check against $judgeUrl.")
            )
        }
    }

    fun setProxyFilter(protocol: ProxyProtocol?) {
        _uiState.update { it.copy(proxyFilter = protocol) }
    }

    fun deleteProxy(id: String) {
        viewModelScope.launch { repository.deleteProxy(id) }
    }

    fun removeBadAndBannedProxies() {
        viewModelScope.launch { repository.deleteBadAndBannedProxies() }
        _uiState.update { it.copy(statusBannerMessage = "Removed Dead & Banned proxies") }
    }

    fun clearProxies() {
        viewModelScope.launch { repository.clearProxies() }
    }

    fun buildAliveProxiesExportText(): String {
        val list = _uiState.value.proxies
        val alive = list.filter { it.status == ProxyStatus.AVAILABLE }
        val exportList = alive.ifEmpty { list }
        return exportList.joinToString("\n") { "${it.host}:${it.port}" }
    }

    // --- Hits DB (Room) ---
    fun updateHitsSearchQuery(query: String) {
        _uiState.update { it.copy(hitsSearchQuery = query) }
    }

    fun setHitsTypeFilter(type: ResultTab?) {
        _uiState.update { it.copy(hitsTypeFilter = type) }
    }

    fun deleteHitItem(id: String) {
        viewModelScope.launch { repository.deleteHit(id) }
    }

    fun clearHitsDb() {
        viewModelScope.launch { repository.clearHitsDb() }
    }

    fun buildHitsExportText(): String {
        val state = _uiState.value
        val filtered = state.hitsDb.filter { item ->
            val matchesType = state.hitsTypeFilter == null || item.type == state.hitsTypeFilter
            val matchesQuery = state.hitsSearchQuery.isBlank() ||
                item.data.contains(state.hitsSearchQuery, ignoreCase = true) ||
                item.configName.contains(state.hitsSearchQuery, ignoreCase = true)
            matchesType && matchesQuery
        }
        return filtered.joinToString("\n") { "${it.data} | ${it.capturedData} | [${it.configName}]" }
    }

    // --- Tools (List Cleaner, Formatter, Regex, Hash, Native Scanner & OkHttp/Retrofit Probe) ---
    fun selectToolsSection(section: ToolsSection) {
        _uiState.update { it.copy(tools = it.tools.copy(activeSection = section)) }
    }

    fun updateCleanerInput(text: String) {
        _uiState.update { it.copy(tools = it.tools.copy(cleanerInput = text)) }
    }

    fun toggleCleanerDuplicates(enabled: Boolean) {
        _uiState.update { it.copy(tools = it.tools.copy(cleanerRemoveDuplicates = enabled)) }
    }

    fun toggleCleanerTrim(enabled: Boolean) {
        _uiState.update { it.copy(tools = it.tools.copy(cleanerTrimWhitespace = enabled)) }
    }

    fun toggleCleanerEmpty(enabled: Boolean) {
        _uiState.update { it.copy(tools = it.tools.copy(cleanerRemoveEmpty = enabled)) }
    }

    fun runListCleaner() {
        val t = _uiState.value.tools
        var lines = t.cleanerInput.lineSequence().toList()
        if (t.cleanerTrimWhitespace) lines = lines.map { it.trim() }
        if (t.cleanerRemoveEmpty) lines = lines.filter { it.isNotEmpty() }
        if (t.cleanerRemoveDuplicates) lines = lines.distinct()
        _uiState.update { it.copy(tools = it.tools.copy(cleanerOutput = lines.joinToString("\n"))) }
    }

    fun updateFormatterInput(text: String) {
        _uiState.update { it.copy(tools = it.tools.copy(formatterInput = text)) }
    }

    fun updateFormatterSeparator(sep: String) {
        _uiState.update { it.copy(tools = it.tools.copy(formatterSeparator = sep)) }
    }

    fun runComboFormatter(swapOrder: Boolean) {
        val t = _uiState.value.tools
        val sep = t.formatterSeparator.ifEmpty { ":" }
        val formatted = t.formatterInput.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line ->
                val parts = line.split(":", ";", "|", "\t", limit = 2)
                if (parts.size == 2) {
                    val left = parts[0].trim()
                    val right = parts[1].trim()
                    if (swapOrder) "$right$sep$left" else "$left$sep$right"
                } else line
            }
            .joinToString("\n")
        _uiState.update { it.copy(tools = it.tools.copy(formatterOutput = formatted)) }
    }

    fun updateRegexInputs(pattern: String? = null, testInput: String? = null) {
        val current = _uiState.value.tools
        val nextPattern = pattern ?: current.regexPattern
        val nextInput = testInput ?: current.regexTestInput
        val matches = runCatching {
            if (nextPattern.isEmpty()) emptyList()
            else Regex(nextPattern).findAll(nextInput).map { it.value }.toList()
        }.getOrDefault(listOf("Invalid Regex"))

        _uiState.update {
            it.copy(tools = it.tools.copy(regexPattern = nextPattern, regexTestInput = nextInput, regexMatches = matches))
        }
    }

    fun updateHashInput(text: String) {
        val b64 = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        _uiState.update {
            it.copy(
                tools = it.tools.copy(
                    hashInput = text,
                    base64Output = if (text.isEmpty()) "" else b64,
                    sha256Output = if (text.isEmpty()) "" else digest
                )
            )
        }
    }

    fun updateProbeUrl(url: String) {
        _uiState.update { it.copy(tools = it.tools.copy(probeUrl = url)) }
    }

    fun runNetworkProbe() {
        val url = _uiState.value.tools.probeUrl.trim()
        if (url.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(tools = it.tools.copy(isProbing = true, probeResult = "Probing $url...")) }
            repository.runHttpProbe(url)
                .onSuccess { res -> _uiState.update { it.copy(tools = it.tools.copy(isProbing = false, probeResult = res)) } }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(tools = it.tools.copy(isProbing = false, probeResult = "Error: ${err.message ?: err.javaClass.simpleName}"))
                    }
                }
        }
    }

    fun updateNativeDiagnosticFields(sampleBody: String? = null, successKey: String? = null, failureKey: String? = null) {
        val cur = _uiState.value.tools
        _uiState.update {
            it.copy(
                tools = cur.copy(
                    nativeSampleBody = sampleBody ?: cur.nativeSampleBody,
                    nativeSuccessKey = successKey ?: cur.nativeSuccessKey,
                    nativeFailureKey = failureKey ?: cur.nativeFailureKey
                )
            )
        }
    }

    fun runNativeScannerTest() {
        val t = _uiState.value.tools
        val startNs = System.nanoTime()
        val (outcome, matched) = NativeScannerBridge.scanResponseKeys(
            body = t.nativeSampleBody,
            successKeys = listOf(t.nativeSuccessKey).filter { it.isNotBlank() },
            customKeys = emptyList(),
            failureKeys = listOf(t.nativeFailureKey).filter { it.isNotBlank() }
        )
        val analysis = NativeScannerBridge.analyzeComboLine(t.nativeSampleBody.lineSequence().firstOrNull().orEmpty())
        val elapsedUs = (System.nanoTime() - startNs) / 1000L
        val summary = "Outcome=${outcome.label} | Key=${matched ?: "None"} | FNV1a=${analysis.fnv1aHashHex.take(8)} | ${elapsedUs}us"
        _uiState.update { it.copy(tools = it.tools.copy(nativeScanSummary = summary)) }
    }

    // --- Settings (Room) ---
    fun selectSettingsSection(section: SettingsSection) {
        _uiState.update { it.copy(settings = it.settings.copy(activeSection = section)) }
    }

    fun updateTimeout(sec: Int) {
        val clamped = sec.coerceIn(1, 120)
        _uiState.update { it.copy(settings = it.settings.copy(requestTimeoutSec = clamped)) }
        viewModelScope.launch { repository.saveSettings { it.copy(requestTimeoutSec = clamped) } }
    }

    fun updateMaxRetries(retries: Int) {
        val clamped = retries.coerceIn(0, 20)
        _uiState.update { it.copy(settings = it.settings.copy(maxRetries = clamped)) }
        viewModelScope.launch { repository.saveSettings { it.copy(maxRetries = clamped) } }
    }

    fun updateProxyJudgeUrl(url: String) {
        _uiState.update { it.copy(settings = it.settings.copy(proxyJudgeUrl = url)) }
        viewModelScope.launch { repository.saveSettings { it.copy(proxyJudgeUrl = url.trim()) } }
    }

    fun toggleSettingAutoSave(v: Boolean) {
        _uiState.update { it.copy(settings = it.settings.copy(autoSaveHits = v)) }
        viewModelScope.launch { repository.saveSettings { it.copy(autoSaveHits = v) } }
    }

    fun toggleSettingIgnoreEmpty(v: Boolean) {
        _uiState.update { it.copy(settings = it.settings.copy(ignoreEmptyLines = v)) }
        viewModelScope.launch { repository.saveSettings { it.copy(ignoreEmptyLines = v) } }
    }

    fun toggleKeepAwake(v: Boolean) {
        _uiState.update { it.copy(settings = it.settings.copy(keepAwakeEnabled = v)) }
        viewModelScope.launch { repository.saveSettings { it.copy(keepAwakeEnabled = v) } }
    }

    fun toggleSettingSoundOnHit(v: Boolean) {
        _uiState.update { it.copy(settings = it.settings.copy(soundOnHit = v)) }
        viewModelScope.launch { repository.saveSettings { it.copy(soundOnHit = v) } }
    }

    fun toggleSettingSoundOnFinish(v: Boolean) {
        _uiState.update { it.copy(settings = it.settings.copy(soundOnFinish = v)) }
        viewModelScope.launch { repository.saveSettings { it.copy(soundOnFinish = v) } }
    }

    fun selectAccentColor(hex: Long) {
        _uiState.update { it.copy(settings = it.settings.copy(accentHex = hex)) }
        viewModelScope.launch { repository.saveSettings { it.copy(accentHex = hex) } }
    }

    private fun recalculateProxyStats(
        list: List<ProxyItem>,
        current: com.example.model.RunnerStats
    ) = current.copy(
        proxiesTotal = list.size,
        proxiesAlive = list.count { it.status == ProxyStatus.AVAILABLE },
        proxiesBanned = list.count { it.status == ProxyStatus.BANNED },
        proxiesBad = list.count { it.status == ProxyStatus.BAD }
    )

    private fun appendLog(state: OpenBulletUiState, message: String): List<String> {
        val line = if (state.settings.showTimestampsInConsole) "[${currentClockString()}] $message" else message
        return (state.consoleLogs + line).takeLast(100)
    }

    private fun currentClockString(): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
    private fun currentDateString(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}
