package com.example.viewmodel

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.ActiveDialog
import com.example.model.ConfigItem
import com.example.model.HitDbItem
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
import com.example.ui.theme.ObColors
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OpenBulletViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(OpenBulletUiState())
    val uiState: StateFlow<OpenBulletUiState> = _uiState.asStateFlow()

    private var runnerJob: Job? = null

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
    }

    fun updateBotsCount(value: Int) {
        val clamped = value.coerceIn(1, 200)
        _uiState.update { it.copy(botsCount = clamped) }
    }

    fun selectProxyMode(mode: ProxyMode) {
        _uiState.update { current ->
            current.copy(
                proxyMode = mode,
                consoleLogs = appendLog(current, "Proxy mode set to ${mode.label}")
            )
        }
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
            val msg = if (config != null) {
                "Loaded config: ${config.name}"
            } else {
                "Cleared selected config"
            }
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
            val msg = if (wordlist != null) {
                "Loaded wordlist: ${wordlist.name} ($total lines)"
            } else {
                "Cleared selected wordlist"
            }
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
            stopRunner("Runner aborted by user.")
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

        startLocalEvaluation(cfg, list)
    }

    private fun startLocalEvaluation(cfg: ConfigItem, list: WordlistItem) {
        runnerJob?.cancel()
        val startIdx = (_uiState.value.startLine - 1).coerceIn(0, list.lines.lastIndex)
        val totalToProcess = list.lines.size
        _uiState.update { current ->
            current.copy(
                isRunning = true,
                elapsedSeconds = 0L,
                progressCurrent = startIdx,
                progressTotal = totalToProcess,
                workerRows = emptyList(),
                resultRows = emptyList(),
                stats = current.stats.copy(
                    dataTotal = totalToProcess,
                    dataHits = 0,
                    dataCustom = 0,
                    dataBad = 0,
                    dataRetries = 0,
                    dataToCheck = 0,
                    cpm = 0
                ),
                consoleLogs = appendLog(
                    current,
                    "Started '${cfg.name}' on '${list.name}' with ${_uiState.value.botsCount} bot(s)."
                )
            )
        }

        runnerJob = viewModelScope.launch {
            var index = startIdx
            var seconds = 0L
            val patternRegex = runCatching {
                Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}:.+$")
            }.getOrNull()

            while (index < list.lines.size) {
                val batchSize = _uiState.value.botsCount.coerceIn(1, 10)
                val batchEnd = (index + batchSize).coerceAtMost(list.lines.size)
                val activeWorkers = mutableListOf<WorkerRow>()
                val newResults = mutableListOf<ResultRow>()
                val newHitsDb = mutableListOf<HitDbItem>()

                var batchHits = 0
                var batchCustom = 0
                var batchBad = 0
                var batchToCheck = 0

                val proxies = _uiState.value.proxies
                val useProxy = _uiState.value.proxyMode == ProxyMode.ON ||
                    (_uiState.value.proxyMode == ProxyMode.DEF && cfg.needsProxies)

                for (i in index until batchEnd) {
                    val line = list.lines[i].trim()
                    val proxyText = if (useProxy && proxies.isNotEmpty()) {
                        val p = proxies[i % proxies.size]
                        "${p.host}:${p.port}"
                    } else {
                        "Direct"
                    }

                    val matchesFormat = patternRegex?.matches(line) == true
                    val hasColon = line.contains(":") && line.substringAfter(":").length >= 4

                    val (statusLabel, statusColor, category) = when {
                        line.isEmpty() -> Triple("EMPTY", ObColors.StatusBadRed, null)
                        matchesFormat -> Triple("VALID", ObColors.StatusHitGreen, ResultTab.HITS)
                        hasColon -> Triple("PARTIAL", ObColors.StatusCustomOrange, ResultTab.CUSTOM)
                        else -> Triple("CHECK", ObColors.StatusToCheckAqua, ResultTab.TO_CHECK)
                    }

                    activeWorkers.add(
                        WorkerRow(
                            id = (i - index) + 1,
                            data = line.ifEmpty { "<empty>" },
                            proxy = proxyText,
                            status = statusLabel,
                            statusColor = statusColor
                        )
                    )

                    val nowTime = currentClockString()
                    when (category) {
                        ResultTab.HITS -> {
                            batchHits++
                            newResults.add(ResultRow(nowTime, line, proxyText, "Format=Email:Token", ResultTab.HITS))
                            newHitsDb.add(
                                HitDbItem(
                                    id = UUID.randomUUID().toString(),
                                    date = nowTime,
                                    data = line,
                                    type = ResultTab.HITS,
                                    configName = cfg.name,
                                    wordlistName = list.name,
                                    capturedData = "Format=Email:Token"
                                )
                            )
                        }
                        ResultTab.CUSTOM -> {
                            batchCustom++
                            newResults.add(ResultRow(nowTime, line, proxyText, "Format=Key:Value", ResultTab.CUSTOM))
                        }
                        ResultTab.TO_CHECK -> {
                            batchToCheck++
                            newResults.add(ResultRow(nowTime, line, proxyText, "Unparsed line", ResultTab.TO_CHECK))
                        }
                        null -> batchBad++
                    }
                }

                index = batchEnd
                seconds++
                val cpmCalc = if (seconds > 0) ((index - startIdx) * 60 / seconds).toInt() else 0

                _uiState.update { state ->
                    val updatedStats = state.stats.copy(
                        dataHits = state.stats.dataHits + batchHits,
                        dataCustom = state.stats.dataCustom + batchCustom,
                        dataBad = state.stats.dataBad + batchBad,
                        dataToCheck = state.stats.dataToCheck + batchToCheck,
                        cpm = cpmCalc
                    )
                    state.copy(
                        elapsedSeconds = seconds,
                        progressCurrent = index,
                        workerRows = activeWorkers,
                        resultRows = state.resultRows + newResults,
                        hitsDb = state.hitsDb + newHitsDb,
                        stats = updatedStats
                    )
                }

                delay(280L)
            }

            stopRunner("Runner finished processing ${list.lines.size} item(s).")
        }
    }

    private fun stopRunner(reason: String) {
        runnerJob?.cancel()
        runnerJob = null
        _uiState.update { current ->
            current.copy(
                isRunning = false,
                consoleLogs = appendLog(current, reason)
            )
        }
    }

    fun toggleTimestampLogging() {
        _uiState.update { current ->
            val next = !current.settings.showTimestampsInConsole
            val msg = if (next) "Console timestamps enabled." else "Console timestamps disabled."
            val updated = current.copy(
                settings = current.settings.copy(showTimestampsInConsole = next)
            )
            updated.copy(
                consoleLogs = appendLog(updated, msg),
                statusBannerMessage = msg
            )
        }
    }

    fun captureSnapshotSummary() {
        val state = _uiState.value
        val summary = "Snapshot -> Bots: ${state.botsCount}, Prog: ${state.progressCurrent}/${state.progressTotal}, Hits: ${state.stats.dataHits}"
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

    // --- Configs CRUD ---
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
                "# LoliScript Block Blueprint\nREQUEST GET \"https://example.com\"\nKEYCHECK\n  KEYCHAIN Success OR\n    KEY \"ok\""
            }
        )
        _uiState.update { current ->
            current.copy(
                configs = current.configs + config,
                selectedConfigPreviewId = config.id,
                activeDialog = ActiveDialog.None,
                consoleLogs = appendLog(current, "Added config '$cleanName'.")
            )
        }
    }

    fun selectConfigForPreview(id: String) {
        _uiState.update { it.copy(selectedConfigPreviewId = id) }
    }

    fun deleteConfig(id: String) {
        _uiState.update { current ->
            val remaining = current.configs.filterNot { it.id == id }
            val clearedSelected = if (current.selectedConfig?.id == id) null else current.selectedConfig
            current.copy(
                configs = remaining,
                selectedConfig = clearedSelected,
                selectedConfigPreviewId = if (current.selectedConfigPreviewId == id) remaining.firstOrNull()?.id else current.selectedConfigPreviewId
            )
        }
    }

    // --- Wordlists CRUD ---
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
            path = "local://$cleanName",
            type = type.trim().ifEmpty { "Default" },
            lines = cleanLines,
            purpose = purpose.trim().ifEmpty { "Runner Input" }
        )
        _uiState.update { current ->
            current.copy(
                wordlists = current.wordlists + item,
                activeDialog = ActiveDialog.None,
                consoleLogs = appendLog(current, "Imported wordlist '$cleanName' (${cleanLines.size} lines).")
            )
        }
    }

    fun deleteWordlist(id: String) {
        _uiState.update { current ->
            val remaining = current.wordlists.filterNot { it.id == id }
            val selected = if (current.selectedWordlist?.id == id) null else current.selectedWordlist
            current.copy(
                wordlists = remaining,
                selectedWordlist = selected
            )
        }
    }

    fun clearWordlists() {
        _uiState.update { it.copy(wordlists = emptyList(), selectedWordlist = null) }
    }

    // --- Proxies CRUD ---
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
                        country = "Local",
                        status = ProxyStatus.UNTESTED
                    )
                } else null
            }
            .toList()

        _uiState.update { current ->
            val all = current.proxies + parsed
            current.copy(
                proxies = all,
                activeDialog = ActiveDialog.None,
                stats = current.stats.copy(proxiesTotal = all.size),
                consoleLogs = appendLog(current, "Imported ${parsed.size} ${protocol.label} proxy entries.")
            )
        }
    }

    fun setProxyFilter(protocol: ProxyProtocol?) {
        _uiState.update { it.copy(proxyFilter = protocol) }
    }

    fun deleteProxy(id: String) {
        _uiState.update { current ->
            val remaining = current.proxies.filterNot { it.id == id }
            current.copy(
                proxies = remaining,
                stats = recalculateProxyStats(remaining, current.stats)
            )
        }
    }

    fun clearProxies() {
        _uiState.update { current ->
            current.copy(
                proxies = emptyList(),
                stats = current.stats.copy(
                    proxiesTotal = 0,
                    proxiesAlive = 0,
                    proxiesBanned = 0,
                    proxiesBad = 0
                )
            )
        }
    }

    // --- Hits DB ---
    fun updateHitsSearchQuery(query: String) {
        _uiState.update { it.copy(hitsSearchQuery = query) }
    }

    fun setHitsTypeFilter(type: ResultTab?) {
        _uiState.update { it.copy(hitsTypeFilter = type) }
    }

    fun deleteHitItem(id: String) {
        _uiState.update { current ->
            current.copy(hitsDb = current.hitsDb.filterNot { it.id == id })
        }
    }

    fun clearHitsDb() {
        _uiState.update { it.copy(hitsDb = emptyList()) }
    }

    // --- Tools (Real Local Utilities) ---
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

    // --- Settings ---
    fun selectSettingsSection(section: SettingsSection) {
        _uiState.update { it.copy(settings = it.settings.copy(activeSection = section)) }
    }

    fun updateTimeout(sec: Int) {
        _uiState.update { it.copy(settings = it.settings.copy(requestTimeoutSec = sec.coerceIn(1, 120))) }
    }

    fun updateMaxRetries(retries: Int) {
        _uiState.update { it.copy(settings = it.settings.copy(maxRetries = retries.coerceIn(0, 20))) }
    }

    fun toggleSettingAutoSave(v: Boolean) {
        _uiState.update { it.copy(settings = it.settings.copy(autoSaveHits = v)) }
    }

    fun toggleSettingIgnoreEmpty(v: Boolean) {
        _uiState.update { it.copy(settings = it.settings.copy(ignoreEmptyLines = v)) }
    }

    fun toggleSettingSoundOnHit(v: Boolean) {
        _uiState.update { it.copy(settings = it.settings.copy(soundOnHit = v)) }
    }

    fun toggleSettingSoundOnFinish(v: Boolean) {
        _uiState.update { it.copy(settings = it.settings.copy(soundOnFinish = v)) }
    }

    fun selectAccentColor(hex: Long) {
        _uiState.update { it.copy(settings = it.settings.copy(accentHex = hex)) }
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

    override fun onCleared() {
        super.onCleared()
        runnerJob?.cancel()
    }
}
