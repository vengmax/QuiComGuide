package com.wllcom.quicomguide.data.source.cloud.google

import android.content.ContentValues.TAG
import android.util.Log
import com.wllcom.quicomguide.data.source.cloud.StorageService
import com.wllcom.quicomguide.data.source.cloud.StorageService.UserInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleStorageDataSource @Inject constructor() : StorageService {

    private val mainFolderName = "QuiComGuide"
    private val generalCourseFolderName = "GENERAL"

    override suspend fun getUserInfo(accessToken:String): UserInfo = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/about?fields=storageQuota")
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        OkHttpClient().newCall(request).execute().use { response ->
            if(response.isSuccessful) {
                val res = response.body.string()
                val jsonObject = JSONObject(res)
                val storageQuota = jsonObject.optJSONObject("storageQuota")
                val usedMemory: Long = storageQuota?.optString("usage")?.toLong() ?: -1
                val maxMemory: Long = storageQuota?.optString("limit")?.toLong() ?: -1
                return@withContext UserInfo(usedMemory, maxMemory)
            }
        }
        return@withContext UserInfo(-1, -1)
    }

    override suspend fun uploadMaterial(
        accessToken: String,
        uniqueFileName: String,
        xml: String,
        uniqueCourseName: String?,
        uniqueGroupName: String?
    ): Boolean = withContext(Dispatchers.IO) {

        // check main folder
        val (status, pairFolderId) = prepareServiceFolder(accessToken)
        if (!status)
            return@withContext false
        val (mainFolderId, generalFolderId) = pairFolderId

        // default general course
        var parentId = generalFolderId

        if(uniqueCourseName != null){
            val (statusFindCourse, courseFolderId) = findFolder(accessToken, mainFolderId!!, uniqueCourseName)
            if(!statusFindCourse || courseFolderId == null)
                return@withContext false

            if(uniqueGroupName != null){
                val (statusFindGroup, groupFolderId) = findFolder(accessToken, courseFolderId, uniqueGroupName)
                if(!statusFindGroup)
                    return@withContext false

                parentId = groupFolderId
            }
            else
                parentId = courseFolderId
        }
        else{
            if(uniqueGroupName != null){
                val (statusFindGroup, groupFolderId) = findFolder(accessToken, generalFolderId!!, uniqueGroupName)
                if(!statusFindGroup)
                    return@withContext false

                parentId = groupFolderId
            }
        }

        return@withContext smartUploadFile(accessToken, parentId!!, uniqueFileName, xml) != null
    }

    override suspend fun downloadMaterial(
        accessToken: String,
        uniqueFileName: String,
        uniqueCourseName: String?,
        uniqueGroupName: String?
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {

        return@withContext Pair(false, "")
    }

    override suspend fun deleteMaterial(
        accessToken: String,
        uniqueFileName: String,
        uniqueCourseName: String?,
        uniqueGroupName: String?
    ): Boolean = withContext(Dispatchers.IO) {

        // check main folder
        val (status, pairFolderId) = prepareServiceFolder(accessToken)
        if (!status)
            return@withContext false
        val (mainFolderId, generalFolderId) = pairFolderId

        // default general course
        var parentId = generalFolderId

        if(uniqueCourseName != null){
            val (statusFindCourse, courseFolderId) = findFolder(accessToken, mainFolderId!!, uniqueCourseName)
            if(!statusFindCourse || courseFolderId == null)
                return@withContext false

            if(uniqueGroupName != null){
                val (statusFindGroup, groupFolderId) = findFolder(accessToken, courseFolderId, uniqueGroupName)
                if(!statusFindGroup)
                    return@withContext false

                parentId = groupFolderId
            }
            else
                parentId = courseFolderId
        }
        else{
            if(uniqueGroupName != null){
                val (statusFindGroup, groupFolderId) = findFolder(accessToken, generalFolderId!!, uniqueGroupName)
                if(!statusFindGroup)
                    return@withContext false

                parentId = groupFolderId
            }
        }

        return@withContext smartDeleteFile(accessToken, parentId!!, uniqueFileName)
    }

    override suspend fun createGroup(accessToken: String, uniqueGroupName: String, uniqueCourseName: String?): Boolean =
        withContext(Dispatchers.IO) {

            // check main folder
            val (status, pairFolderId) = prepareServiceFolder(accessToken)
            if (!status)
                return@withContext false
            val (mainFolderId, generalFolderId) = pairFolderId

            // get course id
            var courseFolderId: String
            if (uniqueCourseName != null) {
                val (statusFindCourse, folderId) = findFolder(accessToken, mainFolderId!!, uniqueCourseName)
                if(!statusFindCourse || folderId == null)
                    return@withContext false

                courseFolderId = folderId
            }
            else
                courseFolderId = generalFolderId!!


            val createdFolderId = smartCreateFolder(accessToken, courseFolderId, uniqueGroupName)
            return@withContext createdFolderId != null
        }
    override suspend fun deleteGroup(accessToken: String, uniqueGroupName: String, uniqueCourseName: String?): Boolean =
        withContext(Dispatchers.IO) {

            // prepare main and general folder
            val (status, pairFolderId) = prepareServiceFolder(accessToken)
            if (!status)
                return@withContext false
            val (mainFolderId, generalFolderId) = pairFolderId

            // get course id
            var courseFolderId: String
            if (uniqueCourseName != null) {
                val (statusFindCourse, folderId) = findFolder(accessToken, mainFolderId!!, uniqueCourseName)
                if(!statusFindCourse || folderId == null)
                    return@withContext false

                courseFolderId = folderId
            }
            else
                courseFolderId = generalFolderId!!

            return@withContext smartDeleteFolder(accessToken, courseFolderId, uniqueGroupName)
        }

    override suspend fun createCourse(accessToken: String, uniqueCourseName: String): Boolean = withContext(Dispatchers.IO) {

        // prepare main and general folder
        val (status, pairFolderId) = prepareServiceFolder(accessToken)
        if(!status)
            return@withContext false
        val (mainFolderId, generalFolderId) = pairFolderId

        // create course folder
        val createdFolderId = smartCreateFolder(accessToken, mainFolderId!!, uniqueCourseName)
        return@withContext createdFolderId != null
    }
    override suspend fun deleteCourse(accessToken:String, uniqueCourseName: String): Boolean = withContext(Dispatchers.IO) {

        // prepare main and general folder
        val (status, pairFolderId) = prepareServiceFolder(accessToken)
        if(!status)
            return@withContext false
        val (mainFolderId, generalFolderId) = pairFolderId

        return@withContext smartDeleteFolder(accessToken, mainFolderId!!, uniqueCourseName)
    }



    /** Private functions */

    private suspend fun smartUploadFile(
        accessToken: String,
        parentId: String,
        fileName: String,
        xmlContent: String
    ): String? =
        withContext(Dispatchers.IO) {

            val (status, fileId) = findFile(accessToken, parentId, fileName)
            if (!status)
                return@withContext null

            val metadata = """
            {
                "name": "$fileName",
                "parents": ["$parentId"]
            }
            """.trimIndent()

            val multipartBody = MultipartBody.Builder()
                .setType("multipart/related".toMediaType())
                .addFormDataPart(
                    "metadata", null,
                    metadata.toRequestBody("application/json; charset=utf-8".toMediaType())
                )
                .addFormDataPart(
                    "file", fileName,
                    xmlContent.toRequestBody("text/xml".toMediaType())
                )
                .build()

            val request =
                if (fileId == null) {
                    Request.Builder()
                        .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                        .addHeader("Authorization", "Bearer $accessToken")
                        .post(multipartBody)
                        .build()
                } else {
                    Request.Builder()
                        .url("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media")
                        .addHeader("Authorization", "Bearer $accessToken")
                        .patch(xmlContent.toRequestBody("text/xml".toMediaType()))
                        .build()
                }

            OkHttpClient().newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val res = response.body.string()
                    val newFileId = JSONObject(res).optString("id")
                    return@withContext newFileId
                }
            }
            return@withContext null
        }

    private suspend fun smartDownloadFile(
        accessToken: String,
        parentId: String,
        fileName: String
    ): Pair<Boolean, String?> =
        withContext(Dispatchers.IO) {

            val (status, fileId) = findFile(accessToken, parentId, fileName)
            if (!status || fileId == null)
                return@withContext Pair<Boolean, String?>(false, null)

            val request = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
                .addHeader("Authorization", "Bearer $accessToken")
                .build()

            OkHttpClient().newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val res = response.body.string()
//                    val folderId = JSONObject(res).optString("id")
                    return@withContext Pair<Boolean, String?>(true, res)
                }
            }
            return@withContext Pair<Boolean, String?>(false, null)
        }

    private suspend fun smartDeleteFile(accessToken: String, parentId: String, fileName: String): Boolean =
        withContext(Dispatchers.IO) {

            val (status, fileId) = findFile(accessToken, parentId, fileName)
            if (!status)
                return@withContext false

            if (fileId == null)
                return@withContext true

            val url = "https://www.googleapis.com/drive/v3/files/$fileId"
            val request = Request.Builder()
                .url(url)
                .delete()
                .addHeader("Authorization", "Bearer $accessToken")
                .build()

            OkHttpClient().newCall(request).execute().use { response ->
                return@withContext response.isSuccessful
            }
            return@withContext false
        }

    private suspend fun findFile(accessToken: String, parentId: String, fileName: String): Pair<Boolean, String?> =
        withContext(Dispatchers.IO) {
            val query = "'$parentId' in parents and name='$fileName' and trashed=false"
            val url = "https://www.googleapis.com/drive/v3/files?q=${URLEncoder.encode(query, "UTF-8")}"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .build()

            OkHttpClient().newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val res = response.body.string()
                    val items = JSONObject(res).optJSONArray("files")
                    return@withContext if (items != null && items.length() > 0)
                        Pair<Boolean, String?>(true, JSONObject(items.optString(0)).optString("id"))
                    else
                        Pair<Boolean, String?>(true, null)
                }
            }
            return@withContext Pair<Boolean, String?>(false, null)
        }

    private suspend fun smartCreateFolder(
        accessToken: String,
        parentId: String,
        folderName: String
    ): String? =
        withContext(Dispatchers.IO) {

            val (status, folderId) = findFolder(accessToken, parentId, folderName)
            if (!status)
                return@withContext null

            if(folderId != null)
                return@withContext folderId

            val json = """
        {
            "name": "$folderName",
            "mimeType": "application/vnd.google-apps.folder",
            "parents": ["$parentId"]
        }
        """.trimIndent()

            val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files")
                .addHeader("Authorization", "Bearer $accessToken")
                .post(body)
                .build()

            OkHttpClient().newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val res = response.body.string()
                    val newFolderId = JSONObject(res).optString("id")
                    return@withContext newFolderId
                }
            }
            return@withContext null
        }

    private suspend fun smartDeleteFolder(accessToken: String, parentId: String, folderName: String): Boolean =
        withContext(Dispatchers.IO) {

            val (status, folderId) = findFolder(accessToken, parentId, folderName)
            if (!status)
                return@withContext false

            if (folderId == null)
                return@withContext true

            val url = "https://www.googleapis.com/drive/v3/files/$folderId"
            val request = Request.Builder()
                .url(url)
                .delete()
                .addHeader("Authorization", "Bearer $accessToken")
                .build()

            OkHttpClient().newCall(request).execute().use { response ->
                return@withContext response.isSuccessful
            }
            return@withContext false
        }

    private suspend fun findFolder(accessToken: String, parentId: String, folderName: String): Pair<Boolean, String?> =
        withContext(Dispatchers.IO) {
            val query =
                "mimeType='application/vnd.google-apps.folder' and '$parentId' in parents and name='$folderName' and trashed=false"
            val url = "https://www.googleapis.com/drive/v3/files?q=${URLEncoder.encode(query, "UTF-8")}"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .build()

            OkHttpClient().newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val res = response.body.string()
                    val items = JSONObject(res).optJSONArray("files")
                    return@withContext if (items != null && items.length() > 0)
                        Pair<Boolean, String?>(true, JSONObject(items.optString(0)).optString("id"))
                    else
                        Pair<Boolean, String?>(true, null)
                }
            }
            return@withContext Pair<Boolean, String?>(false, null)
        }

    private suspend fun prepareServiceFolder(accessToken: String): Pair<Boolean, Pair<String?, String?>> =
        withContext(Dispatchers.IO) {

            val mainFolderId = smartCreateFolder(accessToken, "root", mainFolderName)
            if (mainFolderId != null) {
                val generalFolderId = smartCreateFolder(accessToken, mainFolderId, generalCourseFolderName)
                if (generalFolderId != null) {
                    return@withContext Pair(
                        true,
                        Pair<String?, String?>(mainFolderId, generalFolderId)
                    )
                } else
                    return@withContext Pair(
                        false,
                        Pair<String?, String?>(null, null)
                    )
            } else
                return@withContext Pair(
                    false,
                    Pair<String?, String?>(null, null)
                )

        }
}