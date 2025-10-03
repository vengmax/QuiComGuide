package com.wllcom.quicomguide.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4

// Виртуальная FTS таблица для быстрого полнотекстового поиска по title + searchIndex
@Fts4(contentEntity = com.wllcom.quicomguide.data.model.MaterialEntity::class)
@Entity(tableName = "materials_fts")
data class MaterialFts(
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "searchIndex") val searchIndex: String
)