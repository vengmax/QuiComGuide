package com.wllcom.quicomguide.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import com.wllcom.quicomguide.data.local.entities.MaterialEntity
import com.wllcom.quicomguide.data.local.entities.SectionElementEntity
import com.wllcom.quicomguide.data.local.entities.SectionEntity

data class SectionElementWithContext(
    @Embedded val element: SectionElementEntity,
    @ColumnInfo(name = "parentSectionId") val parentSectionId: Long,
    @ColumnInfo(name = "sectionTitle") val sectionTitle: String,
    @ColumnInfo(name = "materialId") val materialId: Long,
    @ColumnInfo(name = "materialTitle") val materialTitle: String
)

@Dao
interface MaterialQueryDao {

    @Query("SELECT * FROM sections WHERE id = :id")
    suspend fun getSectionById(id: Long): SectionEntity?

    @Query("SELECT * FROM section_elements WHERE id = :id")
    suspend fun getElementById(id: Long): SectionElementEntity?

    // JOIN to get element + section + material info; only elements with embedding (non-null) are useful for embedding search
    @Query("""
        SELECT se.*, s.id as parentSectionId, s.title as sectionTitle, s.materialId as materialId, m.title as materialTitle
        FROM section_elements se
        JOIN sections s ON se.sectionId = s.id
        JOIN materials m ON s.materialId = m.id
        WHERE se.embedding IS NOT NULL
    """)
    suspend fun getAllElementsWithContextHavingEmbedding(): List<SectionElementWithContext>

    // Get one material by id with contentFts for snippet generation
    @Query("SELECT * FROM materials WHERE id = :id")
    suspend fun getMaterialById(id: Long): MaterialEntity?

    // FTS search: find matching material rowids using MATCH (returns rowid of FTS table which equals materials.rowid)
    @Query("SELECT rowid FROM materials_fts WHERE materials_fts MATCH :query")
    suspend fun searchMaterialFtsRowIds(query: String): List<Long>

    // Optional: get top N materials by id list
    @Query("SELECT * FROM materials WHERE id IN(:ids)")
    suspend fun getMaterialsByIds(ids: List<Long>): List<MaterialEntity>

    @Query("SELECT * FROM section_elements WHERE sectionId = :sectionId ORDER BY orderIndex ASC, id ASC")
    suspend fun getElementsBySectionId(sectionId: Long): List<SectionElementEntity>
}