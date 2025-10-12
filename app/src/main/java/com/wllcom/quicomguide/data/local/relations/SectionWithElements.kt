package com.wllcom.quicomguide.data.local.relations

import androidx.room.Embedded
import androidx.room.Relation
import com.wllcom.quicomguide.data.local.entities.SectionElementEntity
import com.wllcom.quicomguide.data.local.entities.SectionEntity

data class SectionWithElements(
    @Embedded val section: SectionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "sectionId"
    )
    val elements: List<SectionElementEntity>
)