package com.wllcom.quicomguide.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.wllcom.quicomguide.data.local.AppDatabase
import com.wllcom.quicomguide.data.local.entities.CourseEntity
import com.wllcom.quicomguide.data.local.entities.MaterialGroupEntity
import com.wllcom.quicomguide.ui.components.FabMenu
import com.wllcom.quicomguide.ui.components.MaterialCard
import com.wllcom.quicomguide.ui.components.MaterialCardGroup
import com.wllcom.quicomguide.ui.components.TopBarWithSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Preview
@Composable
fun PreviewTest() {
    LibraryScreen(rememberNavController(), PaddingValues())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(navController: NavController, contentPadding: PaddingValues) {
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
    val groupsWithMaterials by groupDao.getGroupsWithMaterialsByCourseIdFlow(selectedCourse)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val groupedIds by remember(groupsWithMaterials) {
        derivedStateOf {
            groupsWithMaterials.flatMap { it.materials.map { m -> m.id } }.toSet()
        }
    }

    // preview xml
    val previewsById = remember(baseMaterials) {
        baseMaterials.associate { it.id to previewFromXml(it.xmlRaw) }
    }

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var toDeleteCourse by remember { mutableStateOf<Long?>(null) }

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
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
                ListItem(
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
                                        tint = Color.White,
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
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
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
                },
                snackbarHost = {
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.padding(bottom = 80.dp)
                    )
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(groupsWithMaterials, key = { "group-${it.group.id}" }) { gwm ->
                            val group = gwm.group
                            val displayedGroupMaterials = remember(gwm.materials, baseIds) {
                                if (selectedCourse == null) gwm.materials else gwm.materials.filter { it.id in baseIds }
                            }

                            MaterialCardGroup(
                                groupName = group.name,
                                listMaterials = displayedGroupMaterials.take(3),
                                editMode = isEditMode,
                                onClick = { navController.navigate("group/${group.id}") },
                                onLongClick = { isEditMode = !isEditMode },
                                onDeleteClick = {
                                    coroutineScope.launch {
                                        try {
                                            withContext(Dispatchers.IO) {
                                                dao.deleteMaterialsByGroupId(group.id)
                                                groupDao.deleteGroupById(group.id)
                                            }
                                            snackbarHostState.showSnackbar("Группа удалена")
                                        } catch (e: Exception) {
                                            snackbarHostState.showSnackbar("Ошибка при удалении: ${e.message}")
                                        }
                                    }
                                }
                            ) { id, title, text ->
                                MaterialCard(
                                    title = title,
                                    text = text,
                                    editMode = isEditMode,
                                    modifier = Modifier
                                        .size(width = 260.dp, height = 120.dp)
                                        .padding(end = 8.dp),
                                    onClick = { navController.navigate("material/${id}") },
                                    onLongClick = { isEditMode = !isEditMode },
                                    onDeleteClick = {
                                        coroutineScope.launch {
                                            try {
                                                withContext(Dispatchers.IO) {
                                                    dao.deleteMaterialById(id)
                                                }
                                                snackbarHostState.showSnackbar("Материал удалён")
                                            } catch (e: Exception) {
                                                snackbarHostState.showSnackbar("Ошибка при удалении: ${e.message}")
                                            }
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
                                modifier = Modifier
                                    .height(105.dp)
                                    .fillMaxWidth()
                                    .padding(start = 8.dp, end = 8.dp, bottom = 10.dp),
                                onClick = { navController.navigate("material/${mat.id}") },
                                onLongClick = { isEditMode = !isEditMode },
                                onDeleteClick = {
                                    coroutineScope.launch {
                                        try {
                                            withContext(Dispatchers.IO) {
                                                dao.deleteMaterialById(mat.id)
                                            }
                                            snackbarHostState.showSnackbar("Материал удалён")
                                        } catch (e: Exception) {
                                            snackbarHostState.showSnackbar("Ошибка при удалении: ${e.message}")
                                        }
                                    }
                                }
                            )
                        }

                        item { Spacer(modifier = Modifier.height(88.dp)) }
                    }
                }
            }

            if (!isEditMode) {
                FabMenu(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .padding(16.dp)
                ) { action ->
                    when (action) {
                        "material" -> navController.navigate("addMaterial")
                        "group" -> {
                            showCreateGroupDialog = true
                        }

                        "course" -> {
                            showCreateCourseDialog = true
                        }
                    }
                }
            }
        }
    }

    if (toDeleteCourse != null) {
        AlertDialog(
            onDismissRequest = { toDeleteCourse = null },
            title = { Text("Удалить материал?") },
            text = { Text("Это действие нельзя будет отменить.") },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch {
                        try {
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
                            toDeleteCourse = null
                            snackbarHostState.showSnackbar("Курс удалён")
                        } catch (e: Exception) {
                            toDeleteCourse = null
                            snackbarHostState.showSnackbar("Ошибка при удалении: ${e.message}")
                        }
                    }
                    selectedCourse = null
                }) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { toDeleteCourse = null }) {
                    Text("Отмена")
                }
            }
        )
    }

    if (showCreateGroupDialog) {
        AlertDialog(
            onDismissRequest = { showCreateGroupDialog = false; newName = "" },
            confirmButton = {
                TextButton(onClick = {
                    val name = newName.trim()
                    if (name.isNotEmpty()) {
                        scope.launch {
                            groupDao.insertGroup(
                                MaterialGroupEntity(
                                    name = name,
                                    courseId = selectedCourse
                                )
                            )
                        }
                    }
                    showCreateGroupDialog = false
                    newName = ""
                }) { Text("Создать") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreateGroupDialog = false; newName = ""
                }) { Text("Отмена") }
            },
            title = { Text("Новая группа") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Имя группы") })
            }
        )
    }

    if (showCreateCourseDialog) {
        AlertDialog(
            onDismissRequest = { showCreateCourseDialog = false; newName = "" },
            confirmButton = {
                TextButton(onClick = {
                    val name = newName.trim()
                    if (name.isNotEmpty()) {
                        scope.launch {
                            courseDao.insertCourse(CourseEntity(name = name))
                        }
                    }
                    showCreateCourseDialog = false
                    newName = ""
                }) { Text("Создать") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreateCourseDialog = false; newName = ""
                }) { Text("Отмена") }
            },
            title = { Text("Новый курс") },
            text = {
                OutlinedTextField(
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
