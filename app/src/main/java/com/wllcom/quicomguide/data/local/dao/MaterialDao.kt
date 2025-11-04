package com.wllcom.quicomguide.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
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
    suspend fun insertMaterialFts(ft: MaterialFts)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSection(section: SectionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSectionElements(elements: List<SectionElementEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunkEmbeddings(chunks: List<SectionElementChunkEmbeddingEntity>): List<Long>

    @Transaction
    suspend fun insertMaterialTree(
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



    @Update
    suspend fun updateMaterial(element: MaterialEntity)

    @Query("UPDATE materials SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateMaterialUpdatedAt(id: Long, updatedAt: Long)

    @Update
    suspend fun updateSection(section: SectionEntity)

    @Update
    suspend fun updateSectionElement(element: SectionElementEntity)

    @Update
    suspend fun updateChunkEmbeddings(chunks: SectionElementChunkEmbeddingEntity)



    @Query("SELECT title FROM materials")
    suspend fun getAllMaterialName(): List<String>

    @Query("SELECT * FROM materials WHERE id = :id")
    suspend fun getMaterialById(id: Long): MaterialEntity?

    @Query("SELECT * FROM materials WHERE title = :title")
    suspend fun getMaterialByTitle(title: String): MaterialEntity?

    @Query("SELECT * FROM sections WHERE id = :id")
    suspend fun getSectionById(id: Long): SectionEntity?

    @Query("SELECT * FROM section_elements WHERE id = :id")
    suspend fun getSectionElementById(id: Long): SectionElementEntity?

    @Query("SELECT rowid FROM materials_fts WHERE materials_fts MATCH :query")
    suspend fun searchMaterialFtsRowIds(query: String): List<Long>

//    @Transaction
//    @Query("SELECT rowid, contentFts FROM materials_fts")
//    suspend fun getAllMaterialsFts(): List<MaterialFts>

    @Transaction
    @Query("SELECT * FROM materials WHERE id = :materialId")
    suspend fun getMaterialWithSections(materialId: Long): MaterialWithSections?

    @Transaction
    @Query("SELECT * FROM materials")
    fun getAllMaterialWithSectionsFlow(): Flow<List<MaterialWithSections>>

    @Transaction
    @Query("SELECT * FROM materials")
    suspend fun getAllMaterialWithSections(): List<MaterialWithSections>

    @Transaction
    @Query("SELECT * FROM materials")
    fun getAllMaterialFlow(): Flow<List<MaterialEntity>>


    data class SectionElementWithContext(
        @Embedded val element: SectionElementEntity,
        @ColumnInfo(name = "parentSectionId") val parentSectionId: Long,
        @ColumnInfo(name = "sectionTitle") val sectionTitle: String,
        @ColumnInfo(name = "materialId") val materialId: Long,
        @ColumnInfo(name = "materialTitle") val materialTitle: String
    )
    @Query("""
        SELECT se.*, s.id as parentSectionId, s.title as sectionTitle, s.materialId as materialId, m.title as materialTitle
        FROM section_elements se
        JOIN sections s ON se.sectionId = s.id
        JOIN materials m ON s.materialId = m.id
        WHERE se.embedding IS NOT NULL
    """)
    suspend fun getAllElementsWithContextHavingEmbedding(): List<SectionElementWithContext>


    data class SectionElementChunkWithContext(
        @Embedded val chunk: SectionElementChunkEmbeddingEntity,
        @ColumnInfo(name = "elementId") val elementId: Long,
        @ColumnInfo(name = "elementType") val elementType: String,
        @ColumnInfo(name = "sectionId") val sectionId: Long,
        @ColumnInfo(name = "sectionTitle") val sectionTitle: String,
        @ColumnInfo(name = "materialId") val materialId: Long,   // 👈 это нужно
        @ColumnInfo(name = "materialTitle") val materialTitle: String
    )
    @Query("""
    SELECT 
        c.*, 
        se.id AS elementId,
        se.elementType AS elementType,
        s.id AS sectionId,
        s.title AS sectionTitle,
        m.id AS materialId,          
        m.title AS materialTitle
    FROM section_element_chunk_embeddings c
    JOIN section_elements se ON c.sectionElementId = se.id
    JOIN sections s ON se.sectionId = s.id
    JOIN materials m ON s.materialId = m.id
""")
    suspend fun getAllChunksWithContext(): List<SectionElementChunkWithContext>

    @Query("""
      SELECT * FROM section_element_chunk_embeddings
      WHERE sectionElementId = :sectionElementId AND chunkIndex = :chunkIndex
      LIMIT 1
    """)
    fun getChunkByElementIdAndIndex(
        sectionElementId: Long,
        chunkIndex: Int
    ): SectionElementChunkEmbeddingEntity?


    @Transaction
    @Query("DELETE FROM materials WHERE id = :id")
    suspend fun deleteMaterialById(id: Long)


    // -----------------------
    // Cross-ref (many-to-many)
    // -----------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaterialGroupCrossRefs(refs: List<MaterialGroupCrossRef>)

    @Query("""
      UPDATE material_group_crossref
      SET groupId = :newGroupId
      WHERE materialId = :materialId AND groupId = :oldGroupId
    """)
    suspend fun updateGroupForMaterial(materialId: Long, oldGroupId: Long, newGroupId: Long)

    @Query("DELETE FROM material_group_crossref WHERE materialId = :materialId")
    suspend fun deleteMaterialGroupCrossRefsByMaterial(materialId: Long)

    @Query("DELETE FROM material_group_crossref WHERE materialId = :materialId AND groupId = :oldGroupId")
    suspend fun deleteMaterialGroupCrossRefByMaterial(materialId: Long, oldGroupId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaterialCourseCrossRefs(refs: List<MaterialCourseCrossRef>)

    @Query("""
      UPDATE material_course_crossref
      SET courseId = :newCourseId
      WHERE materialId = :materialId AND courseId = :oldCourseId
    """)
    suspend fun updateCourseForMaterial(materialId: Long, oldCourseId: Long, newCourseId: Long)

    @Query("DELETE FROM material_course_crossref WHERE materialId = :materialId")
    suspend fun deleteMaterialCourseCrossRefsByMaterial(materialId: Long)

    @Query("DELETE FROM material_course_crossref WHERE materialId = :materialId AND courseId = :oldCourseId")
    suspend fun deleteMaterialCourseCrossRefByMaterial(materialId: Long, oldCourseId: Long)

    @Transaction
    @Query("""
        SELECT m.* FROM materials m
        JOIN material_group_crossref mg ON mg.materialId = m.id
        WHERE (:groupId IS NULL AND mg.groupId IS NULL) OR (:groupId IS NOT NULL AND mg.groupId = :groupId)
        ORDER BY m.updatedAt DESC
    """)
    fun getMaterialsByGroupIdFlow(groupId: Long?): Flow<List<MaterialEntity>>

    @Transaction
    @Query("""
        SELECT m.* FROM materials m
        JOIN material_group_crossref mg ON mg.materialId = m.id
        WHERE (:groupId IS NULL AND mg.groupId IS NULL) OR (:groupId IS NOT NULL AND mg.groupId = :groupId)
        ORDER BY m.updatedAt DESC
    """)
    suspend fun getMaterialsByGroupId(groupId: Long?): List<MaterialEntity>

    @Transaction
    @Query("""
        DELETE FROM materials
        WHERE id IN (
            SELECT m.id FROM materials m
            JOIN material_group_crossref mg ON mg.materialId = m.id
            WHERE mg.groupId = :groupId
        )
    """)
    suspend fun deleteMaterialsByGroupId(groupId: Long)

    @Transaction
    @Query("""
        SELECT m.* FROM materials m
        JOIN material_course_crossref mc ON mc.materialId = m.id
        WHERE (:courseId IS NULL AND mc.courseId IS NULL) OR (:courseId IS NOT NULL AND mc.courseId = :courseId)
        ORDER BY m.updatedAt DESC
    """)
    fun getMaterialsByCourseIdFlow(courseId: Long?): Flow<List<MaterialEntity>>

    @Transaction
    @Query("""
        SELECT m.* FROM materials m
        LEFT JOIN material_course_crossref mc ON mc.materialId = m.id
        WHERE (:courseId IS NULL AND mc.courseId IS NULL) OR (:courseId IS NOT NULL AND mc.courseId = :courseId)
        ORDER BY m.updatedAt DESC
    """)
    suspend fun getMaterialsByCourseId(courseId: Long?): List<MaterialEntity>

    @Transaction
    @Query("""
    SELECT m.* FROM materials m
    LEFT JOIN material_course_crossref mc ON mc.materialId = m.id
    LEFT JOIN material_group_crossref mg ON mg.materialId = m.id
    WHERE (
            (:courseId IS NULL AND mc.courseId IS NULL)
         OR (:courseId IS NOT NULL AND mc.courseId = :courseId)
    )
      AND mg.materialId IS NULL
    ORDER BY m.updatedAt DESC
""")
    suspend fun getNoGroupedMaterialsByCourseId(courseId: Long?): List<MaterialEntity>

    @Transaction
    @Query("""
        DELETE FROM materials
        WHERE id IN (
            SELECT m.id FROM materials m
            JOIN material_course_crossref mc ON mc.materialId = m.id
            WHERE mc.courseId = :courseId
        )
    """)
    suspend fun deleteMaterialsByCourseId(courseId: Long)

    // Получить id связанных групп
    @Query("SELECT groupId FROM material_group_crossref WHERE materialId = :materialId")
    suspend fun getGroupIdsByMaterialId(materialId: Long): List<Long>

    // Получить id связанных курсов
    @Query("SELECT courseId FROM material_course_crossref WHERE materialId = :materialId")
    suspend fun getCourseIdsByMaterialId(materialId: Long): List<Long>

    @Transaction
    suspend fun moveMaterial(oldMaterialId: Long, newMaterialId: Long) {
        val oldMaterial = getMaterialById(oldMaterialId)
        val newMaterial = getMaterialById(newMaterialId)
        val createdOldMaterial = oldMaterial?.createdAt?: return
        val updatedNewMaterial = newMaterial?.copy(createdAt = createdOldMaterial, updatedAt = System.currentTimeMillis())?: return
        updateMaterial(updatedNewMaterial)

        val groupIds = getGroupIdsByMaterialId(oldMaterialId)
        val courseIds = getCourseIdsByMaterialId(oldMaterialId)

        deleteMaterialById(oldMaterialId)

        if (groupIds.isNotEmpty()) {
            val refs = groupIds.map { MaterialGroupCrossRef(newMaterialId, it) }
            insertMaterialGroupCrossRefs(refs)
        }

        if (courseIds.isNotEmpty()) {
            val refs = courseIds.map { MaterialCourseCrossRef(newMaterialId, it) }
            insertMaterialCourseCrossRefs(refs)
        }
    }
}