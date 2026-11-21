package com.example.viewmodel

import androidx.lifecycle.SavedStateHandle
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
    private val exportFormatterUseCase: ExportFormatterUseCase,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    private val initialTypeFilter = savedStateHandle.get<String>(KEY_TYPE_FILTER)
        ?.let { name -> runCatching { ResultTab.valueOf(name) }.getOrNull() }

    private val _uiState = MutableStateFlow(
        HitsDbUiState(
            searchQuery = savedStateHandle.get<String>(KEY_SEARCH_QUERY).orEmpty(),
            typeFilter = initialTypeFilter
        )
    )
    val uiState: StateFlow<HitsDbUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.hitsDbFlow.collect { hits ->
                _uiState.update { current ->
                    current.withDerivedHits(hits = hits)
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        savedStateHandle[KEY_SEARCH_QUERY] = query
        _uiState.update { current ->
            current.withDerivedHits(searchQuery = query)
        }
    }

    fun setTypeFilter(type: ResultTab?) {
        savedStateHandle[KEY_TYPE_FILTER] = type?.name
        _uiState.update { current ->
            current.withDerivedHits(typeFilter = type, updateTypeFilter = true)
        }
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

    private fun HitsDbUiState.withDerivedHits(
        hits: List<HitDbItem> = this.hits,
        searchQuery: String = this.searchQuery,
        typeFilter: ResultTab? = this.typeFilter,
        updateTypeFilter: Boolean = false
    ): HitsDbUiState {
        val resolvedFilter = if (updateTypeFilter) typeFilter else this.typeFilter
        return copy(
            hits = hits,
            searchQuery = searchQuery,
            typeFilter = resolvedFilter,
            filteredHits = exportFormatterUseCase.filterHits(
                hits = hits,
                typeFilter = resolvedFilter,
                searchQuery = searchQuery
            )
        )
    }

    companion object {
        private const val KEY_SEARCH_QUERY = "hits_search_query"
        private const val KEY_TYPE_FILTER = "hits_type_filter"
    }
}
