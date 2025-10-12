package com.wllcom.quicomguide.data.local.crossref

import androidx.room.Entity

@Entity(tableName = "material_course_crossref", primaryKeys = ["materialId", "courseId"])
data class MaterialCourseCrossRef(
    val materialId: Long,
    val courseId: Long
)