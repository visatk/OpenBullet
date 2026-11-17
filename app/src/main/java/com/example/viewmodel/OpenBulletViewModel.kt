package com.example.viewmodel

import android.app.Application
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.example.data.OpenBulletRepository
import com.example.model.ActiveDialog
import com.example.model.ConfigItem
import com.example.model.MainTab
import com.example.model.OpenBulletUiState
import com.example.model.ProxyItem
import com.example.model.ProxyMode
import com.example.model.ProxyProtocol
import com.example.model.ProxyStatus
import com.example.model.ResultRow
import com.example.model.ResultTab
import com.example.model.SettingsSection
import com.example.model.ToolsSection
import com.example.model.WordlistItem
import com.example.model.WorkerRow
import com.example.nativeengine.NativeScannerBridge
import com.example.network.LoliScriptParser
import com.example.ui.theme.ObColors
import com.example.worker.ProxyCheckWorker
import com.example.worker.RunnerJobWorker
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    init {
        observeRoomStreams()
        observeWorkManagerStreams()
    }

    private fun observeRoomStreams() {
        viewModelScope.launch {
            repository.configsFlow.collect { configs ->
                _uiState.update { current ->
                    val updatedSelected = current.selectedConfig?.let { sel ->
                        configs.firstOrNull { it.id == sel.id }
                    }
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
                    val updatedSelected = current.selectedWordlist?.let { sel ->
                        wordlists.firstOrNull { it.id == sel.id }
                    }
                    current.copy(
                        wordlists = wordlists,
                        selectedWordlist = updatedSelected
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
                    _uiState.update { current ->
                        current.copy(
                            settings = entity.toSettingsState(current.settings),
                            startLine = entity.startLine,
                            botsCount = entity.botsCount,
                            proxyMode = runCatching { ProxyMode.valueOf(entity.proxyMode) }.getOrDefault(ProxyMode.DEF)
                        )
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
                        _uiState.update { it.copy(isRunning = true) }
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
                        val packedWorkers = p.getString(RunnerJobWorker.KEY_WORKERS_PACKED).orEmpty()
                        val packedResults = p.getString(RunnerJobWorker.KEY_RESULTS_PACKED).orEmpty()
                        val logLine = p.getString(RunnerJobWorker.KEY_LOG_LINE)

                        _uiState.update { state ->
                            val nextLogs = if (!logLine.isNullOrBlank() && logLine != lastObservedWorkLog) {
                                lastObservedWorkLog = logLine
                                appendLog(state, logLine)
                            } else state.consoleLogs

                            val nextWorkers = if (packedWorkers.isNotEmpty() && packedWorkers != lastWorkerPacket) {
                                lastWorkerPacket = packedWorkers
                                unpackWorkers(packedWorkers)
                            } else state.workerRows

                            val nextResults = if (packedResults.isNotEmpty() && packedResults != lastResultPacket) {
                                lastResultPacket = packedResults
                                (state.resultRows + unpackResults(packedResults)).takeLast(250)
                            } else state.resultRows

                            state.copy(
                                isRunning = true,
                                progressCurrent = currentCount,
                                progressTotal = totalCount,
                                elapsedSeconds = elapsed,
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

                        _uiState.update { state ->
                            val wasRunning = state.isRunning
                            val nextLogs = if (wasRunning && !logLine.isNullOrBlank() && logLine != lastObservedWorkLog) {
                                lastObservedWorkLog = logLine
                                appendLog(state, logLine)
                            } else state.consoleLogs

                            state.copy(
                                isRunning = false,
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
                            val reason = if (info.state == WorkInfo.State.CANCELLED) {
                                "Runner job stopped."
                            } else {
                                "Runner job failed."
                            }
                            state.copy(
                                isRunning = false,
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
                        _uiState.update {
                            it.copy(isCheckingProxies = true, proxyCheckStatusText = "Queued...")
                        }
                    }
                    WorkInfo.State.RUNNING -> {
                        val checked = info.progress.getInt(ProxyCheckWorker.KEY_CHECKED, 0)
                        val total = info.progress.getInt(ProxyCheckWorker.KEY_TOTAL, 0)
                        _uiState.update {
                            it.copy(
                                isCheckingProxies = true,
                                proxyCheckStatusText = "Checking $checked / $total"
                            )
                        }
                    }
                    WorkInfo.State.SUCCEEDED, WorkInfo.State.CANCELLED, WorkInfo.State.FAILED -> {
                        _uiState.update {
                            it.copy(isCheckingProxies = false, proxyCheckStatusText = "")
                        }
                    }
                }
            }
        }
    }

    fun selectTab(tab: MainTab) {
        _uiState.update { current ->
            if (current.activeTab == tab) return@update current
            current.copy(
                activeTab = tab,
                tabHistory = (current.tabHistory + current.activeTab).takeLast(12)
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
            current.tabHistory.isNotEmpty() -> {
                val previous = current.tabHistory.last()
                _uiState.update {
                    it.copy(
                        activeTab = previous,
                        tabHistory = it.tabHistory.dropLast(1)
                    )
                }
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
        val clamped = value.coerceIn(1, 1000)
        _uiState.update { it.copy(startLine = clamped) }
        viewModelScope.launch { repository.saveSettings { it.copy(startLine = clamped) } }
    }

    fun updateBotsCount(value: Int) {
        val clamped = value.coerceIn(1, 200)
        _uiState.update { it.copy(botsCount = clamped) }
        viewModelScope.launch { repository.saveSettings { it.copy(botsCount = clamped) } }
    }

    fun selectProxyMode(mode: ProxyMode) {
        _uiState.update { current ->
            current.copy(
                proxyMode = mode,
                consoleLogs = appendLog(current, "Proxy mode set to ${mode.label}")
            )
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
        _uiState.update { current ->
            val msg = if (config != null) "Loaded config: ${config.name}" else "Cleared selected config"
            current.copy(
                selectedConfig = config,
                activeDialog = ActiveDialog.None,
                consoleLogs = appendLog(current, msg)
            )
        }
    }

    fun selectWordlist(wordlist: WordlistItem?) {
        _uiState.update { current ->
            val total = wordlist?.lineCount ?: 0
            val msg = if (wordlist != null) "Loaded wordlist: ${wordlist.name} ($total lines)" else "Cleared selected wordlist"
            current.copy(
                selectedWordlist = wordlist,
                progressCurrent = 0,
                progressTotal = total,
                stats = current.stats.copy(dataTotal = total),
                activeDialog = ActiveDialog.None,
                consoleLogs = appendLog(current, msg)
            )
        }
    }

    fun toggleStartStop() {
        val current = _uiState.value
        if (current.isRunning) {
            repository.cancelRunnerJob()
            _uiState.update {
                it.copy(
                    isRunning = false,
                    consoleLogs = appendLog(it, "Cancelling Runner WorkManager job...")
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

        val parsed = LoliScriptParser.parse(cfg.scriptPreview)
        val requiresNetwork = parsed.targetUrl.isNotBlank()

        _uiState.update {
            it.copy(
                isRunning = true,
                elapsedSeconds = 0L,
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
                consoleLogs = appendLog(
                    it,
                    if (requiresNetwork) {
                        "Enqueued WorkManager + OkHttp job for '${cfg.name}'..."
                    } else {
                        "Enqueued WorkManager local scan job for '${cfg.name}'..."
                    }
                )
            )
        }

        repository.enqueueRunnerJob(
            configId = cfg.id,
            wordlistId = list.id,
            startLine = current.startLine,
            botsCount = current.botsCount,
            proxyMode = current.proxyMode,
            requiresNetwork = requiresNetwork
        )
    }

    fun toggleTimestampLogging() {
        val next = !_uiState.value.settings.showTimestampsInConsole
        _uiState.update { current ->
            val msg = if (next) "Console timestamps enabled." else "Console timestamps disabled."
            val updated = current.copy(settings = current.settings.copy(showTimestampsInConsole = next))
            updated.copy(
                consoleLogs = appendLog(updated, msg),
                statusBannerMessage = msg
            )
        }
        viewModelScope.launch { repository.saveSettings { it.copy(showTimestampsInConsole = next) } }
    }

    fun captureSnapshotSummary() {
        val state = _uiState.value
        val summary = "Snapshot -> Bots: ${state.botsCount}, Prog: ${state.progressCurrent}/${state.progressTotal}, Hits: ${state.stats.dataHits}, Engine: ${state.nativeEngineBanner}"
        _uiState.update {
            it.copy(
                consoleLogs = appendLog(it, summary),
                statusBannerMessage = "UI snapshot logged to console"
            )
        }
    }

    fun clearBannerMessage() {
        _uiState.update { it.copy(statusBannerMessage = null) }
    }

    fun clearConsoleLogs() {
        _uiState.update {
            it.copy(consoleLogs = listOf("Runner initialized succesfully!"))
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
            scriptPreview = scriptPreview.ifBlank {
                "# LoliScript HTTP Check\nREQUEST GET \"https://httpbin.org/get?user=<USER>\"\nHEADER \"Accept: application/json\"\nKEYCHECK\n  KEYCHAIN Success OR\n    KEY \"HTTP/200\"\nPARSE \"<SOURCE>\" LR \"\\\"origin\\\": \\\"\" \"\\\"\" -> CAP \"Origin\""
            }
        )
        viewModelScope.launch {
            repository.saveConfig(config)
        }
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

    // --- Wordlists CRUD (Room + Retrofit Remote Import) ---
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
                consoleLogs = appendLog(current, "Saved ${parsed.size} ${protocol.label} proxy entries to Room DB.")
            )
        }
    }

    fun importProxiesFromUrl(url: String, protocol: ProxyProtocol) {
        if (url.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(statusBannerMessage = "Fetching proxies via Retrofit...") }
            repository.fetchRemoteText(url.trim())
                .onSuccess { text ->
                    addProxies(text, protocol)
                    _uiState.update { it.copy(statusBannerMessage = "Imported proxies from URL") }
                }
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
        repository.enqueueProxyCheck()
        _uiState.update {
            it.copy(
                isCheckingProxies = true,
                consoleLogs = appendLog(it, "Started WorkManager OkHttp proxy check.")
            )
        }
    }

    fun setProxyFilter(protocol: ProxyProtocol?) {
        _uiState.update { it.copy(proxyFilter = protocol) }
    }

    fun deleteProxy(id: String) {
        viewModelScope.launch { repository.deleteProxy(id) }
    }

    fun clearProxies() {
        viewModelScope.launch { repository.clearProxies() }
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
        _uiState.update {
            it.copy(tools = it.tools.copy(cleanerOutput = lines.joinToString("\n")))
        }
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
                } else {
                    line
                }
            }
            .joinToString("\n")
        _uiState.update {
            it.copy(tools = it.tools.copy(formatterOutput = formatted))
        }
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
            it.copy(
                tools = it.tools.copy(
                    regexPattern = nextPattern,
                    regexTestInput = nextInput,
                    regexMatches = matches
                )
            )
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
                .onSuccess { res ->
                    _uiState.update { it.copy(tools = it.tools.copy(isProbing = false, probeResult = res)) }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(tools = it.tools.copy(isProbing = false, probeResult = "Error: ${err.message ?: err.javaClass.simpleName}"))
                    }
                }
        }
    }

    fun updateNativeDiagnosticFields(
        sampleBody: String? = null,
        successKey: String? = null,
        failureKey: String? = null
    ) {
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
        _uiState.update {
            it.copy(tools = it.tools.copy(nativeScanSummary = summary))
        }
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

    fun toggleSettingAutoSave(v: Boolean) {
        _uiState.update { it.copy(settings = it.settings.copy(autoSaveHits = v)) }
        viewModelScope.launch { repository.saveSettings { it.copy(autoSaveHits = v) } }
    }

    fun toggleSettingIgnoreEmpty(v: Boolean) {
        _uiState.update { it.copy(settings = it.settings.copy(ignoreEmptyLines = v)) }
        viewModelScope.launch { repository.saveSettings { it.copy(ignoreEmptyLines = v) } }
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

    private fun unpackWorkers(packed: String): List<WorkerRow> {
        if (packed.isBlank()) return emptyList()
        return packed.split("\u001E").mapNotNull { entry ->
            val parts = entry.split("\u001F")
            if (parts.size < 5) return@mapNotNull null
            val id = parts[0].toIntOrNull() ?: return@mapNotNull null
            val color = when (parts[4]) {
                "HIT" -> ObColors.StatusHitGreen
                "CUSTOM" -> ObColors.StatusCustomOrange
                "BAD" -> ObColors.StatusBadRed
                "RETRY" -> ObColors.StatusRetryYellow
                else -> ObColors.StatusToCheckAqua
            }
            WorkerRow(
                id = id,
                data = parts[1],
                proxy = parts[2],
                status = parts[3],
                statusColor = color
            )
        }
    }

    private fun unpackResults(packed: String): List<ResultRow> {
        if (packed.isBlank()) return emptyList()
        return packed.split("\u001E").mapNotNull { entry ->
            val parts = entry.split("\u001F")
            if (parts.size < 5) return@mapNotNull null
            val cat = runCatching { ResultTab.valueOf(parts[4]) }.getOrDefault(ResultTab.TO_CHECK)
            ResultRow(
                time = parts[0],
                data = parts[1],
                proxy = parts[2],
                capturedData = parts[3],
                category = cat
            )
        }
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
        val line = if (state.settings.showTimestampsInConsole) {
            "[${currentClockString()}] $message"
        } else {
            message
        }
        return (state.consoleLogs + line).takeLast(100)
    }

    private fun currentClockString(): String =
        SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

    private fun currentDateString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}
