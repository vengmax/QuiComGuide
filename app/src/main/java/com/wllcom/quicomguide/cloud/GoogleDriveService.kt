package com.wllcom.quicomguide.cloud

import com.wllcom.quicomguide.data.local.entities.MaterialEntity
import kotlinx.coroutines.delay

class GoogleDriveService : CloudStorageService {
    override suspend fun uploadMaterial(material: MaterialEntity): String? {
        delay(400)
        return "gdrive://1"
    }

    override suspend fun deleteMaterial(pathOrId: String) {
        delay(200)
    }

    override suspend fun downloadMaterial(pathOrId: String): String? {
        delay(300)
        return null
    }

    override suspend fun listIndexFile(): String? {
        delay(200)
        return null
    }

    override suspend fun updateIndexFile(indexContent: String) {
        delay(200)
    }
}