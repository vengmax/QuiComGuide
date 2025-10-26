package com.wllcom.quicomguide.data.repository

import com.wllcom.quicomguide.data.source.cloud.StorageService.UserInfo
import com.wllcom.quicomguide.data.source.cloud.google.GoogleStorageDataSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageRepository @Inject constructor(
    private val google: GoogleStorageDataSource
) {
    suspend fun getUserInfo(accessToken:String) = google.getUserInfo(accessToken)
    suspend fun uploadMaterial(
        accessToken: String,
        uniqueFileName: String,
        xml: String,
        uniqueCourseName: String?,
        uniqueGroupName: String?
    ) = google.uploadMaterial(accessToken, uniqueFileName, xml, uniqueCourseName, uniqueGroupName)
    suspend fun downloadMaterial(
        accessToken: String,
        uniqueFileName: String,
        uniqueCourseName: String?,
        uniqueGroupName: String?
    ) = google.downloadMaterial(accessToken, uniqueFileName, uniqueCourseName, uniqueGroupName)
    suspend fun deleteMaterial(
        accessToken: String,
        uniqueFileName: String,
        uniqueCourseName: String?,
        uniqueGroupName: String?
    ) = google.deleteMaterial(accessToken, uniqueFileName, uniqueCourseName, uniqueGroupName)

    suspend fun createGroup(accessToken: String, uniqueGroupName: String, uniqueCourseName: String?) =
        google.createGroup(accessToken, uniqueGroupName, uniqueCourseName)
    suspend fun deleteGroup(accessToken: String, uniqueGroupName: String, uniqueCourseName: String?) =
        google.deleteGroup(accessToken, uniqueGroupName, uniqueCourseName)

    suspend fun createCourse(accessToken:String, uniqueCourseName: String) =
        google.createCourse(accessToken, uniqueCourseName)
    suspend fun deleteCourse(accessToken:String, uniqueCourseName: String) =
        google.deleteCourse(accessToken, uniqueCourseName)
}