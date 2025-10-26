package com.wllcom.quicomguide.data.repository

import com.wllcom.quicomguide.data.source.EnumSearchMode
import com.wllcom.quicomguide.data.source.MaterialDataSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MaterialsRepository @Inject constructor(
    private val dataSource: MaterialDataSource
) {
    suspend fun search(query: String, mode: EnumSearchMode = EnumSearchMode.BOTH, topK: Int = 10) =
        dataSource.search(query, mode, topK)

    suspend fun addMaterial(title: String, xml: String): Long? = dataSource.addMaterial(title, xml)

    suspend fun updateMaterial(materialId: Long, xml: String): Long? =
        dataSource.updateMaterial(materialId, xml)
}

