package com.wllcom.quicomguide.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wllcom.quicomguide.data.local.entities.CourseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses ORDER BY name")
    fun getAllFlow(): Flow<List<CourseEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(course: CourseEntity): Long

    @Query("SELECT * FROM courses WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CourseEntity?
}