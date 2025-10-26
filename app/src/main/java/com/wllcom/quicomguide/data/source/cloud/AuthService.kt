package com.wllcom.quicomguide.data.source.cloud

import android.app.PendingIntent
import android.net.Uri
import kotlinx.coroutines.flow.StateFlow

interface AuthService {

    sealed class AuthState {
        object NotAuthenticated : AuthState()

        data class Authenticated(
            val accessToken: String? = null,
            val email: String? = null,
            val displayName: String? = null,
            val familyName: String? = null,
            val givenName: String? = null,
            val profilePictureUri: Uri? = null
        ) : AuthState()

        data class Error(val exception: Throwable) : AuthState()
    }

    val authState: StateFlow<AuthState>
    suspend fun signIn(silent: Boolean): PendingIntent?
    suspend fun signOut()
}