package com.wllcom.quicomguide.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.wllcom.quicomguide.data.local.crossref.MaterialCourseCrossRef
import com.wllcom.quicomguide.data.local.entities.MaterialEntity
import com.wllcom.quicomguide.data.local.crossref.MaterialGroupCrossRef
import com.wllcom.quicomguide.data.local.entities.MaterialFts
import com.wllcom.quicomguide.data.local.entities.SectionElementChunkEmbeddingEntity
import com.wllcom.quicomguide.data.local.entities.SectionElementEntity
import com.wllcom.quicomguide.data.local.entities.SectionEntity
import com.wllcom.quicomguide.data.local.relations.MaterialWithSections
import kotlinx.coroutines.flow.Flow

@Dao
interface MaterialDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaterial(material: MaterialEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSection(section: SectionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSectionElements(elements: List<SectionElementEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunkEmbeddings(chunks: List<SectionElementChunkEmbeddingEntity>): List<Long>

    @Update
    suspend fun updateMaterial(element: MaterialEntity)

    @Update
    suspend fun updateSectionElement(element: SectionElementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaterialFts(ft: MaterialFts)

    @Transaction
    suspend fun insertMaterialTreeReturningIds(
        materialTitle: String,
        xmlRaw: String,
        sections: List<Triple<String, Int, List<Pair<String, String>>>>
    ): Pair<Long, List<Pair<Long, List<Long>>>> {
        val materialId = insertMaterial(MaterialEntity(title = materialTitle, xmlRaw = xmlRaw))
        val sectionsResult = mutableListOf<Pair<Long, List<Long>>>()
        for ((title, order, elements) in sections) {
            val sectionId = insertSection(SectionEntity(materialId = materialId, title = title, orderIndex = order))
            val elementEntities = elements.mapIndexed { idx, (type, content) ->
                SectionElementEntity(
                    sectionId = sectionId,
                    elementType = type,
                    content = content,
                    orderIndex = idx
                )
            }
            val elementIds = insertSectionElements(elementEntities)
            sectionsResult.add(sectionId to elementIds)
        }
        return materialId to sectionsResult
    }

    @Transaction
    @Query("SELECT * FROM materials WHERE id = :materialId")
    suspend fun getMaterialWithSections(materialId: Long): MaterialWithSections?

    @Transaction
    @Query("SELECT * FROM materials")
    fun getAllMaterialsFlow(): Flow<List<MaterialWithSections>>

    @Transaction
    @Query("DELETE FROM materials WHERE id = :id")
    suspend fun deleteMaterialById(id: Long)


    // -----------------------
    // Cross-ref (many-to-many)
    // -----------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaterialGroupCrossRefs(refs: List<MaterialGroupCrossRef>)

    @Query("DELETE FROM material_group_crossref WHERE materialId = :materialId")
    suspend fun deleteMaterialGroupCrossRefsByMaterial(materialId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaterialCourseCrossRefs(refs: List<MaterialCourseCrossRef>)

    @Query("DELETE FROM material_course_crossref WHERE materialId = :materialId")
    suspend fun deleteMaterialCourseCrossRefsByMaterial(materialId: Long)

    @Query("""
        SELECT m.* FROM materials m
        JOIN material_group_crossref mg ON mg.materialId = m.id
        WHERE mg.groupId = :groupId
        ORDER BY m.updatedAt DESC
    """)
    fun getMaterialsByGroupIdFlow(groupId: Long): Flow<List<MaterialEntity>>

    @Query("""
        SELECT m.* FROM materials m
        JOIN material_course_crossref mc ON mc.materialId = m.id
        WHERE mc.courseId = :courseId
        ORDER BY m.updatedAt DESC
    """)
    fun getMaterialsByCourseIdFlow(courseId: Long): Flow<List<MaterialEntity>>
}