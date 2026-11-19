package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.OpenBulletRepository
import com.example.domain.usecase.ExportFormatterUseCase
import com.example.domain.usecase.ParseProxiesUseCase
import com.example.model.ProxiesUiState
import com.example.model.ProxyProtocol
import com.example.model.ProxyStatus
import com.example.model.WorkExecutionState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProxiesViewModel(
    private val repository: OpenBulletRepository,
    private val parseProxiesUseCase: ParseProxiesUseCase,
    private val exportFormatterUseCase: ExportFormatterUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProxiesUiState())
    val uiState: StateFlow<ProxiesUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private var proxyJudgeUrl: String = "https://httpbin.org/ip"

    init {
        viewModelScope.launch {
            repository.proxiesFlow.collect { list ->
                _uiState.update { it.copy(proxies = list) }
            }
        }
        viewModelScope.launch {
            repository.settingsFlow.collect { settings ->
                if (settings != null && settings.proxyJudgeUrl.isNotBlank()) {
                    proxyJudgeUrl = settings.proxyJudgeUrl
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
        _uiState.update { it.copy(protocolFilter = protocol) }
    }

    fun setStatusFilter(status: ProxyStatus?) {
        _uiState.update { it.copy(statusFilter = status) }
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
}
