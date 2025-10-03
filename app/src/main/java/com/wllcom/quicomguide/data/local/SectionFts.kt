package com.wllcom.quicomguide.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4

@Fts4(contentEntity = com.wllcom.quicomguide.data.model.MaterialSectionEntity::class)
@Entity(tableName = "sections_fts")
data class SectionFts(
    @ColumnInfo(name = "title") val title: String?,
    @ColumnInfo(name = "content") val content: String
)