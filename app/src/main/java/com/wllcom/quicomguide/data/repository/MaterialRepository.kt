package com.wllcom.quicomguide.data.repository

import com.wllcom.quicomguide.data.source.EnumSearchMode
import com.wllcom.quicomguide.data.source.SmartSearchDataSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MaterialsRepository @Inject constructor(
    private val dataSource: SmartSearchDataSource
) {
    suspend fun search(query: String, mode: EnumSearchMode = EnumSearchMode.BOTH, topK: Int = 10) =
        dataSource.search(query, mode, topK)

    suspend fun addMaterial(xml: String): Long? = dataSource.addMaterial(xml)

    suspend fun updateMaterial(materialId: Long, xml: String): Long? =
        dataSource.updateMaterial(materialId, xml)
}

