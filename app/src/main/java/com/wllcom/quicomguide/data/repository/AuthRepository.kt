package com.wllcom.quicomguide.data.repository

import com.wllcom.quicomguide.data.source.cloud.google.GoogleAuthDataSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val google: GoogleAuthDataSource
)  {
    val authState = google.authState
    suspend fun signIn(silent: Boolean) = google.signIn(silent)
    suspend fun signOut() = google.signOut()
}