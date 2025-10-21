package com.wllcom.quicomguide.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "section_element_chunk_embeddings",
    foreignKeys = [
        ForeignKey(
            entity = SectionElementEntity::class,
            parentColumns = ["id"],
            childColumns = ["sectionElementId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sectionElementId")]
)
data class SectionElementChunkEmbeddingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sectionElementId: Long,
    val chunkIndex: Int,
    val chunkText: String,
    val chunkEmbedding: ByteArray // one embedding blob per chunk

)