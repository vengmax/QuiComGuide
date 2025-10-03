package com.wllcom.quicomguide.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wllcom.quicomguide.data.model.MaterialSectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SectionDao {
    @Query("SELECT * FROM sections WHERE materialId = :materialId ORDER BY position ASC")
    fun getSectionsForMaterialFlow(materialId: Long): Flow<List<MaterialSectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sections: List<MaterialSectionEntity>)

    @Query("DELETE FROM sections WHERE materialId = :materialId")
    suspend fun deleteByMaterialId(materialId: Long)

    // FTS search across sections; returns matching sections
    @Query(
        """
    SELECT s.id, s.materialId, s.uid, s.title, s.content, s.position, s.createdAt
    FROM sections s
    JOIN sections_fts ON sections_fts.rowid = s.id
    WHERE sections_fts MATCH :query
    ORDER BY s.position
"""
    )
    fun searchSectionsByFts(query: String): Flow<List<MaterialSectionEntity>>
}