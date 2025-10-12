package com.wllcom.quicomguide.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.room.withTransaction
import com.wllcom.quicomguide.data.local.AppDatabase
import com.wllcom.quicomguide.data.local.crossref.MaterialCourseCrossRef
import com.wllcom.quicomguide.data.local.crossref.MaterialGroupCrossRef
import com.wllcom.quicomguide.data.local.entities.MaterialEntity
import com.wllcom.quicomguide.data.local.entities.MaterialFts
import com.wllcom.quicomguide.data.local.entities.SectionElementEntity
import com.wllcom.quicomguide.data.local.entities.SectionEntity
import com.wllcom.quicomguide.ui.viewmodel.MaterialsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditMaterialScreen(
    navController: NavController,
    viewModel: MaterialsViewModel,
    materialId: String? = null,
    padding: PaddingValues
) {
    val context = LocalContext.current
    val db = AppDatabase.getInstance(context)
    val materialDao = db.materialDao()
    val sectionDao = db.materialDao()
    val groupDao = db.groupDao()
    val courseDao = db.courseDao()
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var xmlValue by remember { mutableStateOf(TextFieldValue("<material>\n    <!-- ваш XML здесь -->\n</material>")) }

    // selected crossrefs (many-to-many)
    var selectedGroupIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var selectedCourseIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    var showTagsInfoDialog by remember { mutableStateOf(false) }

    // load groups/courses lists for pickers (UI may use them)
    val groups by groupDao.getAllFlow().collectAsState(initial = emptyList())
    val courses by courseDao.getAllFlow().collectAsState(initial = emptyList())

    // load existing material + crossrefs when editing
    LaunchedEffect(materialId) {
        materialId?.toLongOrNull()?.let { id ->
            try {
                val mws = materialDao.getMaterialWithSections(id) // returns MaterialWithSections?
                mws?.let {
                    title = it.material.title
                    xmlValue = TextFieldValue(it.material.xmlRaw)
                }
                // read crossrefs via raw query because DAO does not have select for crossrefs
                val readDb = db.openHelper.readableDatabase
                // groups
                val groupCursor = readDb.query(
                    "SELECT groupId FROM material_group_crossref WHERE materialId = ?",
                    arrayOf(id.toString())
                )
                val gIds = mutableSetOf<Long>()
                groupCursor.use { c ->
                    while (c.moveToNext()) {
                        val gid = c.getLong(0)
                        gIds.add(gid)
                    }
                }
                selectedGroupIds = gIds

                // courses
                val courseCursor = readDb.query(
                    "SELECT courseId FROM material_course_crossref WHERE materialId = ?",
                    arrayOf(id.toString())
                )
                val cIds = mutableSetOf<Long>()
                courseCursor.use { c ->
                    while (c.moveToNext()) {
                        val cid = c.getLong(0)
                        cIds.add(cid)
                    }
                }
                selectedCourseIds = cIds

            } catch (e: Exception) {
                // swallow errors (DB may be empty during tests); consider logging
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (title.isEmpty()) {
                            Text(
                                text = "Введите заголовок",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                        BasicTextField(
                            value = title,
                            onValueChange = { title = it },
                            singleLine = true,
                            textStyle = TextStyle(color = MaterialTheme.colorScheme.onBackground),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Закрыть")
                    }
                },
                actions = {
                    // meta action — can open dialog for selecting groups/courses (not implemented here)
                    IconButton(onClick = { /* TODO: open groups/courses selector */ }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Группа/Курс")
                    }
                    IconButton(onClick = {
                        // Save action
                        if (title.isBlank()) {
                            Toast.makeText(context, "Введите заголовок", Toast.LENGTH_SHORT).show()
                            return@IconButton
                        }
                        scope.launch {
                            val now = System.currentTimeMillis()
                            val searchIndex = (title + " " + stripXmlTags(xmlValue.text)).trim()

                            // prepare material entity (note fields in schema: xmlRaw, contentFts)
                            val materialEntity = MaterialEntity(
                                id = materialId?.toLongOrNull() ?: 0L,
                                title = title,
                                xmlRaw = xmlValue.text,
                                contentFts = searchIndex,
                                cloudPath = null,
                                createdAt = now,
                                updatedAt = now
                            )

                            // parse sections -> returns List<Triple<sectionTitle, orderIndex, List<Pair<elementType, elementContent>>>>
                            val sectionsForInsert = parseSectionsForInsert(xmlValue.text)

                            // persist inside transaction
                            db.withTransaction {
                                if (materialId == null) {
                                    viewModel.addMaterial(xmlValue.text)
//                                    // new: use helper to insert material + sections + elements
//                                    val (newId, _) = materialDao.insertMaterialTreeReturningIds(
//                                        materialTitle = materialEntity.title,
//                                        xmlRaw = materialEntity.xmlRaw,
//                                        sections = sectionsForInsert
//                                    )
//                                    // insert FTS row
//                                    materialDao.insertMaterialFts(
//                                        MaterialFts(
//                                            rowid = newId,
//                                            contentFts = searchIndex
//                                        )
//                                    )
                                    // insert crossrefs if any
//                                    if (selectedGroupIds.isNotEmpty()) {
//                                        val refs = selectedGroupIds.map { gid ->
//                                            MaterialGroupCrossRef(
//                                                materialId = newId,
//                                                groupId = gid
//                                            )
//                                        }
//                                        materialDao.insertMaterialGroupCrossRefs(refs)
//                                    }
//                                    if (selectedCourseIds.isNotEmpty()) {
//                                        val refs = selectedCourseIds.map { cid ->
//                                            MaterialCourseCrossRef(
//                                                materialId = newId,
//                                                courseId = cid
//                                            )
//                                        }
//                                        materialDao.insertMaterialCourseCrossRefs(refs)
//                                    }
                                } else {
                                    // update existing material:
                                    val mid = materialId.toLong()
                                    // update material row (keep id)
                                    materialDao.updateMaterial(
                                        materialEntity.copy(
                                            id = mid,
                                            updatedAt = now
                                        )
                                    )
                                    // replace FTS row
                                    materialDao.insertMaterialFts(
                                        MaterialFts(
                                            rowid = mid,
                                            contentFts = searchIndex
                                        )
                                    )

                                    // Delete existing section_elements and sections for this material via SQL (no explicit DAO method provided)
                                    val writable = db.openHelper.writableDatabase
                                    // delete elements of sections that belong to material
                                    writable.execSQL(
                                        "DELETE FROM section_elements WHERE sectionId IN (SELECT id FROM sections WHERE materialId = ?)",
                                        arrayOf(mid)
                                    )
                                    // delete sections
                                    writable.execSQL(
                                        "DELETE FROM sections WHERE materialId = ?",
                                        arrayOf(mid)
                                    )

                                    // re-insert sections+elements
                                    for ((sIdx, sectionTriple) in sectionsForInsert.withIndex()) {
                                        val (sectionTitle, orderIndex, elements) = sectionTriple
                                        val sectionId = materialDao.insertSection(
                                            SectionEntity(
                                                materialId = mid,
                                                title = sectionTitle,
                                                orderIndex = orderIndex
                                            )
                                        )
                                        if (elements.isNotEmpty()) {
                                            val elementEntities =
                                                elements.mapIndexed { idx, (etype, econtent) ->
                                                    SectionElementEntity(
                                                        sectionId = sectionId,
                                                        elementType = etype,
                                                        content = econtent,
                                                        orderIndex = idx
                                                    )
                                                }
                                            materialDao.insertSectionElements(elementEntities)
                                        }
                                    }

                                    // replace crossrefs
                                    materialDao.deleteMaterialGroupCrossRefsByMaterial(mid)
                                    if (selectedGroupIds.isNotEmpty()) {
                                        val refs = selectedGroupIds.map { gid ->
                                            MaterialGroupCrossRef(
                                                materialId = mid,
                                                groupId = gid
                                            )
                                        }
                                        materialDao.insertMaterialGroupCrossRefs(refs)
                                    }

                                    materialDao.deleteMaterialCourseCrossRefsByMaterial(mid)
                                    if (selectedCourseIds.isNotEmpty()) {
                                        val refs = selectedCourseIds.map { cid ->
                                            MaterialCourseCrossRef(
                                                materialId = mid,
                                                courseId = cid
                                            )
                                        }
                                        materialDao.insertMaterialCourseCrossRefs(refs)
                                    }
                                }
                            } // withTransaction

                            navController.popBackStack()
                        }
                    }) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Сохранить")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(12.dp)) {
            val bg = MaterialTheme.colorScheme.surfaceVariant
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(bg, shape = RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    OutlinedTextField(
                        value = xmlValue,
                        onValueChange = { xmlValue = it },
                        modifier = Modifier.fillMaxSize(),
                        singleLine = false,
                        maxLines = Int.MAX_VALUE
                    )
                }
            }

            // small floating toolbar
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            val insert =
                                "<section>\n  <title>Новый раздел</title>\n  <content>\n\n  </content>\n</section>\n"
                            xmlValue = insertAtCursor(xmlValue, insert)
                        }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = "Добавить секцию",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(onClick = {
                            xmlValue = insertAtCursor(xmlValue, "<title>Заголовок</title>\n")
                        }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Filled.Title,
                                contentDescription = "Добавить title",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(onClick = {
                            xmlValue = insertAtCursor(xmlValue, "<code>\n// код\n</code>\n")
                        }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Filled.Code,
                                contentDescription = "Добавить code",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                xmlValue = indentSelection(xmlValue)
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.FormatIndentIncrease,
                                contentDescription = "Отступ",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                xmlValue = unindentSelection(xmlValue)
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.FormatIndentDecrease,
                                contentDescription = "Убрать отступ",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        VerticalDivider(
                            modifier = Modifier.height(36.dp),
                            thickness = DividerDefaults.Thickness,
                            color = DividerDefaults.color
                        )

                        IconButton(
                            onClick = { showTagsInfoDialog = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Filled.Info,
                                contentDescription = "Информация о тэгs",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        if (showTagsInfoDialog) {
            TagsInfoDialog(db = db, onDismiss = { showTagsInfoDialog = false })
        }
    }
}

@Composable
private fun TagsInfoDialog(db: AppDatabase, onDismiss: () -> Unit) {
    // In current schema there is no tags column on MaterialEntity.
    // Show informational dialog to avoid crashing existing code that expected .tags.
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
        title = { Text("Тэги") },
        text = {
            Column {
                Text("В текущей схеме тэги не хранятся как поле в таблице materials.")
                Text("Если нужно — можно добавить отдельную таблицу тегов/кросс-рефы и соответствующие DAO-запросы.")
            }
        }
    )
}

private fun insertAtCursor(value: TextFieldValue, insert: String): TextFieldValue {
    val start = value.selection.start.coerceAtLeast(0)
    val end = value.selection.end.coerceAtLeast(0)
    val newText = StringBuilder(value.text).replace(start, end, insert).toString()
    val newPos = start + insert.length
    return TextFieldValue(
        text = newText,
        selection = androidx.compose.ui.text.TextRange(newPos, newPos)
    )
}

private fun indentSelection(value: TextFieldValue): TextFieldValue {
    val text = value.text
    val start = value.selection.start.coerceAtLeast(0)
    val end = value.selection.end.coerceAtLeast(0)
    val (sLine, eLine) = getLineBounds(text, start, end)
    val lines = text.substring(sLine, eLine).split("\n")
    val indented = lines.joinToString("\n") { "    $it" }
    val newText = text.substring(0, sLine) + indented + text.substring(eLine)
    val newStart = sLine
    val newEnd = sLine + indented.length
    return TextFieldValue(
        text = newText,
        selection = androidx.compose.ui.text.TextRange(newStart, newEnd)
    )
}

private fun unindentSelection(value: TextFieldValue): TextFieldValue {
    val text = value.text
    val start = value.selection.start.coerceAtLeast(0)
    val end = value.selection.end.coerceAtLeast(0)
    val (sLine, eLine) = getLineBounds(text, start, end)
    val lines = text.substring(sLine, eLine).split("\n")
    val unindented = lines.joinToString("\n") { it.removePrefix("    ").removePrefix("\t") }
    val newText = text.substring(0, sLine) + unindented + text.substring(eLine)
    val newStart = sLine
    val newEnd = sLine + unindented.length
    return TextFieldValue(
        text = newText,
        selection = androidx.compose.ui.text.TextRange(newStart, newEnd)
    )
}

private fun getLineBounds(text: String, start: Int, end: Int): Pair<Int, Int> {
    val sLine = text.lastIndexOf('\n', start - 1).let { if (it == -1) 0 else it + 1 }
    val eLine = text.indexOf('\n', end).let { if (it == -1) text.length else it + 1 }
    return Pair(sLine, eLine)
}

// helper: strip xml tags (simple)
private fun stripXmlTags(xml: String): String {
    return xml.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
}

/**
 * Parse XML into the format expected by MaterialDao.insertMaterialTreeReturningIds:
 * List<Triple<sectionTitle, orderIndex, List<Pair<elementType, elementContent>>>>
 *
 * This is a simple parser based on your earlier regexes: extracts <section>..</section>,
 * inside it looks for <title>..</title> and treats the remainder as a single "content" element.
 */
private fun parseSectionsForInsert(xml: String): List<Triple<String, Int, List<Pair<String, String>>>> {
    val sections = mutableListOf<Triple<String, Int, List<Pair<String, String>>>>()
    val regex = Regex("<section[^>]*>([\\s\\S]*?)</section>", RegexOption.IGNORE_CASE)
    val matches = regex.findAll(xml).toList()
    if (matches.isEmpty()) {
        // treat full document as single unnamed section
        val content = xml.trim()
        sections.add(Triple("", 0, listOf(Pair("content", content))))
        return sections
    }
    var pos = 0
    for (m in matches) {
        val inner = m.groups[1]?.value ?: ""
        val title = Regex("<title[^>]*>([\\s\\S]*?)</title>", RegexOption.IGNORE_CASE)
            .find(inner)?.groups?.get(1)?.value?.trim() ?: ""
        val content = inner.trim()
        val elements = listOf(Pair("content", content))
        sections.add(Triple(title, pos, elements))
        pos++
    }
    return sections
}

