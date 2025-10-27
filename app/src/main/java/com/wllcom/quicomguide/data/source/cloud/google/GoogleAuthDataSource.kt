package com.wllcom.quicomguide.data.source.cloud.google

import android.accounts.Account
import android.app.PendingIntent
import android.content.ContentValues.TAG
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.wllcom.quicomguide.data.source.cloud.AuthService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resumeWithException
import androidx.core.net.toUri


@Singleton
class GoogleAuthDataSource @Inject constructor(
    @param:ApplicationContext private val appContext: Context
): AuthService {

    companion object {
        const val WEB_CLIENT_ID = "647840220611-c6mfa6jmm09nog5ijstg4kshh40nm5rc.apps.googleusercontent.com"
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        const val USER_INFO_SCOPE = "https://www.googleapis.com/auth/userinfo.profile"
    }

    private val _authState = MutableStateFlow<AuthService.AuthState?>(null)
    override val authState: StateFlow<AuthService.AuthState?> = _authState.asStateFlow()

    override suspend fun signIn(silent: Boolean): PendingIntent? = withContext(Dispatchers.IO) {
        try {
            val listScope = listOf(
                Scope(DRIVE_FILE_SCOPE),
                Scope(USER_INFO_SCOPE),
            )
            val authorizationResult = authorize(listScope)

            if (authorizationResult.hasResolution) {
                if(!silent) {
                    val credentialData = obtainCredential()
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credentialData.credential.data)
                    Log.d(TAG, googleIdToken.id)

                    val pendingIntent = authorizationResult.pendingIntent
                    return@withContext pendingIntent
                }
                else
                    _authState.value = AuthService.AuthState.NotAuthenticated
            } else {
                val accessToken = authorizationResult.accessToken

                if (!accessToken.isNullOrBlank()) {
                    val jsonProfile = JSONObject(getUserInfo(accessToken))
                    _authState.value = AuthService.AuthState.Authenticated(
                        accessToken = accessToken,
                        email = jsonProfile.getString("email"),
                        displayName = jsonProfile.getString("name"),
                        givenName = jsonProfile.getString("given_name"),
                        familyName = jsonProfile.getString("family_name"),
                        profilePictureUri = jsonProfile.getString("picture").toUri()
                    )
                } else {
                    _authState.value = AuthService.AuthState.Error(IllegalStateException("No access token"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Auth flow failed", e)
            _authState.value = AuthService.AuthState.Error(e)
        }
        return@withContext null
    }

    override suspend fun signOut() = withContext(Dispatchers.IO) {
        try {
            val listScope = listOf(
                Scope(DRIVE_FILE_SCOPE),
                Scope(USER_INFO_SCOPE),
            )

            if(_authState.value is AuthService.AuthState.Authenticated){
                val au = _authState.value as AuthService.AuthState.Authenticated

                val accessToken = au.accessToken
                if(accessToken != null)
                    clearToken(accessToken)

                val email = au.email
                if(email != null)
                    revokeGoogleAccess(listScope, email)

                _authState.value = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Auth flow failed", e)

            _authState.value = AuthService.AuthState.Error(e)
        }
    }




    /** Private functions */

    private suspend fun obtainCredential(): GetCredentialResponse = withContext(Dispatchers.IO) {
        val credentialManager = CredentialManager.create(appContext)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(WEB_CLIENT_ID)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        credentialManager.getCredential(context = appContext, request = request)
    }

    data class AuthorizationResultWrapper(
        val hasResolution: Boolean,
        val pendingIntent: PendingIntent?,
        val accessToken: String?
    )
    private fun AuthorizationResult.toWrapper(): AuthorizationResultWrapper {
        return AuthorizationResultWrapper(
            hasResolution = this.hasResolution(),
            pendingIntent = this.pendingIntent,
            accessToken = this.accessToken
        )
    }
    private suspend fun authorize(requestedScopes: List<Scope>): AuthorizationResultWrapper =
        withContext(Dispatchers.IO) {
            val authorizationRequest = AuthorizationRequest.builder()
                .setRequestedScopes(requestedScopes)
                .build()

            val authorizationClient = Identity.getAuthorizationClient(appContext)
            suspendCancellableCoroutine<AuthorizationResult> { cont ->
                val task = authorizationClient.authorize(authorizationRequest)
                val successListener = OnSuccessListener<AuthorizationResult> { result ->
                    if (!cont.isCompleted) cont.resume(result) {}
                }
                val failureListener = OnFailureListener { e ->
                    if (!cont.isCompleted) cont.resumeWithException(e)
                }
                task.addOnSuccessListener(successListener)
                task.addOnFailureListener(failureListener)
                cont.invokeOnCancellation {
                    task.addOnFailureListener { /* no-op */ }
                }
            }.toWrapper()
        }

    private suspend fun revokeGoogleAccess(requestedScopes: List<Scope>, email: String) = withContext(Dispatchers.IO) {
        val account = Account(email,"com.google")
        val revokeAccessRequest = RevokeAccessRequest.builder()
            .setAccount(account)
            .setScopes(requestedScopes)
            .build()

        val client = Identity.getAuthorizationClient(appContext)
        val task = client.revokeAccess(revokeAccessRequest)

        suspendCancellableCoroutine { cont ->
            val successListener = OnSuccessListener<Void?> {
                if (!cont.isCompleted) {
                    Log.i(TAG, "Successfully revoked access")
                    cont.resume(Unit) {}
                }
            }
            val failureListener = OnFailureListener { e ->
                if (!cont.isCompleted) {
                    Log.e(TAG, "Failed to revoke access", e)
                    cont.resumeWithException(e)
                }
            }

            task.addOnSuccessListener(successListener)
            task.addOnFailureListener(failureListener)

            cont.invokeOnCancellation {
                task.addOnFailureListener{}
            }
        }
    }

    private suspend fun clearToken(invalidAccessToken: String) = withContext(Dispatchers.IO){
        Identity.getAuthorizationClient(appContext)
            .clearToken(ClearTokenRequest.builder().setToken(invalidAccessToken).build())
            .addOnSuccessListener { Log.i(TAG, "Successfully removed the token from the cache") }
            .addOnFailureListener{ e -> Log.e(TAG, "Failed to clear token", e) }
    }



     fun getUserInfo(accessToken: String?): String {
        val request = Request.Builder()
            .url("https://www.googleapis.com/oauth2/v1/userinfo?alt=json")
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        val response = OkHttpClient().newCall(request).execute()
        return response.body.string()
    }
}