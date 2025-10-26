package com.wllcom.quicomguide.data.local.crossref

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.wllcom.quicomguide.data.local.entities.CourseEntity
import com.wllcom.quicomguide.data.local.entities.MaterialEntity

@Entity(
    tableName = "material_course_crossref",
    primaryKeys = ["materialId", "courseId"],
    foreignKeys = [
        ForeignKey(
            entity = MaterialEntity::class,
            parentColumns = ["id"],
            childColumns = ["materialId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("materialId"), Index("courseId")]
)
data class MaterialCourseCrossRef(
    val materialId: Long,
    val courseId: Long
)