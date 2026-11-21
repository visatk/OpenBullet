package com.example.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.OpenBulletRepository
import com.example.model.AppThemeMode
import com.example.model.SettingsSection
import com.example.model.SettingsState
import com.example.util.SoundNotifier
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: OpenBulletRepository,
    private val soundNotifier: SoundNotifier,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    private val initialSection = savedStateHandle.get<String>(KEY_ACTIVE_SECTION)
        ?.let { name -> runCatching { SettingsSection.valueOf(name) }.getOrNull() }
        ?: SettingsSection.GENERAL

    private val _uiState = MutableStateFlow(SettingsState(activeSection = initialSection))
    val uiState: StateFlow<SettingsState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.settingsFlow.collect { saved ->
                if (saved != null) {
                    _uiState.update { current ->
                        current.copy(
                            requestTimeoutSec = saved.requestTimeoutSec,
                            maxRetries = saved.maxRetries,
                            autoSaveHits = saved.autoSaveHits,
                            ignoreEmptyLines = saved.ignoreEmptyLines,
                            showTimestampsInConsole = saved.showTimestampsInConsole,
                            soundOnHit = saved.soundOnHit,
                            soundOnFinish = saved.soundOnFinish,
                            keepAwakeEnabled = saved.keepAwakeEnabled,
                            proxyJudgeUrl = saved.proxyJudgeUrl,
                            accentHex = saved.accentHex,
                            themeMode = saved.themeMode,
                            useDynamicColor = saved.useDynamicColor
                        )
                    }
                }
            }
        }
    }

    fun selectSection(section: SettingsSection) {
        savedStateHandle[KEY_ACTIVE_SECTION] = section.name
        _uiState.update { it.copy(activeSection = section) }
    }

    fun updateTimeoutDraft(sec: Int) {
        val clamped = sec.coerceIn(1, 120)
        _uiState.update { it.copy(requestTimeoutSec = clamped) }
    }

    fun commitTimeout(sec: Int? = null) {
        val clamped = (sec ?: _uiState.value.requestTimeoutSec).coerceIn(1, 120)
        _uiState.update { it.copy(requestTimeoutSec = clamped) }
        viewModelScope.launch { repository.saveSettings { it.copy(requestTimeoutSec = clamped) } }
    }

    fun updateMaxRetriesDraft(retries: Int) {
        val clamped = retries.coerceIn(0, 20)
        _uiState.update { it.copy(maxRetries = clamped) }
    }

    fun commitMaxRetries(retries: Int? = null) {
        val clamped = (retries ?: _uiState.value.maxRetries).coerceIn(0, 20)
        _uiState.update { it.copy(maxRetries = clamped) }
        viewModelScope.launch { repository.saveSettings { it.copy(maxRetries = clamped) } }
    }

    fun updateProxyJudgeUrl(url: String) {
        _uiState.update { it.copy(proxyJudgeUrl = url) }
        viewModelScope.launch { repository.saveSettings { it.copy(proxyJudgeUrl = url.trim()) } }
    }

    fun toggleAutoSave(enabled: Boolean) {
        _uiState.update { it.copy(autoSaveHits = enabled) }
        viewModelScope.launch { repository.saveSettings { it.copy(autoSaveHits = enabled) } }
    }

    fun toggleIgnoreEmpty(enabled: Boolean) {
        _uiState.update { it.copy(ignoreEmptyLines = enabled) }
        viewModelScope.launch { repository.saveSettings { it.copy(ignoreEmptyLines = enabled) } }
    }

    fun toggleKeepAwake(enabled: Boolean) {
        _uiState.update { it.copy(keepAwakeEnabled = enabled) }
        viewModelScope.launch { repository.saveSettings { it.copy(keepAwakeEnabled = enabled) } }
    }

    fun toggleTimestamps(enabled: Boolean) {
        _uiState.update { it.copy(showTimestampsInConsole = enabled) }
        viewModelScope.launch { repository.saveSettings { it.copy(showTimestampsInConsole = enabled) } }
    }

    fun toggleSoundOnHit(enabled: Boolean) {
        _uiState.update { it.copy(soundOnHit = enabled) }
        viewModelScope.launch { repository.saveSettings { it.copy(soundOnHit = enabled) } }
    }

    fun toggleSoundOnFinish(enabled: Boolean) {
        _uiState.update { it.copy(soundOnFinish = enabled) }
        viewModelScope.launch { repository.saveSettings { it.copy(soundOnFinish = enabled) } }
    }

    fun selectThemeMode(mode: AppThemeMode) {
        _uiState.update { it.copy(themeMode = mode) }
        viewModelScope.launch { repository.saveSettings { it.copy(themeMode = mode) } }
    }

    fun toggleDynamicColor(enabled: Boolean) {
        _uiState.update { it.copy(useDynamicColor = enabled) }
        viewModelScope.launch { repository.saveSettings { it.copy(useDynamicColor = enabled) } }
    }

    fun selectAccentColor(hex: Long) {
        _uiState.update { it.copy(accentHex = hex, useDynamicColor = false) }
        viewModelScope.launch {
            repository.saveSettings { it.copy(accentHex = hex, useDynamicColor = false) }
        }
    }

    fun testHitSound() {
        soundNotifier.playAlertTone(isHit = true)
    }

    fun testFinishSound() {
        soundNotifier.playAlertTone(isHit = false)
    }

    companion object {
        private const val KEY_ACTIVE_SECTION = "settings_active_section"
    }
}
