package com.wllcom.quicomguide.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "materials")
data class MaterialEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,                // uuid (локальный уникальный идентификатор)
    val title: String,
    val xmlContent: String,
    val groupId: String? = null,    // id группы (если есть)
    val courseId: String? = null,   // id курса (если есть)
    val tags: String? = null,       // CSV / JSON of tags
    val searchIndex: String? = null,// скомпилированный текст для FTS
    val cloudPath: String? = null,  // единый путь на облако (Yandex OR Google)
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)