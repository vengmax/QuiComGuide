package com.wllcom.quicomguide.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.room.withTransaction
import com.wllcom.quicomguide.data.local.AppDatabase
import com.wllcom.quicomguide.data.model.MaterialEntity
import com.wllcom.quicomguide.data.model.MaterialSectionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditMaterialScreen(
    navController: NavController,
    materialId: String? = null,
    padding: androidx.compose.foundation.layout.PaddingValues
) {
    // 1. Получаем контекст и DB
    val context = LocalContext.current
    val db = AppDatabase.getInstance(context)
    val materialDao = db.materialDao()
    val sectionDao = db.sectionDao()
    val scope = rememberCoroutineScope()

    // 2. Состояния формы
    var title by remember { mutableStateOf("") }
    var xmlContent by remember { mutableStateOf("<material>\n    <!-- ваш XML здесь -->\n</material>") }
    var editingId by remember { mutableStateOf<Long?>(null) }

    // 3. Группы/курсы: локальные списки (mutableStateList, чтобы UI реагировал)
    val allGroups = remember { mutableStateListOf<String>() }
    val allCourses = remember { mutableStateListOf<String>() }

    var groupSelected by remember { mutableStateOf<String?>(null) }
    var showNewGroupField by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }

    var courseSelected by remember { mutableStateOf<String?>(null) }
    var showNewCourseField by remember { mutableStateOf(false) }
    var newCourseName by remember { mutableStateOf("") }

    // 4. Загружаем списки групп/курсов один раз (suspend внутри LaunchedEffect) — НЕ используем collectAsState внутри корутины
    LaunchedEffect(Unit) {
        try {
            // получаем текущие материалы разово и формируем уникальные группы/курсы
            val mats = materialDao.getAllFlow().first()
            val groups = mats.mapNotNull { it.groupId }.distinct()
            val courses = mats.mapNotNull { it.courseId }.distinct()
            allGroups.clear(); allGroups.addAll(groups)
            allCourses.clear(); allCourses.addAll(courses)
        } catch (e: Exception) {
            // лог — не падаем
            android.util.Log.e(
                "AddEditMaterial",
                "Ошибка при инициализации групп/курсов: ${e.message}",
                e
            )
        }
    }

    // 5. Если пришёл materialId — подгружаем материал в форму (разовый запуск)
    LaunchedEffect(materialId) {
        materialId?.toLongOrNull()?.let { id ->
            try {
                val mat = materialDao.getById(id)
                mat?.let {
                    editingId = it.id
                    title = it.title
                    xmlContent = it.xmlContent
                    groupSelected = it.groupId
                    courseSelected = it.courseId
                }
            } catch (e: Exception) {
                android.util.Log.e(
                    "AddEditMaterial",
                    "Ошибка при загрузке материала: ${e.message}",
                    e
                )
            }
        }
    }

    // 6. UI: Scaffold с TopAppBar и кнопкой Сохранить
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editingId == null) "Добавить материал" else "Редактировать материал") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.Close, contentDescription = "Отмена")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (title.isBlank()) {
                            Toast.makeText(context, "Введите заголовок", Toast.LENGTH_SHORT).show()
                            return@IconButton
                        }

                        // 7. Сохраняем материал — в корутине
                        scope.launch {
                            // парсим секции на бекграунде
                            val sections = withContext(Dispatchers.Default) {
                                parseSectionsFromXml(xmlContent, 0L, UUID.randomUUID().toString())
                            }

                            val now = System.currentTimeMillis()
                            val uid =
                                editingId?.let { materialDao.getById(it)?.uid } ?: UUID.randomUUID()
                                    .toString()
                            val searchIndex = (title + " " + stripXmlTags(xmlContent)).trim()

                            val material = MaterialEntity(
                                id = editingId ?: 0,
                                uid = uid,
                                title = title,
                                xmlContent = xmlContent,
                                groupId = if (showNewGroupField) newGroupName.ifBlank { null } else groupSelected,
                                courseId = if (showNewCourseField) newCourseName.ifBlank { null } else courseSelected,
                                tags = null,
                                searchIndex = searchIndex,
                                cloudPath = null,
                                createdAt = now,
                                updatedAt = now
                            )

                            // 8. Транзакционно сохраняем материал и секции — используем suspend withTransaction
                            try {
                                db.withTransaction {
                                    if (editingId == null) {
                                        val newId = materialDao.insert(material)
                                        val sectionsToInsert = sections.mapIndexed { idx, sec ->
                                            sec.copy(materialId = newId, position = idx)
                                        }
                                        sectionDao.deleteByMaterialId(newId)
                                        sectionDao.insertAll(sectionsToInsert)
                                    } else {
                                        materialDao.update(material.copy(id = editingId!!))
                                        val sectionsToInsert = sections.mapIndexed { idx, sec ->
                                            sec.copy(materialId = editingId!!, position = idx)
                                        }
                                        sectionDao.deleteByMaterialId(editingId!!)
                                        sectionDao.insertAll(sectionsToInsert)
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e(
                                    "AddEditMaterial",
                                    "Ошибка при сохранении: ${e.message}",
                                    e
                                )
                            }

                            // Обновляем локальные списки, если добавлены новые группа/курс
                            if (showNewGroupField && newGroupName.isNotBlank() && !allGroups.contains(
                                    newGroupName
                                )
                            ) {
                                allGroups.add(newGroupName)
                            }
                            if (showNewCourseField && newCourseName.isNotBlank() && !allCourses.contains(
                                    newCourseName
                                )
                            ) {
                                allCourses.add(newCourseName)
                            }

                            // Возвращаемся назад после сохранения
                            navController.popBackStack()
                        }
                    }) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Сохранить")
                    }
                }
            )
        }
    ) { innerPadding ->
        // Контент: верх — метаданные, низ — редактор (weight=1)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(12.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Заголовок") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Dropdown для выбора группы
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Groups dropdown (read-only field + menu)
                var expandedGroups by remember { mutableStateOf(false) }
                Box {
                    OutlinedTextField(
                        value = groupSelected ?: "",
                        onValueChange = { /* read-only */ },
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { expandedGroups = !expandedGroups }) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Выбрать группу"
                                )
                            }
                        },
                        label = { Text("Группа") },
                        modifier = Modifier.width(220.dp)
                    )
                    DropdownMenu(
                        expanded = expandedGroups,
                        onDismissRequest = { expandedGroups = false }) {
                        DropdownMenuItem(text = { Text("Без группы") }, onClick = {
                            groupSelected = null; expandedGroups = false
                        })
                        allGroups.forEach { g ->
                            DropdownMenuItem(text = { Text(g) }, onClick = {
                                groupSelected = g; expandedGroups = false
                            })
                        }
                        DropdownMenuItem(text = { Text("Добавить новую...") }, onClick = {
                            showNewGroupField = true; expandedGroups = false
                        })
                    }
                }

                // Courses dropdown (аналогично)
                var expandedCourses by remember { mutableStateOf(false) }
                Box {
                    OutlinedTextField(
                        value = courseSelected ?: "",
                        onValueChange = { /* read-only */ },
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { expandedCourses = !expandedCourses }) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Выбрать курс"
                                )
                            }
                        },
                        label = { Text("Курс") },
                        modifier = Modifier.width(220.dp)
                    )
                    DropdownMenu(
                        expanded = expandedCourses,
                        onDismissRequest = { expandedCourses = false }) {
                        DropdownMenuItem(text = { Text("Без курса") }, onClick = {
                            courseSelected = null; expandedCourses = false
                        })
                        allCourses.forEach { c ->
                            DropdownMenuItem(text = { Text(c) }, onClick = {
                                courseSelected = c; expandedCourses = false
                            })
                        }
                        DropdownMenuItem(text = { Text("Добавить новый...") }, onClick = {
                            showNewCourseField = true; expandedCourses = false
                        })
                    }
                }
            }

            if (showNewGroupField) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    label = { Text("Название новой группы") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (showNewCourseField) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newCourseName,
                    onValueChange = { newCourseName = it },
                    label = { Text("Название нового курса") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Редактор XML занимает большую часть экрана
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                BasicTextField(
                    value = xmlContent,
                    onValueChange = { xmlContent = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onBackground)
                )
            }
        }
    }
}

// Вспомогательные функции

private fun stripXmlTags(xml: String): String {
    return xml.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
}

private fun parseSectionsFromXml(
    xml: String,
    materialId: Long,
    materialUid: String
): List<MaterialSectionEntity> {
    val sections = mutableListOf<MaterialSectionEntity>()
    val regex = Regex("<section[^>]*>([\\s\\S]*?)</section>", RegexOption.IGNORE_CASE)
    val matches = regex.findAll(xml).toList()
    if (matches.isEmpty()) {
        sections.add(
            MaterialSectionEntity(
                materialId = materialId,
                uid = "$materialUid-sec-0",
                title = null,
                content = xml,
                position = 0
            )
        )
        return sections
    }
    var pos = 0
    for (m in matches) {
        val inner = m.groups[1]?.value ?: ""
        val title = Regex(
            "<title[^>]*>([\\s\\S]*?)</title>",
            RegexOption.IGNORE_CASE
        ).find(inner)?.groups?.get(1)?.value?.trim()
        val content = inner.trim()
        sections.add(
            MaterialSectionEntity(
                materialId = materialId,
                uid = "$materialUid-sec-$pos",
                title = title,
                content = content,
                position = pos
            )
        )
        pos++
    }
    return sections
}