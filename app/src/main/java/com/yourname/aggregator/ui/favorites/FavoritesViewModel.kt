package com.yourname.aggregator.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yourname.aggregator.core.model.VideoItem
import com.yourname.aggregator.data.repository.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FavoritesViewModel(
    private val repository: VideoRepository
) : ViewModel() {

    private val _filterQuery = MutableStateFlow("")
    val filterQuery: StateFlow<String> = _filterQuery.asStateFlow()

    val favorites: StateFlow<List<VideoItem>> = combine(
        repository.observeFavorites(),
        _filterQuery
    ) { allFavorites, query ->
        val clean = query.trim().lowercase()
        if (clean.isEmpty()) {
            allFavorites
        } else {
            allFavorites.filter { video ->
                video.title.lowercase().contains(clean) ||
                    video.sourceName.lowercase().contains(clean) ||
                    video.tags.any { it.lowercase().contains(clean) }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onFilterQueryChange(query: String) {
        _filterQuery.value = query
    }

    fun removeFavorite(video: VideoItem) {
        viewModelScope.launch {
            repository.toggleFavorite(video)
        }
    }

    class Factory(
        private val repository: VideoRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FavoritesViewModel(repository) as T
        }
    }
}
