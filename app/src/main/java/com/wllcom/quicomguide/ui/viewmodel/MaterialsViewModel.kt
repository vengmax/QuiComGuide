package com.wllcom.quicomguide.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.wllcom.quicomguide.data.ml.EmbeddingProvider
import com.wllcom.quicomguide.data.repository.MaterialsRepository
import com.wllcom.quicomguide.data.source.EnumSearchMode
import com.wllcom.quicomguide.data.source.SearchResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class MaterialsViewModel @Inject constructor(
    private val repository: MaterialsRepository,
    private val embeddingProvider: EmbeddingProvider
) : ViewModel() {

    private val _searchResults = MutableStateFlow(SearchResponse("", emptyList(), emptyList()))
    val searchResults = _searchResults.asStateFlow()

    val isAiSearchReady: StateFlow<Boolean> = embeddingProvider.isReady

    suspend fun search(
        query: String,
        mode: EnumSearchMode = EnumSearchMode.BOTH,
        topK: Int = 10
    ): SearchResponse {
        val res = repository.search(query, mode, topK)
        _searchResults.value = res
        return res
    }

    suspend fun addMaterial(title: String, xml: String): Long? {
        return repository.addMaterial(title, xml)
    }

    suspend fun updateMaterial(materialId: Long, xml: String): Long? {
        return repository.updateMaterial(materialId, xml)
    }
}