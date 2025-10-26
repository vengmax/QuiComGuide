package com.wllcom.quicomguide.data.local.crossref

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.wllcom.quicomguide.data.local.entities.MaterialEntity
import com.wllcom.quicomguide.data.local.entities.MaterialGroupEntity

@Entity(
    tableName = "material_group_crossref",
    primaryKeys = ["materialId", "groupId"],
    foreignKeys = [
        ForeignKey(
            entity = MaterialEntity::class,
            parentColumns = ["id"],
            childColumns = ["materialId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MaterialGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("materialId"), Index("groupId")]
)
data class MaterialGroupCrossRef(
    val materialId: Long,
    val groupId: Long
)