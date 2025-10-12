package com.wllcom.quicomguide.data.local.relations

import androidx.room.Embedded
import androidx.room.Relation
import com.wllcom.quicomguide.data.local.entities.MaterialEntity
import com.wllcom.quicomguide.data.local.entities.SectionEntity

data class MaterialWithSections(
    @Embedded val material: MaterialEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "materialId",
        entity = SectionEntity::class
    )
    val sections: List<SectionWithElements>
)