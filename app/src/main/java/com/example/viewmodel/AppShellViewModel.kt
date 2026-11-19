package com.example.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.OpenBulletRepository
import com.example.model.ActiveDialog
import com.example.model.AppShellUiState
import com.example.model.MainTab
import com.example.model.ProxyStatus
import com.example.model.WorkExecutionState
import com.example.nativeengine.NativeScannerBridge
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppShellViewModel(
    private val repository: OpenBulletRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val initialTab = savedStateHandle.get<String>(KEY_ACTIVE_TAB)
        ?.let { name -> runCatching { MainTab.valueOf(name) }.getOrNull() }
        ?: MainTab.RUNNER

    private val _uiState = MutableStateFlow(
        AppShellUiState(
            activeTab = initialTab,
            nativeEngineBanner = NativeScannerBridge.engineBanner()
        )
    )
    val uiState: StateFlow<AppShellUiState> = _uiState.asStateFlow()

    private val _activeDialog = MutableStateFlow<ActiveDialog>(ActiveDialog.None)
    val activeDialog: StateFlow<ActiveDialog> = _activeDialog.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.settingsFlow.collect { saved ->
                if (saved != null) {
                    _uiState.update {
                        it.copy(
                            themeMode = saved.themeMode,
                            useDynamicColor = saved.useDynamicColor,
                            accentHex = saved.accentHex,
                            showTimestampsInConsole = saved.showTimestampsInConsole
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            repository.observeRunnerWork().collect { work ->
                val active = work?.state == WorkExecutionState.ENQUEUED ||
                    work?.state == WorkExecutionState.RUNNING
                _uiState.update { it.copy(isRunnerActive = active) }
            }
        }
        viewModelScope.launch {
            repository.hitsDbFlow.collect { hits ->
                _uiState.update { it.copy(hitsCount = hits.size) }
            }
        }
        viewModelScope.launch {
            repository.proxiesFlow.collect { proxies ->
                _uiState.update {
                    it.copy(
                        totalProxiesCount = proxies.size,
                        aliveProxiesCount = proxies.count { p -> p.status == ProxyStatus.AVAILABLE }
                    )
                }
            }
        }
        viewModelScope.launch {
            repository.configsFlow.collect { configs ->
                _uiState.update { it.copy(configsCount = configs.size) }
            }
        }
        viewModelScope.launch {
            repository.wordlistsFlow.collect { lists ->
                _uiState.update { it.copy(wordlistsCount = lists.size) }
            }
        }
    }

    fun selectTab(tab: MainTab) {
        _uiState.update { current ->
            if (current.activeTab == tab) return@update current
            savedStateHandle[KEY_ACTIVE_TAB] = tab.name
            current.copy(
                activeTab = tab,
                tabHistory = (current.tabHistory + current.activeTab).takeLast(12)
            )
        }
    }

    fun openDialog(dialog: ActiveDialog) {
        _activeDialog.value = dialog
    }

    fun dismissDialog() {
        _activeDialog.value = ActiveDialog.None
    }

    fun navigateBack(hasOpenEditor: Boolean, closeEditor: () -> Unit): Boolean {
        val current = _uiState.value
        return when {
            _activeDialog.value != ActiveDialog.None -> {
                dismissDialog()
                true
            }
            hasOpenEditor -> {
                closeEditor()
                true
            }
            current.tabHistory.isNotEmpty() -> {
                val previous = current.tabHistory.last()
                savedStateHandle[KEY_ACTIVE_TAB] = previous.name
                _uiState.update {
                    it.copy(activeTab = previous, tabHistory = it.tabHistory.dropLast(1))
                }
                true
            }
            current.activeTab != MainTab.RUNNER -> {
                savedStateHandle[KEY_ACTIVE_TAB] = MainTab.RUNNER.name
                _uiState.update { it.copy(activeTab = MainTab.RUNNER) }
                true
            }
            else -> false
        }
    }

    fun toggleTimestamps() {
        val next = !_uiState.value.showTimestampsInConsole
        _uiState.update { it.copy(showTimestampsInConsole = next) }
        viewModelScope.launch {
            repository.saveSettings { it.copy(showTimestampsInConsole = next) }
        }
        _events.tryEmit(
            UiEvent.ShowSnackbar(
                if (next) "Console timestamps enabled" else "Console timestamps disabled"
            )
        )
    }

    fun showMessage(message: String) {
        _events.tryEmit(UiEvent.ShowSnackbar(message))
    }

    companion object {
        private const val KEY_ACTIVE_TAB = "active_tab"
    }
}
