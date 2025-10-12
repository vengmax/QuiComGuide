package com.wllcom.quicomguide.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

@Fts4(contentEntity = MaterialEntity::class)
@Entity(tableName = "materials_fts")
data class MaterialFts(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowid: Long,
    val contentFts: String
)