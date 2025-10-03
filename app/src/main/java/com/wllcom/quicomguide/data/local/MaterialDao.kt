package com.wllcom.quicomguide.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wllcom.quicomguide.data.model.MaterialEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MaterialDao {

    @Query("SELECT * FROM materials ORDER BY updatedAt DESC")
    fun getAllFlow(): Flow<List<MaterialEntity>>

    @Query("SELECT * FROM materials WHERE id = :id LIMIT 1")
    fun getByIdFlow(id: Long): Flow<MaterialEntity?>

    @Query("SELECT * FROM materials WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MaterialEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(material: MaterialEntity): Long

    @Update
    suspend fun update(material: MaterialEntity)

    @Delete
    suspend fun delete(material: MaterialEntity)

    /**
     * FTS search.
     * - Мы связываем виртуальную FTS-таблицу с основной таблицей через rowid = id.
     * - Запрос возвращает все поля основной таблицы (m.*), чтобы Room мог сопоставить MaterialEntity.
     * - :query должен быть сформирован под FTS MATCH (например: "анимированн* OR qt").
     */
    @Query(
        """
        SELECT m.* FROM materials m
        JOIN materials_fts ON materials_fts.rowid = m.id
        WHERE materials_fts MATCH :query
        ORDER BY m.updatedAt DESC
    """
    )
    fun searchByFts(query: String): Flow<List<MaterialEntity>>

    /**
     * Простой LIKE-поиск (fallback).
     */
    @Query("SELECT * FROM materials WHERE title LIKE '%' || :term || '%' OR searchIndex LIKE '%' || :term || '%' ORDER BY updatedAt DESC")
    fun searchSimple(term: String): Flow<List<MaterialEntity>>
}