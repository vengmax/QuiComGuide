package com.wllcom.quicomguide.data.source.cloud

interface StorageService {

    data class UserInfo(val usedMemory: Long, val maxMemory: Long)
    suspend fun getUserInfo(accessToken:String): UserInfo

    suspend fun uploadMaterial(
        accessToken: String,
        uniqueFileName: String,
        xml: String,
        uniqueCourseName: String?,
        uniqueGroupName: String?
    ): Boolean

    suspend fun downloadMaterial(
        accessToken: String,
        uniqueFileName: String,
        uniqueCourseName: String?,
        uniqueGroupName: String?
    ): Pair<Boolean, String>

    suspend fun deleteMaterial(
        accessToken: String,
        uniqueFileName: String,
        uniqueCourseName: String?,
        uniqueGroupName: String?
    ): Boolean

    suspend fun createGroup(accessToken:String, uniqueGroupName: String, uniqueCourseName: String?): Boolean
    suspend fun deleteGroup(accessToken:String, uniqueGroupName: String, uniqueCourseName: String?): Boolean

    suspend fun createCourse(accessToken:String, uniqueCourseName: String): Boolean
    suspend fun deleteCourse(accessToken:String, uniqueCourseName: String): Boolean
}