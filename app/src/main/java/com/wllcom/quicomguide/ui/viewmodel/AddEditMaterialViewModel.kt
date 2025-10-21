package com.wllcom.quicomguide.ui.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class AddEditMaterialViewModel @Inject constructor() : ViewModel() {

    private val _selectedCourseName = MutableStateFlow<String?>(null)
    val selectedCourseName = _selectedCourseName.asStateFlow()

    private val _selectedCourseId = MutableStateFlow<Long?>(null)
    val selectedCourseId = _selectedCourseId.asStateFlow()

    private val _selectedGroupName = MutableStateFlow<String?>(null)
    val selectedGroupName = _selectedGroupName.asStateFlow()

    private val _selectedGroupId = MutableStateFlow<Long?>(null)
    val selectedGroupId = _selectedGroupId.asStateFlow()

    fun setCourse(courseName: String?, courseId: Long?) {
        _selectedCourseName.value = courseName
        _selectedCourseId.value = courseId
    }

    fun newCourse(courseName: String) {
        _selectedCourseName.value = courseName
        _selectedCourseId.value = null
    }

    fun updateCourseId(courseId: Long?) {
        _selectedCourseId.value = courseId
    }

    fun setGroup(groupName: String?, groupId: Long?) {
        _selectedGroupName.value = groupName
        _selectedGroupId.value = groupId
    }

    fun newGroup(groupName: String) {
        _selectedGroupName.value = groupName
        _selectedGroupId.value = null
    }

    fun updateGroupId(groupId: Long?) {
        _selectedGroupId.value = groupId
    }
}