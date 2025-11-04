package com.wllcom.quicomguide.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wllcom.quicomguide.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository
) : ViewModel() {

    val isAutoSync: StateFlow<Boolean> = repo.autoSyncFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isNotification: StateFlow<Boolean> = repo.notificationFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val lastAccountSync: StateFlow<String> = repo.accountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun setAutoSync(value: Boolean) {
        viewModelScope.launch {
            repo.setAutoSync(value)
        }
    }

    fun setNotification(value: Boolean) {
        viewModelScope.launch {
            repo.setNotification(value)
        }
    }

    fun setLastAccountSync(email: String) {
        viewModelScope.launch {
            repo.setLastAccountSync(email)
        }
    }
}