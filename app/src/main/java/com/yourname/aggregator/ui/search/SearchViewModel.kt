package com.yourname.aggregator.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yourname.aggregator.core.model.SearchFilter
import com.yourname.aggregator.core.model.VideoItem
import com.yourname.aggregator.data.repository.VideoRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val videos: List<VideoItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasSearched: Boolean = false,
    val currentPage: Int = 1,
    val filter: SearchFilter = SearchFilter(),
    val failedSources: List<String> = emptyList()
)

class SearchViewModel(
    private val repository: VideoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    val favoriteIds: StateFlow<Set<String>> = repository.observeFavoriteIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private var activeSearchJob: Job? = null

    fun onQueryChange(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
    }

    fun executeSearch() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) return

        activeSearchJob?.cancel()
        activeSearchJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    hasSearched = true,
                    currentPage = 1,
                    failedSources = emptyList()
                )
            }
            val result = repository.search(
                query = query,
                page = 1,
                filter = _uiState.value.filter
            )
            _uiState.update {
                it.copy(
                    videos = result.videos,
                    isLoading = false,
                    failedSources = result.failedSources
                )
            }
        }
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || state.query.isBlank()) return

        val nextPage = state.currentPage + 1
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            val result = repository.search(
                query = state.query,
                page = nextPage,
                filter = state.filter
            )
            _uiState.update { current ->
                current.copy(
                    videos = (current.videos + result.videos).distinctBy { it.id },
                    currentPage = nextPage,
                    isLoadingMore = false,
                    failedSources = (current.failedSources + result.failedSources).distinct()
                )
            }
        }
    }

    fun updateFilter(newFilter: SearchFilter) {
        _uiState.update { it.copy(filter = newFilter) }
        if (_uiState.value.query.isNotBlank()) {
            executeSearch()
        }
    }

    fun toggleFavorite(video: VideoItem) {
        viewModelScope.launch {
            repository.toggleFavorite(video)
        }
    }

    class Factory(
        private val repository: VideoRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SearchViewModel(repository) as T
        }
    }
}
