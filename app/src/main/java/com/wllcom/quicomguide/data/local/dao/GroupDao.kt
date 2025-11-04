package com.wllcom.quicomguide.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.wllcom.quicomguide.data.local.entities.MaterialGroupEntity
import com.wllcom.quicomguide.data.local.relations.GroupWithMaterials
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {

    @Query("SELECT name FROM material_groups")
    suspend fun getAllGroupName(): List<String>

    @Query("SELECT * FROM material_groups ORDER BY name")
    fun getAllGroupsFlow(): Flow<List<MaterialGroupEntity>>

    @Query("SELECT * FROM material_groups ORDER BY name")
    suspend fun getAllGroups(): List<MaterialGroupEntity>

    @Query("SELECT * FROM material_groups WHERE ((:courseId IS NULL AND courseId IS NULL) OR courseId = :courseId)")
    suspend fun getGroupsByCourseId(courseId: Long?): List<MaterialGroupEntity>

    @Query("SELECT * FROM material_groups WHERE id = :id LIMIT 1")
    suspend fun getGroupById(id: Long): MaterialGroupEntity?

    @Query("SELECT * FROM material_groups WHERE name = :name LIMIT 1")
    suspend fun getGroupByName(name: String): MaterialGroupEntity?

    @Transaction
    @Query("SELECT * FROM material_groups")
    fun getAllGroupsWithMaterialsFlow(): Flow<List<GroupWithMaterials>>

    @Transaction
    @Query("SELECT * FROM material_groups WHERE ((:courseId IS NULL AND courseId IS NULL) OR courseId = :courseId)")
    fun getGroupsWithMaterialsByCourseIdFlow(courseId: Long?): Flow<List<GroupWithMaterials>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGroup(group: MaterialGroupEntity): Long

    @Query("DELETE FROM material_groups WHERE id = :groupId")
    suspend fun deleteGroupById(groupId: Long)
}