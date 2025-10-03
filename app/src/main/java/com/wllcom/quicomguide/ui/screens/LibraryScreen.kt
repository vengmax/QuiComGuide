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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(navController: NavController, contentPadding: PaddingValues) {
    // Контекст + DB
    val context = LocalContext.current
    val db = AppDatabase.getInstance(context)
    val dao = db.materialDao()

    // Поток материалов -> State
    val materials by dao.getAllFlow().collectAsState(initial = emptyList())

    // Поисковая строка
    var query by remember { mutableStateOf("") }

    // Drawer state и scope для coroutine
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Список уникальных courseId (без null), используем для Drawer
    val courses = remember(materials) {
        val list = materials.mapNotNull { it.courseId }.distinct()
        list
    }

    // выбранный курс (null = все курсы)
    var selectedCourse by remember { mutableStateOf<String?>(null) }

    // Группы рассчитываем исходя из выбранного курса:
    // если selectedCourse != null — берём материалы только этого курса, затем группируем
    val groupsMap = remember(materials, selectedCourse) {
        val filteredByCourse =
            if (selectedCourse == null) materials else materials.filter { it.courseId == selectedCourse }
        filteredByCourse.groupBy { it.groupId ?: "ungrouped" }
    }
    val groups = remember(groupsMap) { groupsMap.keys.filter { it != "ungrouped" } }

    // Фильтрация списка материалов учитывает и строку поиска, и выбранный курс
    val filtered = remember(materials, query, selectedCourse) {
        val byCourse =
            if (selectedCourse == null) materials else materials.filter { it.courseId == selectedCourse }
        if (query.isBlank()) byCourse
        else byCourse.filter {
            it.title.contains(query, ignoreCase = true) || (it.searchIndex?.contains(
                query,
                ignoreCase = true
            ) ?: false)
        }
    }

    // Drawer: уменьшенная ширина, список курсов + опция "Все курсы" и индикатор выбранного курса
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
                // "Все курсы" опция
                ListItem(
                    headlineContent = { Text("Все курсы") },
                    supportingContent = { if (selectedCourse == null) Text("Фильтр отключён") },
                    modifier = Modifier
                        .clickable {
                            selectedCourse = null
                            scope.launch { drawerState.close() }
                        }
                )
                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
                // Список курсов
                LazyColumn(modifier = Modifier.fillMaxHeight()) {
                    items(courses) { courseId ->
                        ListItem(
                            headlineContent = { Text(courseId) },
                            supportingContent = { if (selectedCourse == courseId) Text("Выбран") },
                            modifier = Modifier
                                .clickable {
                                    selectedCourse = courseId
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
                                Text("Курс: ${selectedCourse}")
                            } else
                                Text("Все курсы")
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Открыть меню")
                            }
                        },
                        actions = {
                            // Кнопка редактирования (заглушка)
                            IconButton(onClick = { /* TODO: action */ }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit")
                            }
                        }
                    )
                }
            ) { innerPadding ->
                // Контент: учитываем innerPadding (Scaffold) — затем аккуратные внешние отступы
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {

                    // Сначала группы (в рамках курса, если он выбран)
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(groups) { groupId ->
                            val groupMaterials = (groupsMap[groupId] ?: emptyList())
                            if (groupMaterials.isNotEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = "Группа: $groupId",
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                navController.navigate("group/$groupId")
                                            }
                                            .background(
                                                Color.DarkGray.copy(alpha = 0.3f),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(8.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    // Горизонтальный ряд превью первых 3 материалов группы (в рамках выбранного курса)
                                    LazyRow {
                                        itemsIndexed(groupMaterials.take(3)) { _, mat ->
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
                                                        mat.searchIndex ?: "",
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

                        // Материалы без группы (и с учетом фильтра по курсу)
                        item {
                            Text(
                                "Прочие материалы",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                        items(filtered.filter { it.groupId == null || it.groupId == "ungrouped" }) { mat ->
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
                                        mat.searchIndex ?: "",
                                        maxLines = 3,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }

                        // Отступ внизу, чтобы FAB не перекрывал контент
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
                    "group" -> { /* открыть создание группы */
                    }
                    "course" -> { /* создать курс */
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun PreviewTest() {
    LibraryScreen(rememberNavController(), PaddingValues())
}