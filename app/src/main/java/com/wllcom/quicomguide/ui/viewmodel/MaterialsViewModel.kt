package com.wllcom.quicomguide.ui.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wllcom.quicomguide.data.ml.EmbeddingProvider
import com.wllcom.quicomguide.data.repository.MaterialsRepository
import com.wllcom.quicomguide.data.source.SearchResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class MaterialsViewModel @Inject constructor(
    private val repository: MaterialsRepository,
    private val embeddingProvider: EmbeddingProvider
) : ViewModel() {

    var results = mutableStateOf(SearchResponse("", emptyList(), emptyList()))
        private set

    val isReadyFlow: StateFlow<Boolean> = embeddingProvider.isReadyFlow

    fun search(query: String) {
        viewModelScope.launch {
            results.value = repository.search(query)
        }
    }

    fun addMaterial(name: String) {
        viewModelScope.launch {
            repository.addMaterial(name)
        }
    }
}