package com.wllcom.quicomguide.data.repository

import com.wllcom.quicomguide.data.source.SmartSearchDataSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MaterialsRepository @Inject constructor(
    private val dataSource: SmartSearchDataSource
) {
    suspend fun search(query: String) = dataSource.search(query)
    suspend fun addMaterial(name: String) = dataSource.addMaterial(name)
}

