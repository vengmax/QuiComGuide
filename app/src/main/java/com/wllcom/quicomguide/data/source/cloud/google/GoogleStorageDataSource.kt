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

    val mainFolderName = "QuiComGuide"
    val generalCourseFolderName = "GENERAL"

    override suspend fun getUserInfo(accessToken:String): UserInfo = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/about?fields=storageQuota")
                .addHeader("Authorization", "Bearer $accessToken")
                .build()

            OkHttpClient().newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val res = response.body.string()
                    val jsonObject = JSONObject(res)
                    val storageQuota = jsonObject.optJSONObject("storageQuota")
                    val usedMemory: Long = storageQuota?.optString("usage")?.toLong() ?: -1
                    val maxMemory: Long = storageQuota?.optString("limit")?.toLong() ?: -1
                    return@withContext UserInfo(usedMemory, maxMemory)
                }
            }
            return@withContext UserInfo(-1, -1)
        } catch (e: Exception) {
            return@withContext UserInfo(-1, -1)
        }
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
    suspend fun fastUploadMaterial(
        accessToken: String,
        uniqueFileName: String,
        xml: String,
        parentId: String,
    ): String? = withContext(Dispatchers.IO) {

        val res = smartUploadFile(accessToken, parentId, uniqueFileName, xml)
        if(res != null){
            return@withContext res.second
        }
        return@withContext  null
    }

    override suspend fun downloadMaterial(
        accessToken: String,
        uniqueFileName: String,
        uniqueCourseName: String?,
        uniqueGroupName: String?
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {

        // check main folder
        val (status, pairFolderId) = prepareServiceFolder(accessToken)
        if (!status)
            return@withContext Pair(false, "")
        val (mainFolderId, generalFolderId) = pairFolderId

        // default general course
        var parentId = generalFolderId

        if(uniqueCourseName != null){
            val (statusFindCourse, courseFolderId) = findFolder(accessToken, mainFolderId!!, uniqueCourseName)
            if(!statusFindCourse || courseFolderId == null)
                return@withContext Pair(false, "")

            if(uniqueGroupName != null){
                val (statusFindGroup, groupFolderId) = findFolder(accessToken, courseFolderId, uniqueGroupName)
                if(!statusFindGroup)
                    return@withContext Pair(false, "")

                parentId = groupFolderId
            }
            else
                parentId = courseFolderId
        }
        else{
            if(uniqueGroupName != null){
                val (statusFindGroup, groupFolderId) = findFolder(accessToken, generalFolderId!!, uniqueGroupName)
                if(!statusFindGroup)
                    return@withContext Pair(false, "")

                parentId = groupFolderId
            }
        }

        return@withContext smartDownloadFile(accessToken, parentId!!, uniqueFileName)
    }
    suspend fun fastDownloadMaterial(
        accessToken: String,
        materialId: String
    ): Triple<Boolean, String, String> = withContext(Dispatchers.IO) {
        val fileMeta = getFileMeta(accessToken, materialId)
        val downloadVal = downloadFile(accessToken, materialId)
        return@withContext Triple(downloadVal.first, downloadVal.second, fileMeta!!.third)
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

    override suspend fun createGroup(accessToken: String, uniqueGroupName: String, uniqueCourseName: String?): String? =
        withContext(Dispatchers.IO) {

            // check main folder
            val (status, pairFolderId) = prepareServiceFolder(accessToken)
            if (!status)
                return@withContext null
            val (mainFolderId, generalFolderId) = pairFolderId

            // get course id
            var courseFolderId: String
            if (uniqueCourseName != null) {
                val (statusFindCourse, folderId) = findFolder(accessToken, mainFolderId!!, uniqueCourseName)
                if(!statusFindCourse || folderId == null)
                    return@withContext null

                courseFolderId = folderId
            }
            else
                courseFolderId = generalFolderId!!


            return@withContext smartCreateFolder(accessToken, courseFolderId, uniqueGroupName)
        }
    suspend fun fastCreateGroup(accessToken: String, uniqueGroupName: String, parentId: String): String? =
        withContext(Dispatchers.IO) {
            return@withContext createFolder(accessToken, parentId, uniqueGroupName)
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

    override suspend fun createCourse(accessToken: String, uniqueCourseName: String): String? = withContext(Dispatchers.IO) {

        // prepare main and general folder
        val (status, pairFolderId) = prepareServiceFolder(accessToken)
        if(!status)
            return@withContext null
        val (mainFolderId, generalFolderId) = pairFolderId

        // create course folder
        return@withContext smartCreateFolder(accessToken, mainFolderId!!, uniqueCourseName)
    }
    override suspend fun deleteCourse(accessToken:String, uniqueCourseName: String): Boolean = withContext(Dispatchers.IO) {

        // prepare main and general folder
        val (status, pairFolderId) = prepareServiceFolder(accessToken)
        if(!status)
            return@withContext false
        val (mainFolderId, generalFolderId) = pairFolderId

        return@withContext smartDeleteFolder(accessToken, mainFolderId!!, uniqueCourseName)
    }


    suspend fun listMaterials(
        accessToken: String,
        uniqueCourseName: String? = null,
        uniqueGroupName: String? = null
    ): Pair<Boolean, List<Triple<String, String, String>>> =
        withContext(Dispatchers.IO) {

            // check main folder
            val (status, pairFolderId) = prepareServiceFolder(accessToken)
            if (!status)
                return@withContext Pair(false, emptyList())
            val (mainFolderId, generalFolderId) = pairFolderId

            // default general course
            var parentId = generalFolderId

            if(uniqueCourseName != null){
                val (statusFindCourse, courseFolderId) = findFolder(accessToken, mainFolderId!!, uniqueCourseName)
                if(!statusFindCourse || courseFolderId == null)
                    return@withContext Pair(false, emptyList())

                if(uniqueGroupName != null){
                    val (statusFindGroup, groupFolderId) = findFolder(accessToken, courseFolderId, uniqueGroupName)
                    if(!statusFindGroup)
                        return@withContext Pair(false, emptyList())

                    parentId = groupFolderId
                }
                else
                    parentId = courseFolderId
            }
            else{
                if(uniqueGroupName != null){
                    val (statusFindGroup, groupFolderId) = findFolder(accessToken, generalFolderId!!, uniqueGroupName)
                    if(!statusFindGroup)
                        return@withContext Pair(false, emptyList())

                    parentId = groupFolderId
                }
            }

            return@withContext listFiles(accessToken, parentId!!)
        }

    suspend fun listMaterialsById(
        accessToken: String,
        parentId: String,
    ): Pair<Boolean, List<Triple<String, String, String>>> =
        withContext(Dispatchers.IO) {
            return@withContext listFiles(accessToken, parentId)
        }

    suspend fun listGroups(
        accessToken: String,
        uniqueCourseName: String? = null
    ): Pair<Boolean, List<Pair<String, String>>> =
        withContext(Dispatchers.IO) {

            // check main folder
            val (status, pairFolderId) = prepareServiceFolder(accessToken)
            if (!status)
                return@withContext Pair(false, emptyList())
            val (mainFolderId, generalFolderId) = pairFolderId

            // default general course
            var parentId = generalFolderId

            if(uniqueCourseName != null) {
                val (statusFindCourse, courseFolderId) = findFolder(accessToken, mainFolderId!!, uniqueCourseName)
                if (!statusFindCourse || courseFolderId == null)
                    return@withContext Pair(false, emptyList())

                parentId = courseFolderId
            }

            return@withContext listFolders(accessToken, parentId!!)
        }

    suspend fun listGroupsById(
        accessToken: String,
        parentId: String
    ): Pair<Boolean, List<Pair<String, String>>> =
        withContext(Dispatchers.IO) {
            return@withContext listFolders(accessToken, parentId)
        }

    suspend fun listCourses(
        accessToken: String,
    ): Pair<Boolean, List<Pair<String, String>>> =
        withContext(Dispatchers.IO) {

            // check main folder
            val (status, pairFolderId) = prepareServiceFolder(accessToken)
            if (!status)
                return@withContext Pair(false, emptyList())
            val (mainFolderId, generalFolderId) = pairFolderId

            return@withContext listFolders(accessToken, mainFolderId!!)
        }

    suspend fun deleteAll(accessToken: String): Boolean = withContext(Dispatchers.IO) {
        smartDeleteFolder(accessToken, "root", mainFolderName)

        // prepare main and general folder
        val (status2, pairFolderId2) = prepareServiceFolder(accessToken)
        if (!status2)
            return@withContext false

        return@withContext true
    }

    /** Private functions */

    private suspend fun listFiles(
        accessToken: String,
        parentId: String,
    ): Pair<Boolean, List<Triple<String, String, String>>> =
        withContext(Dispatchers.IO) {
            try {

                val query =
                    "mimeType != 'application/vnd.google-apps.folder' and '$parentId' in parents and trashed=false"
                val url = "https://www.googleapis.com/drive/v3/files?q=${URLEncoder.encode(query, "UTF-8")}" +
                        "&fields=files(id,name,mimeType,modifiedTime)"

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .build()

                OkHttpClient().newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val res = response.body.string()
                        val jsonArray = JSONObject(res).optJSONArray("files")
                        val list = (0 until jsonArray!!.length()).map {
                            val jsonObjectFile = JSONObject(jsonArray.optString(it))
                            Triple(
                                jsonObjectFile.optString("id"),
                                jsonObjectFile.optString("name"),
                                jsonObjectFile.optString("modifiedTime")
                            )
                        }
                        return@withContext Pair(true, list)
                    }
                }

                return@withContext Pair(false, emptyList())
            } catch (e: Exception) {
                return@withContext Pair(false, emptyList())
            }
        }

    private suspend fun listFolders(
        accessToken: String,
        parentId: String,
    ): Pair<Boolean, List<Pair<String, String>>> =
        withContext(Dispatchers.IO) {
            try {
                val query = "mimeType='application/vnd.google-apps.folder' and '$parentId' in parents and trashed=false"
                val url = "https://www.googleapis.com/drive/v3/files?q=${URLEncoder.encode(query, "UTF-8")}" +
                        "&fields=files(id,name,mimeType,modifiedTime)"

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .build()

                OkHttpClient().newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val res = response.body.string()
                        val jsonArray = JSONObject(res).optJSONArray("files")
                        val list = (0 until jsonArray!!.length()).map {
                            val str = jsonArray.optString(it)
                            JSONObject(str).optString("id") to JSONObject(str).optString("name")
                        }
                        return@withContext Pair(true, list)
                    }
                }

                return@withContext Pair(false, emptyList())
            } catch (e: Exception) {
                return@withContext Pair(false, emptyList())
            }
        }

    private suspend fun smartUploadFile(
        accessToken: String,
        parentId: String,
        fileName: String,
        xmlContent: String
    ): Pair<String, String>? =
        withContext(Dispatchers.IO) {
            try {
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
                            .url(
                                "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart" +
                                        "&fields=id,name,mimeType,modifiedTime"
                            )
                            .addHeader("Authorization", "Bearer $accessToken")
                            .post(multipartBody)
                            .build()
                    } else {
                        Request.Builder()
                            .url(
                                "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media" +
                                        "&fields=id,name,mimeType,modifiedTime"
                            )
                            .addHeader("Authorization", "Bearer $accessToken")
                            .patch(xmlContent.toRequestBody("text/xml".toMediaType()))
                            .build()
                    }

                OkHttpClient().newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val res = response.body.string()
                        val jsonObjAns = JSONObject(res)
                        val newFileId = jsonObjAns.optString("id")
                        val modifiedTimeFile = jsonObjAns.optString("modifiedTime")
                        return@withContext Pair(newFileId, modifiedTimeFile)
                    }
                }
                return@withContext null
            } catch (e: Exception) {
                return@withContext null
            }
        }

    private suspend fun smartDownloadFile(
        accessToken: String,
        parentId: String,
        fileName: String
    ): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            try {
                val (status, fileId) = findFile(accessToken, parentId, fileName)
                if (!status || fileId == null)
                    return@withContext Pair(false, "")

                val request = Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .build()

                OkHttpClient().newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val res = response.body.string()
                        return@withContext Pair(true, res)
                    }
                }
                return@withContext Pair(false, "")
            } catch (e: Exception) {
                return@withContext Pair(false, "")
            }
        }

    private suspend fun downloadFile(
        accessToken: String,
        fileId: String,
    ): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .build()

                OkHttpClient().newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val res = response.body.string()
                        return@withContext Pair(true, res)
                    }
                }
                return@withContext Pair(false, "")
            } catch (e: Exception) {
                return@withContext Pair(false, "")
            }
        }

    private suspend fun getFileMeta(accessToken: String, fileId: String): Triple<String, String, String>? =
        withContext(Dispatchers.IO) {
            try {
                val metaReq = Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files/$fileId?fields=id,name,mimeType,modifiedTime")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .build()

                OkHttpClient().newCall(metaReq).execute().use { response ->
                    if (response.isSuccessful) {
                        val res = response.body.string()
                        val jsonObjAns = JSONObject(res)
                        val fileId = jsonObjAns.optString("id")
                        val fileName = jsonObjAns.optString("name")
                        val fileModifiedTime = jsonObjAns.optString("modifiedTime")
                        return@withContext Triple(fileId, fileName, fileModifiedTime)
                    }
                }
                return@withContext null
            } catch (e: Exception) {
                return@withContext null
            }
        }

    private suspend fun smartDeleteFile(accessToken: String, parentId: String, fileName: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
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
            } catch (e: Exception) {
                return@withContext false
            }
        }

    private suspend fun findFile(accessToken: String, parentId: String, fileName: String): Pair<Boolean, String?> =
        withContext(Dispatchers.IO) {
            try {
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
            } catch (e: Exception) {
                return@withContext Pair<Boolean, String?>(false, null)
            }
        }

    private suspend fun smartCreateFolder(
        accessToken: String,
        parentId: String,
        folderName: String
    ): String? =
        withContext(Dispatchers.IO) {
            try {
                val (status, folderId) = findFolder(accessToken, parentId, folderName)
                if (!status)
                    return@withContext null

                if (folderId != null)
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
            } catch (e: Exception) {
                return@withContext null
            }
        }

    private suspend fun createFolder(
        accessToken: String,
        parentId: String,
        folderName: String
    ): String? =
        withContext(Dispatchers.IO) {
            try {
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
            } catch (e: Exception) {
                return@withContext null
            }
        }

    private suspend fun smartDeleteFolder(accessToken: String, parentId: String, folderName: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
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
            } catch (e: Exception) {
                return@withContext false
            }
        }

    private suspend fun findFolder(accessToken: String, parentId: String, folderName: String): Pair<Boolean, String?> =
        withContext(Dispatchers.IO) {
            try {
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
            } catch (e: Exception) {
                return@withContext Pair<Boolean, String?>(false, null)
            }
        }

    private suspend fun prepareServiceFolder(accessToken: String): Pair<Boolean, Pair<String?, String?>> =
        withContext(Dispatchers.IO) {
            try {
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
            } catch (e: Exception) {
                return@withContext Pair(
                    false,
                    Pair<String?, String?>(null, null)
                )
            }
        }
}