package com.wllcom.quicomguide.ui.screens

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.wllcom.quicomguide.data.local.AppDatabase
import com.wllcom.quicomguide.data.local.entities.CourseEntity
import com.wllcom.quicomguide.data.local.entities.MaterialGroupEntity
import com.wllcom.quicomguide.data.source.cloud.AuthService
import com.wllcom.quicomguide.ui.components.FabMenu
import com.wllcom.quicomguide.ui.components.MaterialCard
import com.wllcom.quicomguide.ui.components.MaterialCardGroup
import com.wllcom.quicomguide.ui.components.TopBar
import com.wllcom.quicomguide.ui.viewmodel.AuthViewModel
import com.wllcom.quicomguide.ui.viewmodel.SettingsViewModel
import com.wllcom.quicomguide.ui.viewmodel.StorageViewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(systemPadding: PaddingValues, navController: NavController) {
    val configuration = LocalConfiguration.current

    val context = LocalContext.current
    val db = AppDatabase.getInstance(context)
    val dao = db.materialDao()
    val groupDao = db.groupDao()
    val courseDao = db.courseDao()
    var query by rememberSaveable { mutableStateOf("") }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var isEditMode by remember { mutableStateOf(false) }
    var isEditModeDrawer by remember { mutableStateOf(false) }
    if (drawerState.isClosed)
        isEditModeDrawer = false

    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showCreateCourseDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    val coursesList by courseDao.getAllCoursesFlow()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    if (coursesList.isEmpty() && isEditModeDrawer) {
        isEditModeDrawer = false
    }
    var selectedCourse by rememberSaveable { mutableStateOf<Long?>(null) }

    // get materials by course
    val materialsFlow = remember(selectedCourse) {
        if (selectedCourse == null) {
            dao.getAllMaterialFlow()
        } else {
            dao.getMaterialsByCourseIdFlow(selectedCourse!!)
        }
    }

    val baseMaterials by materialsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val baseIds = remember(baseMaterials) { baseMaterials.map { it.id }.toSet() }

    // search material
    val filtered = remember(baseMaterials, query) {
        if (query.isBlank()) baseMaterials
        else baseMaterials.filter { mat ->
            mat.title.contains(query, ignoreCase = true) ||
                    (mat.contentFts?.contains(query, ignoreCase = true) ?: false) ||
                    mat.xmlRaw.contains(query, ignoreCase = true)
        }
    }

    // groups by selected course
    val groupsFlow = remember(selectedCourse) {
        if (selectedCourse == null) {
            groupDao.getAllGroupsWithMaterialsFlow()
        } else {
            groupDao.getGroupsWithMaterialsByCourseIdFlow(selectedCourse)
        }
    }
    val groupsWithMaterials by groupsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val groupedIds by remember(groupsWithMaterials) {
        derivedStateOf {
            groupsWithMaterials.flatMap { it.materials.map { m -> m.id } }.toSet()
        }
    }

    if (baseMaterials.isEmpty() && groupsWithMaterials.isEmpty() && coursesList.isEmpty() && isEditMode) {
        isEditMode = false
    }

    // preview xml
    val previewsById = remember(baseMaterials) {
        baseMaterials.associate { it.id to previewFromXml(it.xmlRaw) }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    var toDeleteCourse by remember { mutableStateOf<Long?>(null) }

    // online
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val isAutoSync by settingsViewModel.isAutoSync.collectAsState()

    val authViewMode: AuthViewModel = hiltViewModel()
    val storageViewMode: StorageViewModel = hiltViewModel()

    // create course with online mode
    val statusCreateCourse by storageViewMode.statusCreateCourse.collectAsState()
    var creatingNewCourse by remember { mutableStateOf(false) }
    var jobCreateCourse by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(statusCreateCourse) {
        if (statusCreateCourse == true && creatingNewCourse) {
            jobCreateCourse!!.start()
        } else if (statusCreateCourse == false && creatingNewCourse) {
            snackbarHostState.showSnackbar(message = "Ошибка операции", withDismissAction = true)
            creatingNewCourse = false
        }
    }

    // delete course with online mode
    val statusDeleteCourse by storageViewMode.statusDeleteCourse.collectAsState()
    var deletingCourse by remember { mutableStateOf(false) }
    var jobDeleteCourse by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(statusDeleteCourse) {
        if (statusDeleteCourse == true && deletingCourse) {
            jobDeleteCourse!!.start()
        } else if (statusDeleteCourse == false && deletingCourse) {
            snackbarHostState.showSnackbar(message = "Ошибка операции", withDismissAction = true)
            deletingCourse = false
        }
    }

    // create group with online mode
    val statusCreateGroup by storageViewMode.statusCreateGroup.collectAsState()
    var creatingNewGroup by remember { mutableStateOf(false) }
    var jobCreateGroup by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(statusCreateGroup) {
        if (statusCreateGroup == true && creatingNewGroup) {
            jobCreateGroup!!.start()
        } else if (statusCreateGroup == false && creatingNewGroup) {
            snackbarHostState.showSnackbar(message = "Ошибка операции", withDismissAction = true)
            creatingNewGroup = false
        }
    }

    // delete group with online mode
    val statusDeleteGroup by storageViewMode.statusDeleteGroup.collectAsState()
    var deletingGroup by remember { mutableStateOf(false) }
    var jobDeleteGroup by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(statusDeleteGroup) {
        if (statusDeleteGroup == true && deletingGroup) {
            jobDeleteGroup!!.start()
        } else if (statusDeleteGroup == false && deletingGroup) {
            snackbarHostState.showSnackbar(message = "Ошибка операции", withDismissAction = true)
            deletingGroup = false
        }
    }

    // delete material with online mode
    val statusDeleteMaterial by storageViewMode.statusDeleteMaterial.collectAsState()
    var deletingMaterial by remember { mutableStateOf(false) }
    var jobDeleteMaterial by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(statusDeleteMaterial) {
        if (statusDeleteMaterial == true && deletingMaterial) {
            jobDeleteMaterial!!.start()
        } else if (statusDeleteMaterial == false && deletingMaterial) {
            snackbarHostState.showSnackbar(message = "Ошибка операции", withDismissAction = true)
            deletingMaterial = false
        }
    }

    val density = LocalDensity.current
    val insets = WindowInsets.navigationBars.asPaddingValues()
    val navigationPadding = with(density) {
        insets.calculateBottomPadding()
    }
    val insetsStatus = WindowInsets.statusBars.asPaddingValues()
    val statusPadding = with(density) {
        insetsStatus.calculateTopPadding()
    }

    var allGroupNameList by remember { mutableStateOf<List<String>>(emptyList()) }
    var receivedAllGroupNameList by remember {mutableStateOf(false)}
    var allCourseNameList by remember { mutableStateOf<List<String>>(emptyList()) }
    var receivedAllCourseNameList by remember {mutableStateOf(false)}
    LaunchedEffect(receivedAllGroupNameList, receivedAllCourseNameList) {
        allGroupNameList = groupDao.getAllGroupName().map { it.lowercase() }
        receivedAllGroupNameList = true
        allCourseNameList = courseDao.getAllCourseName().map { it.lowercase() }
        receivedAllCourseNameList = true
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = {
            SnackbarHost(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 50.dp),
                hostState = snackbarHostState,
            )
        }
    ) { bottomBarPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Spacer(modifier = Modifier.height(statusPadding + 48.dp))
                }
                items(groupsWithMaterials, key = { "group-${it.group.id}" }) { gwm ->
                    val group = gwm.group
                    val displayedGroupMaterials = remember(gwm.materials, baseIds) {
                        if (selectedCourse == null) gwm.materials else gwm.materials.filter { it.id in baseIds }
                    }

                    MaterialCardGroup(
                        groupName = group.name,
                        listMaterials = displayedGroupMaterials.take(3),
                        editMode = isEditMode,
                        deletingGroup = deletingGroup,
                        onClick = { navController.navigate("group/${group.id}") },
                        onLongClick = { isEditMode = !isEditMode },
                        onDeleteClick = {

                            deletingGroup = true

                            jobDeleteGroup?.cancel()
                            jobDeleteGroup = scope.launch(start = CoroutineStart.LAZY) {
                                withContext(Dispatchers.IO) {
                                    dao.deleteMaterialsByGroupId(group.id)
                                    groupDao.deleteGroupById(group.id)
                                }
                                deletingGroup = false
                                snackbarHostState.showSnackbar("Группа удалена", withDismissAction = true)
                            }

                            if (authViewMode.authState.value is AuthService.AuthState.Authenticated && isAutoSync) {
                                val groupName = group.name
                                val courseId = selectedCourse
                                val courseName = coursesList.find { it.id == courseId }?.name
                                val au = authViewMode.authState.value as AuthService.AuthState.Authenticated
                                if (au.accessToken != null) {
                                    storageViewMode.deleteGroup(
                                        au.accessToken,
                                        groupName,
                                        courseName
                                    )
                                }
                            } else {
                                jobDeleteGroup!!.start()
                            }
                        }
                    ) { id, title, text ->
                        MaterialCard(
                            title = title,
                            text = text,
                            editMode = isEditMode,
                            deletingMaterial = deletingMaterial,
                            modifier = Modifier
                                .width(260.dp),
                            onClick = { navController.navigate("material/${id}") },
                            onLongClick = { isEditMode = !isEditMode },
                            onDeleteClick = {

                                deletingMaterial = true

                                val matId = id
                                jobDeleteMaterial?.cancel()
                                jobDeleteMaterial = scope.launch(start = CoroutineStart.LAZY) {
                                    withContext(Dispatchers.IO) {
                                        dao.deleteMaterialById(matId)
                                    }
                                    deletingMaterial = false
                                    snackbarHostState.showSnackbar(
                                        "Материал удалён",
                                        withDismissAction = true
                                    )
                                }

                                if (authViewMode.authState.value is AuthService.AuthState.Authenticated && isAutoSync) {
                                    val materialName = title
                                    val groupName = group.name
                                    val courseId = selectedCourse
                                    val courseName = coursesList.find { it.id == courseId }?.name
                                    val au = authViewMode.authState.value as AuthService.AuthState.Authenticated
                                    if (au.accessToken != null) {
                                        storageViewMode.deleteMaterial(
                                            au.accessToken,
                                            materialName,
                                            courseName,
                                            groupName
                                        )
                                    }
                                } else {
                                    jobDeleteMaterial!!.start()
                                }
                            }
                        )
                    }
                }

                item {
                    Text(
                        "Прочие материалы",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(8.dp)
                    )
                }

                val ungrouped = filtered.filter { it.id !in groupedIds }
                items(ungrouped, key = { "mat-${it.id}" }) { mat ->
                    val preview = previewsById[mat.id] ?: ""
                    MaterialCard(
                        title = mat.title,
                        text = preview,
                        editMode = isEditMode,
                        deletingMaterial = deletingMaterial,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, end = 8.dp, bottom = 10.dp),
                        onClick = { navController.navigate("material/${mat.id}") },
                        onLongClick = { isEditMode = !isEditMode },
                        onDeleteClick = {

                            deletingMaterial = true

                            val matId = mat.id
                            jobDeleteMaterial?.cancel()
                            jobDeleteMaterial = scope.launch(start = CoroutineStart.LAZY) {
                                withContext(Dispatchers.IO) {
                                    dao.deleteMaterialById(matId)
                                }
                                deletingMaterial = false
                                snackbarHostState.showSnackbar(
                                    "Материал удалён",
                                    withDismissAction = true
                                )
                            }

                            if (authViewMode.authState.value is AuthService.AuthState.Authenticated && isAutoSync) {
                                val materialName = mat.title
                                val courseId = selectedCourse
                                val courseName = coursesList.find { it.id == courseId }?.name
                                val au = authViewMode.authState.value as AuthService.AuthState.Authenticated
                                if (au.accessToken != null) {
                                    storageViewMode.deleteMaterial(
                                        au.accessToken,
                                        materialName,
                                        courseName,
                                        null
                                    )
                                }
                            } else {
                                jobDeleteMaterial!!.start()
                            }
                        }
                    )
                }

                item { Spacer(modifier = Modifier.height(60.dp + navigationPadding)) }
            }
        }

        TopBar(
            title =
                if (!isEditMode) {
                    if (selectedCourse != null) {
                        val name = coursesList.find { it.id == selectedCourse }?.name
                            ?: selectedCourse.toString()
                        "Курс: $name"
                    } else "Все курсы"
                } else "Редактирование",
            dropDown = true,
            generalDropDownItem = "Все курсы",
            onGeneralDropDownItem = { selectedCourse = null },
            dropDownList = coursesList.map { it.name },
            onDropDownList = { index -> selectedCourse = coursesList[index].id },
            deleteModeDropDownList = isEditMode,
            onDeleteDropDownList = { index -> toDeleteCourse = coursesList[index].id },
            search = true,
            query = query,
            onQueryChange = { query = it },
            onDebouncedQuery = { debounced -> query = debounced },
            customButtons = true,
            composeCustomButtons = { modifier ->
                Box(contentAlignment = Alignment.Center,modifier = modifier
                    .width(44.dp)
                    .clickable { isEditMode = !isEditMode }) {
                    Icon(
                        imageVector = if (!isEditMode) Icons.Default.Edit else Icons.Default.Close,
                        contentDescription = if (!isEditMode) "Edit" else "Close edit"
                    )
                }
            },
        )

        if (!isEditMode) {
            FabMenu(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottomBarPadding)
                    .let {
                        if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
                            it.padding(bottom = 66.dp)
                        else
                            it.padding(bottom = 14.dp)
                    }
                    .padding(end = 16.dp)
            ) { action ->
                when (action) {
                    "material" -> navController.navigate("addMaterial")
                    "group" -> {
                        newName = ""
                        showCreateGroupDialog = true
                    }

                    "course" -> {
                        newName = ""
                        showCreateCourseDialog = true
                    }
                }
            }
        }
    }

    if (toDeleteCourse != null) {
        AlertDialog(
            onDismissRequest = { if (!deletingCourse) toDeleteCourse = null },
            title = { Text("Удалить материал?") },
            text = { Text("Это действие нельзя будет отменить.") },
            confirmButton = {
                TextButton(onClick = {
                    deletingCourse = true

                    jobDeleteCourse?.cancel()
                    jobDeleteCourse = scope.launch(start = CoroutineStart.LAZY) {
                        withContext(Dispatchers.IO) {
                            // delete groups
                            val courseGroups = groupDao.getGroupsByCourseId(toDeleteCourse)
                            for (group in courseGroups) {
                                dao.deleteMaterialsByGroupId(group.id)
                            }
                            // delete ungrouped materials
                            dao.deleteMaterialsByCourseId(toDeleteCourse!!)

                            courseDao.deleteCourseById(toDeleteCourse!!)
                        }
                        deletingCourse = false
                        toDeleteCourse = null
                        snackbarHostState.showSnackbar("Курс удалён", withDismissAction = true)
                    }

                    if (authViewMode.authState.value is AuthService.AuthState.Authenticated && isAutoSync) {
                        val nameCourse = coursesList.find { it.id == toDeleteCourse }?.name!!
                        val au = authViewMode.authState.value as AuthService.AuthState.Authenticated
                        if (au.accessToken != null)
                            storageViewMode.deleteCourse(au.accessToken, nameCourse)
                    }
                    // offline mode
                    else {
                        jobDeleteCourse!!.start()
                    }

                    // back to general course
                    selectedCourse = null
                }) {
                    if (!deletingCourse)
                        Text("Удалить")
                    else
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            },
            dismissButton = {
                if (!deletingCourse) {
                    TextButton(onClick = { toDeleteCourse = null }) {
                        Text("Отмена")
                    }
                }
            }
        )
    }

    if (showCreateGroupDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!creatingNewGroup) {
                    showCreateGroupDialog = false
                    newName = ""
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = newName.trim()
                    val courseId = selectedCourse
                    if (name.isNotEmpty()) {

                        if(name.length > 128){
                            Toast.makeText(context,
                                "Имя группы не может быть больше 128 символов!",
                                Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }

                        if(!receivedAllGroupNameList){
                            Toast.makeText(context,
                                "Не удалось выполнить проверку имени группы, попробуйте повторить позже",
                                Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        else {
                            if (name.lowercase() in allGroupNameList){
                                Toast.makeText(context,
                                    "Такой название уже существует. Введите уникальное название",
                                    Toast.LENGTH_SHORT).show()
                                return@TextButton
                            }
                            receivedAllGroupNameList = false
                        }

                        creatingNewGroup = true

                        jobCreateGroup?.cancel()
                        jobCreateGroup = scope.launch(context = Dispatchers.IO, start = CoroutineStart.LAZY) {
                            groupDao.insertGroup(
                                MaterialGroupEntity(
                                    name = name,
                                    courseId = courseId
                                )
                            )
                            creatingNewGroup = false
                            showCreateGroupDialog = false
                        }

                        // sync with storage
                        if (authViewMode.authState.value is AuthService.AuthState.Authenticated && isAutoSync) {
                            val courseName = coursesList.find { it.id == courseId }?.name
                            val au = authViewMode.authState.value as AuthService.AuthState.Authenticated
                            if (au.accessToken != null) {
                                storageViewMode.createGroup(
                                    accessToken = au.accessToken,
                                    uniqueGroupName = name,
                                    uniqueCourseName = courseName
                                )
                            }
                        }
                        // offline mode
                        else {
                            jobCreateGroup!!.start()
                        }
                    }
                }) {
                    if (!creatingNewGroup)
                        Text("Создать")
                    else
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            },
            dismissButton = {
                if (!creatingNewGroup) {
                    TextButton(onClick = {
                        showCreateGroupDialog = false;
                    }) { Text("Отмена") }
                }
            },
            title = { Text("Новая группа") },
            text = {
                OutlinedTextField(
                    enabled = !creatingNewGroup,
                    value = newName,
                    onValueChange = {
                        if (it.length <= 128)
                            newName = it
                        else {
                            Toast.makeText(
                                context,
                                "Имя группы не может быть больше 128 символов!",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    label = { Text("Имя группы") })
            }
        )
    }

    if (showCreateCourseDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!creatingNewCourse) {
                    showCreateCourseDialog = false
                    newName = ""
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = newName.trim()
                    if (name.isNotEmpty()) {

                        if(name.length > 128){
                            Toast.makeText(context,
                                "Имя курса не может быть больше 128 символов!",
                                Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }

                        if(!receivedAllCourseNameList){
                            Toast.makeText(context,
                                "Не удалось выполнить проверку имени курса, попробуйте повторить позже",
                                Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        else {
                            if (name.lowercase() in allCourseNameList){
                                Toast.makeText(context,
                                    "Такой название уже существует. Введите уникальное название",
                                    Toast.LENGTH_SHORT).show()
                                return@TextButton
                            }
                            receivedAllCourseNameList = false
                        }

                        creatingNewCourse = true

                        jobCreateCourse?.cancel()
                        jobCreateCourse = scope.launch(context = Dispatchers.IO, start = CoroutineStart.LAZY) {
                            courseDao.insertCourse(CourseEntity(name = name))
                            showCreateCourseDialog = false
                            creatingNewCourse = false
                        }

                        // online mode
                        if (authViewMode.authState.value is AuthService.AuthState.Authenticated && isAutoSync) {
                            val au = authViewMode.authState.value as AuthService.AuthState.Authenticated
                            if (au.accessToken != null)
                                storageViewMode.createCourse(au.accessToken, name)
                        }
                        // offline mode
                        else {
                            jobCreateCourse!!.start()
                        }
                    }
                }) {
                    if (!creatingNewCourse)
                        Text("Создать")
                    else {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            },
            dismissButton = {
                if (!creatingNewCourse) {
                    TextButton(onClick = {
                        showCreateCourseDialog = false; newName = ""
                    }) { Text("Отмена") }
                }
            },
            title = { Text("Новый курс") },
            text = {
                OutlinedTextField(
                    enabled = !creatingNewCourse,
                    value = newName,
                    onValueChange = {
                        if (it.length <= 128)
                            newName = it
                        else {
                            Toast.makeText(
                                context,
                                "Имя курса не может быть больше 128 символов!",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    label = { Text("Имя курса") })
            }
        )
    }
}

private fun previewFromXml(xmlRaw: String): String {
    val text = xmlRaw.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
    return if (text.length <= 200) text else text.substring(0, 200) + "..."
}
