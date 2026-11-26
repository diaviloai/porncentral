package com.yourname.aggregator.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yourname.aggregator.core.model.SearchFilter
import com.yourname.aggregator.core.model.VideoItem
import com.yourname.aggregator.data.local.PreferenceDao
import com.yourname.aggregator.data.repository.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val videos: List<VideoItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val currentPage: Int = 1,
    val filter: SearchFilter = SearchFilter(),
    val failedSources: List<String> = emptyList(),
    val errorMessage: String? = null
)

class HomeViewModel(
    private val repository: VideoRepository,
    preferenceDao: PreferenceDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val favoriteIds: StateFlow<Set<String>> = repository.observeFavoriteIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val activeKeywords: StateFlow<List<String>> = preferenceDao.observeKeywords()
        .map { list -> list.map { it.keyword } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    currentPage = 1,
                    errorMessage = null,
                    failedSources = emptyList()
                )
            }
            val result = repository.getHomeFeed(page = 1, filter = _uiState.value.filter)
            _uiState.update {
                it.copy(
                    videos = result.videos,
                    isLoading = false,
                    failedSources = result.failedSources,
                    errorMessage = if (result.videos.isEmpty()) "No videos returned. Check your preferences or site connections." else null
                )
            }
        }
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore) return

        val nextPage = state.currentPage + 1
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            val result = repository.getHomeFeed(page = nextPage, filter = state.filter)
            _uiState.update { current ->
                val combined = (current.videos + result.videos).distinctBy { it.id }
                current.copy(
                    videos = combined,
                    currentPage = nextPage,
                    isLoadingMore = false,
                    failedSources = (current.failedSources + result.failedSources).distinct()
                )
            }
        }
    }

    fun updateFilter(newFilter: SearchFilter) {
        _uiState.update { it.copy(filter = newFilter) }
        refresh()
    }

    fun toggleFavorite(video: VideoItem) {
        viewModelScope.launch {
            repository.toggleFavorite(video)
        }
    }

    class Factory(
        private val repository: VideoRepository,
        private val preferenceDao: PreferenceDao
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(repository, preferenceDao) as T
        }
    }
}
