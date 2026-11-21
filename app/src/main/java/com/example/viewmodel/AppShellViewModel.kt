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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppShellViewModel(
    private val repository: OpenBulletRepository,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    private val initialTab = savedStateHandle.get<String>(KEY_ACTIVE_TAB)
        ?.let { name -> runCatching { MainTab.valueOf(name) }.getOrNull() }
        ?: MainTab.RUNNER

    private val initialHistory = savedStateHandle.get<String>(KEY_TAB_HISTORY)
        ?.split(',')
        ?.mapNotNull { name ->
            name.takeIf { it.isNotBlank() }?.let { runCatching { MainTab.valueOf(it) }.getOrNull() }
        }
        .orEmpty()

    private val _uiState = MutableStateFlow(
        AppShellUiState(
            activeTab = initialTab,
            tabHistory = initialHistory,
            nativeEngineBanner = NativeScannerBridge.engineBanner()
        )
    )
    val uiState: StateFlow<AppShellUiState> = _uiState.asStateFlow()

    private val _activeDialog = MutableStateFlow<ActiveDialog>(ActiveDialog.None)
    val activeDialog: StateFlow<ActiveDialog> = _activeDialog.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    init {
        val countsFlow = combine(
            repository.hitsDbFlow,
            repository.proxiesFlow,
            repository.configsFlow,
            repository.wordlistsFlow
        ) { hits, proxies, configs, wordlists ->
            WorkspaceCounts(
                hitsCount = hits.size,
                totalProxiesCount = proxies.size,
                aliveProxiesCount = proxies.count { it.status == ProxyStatus.AVAILABLE },
                configsCount = configs.size,
                wordlistsCount = wordlists.size
            )
        }

        viewModelScope.launch {
            combine(
                repository.settingsFlow,
                repository.observeRunnerWork(),
                countsFlow
            ) { saved, work, counts ->
                Triple(saved, work, counts)
            }.collect { (saved, work, counts) ->
                val isRunnerActive = work?.state == WorkExecutionState.ENQUEUED ||
                    work?.state == WorkExecutionState.RUNNING
                _uiState.update { current ->
                    current.copy(
                        themeMode = saved?.themeMode ?: current.themeMode,
                        useDynamicColor = saved?.useDynamicColor ?: current.useDynamicColor,
                        accentHex = saved?.accentHex ?: current.accentHex,
                        showTimestampsInConsole = saved?.showTimestampsInConsole ?: current.showTimestampsInConsole,
                        isRunnerActive = isRunnerActive,
                        hitsCount = counts.hitsCount,
                        totalProxiesCount = counts.totalProxiesCount,
                        aliveProxiesCount = counts.aliveProxiesCount,
                        configsCount = counts.configsCount,
                        wordlistsCount = counts.wordlistsCount
                    )
                }
            }
        }
    }

    fun selectTab(tab: MainTab) {
        _uiState.update { current ->
            if (current.activeTab == tab) return@update current
            val nextHistory = (current.tabHistory + current.activeTab).takeLast(12)
            persistNavigationState(tab, nextHistory)
            current.copy(
                activeTab = tab,
                tabHistory = nextHistory
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
                val nextHistory = current.tabHistory.dropLast(1)
                persistNavigationState(previous, nextHistory)
                _uiState.update {
                    it.copy(activeTab = previous, tabHistory = nextHistory)
                }
                true
            }
            current.activeTab != MainTab.RUNNER -> {
                persistNavigationState(MainTab.RUNNER, emptyList())
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

    private fun persistNavigationState(activeTab: MainTab, history: List<MainTab>) {
        savedStateHandle[KEY_ACTIVE_TAB] = activeTab.name
        savedStateHandle[KEY_TAB_HISTORY] = history.joinToString(",") { it.name }
    }

    private data class WorkspaceCounts(
        val hitsCount: Int,
        val totalProxiesCount: Int,
        val aliveProxiesCount: Int,
        val configsCount: Int,
        val wordlistsCount: Int
    )

    companion object {
        private const val KEY_ACTIVE_TAB = "active_tab"
        private const val KEY_TAB_HISTORY = "tab_history"
    }
}
