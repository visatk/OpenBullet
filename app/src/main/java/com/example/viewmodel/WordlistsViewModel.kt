package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.OpenBulletRepository
import com.example.domain.usecase.CreateWordlistUseCase
import com.example.model.WordlistItem
import com.example.model.WordlistsUiState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WordlistsViewModel(
    private val repository: OpenBulletRepository,
    private val createWordlistUseCase: CreateWordlistUseCase = CreateWordlistUseCase(repository)
) : ViewModel() {

    private val _uiState = MutableStateFlow(WordlistsUiState())
    val uiState: StateFlow<WordlistsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private var ignoreEmptyLines: Boolean = true

    init {
        viewModelScope.launch {
            combine(
                repository.wordlistsFlow,
                repository.settingsFlow
            ) { lists, settings ->
                lists to settings
            }.collect { (lists, settings) ->
                if (settings != null) {
                    ignoreEmptyLines = settings.ignoreEmptyLines
                }
                _uiState.update { current ->
                    current.copy(
                        wordlists = lists,
                        selectedWordlistId = if (settings != null) {
                            settings.selectedWordlistId
                        } else {
                            current.selectedWordlistId
                        }
                    )
                }
            }
        }
    }

    fun addWordlist(name: String, type: String, purpose: String, rawText: String) {
        val fallbackIdx = _uiState.value.wordlists.size + 1
        viewModelScope.launch {
            val created = createWordlistUseCase(
                rawText = rawText,
                name = name,
                type = type,
                purpose = purpose,
                ignoreEmptyLines = ignoreEmptyLines,
                requireNonEmpty = false,
                fallbackIndex = fallbackIdx
            ) ?: return@launch
            _events.tryEmit(
                UiEvent.ShowSnackbar("Saved wordlist '${created.name}' (${created.lineCount} lines)")
            )
        }
    }

    fun selectForRunner(wordlist: WordlistItem) {
        _uiState.update { it.copy(selectedWordlistId = wordlist.id) }
        viewModelScope.launch {
            repository.saveSettings {
                val maxLine = wordlist.lineCount.coerceAtLeast(1)
                it.copy(
                    selectedWordlistId = wordlist.id,
                    startLine = it.startLine.coerceIn(1, maxLine)
                )
            }
        }
        _events.tryEmit(UiEvent.ShowSnackbar("Loaded '${wordlist.name}' into Runner"))
    }

    fun deleteWordlist(id: String) {
        viewModelScope.launch {
            repository.deleteWordlist(id)
            if (_uiState.value.selectedWordlistId == id) {
                repository.saveSettings { it.copy(selectedWordlistId = null) }
            }
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.clearWordlists()
            repository.saveSettings { it.copy(selectedWordlistId = null) }
        }
        _events.tryEmit(UiEvent.ShowSnackbar("Cleared all wordlists"))
    }
}
