package com.wllcom.quicomguide.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "material_groups")
data class MaterialGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)