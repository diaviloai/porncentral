package com.yourname.aggregator.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yourname.aggregator.data.local.PreferenceDao
import com.yourname.aggregator.data.local.PreferenceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferenceDao: PreferenceDao
) : ViewModel() {

    val savedPreferences: StateFlow<List<PreferenceEntity>> = preferenceDao.observeKeywords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addKeyword(rawKeyword: String) {
        val cleaned = rawKeyword.trim().lowercase()
        if (cleaned.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            preferenceDao.upsertKeyword(PreferenceEntity(keyword = cleaned))
        }
    }

    fun removeKeyword(keyword: String) {
        viewModelScope.launch(Dispatchers.IO) {
            preferenceDao.deleteKeyword(keyword)
        }
    }

    class Factory(
        private val preferenceDao: PreferenceDao
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(preferenceDao) as T
        }
    }
}
