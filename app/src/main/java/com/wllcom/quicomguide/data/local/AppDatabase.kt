package com.wllcom.quicomguide.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.wllcom.quicomguide.data.local.dao.CourseDao
import com.wllcom.quicomguide.data.local.dao.GroupDao
import com.wllcom.quicomguide.data.local.dao.MaterialDao
import com.wllcom.quicomguide.data.local.entities.MaterialEntity
import com.wllcom.quicomguide.data.local.entities.MaterialFts
import com.wllcom.quicomguide.data.local.entities.MaterialGroupEntity
import com.wllcom.quicomguide.data.local.entities.CourseEntity
import com.wllcom.quicomguide.data.local.crossref.MaterialCourseCrossRef
import com.wllcom.quicomguide.data.local.crossref.MaterialGroupCrossRef
import com.wllcom.quicomguide.data.local.entities.SectionElementChunkEmbeddingEntity
import com.wllcom.quicomguide.data.local.entities.SectionElementEntity
import com.wllcom.quicomguide.data.local.entities.SectionEntity

@Database(
    entities = [
        MaterialEntity::class,
        MaterialFts::class,
        SectionEntity::class,
        SectionElementEntity::class,
        SectionElementChunkEmbeddingEntity::class,
        MaterialGroupEntity::class,
        CourseEntity::class,
        MaterialGroupCrossRef::class,
        MaterialCourseCrossRef::class,
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun materialDao(): MaterialDao
    abstract fun groupDao(): GroupDao
    abstract fun courseDao(): CourseDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val inst = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "quicomguide_db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = inst
                inst
            }
        }
    }
}