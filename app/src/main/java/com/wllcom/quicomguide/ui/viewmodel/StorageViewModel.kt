package com.wllcom.quicomguide.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wllcom.quicomguide.data.repository.StorageRepository
import com.wllcom.quicomguide.data.repository.StorageRepository.StatusSync
import com.wllcom.quicomguide.data.source.cloud.StorageService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StorageViewModel @Inject constructor(
    private val storage: StorageRepository
) : ViewModel() {

    private val _statusUserInfo = MutableStateFlow<StorageService.UserInfo?>(null)
    val statusUserInfo: StateFlow<StorageService.UserInfo?> = _statusUserInfo.asStateFlow()

    private val _statusUploadMaterial = MutableStateFlow<Boolean?>(null)
    val statusUploadMaterial: StateFlow<Boolean?> = _statusUploadMaterial.asStateFlow()

    private val _statusDownloadMaterial = MutableStateFlow<Boolean?>(null)
    val statusDownloadMaterial: StateFlow<Boolean?> = _statusDownloadMaterial.asStateFlow()
    private var xmlContent = ""
    fun getDownloadedXml() = xmlContent

    private val _statusDeleteMaterial = MutableStateFlow<Boolean?>(null)
    val statusDeleteMaterial: StateFlow<Boolean?> = _statusDeleteMaterial.asStateFlow()

    private val _statusCreateGroup = MutableStateFlow<Boolean?>(null)
    val statusCreateGroup: StateFlow<Boolean?> = _statusCreateGroup.asStateFlow()

    private val _statusDeleteGroup = MutableStateFlow<Boolean?>(null)
    val statusDeleteGroup: StateFlow<Boolean?> = _statusDeleteGroup.asStateFlow()

    private val _statusCreateCourse = MutableStateFlow<Boolean?>(null)
    val statusCreateCourse: StateFlow<Boolean?> = _statusCreateCourse.asStateFlow()

    private val _statusDeleteCourse = MutableStateFlow<Boolean?>(null)
    val statusDeleteCourse: StateFlow<Boolean?> = _statusDeleteCourse.asStateFlow()

    private val _statusSync = MutableStateFlow<StatusSync?>(null)
    val statusSync: StateFlow<StatusSync?> = _statusSync.asStateFlow()
    val syncPercentage = storage.syncPercentage

    fun getUserInfo(accessToken:String){
        _statusUserInfo.value = null
        viewModelScope.launch(Dispatchers.IO) {
            _statusUserInfo.value = storage.getUserInfo(accessToken)
        }
    }

    fun uploadMaterial(
        accessToken: String,
        uniqueFileName: String,
        xml: String,
        uniqueCourseName: String?,
        uniqueGroupName: String?
    ) {
        _statusUploadMaterial.value = null
        viewModelScope.launch(Dispatchers.IO) {
            _statusUploadMaterial.value = storage.uploadMaterial(accessToken, uniqueFileName, xml, uniqueCourseName, uniqueGroupName)
        }
    }
    fun downloadMaterial(
        accessToken: String,
        uniqueFileName: String,
        uniqueCourseName: String?,
        uniqueGroupName: String?
    ) {
        _statusDownloadMaterial.value = null
        viewModelScope.launch(Dispatchers.IO) {
            val (status, xml) = storage.downloadMaterial(accessToken, uniqueFileName, uniqueCourseName, uniqueGroupName)
            xmlContent = xml
            _statusDownloadMaterial.value = status
        }
    }
    fun deleteMaterial(
        accessToken: String,
        uniqueFileName: String,
        uniqueCourseName: String?,
        uniqueGroupName: String?
    ){
        _statusDeleteMaterial.value = null
        viewModelScope.launch(Dispatchers.IO) {
            _statusDeleteMaterial.value = storage.deleteMaterial(accessToken, uniqueFileName, uniqueCourseName, uniqueGroupName)
        }
    }

    fun createGroup(accessToken:String, uniqueGroupName: String, uniqueCourseName: String? = null){
        _statusCreateGroup.value = null
        viewModelScope.launch(Dispatchers.IO) {
            _statusCreateGroup.value = storage.createGroup(accessToken, uniqueGroupName, uniqueCourseName) != null
        }
    }
    fun deleteGroup(accessToken:String, uniqueGroupName: String, uniqueCourseName: String? = null){
        _statusDeleteGroup.value = null
        viewModelScope.launch(Dispatchers.IO) {
            _statusDeleteGroup.value = storage.deleteGroup(accessToken, uniqueGroupName, uniqueCourseName)
        }
    }

    fun createCourse(accessToken:String, uniqueCourseName: String){
        _statusCreateCourse.value = null
        viewModelScope.launch(Dispatchers.IO) {
            _statusCreateCourse.value = storage.createCourse(accessToken, uniqueCourseName) != null
        }
    }
    fun deleteCourse(accessToken:String, uniqueCourseName: String){
        _statusDeleteCourse.value = null
        viewModelScope.launch(Dispatchers.IO) {
            _statusDeleteCourse.value = storage.deleteCourse(accessToken, uniqueCourseName)
        }
    }

    fun sync(accessToken:String, mode: StorageRepository.SyncMode = StorageRepository.SyncMode.AUTO){
        _statusSync.value = null
        viewModelScope.launch(Dispatchers.IO) {
            _statusSync.value = storage.sync(accessToken, mode)
        }
    }
}