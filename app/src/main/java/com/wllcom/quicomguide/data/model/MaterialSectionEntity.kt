package com.wllcom.quicomguide.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sections")
data class MaterialSectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val materialId: Long,    // FK to materials.id
    val uid: String,         // uuid for section
    val title: String?,
    val content: String,
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)