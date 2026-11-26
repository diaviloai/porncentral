package com.yourname.aggregator.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yourname.aggregator.core.model.StreamSource
import com.yourname.aggregator.core.model.VideoItem
import com.yourname.aggregator.data.repository.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface PlayerUiState {
    data object Resolving : PlayerUiState
    data class Ready(val source: StreamSource) : PlayerUiState
}

class PlayerViewModel(
    private val video: VideoItem,
    private val repository: VideoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Resolving)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    val isFavorite: StateFlow<Boolean> = repository.observeFavoriteIds()
        .map { ids -> video.id in ids }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        resolve()
    }

    fun resolve() {
        viewModelScope.launch {
            _uiState.value = PlayerUiState.Resolving
            val resolved = repository.resolveStream(video)
            _uiState.value = PlayerUiState.Ready(resolved)
        }
    }

    /** Called when the WebView intercepts a live .m3u8 or .mp4 stream URL during page load. */
    fun promoteToDirectStream(streamUrl: String, headers: Map<String, String>) {
        val current = (_uiState.value as? PlayerUiState.Ready)?.source
        if (current is StreamSource.DirectStream) return

        _uiState.value = PlayerUiState.Ready(
            StreamSource.DirectStream(
                url = streamUrl,
                headers = headers
            )
        )
    }

    fun switchToWebFallback() {
        _uiState.value = PlayerUiState.Ready(StreamSource.WebFallback(video.pageUrl))
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            repository.toggleFavorite(video)
        }
    }

    class Factory(
        private val video: VideoItem,
        private val repository: VideoRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PlayerViewModel(video, repository) as T
        }
    }
}
