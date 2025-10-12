package com.wllcom.quicomguide.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.material3.Card
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.wllcom.quicomguide.data.local.AppDatabase
import com.wllcom.quicomguide.ui.components.FabMenu
import com.wllcom.quicomguide.ui.components.TopBarWithSearch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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

    val allMaterialsWithSections by dao.getAllMaterialsFlow().collectAsState(initial = emptyList())
    val allMaterials =
        remember(allMaterialsWithSections) { allMaterialsWithSections.map { it.material } }

    val groupsList by groupDao.getAllFlow().collectAsState(initial = emptyList())
    val coursesList by courseDao.getAllFlow().collectAsState(initial = emptyList())

    var query by remember { mutableStateOf("") }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var selectedCourse by remember { mutableStateOf<Long?>(null) }

    val materialsFlow = remember(selectedCourse) {
        if (selectedCourse == null) {
            dao.getAllMaterialsFlow()
                .map { list -> list.map { it.material } } // Flow<List<MaterialEntity>>
        } else {
            dao.getMaterialsByCourseIdFlow(selectedCourse!!)
        }
    }

    val baseMaterials by materialsFlow.collectAsState(initial = emptyList())
    val baseIds = remember(baseMaterials) { baseMaterials.map { it.id }.toSet() }

    val filtered = remember(baseMaterials, query) {
        if (query.isBlank()) baseMaterials
        else baseMaterials.filter { mat ->
            mat.title.contains(query, ignoreCase = true) ||
                    (mat.contentFts?.contains(query, ignoreCase = true) ?: false) ||
                    mat.xmlRaw.contains(query, ignoreCase = true)
        }
    }

    val groupedIdsState = remember { mutableStateOf(setOf<Long>()) }
    LaunchedEffect(selectedCourse, groupsList) { groupedIdsState.value = emptySet() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                Text(
                    "Курсы",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )
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
                        ListItem(
                            headlineContent = { Text(course.name) },
                            supportingContent = { if (selectedCourse == course.id) Text("Выбран") },
                            modifier = Modifier.clickable {
                                selectedCourse = course.id
                                scope.launch { drawerState.close() }
                            }
                        )
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
                            if (selectedCourse != null) {
                                val name = coursesList.find { it.id == selectedCourse }?.name
                                    ?: selectedCourse.toString()
                                Text("Курс: $name")
                            } else Text("Все курсы")
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Открыть меню")
                            }
                        },
                        actions = {
                            IconButton(onClick = { /* TODO */ }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit")
                            }
                        },
                        query = query,
                        onQueryChange = { query = it },
                        onDebouncedQuery = { debounced -> query = debounced },
                    )
                }
            ) { innerPadding ->
                Column(modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(groupsList) { group ->
                            val groupMaterials by dao.getMaterialsByGroupIdFlow(group.id)
                                .collectAsState(initial = emptyList())

                            val displayedGroupMaterials = remember(groupMaterials, baseIds) {
                                if (selectedCourse == null) groupMaterials else groupMaterials.filter { it.id in baseIds }
                            }

                            LaunchedEffect(displayedGroupMaterials) {
                                if (displayedGroupMaterials.isNotEmpty()) {
                                    groupedIdsState.value =
                                        groupedIdsState.value + displayedGroupMaterials.map { it.id }
                                }
                            }

                            if (displayedGroupMaterials.isNotEmpty()) {
                                Column(modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)) {
                                    Text(
                                        text = "Группа: ${group.name}",
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { navController.navigate("group/${group.id}") }
                                            .background(
                                                Color.DarkGray.copy(alpha = 0.3f),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(8.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LazyRow {
                                        itemsIndexed(displayedGroupMaterials.take(3)) { _, mat ->
                                            Card(
                                                modifier = Modifier
                                                    .size(width = 260.dp, height = 120.dp)
                                                    .padding(end = 8.dp)
                                                    .clickable { navController.navigate("material/${mat.id}") }
                                            ) {
                                                Column(modifier = Modifier.padding(8.dp)) {
                                                    Text(mat.title, maxLines = 2)
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Text(
                                                        previewFromXml(mat.xmlRaw),
                                                        maxLines = 3,
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Text(
                                "Прочие материалы",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(8.dp)
                            )
                        }

                        val ungrouped = filtered.filter { it.id !in groupedIdsState.value }
                        items(ungrouped) { mat ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                                    .clickable { navController.navigate("material/${mat.id}") }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(mat.title)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        previewFromXml(mat.xmlRaw),
                                        maxLines = 3,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }

                        item { Spacer(modifier = Modifier.height(88.dp)) }
                    }
                }
            }

            FabMenu(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(16.dp)
            ) { action ->
                when (action) {
                    "material" -> navController.navigate("addMaterial")
                    "group" -> { /* TODO: create group */
                    }

                    "course" -> { /* TODO: create course */
                    }
                }
            }
        }
    }
}

private fun previewFromXml(xmlRaw: String): String {
    val text = xmlRaw.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
    return if (text.length <= 200) text else text.substring(0, 200) + "..."
}