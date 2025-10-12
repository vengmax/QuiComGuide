package com.wllcom.quicomguide.cloud

import com.wllcom.quicomguide.data.local.entities.MaterialEntity

interface CloudStorageService {
    suspend fun uploadMaterial(material: MaterialEntity): String?
    suspend fun deleteMaterial(pathOrId: String)
    suspend fun downloadMaterial(pathOrId: String): String?
    suspend fun listIndexFile(): String?
    suspend fun updateIndexFile(indexContent: String)
}