package com.wllcom.quicomguide.ui.screens

import android.widget.Space
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.wllcom.quicomguide.data.local.AppDatabase
import com.wllcom.quicomguide.data.local.entities.CourseEntity
import com.wllcom.quicomguide.data.local.entities.MaterialGroupEntity
import com.wllcom.quicomguide.data.source.cloud.AuthService
import com.wllcom.quicomguide.ui.components.BottomBar
import com.wllcom.quicomguide.ui.components.FabMenu
import com.wllcom.quicomguide.ui.components.MaterialCard
import com.wllcom.quicomguide.ui.components.MaterialCardGroup
import com.wllcom.quicomguide.ui.components.TopBarWithSearch
import com.wllcom.quicomguide.ui.viewmodel.AuthViewModel
import com.wllcom.quicomguide.ui.viewmodel.StorageViewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(systemPadding: PaddingValues, navController: NavController) {
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

    if (baseMaterials.isEmpty() && groupsWithMaterials.isEmpty() && isEditMode) {
        isEditMode = false
    }

    // preview xml
    val previewsById = remember(baseMaterials) {
        baseMaterials.associate { it.id to previewFromXml(it.xmlRaw) }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    var toDeleteCourse by remember { mutableStateOf<Long?>(null) }

    // online
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Курсы",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                    IconButton(
                        onClick = { isEditModeDrawer = !isEditModeDrawer },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                    ) {
                        Icon(
                            imageVector = if (!isEditModeDrawer) Icons.Default.Edit else Icons.Default.Close,
                            contentDescription = if (!isEditModeDrawer) "Edit" else "Close edit",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    headlineContent = { Text("Все курсы") },
                    supportingContent = { if (selectedCourse == null) Text("Фильтр отключён") },
                    modifier = Modifier.clickable {
                        selectedCourse = null
                        scope.launch { drawerState.close() }
                    }
                )
                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
                LazyColumn(modifier = Modifier.fillMaxHeight()) {
                    items(coursesList) { course ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                headlineContent = { Text(course.name) },
                                supportingContent = { if (selectedCourse == course.id) Text("Выбран") },
                                modifier = Modifier.clickable {
                                    selectedCourse = course.id
                                    scope.launch { drawerState.close() }
                                }
                            )
                            if (isEditModeDrawer) {
                                IconButton(
                                    onClick = {
                                        toDeleteCourse = course.id
                                    },
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Удалить",
                                        tint = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.size(23.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = { BottomBar(navController = navController) },
            snackbarHost = {
                SnackbarHost(
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
                        Spacer(modifier = Modifier.height(statusPadding + 64.dp))
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

                                if (authViewMode.authState.value is AuthService.AuthState.Authenticated) {
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
                                    .size(width = 260.dp, height = 120.dp)
                                    .padding(end = 8.dp),
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

                                    if (authViewMode.authState.value is AuthService.AuthState.Authenticated) {
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
                                .height(105.dp)
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

                                if (authViewMode.authState.value is AuthService.AuthState.Authenticated) {
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
            TopBarWithSearch(
                title = {
                    if (!isEditMode) {
                        if (selectedCourse != null) {
                            val name = coursesList.find { it.id == selectedCourse }?.name
                                ?: selectedCourse.toString()
                            Text("Курс: $name")
                        } else Text("Все курсы")
                    } else Text("Редактирование")
                },
                navigationIcon = {
                    if (!isEditMode) {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Открыть меню")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { isEditMode = !isEditMode }) {
                        Icon(
                            imageVector = if (!isEditMode) Icons.Default.Edit else Icons.Default.Close,
                            contentDescription = if (!isEditMode) "Edit" else "Close edit"
                        )
                    }
                },
                query = query,
                onQueryChange = { query = it },
                onDebouncedQuery = { debounced -> query = debounced },
            )
            if (!isEditMode) {
                FabMenu(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottomBarPadding)
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

                    if (authViewMode.authState.value is AuthService.AuthState.Authenticated) {
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
                        if (authViewMode.authState.value is AuthService.AuthState.Authenticated) {
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
                    onValueChange = { newName = it },
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
                        creatingNewCourse = true

                        jobCreateCourse?.cancel()
                        jobCreateCourse = scope.launch(context = Dispatchers.IO, start = CoroutineStart.LAZY) {
                            courseDao.insertCourse(CourseEntity(name = name))
                            showCreateCourseDialog = false
                            creatingNewCourse = false
                        }

                        // online mode
                        if (authViewMode.authState.value is AuthService.AuthState.Authenticated) {
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
                    onValueChange = { newName = it },
                    label = { Text("Имя курса") })
            }
        )
    }
}

private fun previewFromXml(xmlRaw: String): String {
    val text = xmlRaw.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
    return if (text.length <= 200) text else text.substring(0, 200) + "..."
}
