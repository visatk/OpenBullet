package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.OpenBulletRepository
import com.example.domain.usecase.ExportFormatterUseCase
import com.example.model.HitDbItem
import com.example.model.HitsDbUiState
import com.example.model.ResultTab
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HitsDbViewModel(
    private val repository: OpenBulletRepository,
    private val exportFormatterUseCase: ExportFormatterUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HitsDbUiState())
    val uiState: StateFlow<HitsDbUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.hitsDbFlow.collect { hits ->
                _uiState.update { it.copy(hits = hits) }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setTypeFilter(type: ResultTab?) {
        _uiState.update { it.copy(typeFilter = type) }
    }

    fun deleteHit(id: String) {
        viewModelScope.launch { repository.deleteHit(id) }
    }

    fun clearAll() {
        viewModelScope.launch { repository.clearHitsDb() }
        _events.tryEmit(UiEvent.ShowSnackbar("Cleared Hits DB"))
    }

    fun copyHit(hit: HitDbItem) {
        val text = if (hit.capturedData.isNotBlank()) "${hit.data} | ${hit.capturedData}" else hit.data
        _events.tryEmit(
            UiEvent.CopyText(
                label = "OpenBullet Hit",
                text = text,
                feedbackMessage = "Copied ${hit.data}"
            )
        )
    }

    fun exportHits() {
        val state = _uiState.value
        val content = exportFormatterUseCase.formatFilteredHits(
            hits = state.hits,
            typeFilter = state.typeFilter,
            searchQuery = state.searchQuery
        )
        if (content.isBlank()) {
            _events.tryEmit(UiEvent.ShowSnackbar("No hits to export"))
        } else {
            _events.tryEmit(
                UiEvent.ShareFile(
                    fileName = "openbullet_hits.txt",
                    content = content,
                    mimeType = "text/plain"
                )
            )
        }
    }
}
