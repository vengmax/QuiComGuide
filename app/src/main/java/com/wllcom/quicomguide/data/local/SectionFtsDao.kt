package com.wllcom.quicomguide.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SectionFtsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<SectionFts>)

    @Query("DELETE FROM sections_fts")
    suspend fun clearAll()
}