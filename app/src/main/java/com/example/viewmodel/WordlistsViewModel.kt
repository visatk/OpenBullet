package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.OpenBulletRepository
import com.example.model.WordlistItem
import com.example.model.WordlistsUiState
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WordlistsViewModel(
    private val repository: OpenBulletRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WordlistsUiState())
    val uiState: StateFlow<WordlistsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private var ignoreEmptyLines: Boolean = true

    init {
        viewModelScope.launch {
            repository.wordlistsFlow.collect { lists ->
                _uiState.update { it.copy(wordlists = lists) }
            }
        }
        viewModelScope.launch {
            repository.settingsFlow.collect { settings ->
                if (settings != null) {
                    ignoreEmptyLines = settings.ignoreEmptyLines
                    _uiState.update { it.copy(selectedWordlistId = settings.selectedWordlistId) }
                }
            }
        }
    }

    fun addWordlist(name: String, type: String, purpose: String, rawText: String) {
        val cleanLines = rawText
            .lineSequence()
            .map { it.trim() }
            .filter { !(ignoreEmptyLines && it.isEmpty()) }
            .toList()
        val cleanName = name.trim().ifEmpty { "wordlist_${_uiState.value.wordlists.size + 1}.txt" }
        val id = UUID.randomUUID().toString()
        val item = WordlistItem(
            id = id,
            name = cleanName,
            path = "files://wordlists/$id.txt",
            type = type.trim().ifEmpty { "Default" },
            lines = cleanLines,
            purpose = purpose.trim().ifEmpty { "Runner Input" }
        )
        viewModelScope.launch { repository.saveWordlist(item) }
        _events.tryEmit(UiEvent.ShowSnackbar("Saved wordlist '$cleanName' (${cleanLines.size} lines)"))
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
