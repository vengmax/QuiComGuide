package com.wllcom.quicomguide.data.local.crossref

import androidx.room.Entity

@Entity(tableName = "material_group_crossref", primaryKeys = ["materialId", "groupId"])
data class MaterialGroupCrossRef(
    val materialId: Long,
    val groupId: Long
)