package com.wllcom.quicomguide.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.wllcom.quicomguide.data.model.MaterialEntity
import com.wllcom.quicomguide.data.model.MaterialSectionEntity

@Database(
    entities = [MaterialEntity::class, MaterialFts::class, MaterialSectionEntity::class, SectionFts::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun materialDao(): MaterialDao
    abstract fun sectionDao(): SectionDao
    abstract fun sectionFtsDao(): SectionFtsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

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