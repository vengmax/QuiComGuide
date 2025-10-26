package com.wllcom.quicomguide.data.repository

import android.accounts.Account
import android.app.PendingIntent
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resumeWithException



//@Singleton
//class GoogleAuthRepository @Inject constructor(
//    @param:ApplicationContext private val appContext: Context
//) {
//
//    private val client = OkHttpClient()
//
//    companion object {
//        const val WEB_CLIENT_ID = "647840220611-c6mfa6jmm09nog5ijstg4kshh40nm5rc.apps.googleusercontent.com"
//        const val ANDROID_CLIENT_ID = "647840220611-rrth7ar41megp3askkgptm4sdri7la71.apps.googleusercontent.com"
//        private const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive"
//    }
//
//    suspend fun obtainCredential(): GetCredentialResponse = withContext(Dispatchers.IO) {
//        val credentialManager = CredentialManager.create(appContext)
//
//        val googleIdOption = GetGoogleIdOption.Builder()
//            .setServerClientId(WEB_CLIENT_ID)
//            .setFilterByAuthorizedAccounts(false)
//            .setAutoSelectEnabled(false)
//            .build()
//
//        val request = GetCredentialRequest.Builder()
//            .addCredentialOption(googleIdOption)
//            .build()
//
//        credentialManager.getCredential(context = appContext, request = request)
//    }
//
//    suspend fun parseGoogleIdToken(credentialResponse: GetCredentialResponse): GoogleIdTokenCredential {
//        return withContext(Dispatchers.Default) {
//            val credential = credentialResponse.credential
//            GoogleIdTokenCredential.createFrom(credential.data)
//        }
//    }
//
//    suspend fun authorizeForScopes(requestedScopes: List<Scope>): AuthorizationResultWrapper =
//        withContext(Dispatchers.IO) {
//            val authorizationRequest = AuthorizationRequest.builder()
//                .setRequestedScopes(requestedScopes)
//                .build()
//
//            val authorizationClient = Identity.getAuthorizationClient(appContext)
//            suspendCancellableCoroutine<AuthorizationResult> { cont ->
//                val task = authorizationClient.authorize(authorizationRequest)
//                val successListener = OnSuccessListener<AuthorizationResult> { result ->
//                    if (!cont.isCompleted) cont.resume(result) {}
//                }
//                val failureListener = OnFailureListener { e ->
//                    if (!cont.isCompleted) cont.resumeWithException(e)
//                }
//                task.addOnSuccessListener(successListener)
//                task.addOnFailureListener(failureListener)
//                cont.invokeOnCancellation {
//                    task.addOnFailureListener { /* no-op */ }
//                }
//            }.toWrapper()
//        }
//
//    suspend fun revokeGoogleAccess(requestedScopes: List<Scope>, email: String) = withContext(Dispatchers.IO) {
//        val account = Account(email,"com.google")
//        val revokeAccessRequest = RevokeAccessRequest.builder()
//            .setAccount(account)
//            .setScopes(requestedScopes)
//            .build()
//
//        val client = Identity.getAuthorizationClient(appContext)
//        val task = client.revokeAccess(revokeAccessRequest)
//
//        suspendCancellableCoroutine { cont ->
//            val successListener = OnSuccessListener<Void?> {
//                if (!cont.isCompleted) {
//                    Log.i(TAG, "Successfully revoked access")
//                    cont.resume(Unit) {}
//                }
//            }
//            val failureListener = OnFailureListener { e ->
//                if (!cont.isCompleted) {
//                    Log.e(TAG, "Failed to revoke access", e)
//                    cont.resumeWithException(e)
//                }
//            }
//
//            task.addOnSuccessListener(successListener)
//            task.addOnFailureListener(failureListener)
//
//            cont.invokeOnCancellation {
//                task.addOnFailureListener{}
//            }
//        }
//    }
//
//    suspend fun clearToken(invalidAccessToken: String) = withContext(Dispatchers.IO){
//        Identity.getAuthorizationClient(appContext)
//            .clearToken(ClearTokenRequest.builder().setToken(invalidAccessToken).build())
//            .addOnSuccessListener { Log.i(TAG, "Successfully removed the token from the cache") }
//            .addOnFailureListener{ e -> Log.e(TAG, "Failed to clear token", e) }
//    }
//
//    suspend fun getAuthorizationResultFromIntent(intent: Intent?): AuthorizationResultWrapper =
//        withContext(Dispatchers.IO) {
//            val authorizationClient = Identity.getAuthorizationClient(appContext)
//            authorizationClient.getAuthorizationResultFromIntent(intent).toWrapper()
//        }
//
//    suspend fun getUserInfo(accessToken: String?): String? {
//        val request = Request.Builder()
//            .url("https://www.googleapis.com/oauth2/v1/userinfo?alt=json")
//            .addHeader("Authorization", "Bearer $accessToken")
//            .build()
//
//        val response = OkHttpClient().newCall(request).execute()
//        return response.body.string()
//    }
//
//    suspend fun getDriveInfo(accessToken: String?): String? {
//        val request = Request.Builder()
//            .url("https://www.googleapis.com/drive/v3/about?fields=*")
//            .addHeader("Authorization", "Bearer $accessToken")
//            .build()
//
//        val response = OkHttpClient().newCall(request).execute()
//        return response.body.string()
//    }
//
//    suspend fun uploadToDrive(accessToken: String) {
//        withContext(Dispatchers.IO) {
//            try {
//                val file = File.createTempFile("sample", ".txt").apply {
//                    writeText("Hello from new Google Identity API!")
//                }
//
//                val metadata = JSONObject().apply { put("name", "SampleFile.txt") }.toString()
//
//                val multipartBody = MultipartBody.Builder()
//                    .setType(MultipartBody.MIXED)
//                    .addPart(
//                        Headers.headersOf(
//                            "Content-Disposition",
//                            "form-data; name=\"metadata\""
//                        ),
//                        metadata.toRequestBody("application/json; charset=UTF-8".toMediaType())
//                    )
//                    .addPart(
//                        Headers.headersOf(
//                            "Content-Disposition",
//                            "form-data; name=\"file\"; filename=\"${file.name}\""
//                        ),
//                        file.asRequestBody("text/plain".toMediaType())
//                    )
//                    .build()
//
//                val request = Request.Builder()
//                    .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
//                    .addHeader("Authorization", "Bearer $accessToken")
//                    .post(multipartBody)
//                    .build()
//
//                CoroutineScope(Dispatchers.IO).launch {
//                    client.newCall(request).execute().use { resp ->
//                        if (resp.isSuccessful) {
//                            Log.d("Drive", "Uploaded: ${resp.body?.string()}")
//                        } else {
//                            Log.d("Drive", "Upload failed: ${resp.code} ${resp.body?.string()}")
//                        }
//                    }
//                }
//            } catch (e: Exception) {
//                Log.d("Drive", "Error: ${e.message}", e)
//            }
//        }
//    }
//
//    private fun AuthorizationResult.toWrapper(): AuthorizationResultWrapper {
//        return AuthorizationResultWrapper(
//            hasResolution = this.hasResolution(),
//            pendingIntent = this.pendingIntent,
//            accessToken = this.accessToken
//        )
//    }
//}