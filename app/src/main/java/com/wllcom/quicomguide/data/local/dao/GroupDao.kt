package com.wllcom.quicomguide.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wllcom.quicomguide.data.local.entities.MaterialGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM material_groups ORDER BY name")
    fun getAllFlow(): Flow<List<MaterialGroupEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(group: MaterialGroupEntity): Long

    @Query("SELECT * FROM material_groups WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MaterialGroupEntity?
}