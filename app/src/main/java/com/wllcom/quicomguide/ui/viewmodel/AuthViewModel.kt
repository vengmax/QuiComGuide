package com.wllcom.quicomguide.ui.viewmodel

import android.app.PendingIntent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wllcom.quicomguide.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _pendingIntentEvent = MutableStateFlow<PendingIntent?>(null)
    val pendingIntentEvent: StateFlow<PendingIntent?> = _pendingIntentEvent.asStateFlow()

    val authState = authRepository.authState

    fun signIn(silent: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            _pendingIntentEvent.value = authRepository.signIn(silent)
        }
    }

    fun signOut(){
        viewModelScope.launch(Dispatchers.IO) {
            authRepository.signOut()
        }
    }
}
