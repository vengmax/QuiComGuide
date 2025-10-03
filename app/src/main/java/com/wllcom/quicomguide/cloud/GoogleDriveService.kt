package com.wllcom.quicomguide.cloud

import com.wllcom.quicomguide.data.model.MaterialEntity
import kotlinx.coroutines.delay

class GoogleDriveService : CloudStorageService {
    override suspend fun uploadMaterial(material: MaterialEntity): String? {
        // TODO: Реализовать реальную загрузку через Google Drive REST / SDK
        // Для прототипа — имитируем задержку и возвращаем строковый id
        delay(400)
        return "gdrive://${material.uid}"
    }

    override suspend fun deleteMaterial(pathOrId: String) {
        // TODO
        delay(200)
    }

    override suspend fun downloadMaterial(pathOrId: String): String? {
        delay(300)
        return null
    }

    override suspend fun listIndexFile(): String? {
        // TODO: вернуть JSON списка материалов на диске
        delay(200)
        return null
    }

    override suspend fun updateIndexFile(indexContent: String) {
        delay(200)
    }
}