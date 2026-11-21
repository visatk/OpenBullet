package com.example.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.OpenBulletRepository
import com.example.domain.usecase.ExportFormatterUseCase
import com.example.domain.usecase.ParseProxiesUseCase
import com.example.model.ProxiesUiState
import com.example.model.ProxyItem
import com.example.model.ProxyProtocol
import com.example.model.ProxyStatus
import com.example.model.WorkExecutionState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProxiesViewModel(
    private val repository: OpenBulletRepository,
    private val parseProxiesUseCase: ParseProxiesUseCase,
    private val exportFormatterUseCase: ExportFormatterUseCase,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    private val initialProtocolFilter = savedStateHandle.get<String>(KEY_PROTOCOL_FILTER)
        ?.let { name -> runCatching { ProxyProtocol.valueOf(name) }.getOrNull() }

    private val initialStatusFilter = savedStateHandle.get<String>(KEY_STATUS_FILTER)
        ?.let { name -> runCatching { ProxyStatus.valueOf(name) }.getOrNull() }

    private val _uiState = MutableStateFlow(
        ProxiesUiState(
            protocolFilter = initialProtocolFilter,
            statusFilter = initialStatusFilter
        )
    )
    val uiState: StateFlow<ProxiesUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private var proxyJudgeUrl: String = "https://httpbin.org/ip"

    init {
        viewModelScope.launch {
            combine(
                repository.proxiesFlow,
                repository.settingsFlow
            ) { list, settings ->
                list to settings
            }.collect { (list, settings) ->
                if (settings != null && settings.proxyJudgeUrl.isNotBlank()) {
                    proxyJudgeUrl = settings.proxyJudgeUrl
                }
                _uiState.update { current ->
                    current.withDerivedProxies(proxies = list)
                }
            }
        }
        viewModelScope.launch {
            repository.observeProxyCheckWork().collect { work ->
                if (work == null) return@collect
                when (work.state) {
                    WorkExecutionState.IDLE -> Unit
                    WorkExecutionState.ENQUEUED -> {
                        _uiState.update {
                            it.copy(
                                isCheckingProxies = true,
                                checkedCount = 0,
                                checkingTotal = it.proxies.size,
                                proxyCheckStatusText = "Queued..."
                            )
                        }
                    }
                    WorkExecutionState.RUNNING -> {
                        _uiState.update {
                            it.copy(
                                isCheckingProxies = true,
                                checkedCount = work.checked,
                                checkingTotal = work.total,
                                proxyCheckStatusText = "Checking ${work.checked} / ${work.total}"
                            )
                        }
                    }
                    WorkExecutionState.SUCCEEDED,
                    WorkExecutionState.FAILED,
                    WorkExecutionState.CANCELLED -> {
                        _uiState.update {
                            it.copy(
                                isCheckingProxies = false,
                                checkedCount = 0,
                                checkingTotal = 0,
                                proxyCheckStatusText = ""
                            )
                        }
                    }
                }
            }
        }
    }

    fun setProtocolFilter(protocol: ProxyProtocol?) {
        savedStateHandle[KEY_PROTOCOL_FILTER] = protocol?.name
        _uiState.update { current ->
            current.withDerivedProxies(protocolFilter = protocol, updateProtocolFilter = true)
        }
    }

    fun setStatusFilter(status: ProxyStatus?) {
        savedStateHandle[KEY_STATUS_FILTER] = status?.name
        _uiState.update { current ->
            current.withDerivedProxies(statusFilter = status, updateStatusFilter = true)
        }
    }

    fun addProxies(rawText: String, protocol: ProxyProtocol) {
        val result = parseProxiesUseCase(
            rawText = rawText,
            defaultProtocol = protocol,
            existingProxies = _uiState.value.proxies
        )
        if (result.newProxies.isNotEmpty()) {
            viewModelScope.launch { repository.saveProxies(result.newProxies) }
        }
        val summary = buildString {
            append("Imported ${result.newProxies.size} ${protocol.label} proxies")
            if (result.duplicateCount > 0 || result.invalidCount > 0) {
                append(" (${result.duplicateCount} dup, ${result.invalidCount} invalid)")
            }
        }
        _events.tryEmit(UiEvent.ShowSnackbar(summary))
    }

    fun importProxiesFromUrl(url: String, protocol: ProxyProtocol) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isImportingFromUrl = true) }
            _events.tryEmit(UiEvent.ShowSnackbar("Fetching proxies from URL..."))
            repository.fetchRemoteText(trimmed)
                .onSuccess { text ->
                    _uiState.update { it.copy(isImportingFromUrl = false) }
                    addProxies(text, protocol)
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isImportingFromUrl = false) }
                    _events.tryEmit(
                        UiEvent.ShowSnackbar("Import failed: ${err.message ?: "Network error"}")
                    )
                }
        }
    }

    fun toggleCheckProxies() {
        if (_uiState.value.isCheckingProxies) {
            repository.cancelProxyCheck()
            _uiState.update {
                it.copy(isCheckingProxies = false, checkedCount = 0, checkingTotal = 0, proxyCheckStatusText = "")
            }
            return
        }
        if (_uiState.value.proxies.isEmpty()) {
            _events.tryEmit(UiEvent.ShowSnackbar("Import proxies before checking"))
            return
        }
        repository.enqueueProxyCheck(probeUrl = proxyJudgeUrl)
        _uiState.update {
            it.copy(
                isCheckingProxies = true,
                checkingTotal = it.proxies.size,
                proxyCheckStatusText = "Starting..."
            )
        }
        _events.tryEmit(UiEvent.ShowSnackbar("Started Proxy Check against $proxyJudgeUrl"))
    }

    fun deleteProxy(id: String) {
        viewModelScope.launch { repository.deleteProxy(id) }
    }

    fun removeBadAndBanned() {
        viewModelScope.launch { repository.deleteBadAndBannedProxies() }
        _events.tryEmit(UiEvent.ShowSnackbar("Removed Dead & Banned proxies"))
    }

    fun clearAll() {
        viewModelScope.launch { repository.clearProxies() }
        _events.tryEmit(UiEvent.ShowSnackbar("Cleared all proxies"))
    }

    fun exportProxies() {
        val text = exportFormatterUseCase.formatAliveProxies(_uiState.value.proxies)
        if (text.isBlank()) {
            _events.tryEmit(UiEvent.ShowSnackbar("No proxies to export"))
        } else {
            _events.tryEmit(
                UiEvent.ShareFile(
                    fileName = "openbullet_proxies.txt",
                    content = text,
                    mimeType = "text/plain"
                )
            )
        }
    }

    private fun ProxiesUiState.withDerivedProxies(
        proxies: List<ProxyItem> = this.proxies,
        protocolFilter: ProxyProtocol? = this.protocolFilter,
        statusFilter: ProxyStatus? = this.statusFilter,
        updateProtocolFilter: Boolean = false,
        updateStatusFilter: Boolean = false
    ): ProxiesUiState {
        val resolvedProto = if (updateProtocolFilter) protocolFilter else this.protocolFilter
        val resolvedStatus = if (updateStatusFilter) statusFilter else this.statusFilter
        val filtered = proxies.filter { proxy ->
            val matchProto = resolvedProto == null || proxy.protocol == resolvedProto
            val matchStatus = resolvedStatus == null || proxy.status == resolvedStatus
            matchProto && matchStatus
        }
        val pings = proxies.mapNotNull { it.pingMs }
        return copy(
            proxies = proxies,
            filteredProxies = filtered,
            protocolFilter = resolvedProto,
            statusFilter = resolvedStatus,
            aliveCount = proxies.count { it.status == ProxyStatus.AVAILABLE },
            bannedCount = proxies.count { it.status == ProxyStatus.BANNED },
            badCount = proxies.count { it.status == ProxyStatus.BAD || it.status == ProxyStatus.BANNED },
            avgPingMs = if (pings.isEmpty()) null else pings.average().toInt()
        )
    }

    companion object {
        private const val KEY_PROTOCOL_FILTER = "proxies_protocol_filter"
        private const val KEY_STATUS_FILTER = "proxies_status_filter"
    }
}
