package com.wllcom.quicomguide.data.local.relations

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.wllcom.quicomguide.data.local.crossref.MaterialGroupCrossRef
import com.wllcom.quicomguide.data.local.entities.MaterialEntity
import com.wllcom.quicomguide.data.local.entities.MaterialGroupEntity

data class GroupWithMaterials(
    @Embedded val group: MaterialGroupEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            MaterialGroupCrossRef::class,
            parentColumn = "groupId",    // имя колонки в crossref, которая ссылается на group
            entityColumn = "materialId"  // имя колонки в crossref, которая ссылается на material
        )
    )
    val materials: List<MaterialEntity>
)