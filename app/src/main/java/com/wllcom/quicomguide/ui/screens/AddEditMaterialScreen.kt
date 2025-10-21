package com.wllcom.quicomguide.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.wllcom.quicomguide.data.local.AppDatabase
import com.wllcom.quicomguide.data.local.crossref.MaterialCourseCrossRef
import com.wllcom.quicomguide.data.local.crossref.MaterialGroupCrossRef
import com.wllcom.quicomguide.data.local.entities.CourseEntity
import com.wllcom.quicomguide.data.local.entities.MaterialGroupEntity
import com.wllcom.quicomguide.ui.viewmodel.AddEditMaterialViewModel
import com.wllcom.quicomguide.ui.viewmodel.MaterialsViewModel
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

@Preview
@Composable
fun PreviewAddEditMaterialScreen() {
    AddEditMaterialScreen(rememberNavController(), viewModel(), "0", PaddingValues())
}

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
    val groupDao = db.groupDao()
    val courseDao = db.courseDao()
    val scope = rememberCoroutineScope()
    var updatedMaterialId by remember { mutableStateOf<Long?>(null) }

    var editingMaterial by remember { mutableStateOf(false) }
    var editingMaterialState by remember { mutableStateOf(false) }
    if (!editingMaterial && editingMaterialState) {
        navController.previousBackStackEntry
            ?.savedStateHandle
            ?.set("updatedMaterialId", updatedMaterialId)

        navController.popBackStack()
    }

    var showSettingsDialog by remember { mutableStateOf(false) }

    var xmlValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = "<material>\n" +
                        "   <title>Заголовок</title>\n" +
                        "   <section>\n" +
                        "       <title>Новый раздел</title>\n" +
                        "       <content>\n" +
                        "           Пример втроенного кода: <inline-code language=\"cpp\">int main()</inline-code><br/>\n" +
                        "           Пример матрицы:\n" +
                        "           <tex>\n" +
                        "               \\begin{bmatrix}\n" +
                        "               a_{11} & a_{12} & a_{13} \\\\\n" +
                        "               a_{21} & a_{22} & a_{23} \\\\\n" +
                        "               a_{31} & a_{32} & a_{33}\n" +
                        "               \\end{bmatrix}\n" +
                        "           </tex>\n" +
                        "           Пример таблицы через тэг tex:\n" +
                        "           <tex>\n" +
                        "               \\begin{array}{l l r}\n" +
                        "               \\textsf{\\textbf{№}} & \\textsf{\\textbf{Имя}} & \\textsf{\\textbf{Возраст}} \\\\[3pt]\n" +
                        "               \\hline\n" +
                        "               1 & Анна & 25 \\\\\n" +
                        "               2 & Борис & 30 \\\\\n" +
                        "               3 & Виктор & 28 \\\\\n" +
                        "               \\end{array}\n" +
                        "           </tex>\n" +
                        "           Пример таблицы:\n" +
                        "           <table>\n" +
                        "| № | Имя | Пример |\n" +
                        "|---|-----|--------|\n" +
                        "| 1 | std::vector | <inline-code>std::vector<int></inline-code> |\n" +
                        "| 2 | move | <inline-code language=\"cpp\">std::move(x)</inline-code> |\n" +
                        "| 3 | main | <inline-code language=\"cpp\">int main()</inline-code> |\n" +
                        "           </table>\n" +
                        "       </content>\n" +
                        "       <example>\n" +
                        "           Пример кода:\n" +
                        "           <code language=\"cpp\">\n" +
                        "int main() {\n" +
                        "   cout << \"Hello world\" << endl;\n" +
                        "}\n" +
                        "           </code>\n" +
                        "       </example>\n" +
                        "   </section>\n" +
                        "</material>\n\n\n\n\n",
                selection = TextRange(11)
            )
        )
    }
    LaunchedEffect(materialDao) {
        materialId?.toLongOrNull()?.let { id ->
            xmlValue =
                TextFieldValue(materialDao.getMaterialById(id)?.xmlRaw ?: "Материал не найден!")
        }
    }

    var showTagsInfoDialog by remember { mutableStateOf(false) }

    val vmAddEditMaterial: AddEditMaterialViewModel = hiltViewModel()
    val selectedCourseName by vmAddEditMaterial.selectedCourseName.collectAsState()
    val selectedCourseId by vmAddEditMaterial.selectedCourseId.collectAsState()
    val selectedGroupName by vmAddEditMaterial.selectedGroupName.collectAsState()
    val selectedGroupId by vmAddEditMaterial.selectedGroupId.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.padding(end = 8.dp),
                title = {
                    Text(text = "Редактирование")
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Закрыть")
                    }
                },
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Группа/Курс")
                    }
                    if (editingMaterial) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        IconButton(onClick = {
                            editingMaterial = true
                            editingMaterialState = false
                            scope.launch {
                                if (materialId == null) {
                                    val idNewMaterial = viewModel.addMaterial(xmlValue.text)
                                    if (idNewMaterial != null) {
                                        editingMaterialState = true

                                        /** add Course */
                                        if (selectedCourseId == null) {
                                            if (selectedCourseName != null) {
                                                val newCourseId =
                                                    courseDao.insertCourse(CourseEntity(name = selectedCourseName!!))
                                                vmAddEditMaterial.updateCourseId(newCourseId)
                                                val crossRef = MaterialCourseCrossRef(
                                                    idNewMaterial,
                                                    newCourseId
                                                )
                                                materialDao.insertMaterialCourseCrossRefs(
                                                    listOf(
                                                        crossRef
                                                    )
                                                )
                                            }
                                        } else {
                                            val crossRef = MaterialCourseCrossRef(
                                                idNewMaterial,
                                                selectedCourseId!!
                                            )
                                            materialDao.insertMaterialCourseCrossRefs(
                                                listOf(
                                                    crossRef
                                                )
                                            )
                                        }

                                        /** add Group */
                                        if (selectedGroupId == null) {
                                            if (selectedGroupName != null) {
                                                val newGroupId = groupDao.insertGroup(
                                                    MaterialGroupEntity(
                                                        name = selectedGroupName!!,
                                                        courseId = selectedCourseId
                                                    )
                                                )
                                                vmAddEditMaterial.updateGroupId(newGroupId)
                                                val crossRef =
                                                    MaterialGroupCrossRef(idNewMaterial, newGroupId)
                                                materialDao.insertMaterialGroupCrossRefs(
                                                    listOf(
                                                        crossRef
                                                    )
                                                )
                                            }
                                        } else {
                                            val crossRef = MaterialGroupCrossRef(
                                                idNewMaterial,
                                                selectedGroupId!!
                                            )
                                            materialDao.insertMaterialGroupCrossRefs(listOf(crossRef))
                                        }
                                    }
                                } else {
                                    val newId =
                                        viewModel.updateMaterial(materialId.toLong(), xmlValue.text)
                                    if (newId != null) {
                                        editingMaterialState = true
                                        updatedMaterialId = newId
                                    }
                                }
                                editingMaterial = false
                            }
                        }) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Сохранить")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(start = 5.dp, top = 0.dp, end = 5.dp, bottom = 4.dp)
                .imePadding()
        ) {
            val bg = MaterialTheme.colorScheme.outlineVariant
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(bg, shape = RoundedCornerShape(8.dp))
                        .padding(4.dp)
                ) {
                    OutlinedTextField(
                        value = xmlValue,
                        onValueChange = {
                            var text = it.text
                            if (text.substring(text.length - 5, text.length) != "\n\n\n\n\n")
                                text += "\n\n\n\n\n"
                            xmlValue = it.copy(text)
                        },
                        modifier = Modifier.fillMaxSize(),
                        singleLine = false,
                        maxLines = Int.MAX_VALUE,
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        ),
                    )
                }
            }

            // small floating toolbar
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.padding(bottom = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            xmlValue = insertAtCursor(xmlValue, "<title>Заголовок</title>")
                        }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Filled.Title,
                                contentDescription = "Добавить title",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(onClick = {
                            val insert =
                                "<section>\n  <title>Новый раздел</title>\n  <content>\n\n  </content>\n</section>"
                            xmlValue = insertAtCursor(xmlValue, insert)
                        }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Filled.TextFields,
                                contentDescription = "Добавить секцию",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(onClick = {
                            xmlValue =
                                insertAtCursor(xmlValue, "<code language=\"cpp\">\n// код\n</code>")
                        }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Filled.Code,
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
            TagsInfoDialog(onDismiss = { showTagsInfoDialog = false })
        }
    }

    if (showSettingsDialog) {
        MaterialSettingsDialog(
            viewModel = vmAddEditMaterial,
            onDismiss = { showSettingsDialog = false }
        )
    }
}

@Composable
private fun TagsInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
        title = { Text("Тэги") },
        text = {
            LazyColumn {
                item {
                    Text(buildAnnotatedString {
                        pushStyle(SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        append("1) <material> ")
                        pop()
                        append("- обязательный тэг для границ материала. Количество: один")
                    }, fontSize = 14.sp)
                }
                item {
                    Text(buildAnnotatedString {
                        pushStyle(SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        append("2) <section> ")
                        pop()
                        append("- секция(параграф) материала. Количество: неограниченно")
                    }, fontSize = 14.sp)
                }
                item {
                    Text(buildAnnotatedString {
                        pushStyle(SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        append("3) <title> ")
                        pop()
                        append("- заголовок материала или секции. Количество: по одному для каждого")
                    }, fontSize = 14.sp)
                }
                item {
                    Text(buildAnnotatedString {
                        pushStyle(SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        append("4) <content> ")
                        pop()
                        append("- основное содержимое секции, можно встраивать в текст inline тэги. Количество: неограниченно")
                    }, fontSize = 14.sp)
                }
                item {
                    Text(buildAnnotatedString {
                        pushStyle(SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        append("5) <example> ")
                        pop()
                        append("- пример в секции, можно встраивать в текст inline тэги. Количество: неограниченно")
                    }, fontSize = 14.sp)
                }
                item {
                    Text(buildAnnotatedString {
                        pushStyle(SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        append("6) <code> ")
                        pop()
                        append(
                            "- блок с кодом в основном содержимом или примере, имеет необязательный атрибут language" +
                                    " для указания языка программирования. Код пишется от начала строки! Количество: неограниченно"
                        )
                    }, fontSize = 14.sp)
                }
                item {
                    Text(buildAnnotatedString {
                        pushStyle(SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        append("7) <inline-code> ")
                        pop()
                        append(
                            "- код в тексте основного содержимого или примера, имеет необязательный атрибут language" +
                                    "для указания языка программирования. Количество: неограниченно"
                        )
                    }, fontSize = 14.sp)
                }
                item {
                    Text(buildAnnotatedString {
                        pushStyle(SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        append("8) <tex> ")
                        pop()
                        append("- формула в формате KaTeX в основном содержимом или примере. Количество: неограниченно")
                    }, fontSize = 14.sp)
                }
                item {
                    Text(buildAnnotatedString {
                        pushStyle(SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        append("9) <table> ")
                        pop()
                        append("- текстовый формат таблицы, Markdown-подобный синтаксис. Количество: неограниченно")
                    }, fontSize = 14.sp)
                }
                item {
                    Text(buildAnnotatedString {
                        pushStyle(SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        append("10) <br/> ")
                        pop()
                        append("- перенос строки. Количество: неограниченно")
                    }, fontSize = 14.sp)
                }
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
        selection = TextRange(newPos, newPos)
    )
}

private fun indentSelection(value: TextFieldValue): TextFieldValue {
    val text = value.text
    val start = value.selection.start.coerceAtLeast(0)
    val end = value.selection.end.coerceAtLeast(0)
    val (sLine, eLine) = getLineBounds(text, start, end)
    val indented = text.substring(sLine, eLine).let { "    $it" }
    val newText = text.substring(0, sLine) + indented + text.substring(eLine)
    val newStart = sLine
    val newEnd = sLine + indented.length - 1
    return TextFieldValue(
        text = newText,
        selection = TextRange(newStart, newEnd)
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
    val newEnd = sLine + unindented.length - 1
    return TextFieldValue(
        text = newText,
        selection = TextRange(newStart, newEnd)
    )
}

private fun getLineBounds(text: String, start: Int, end: Int): Pair<Int, Int> {
    val sLine = text.lastIndexOf('\n', start - 1).let { if (it == -1) 0 else it + 1 }
    val eLine = text.indexOf('\n', end).let { if (it == -1) text.length else it + 1 }
    return Pair(sLine, eLine)
}

@Composable
fun MaterialSettingsDialog(
    viewModel: AddEditMaterialViewModel,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val db = AppDatabase.getInstance(ctx)
    val courses by db.courseDao().getAllCoursesFlow().collectAsState(initial = emptyList())
    var groups by remember { mutableStateOf<List<MaterialGroupEntity>>(emptyList()) }

    var courseInputMode by remember { mutableStateOf(false) }
    var newCourseName by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        merge(
            viewModel.selectedCourseName,
            viewModel.selectedCourseId
        ).collect {

            groups = if (viewModel.selectedCourseId.value != null)
                db.groupDao().getGroupsByCourseId(viewModel.selectedCourseId.value)
            else {
                if (viewModel.selectedCourseName.value == null)
                    db.groupDao().getAllGroups()
                else
                    emptyList()
            }
        }
    }

    var groupInputMode by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройки материала") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                Text("Курс:")
                if (courseInputMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newCourseName,
                            onValueChange = { newCourseName = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Название курса") }
                        )
                        IconButton(onClick = {
                            courseInputMode = false; viewModel.newCourse(
                            newCourseName
                        )
                        }) {
                            Icon(Icons.Default.Check, contentDescription = "Сохранить курс")
                        }
                        IconButton(onClick = { courseInputMode = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Отмена")
                        }
                    }
                } else {
                    var expanded by remember { mutableStateOf(false) }
                    var buttonWidth by remember { mutableStateOf(0.dp) }
                    val locDen = LocalDensity.current
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { expanded = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onGloballyPositioned { coordinates ->
                                        buttonWidth = with(locDen) { coordinates.size.width.toDp() }
                                    }
                            ) {
                                Text(viewModel.selectedCourseName.value ?: "Не выбран")
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier
                                    .width(buttonWidth)
                                    .heightIn(max = 235.dp)
                                    .border(
                                        3.dp,
                                        MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(24.dp)
                                    )
                            ) {
                                ActionMenuButton(
                                    text = "Не выбран",
                                    onClick = {
                                        viewModel.setCourse(null, null)
                                        expanded = false
                                    }
                                )
                                if (!courses.isEmpty()) {
                                    HorizontalDivider(
                                        Modifier,
                                        DividerDefaults.Thickness,
                                        DividerDefaults.color
                                    )
                                }

                                courses.forEach { course ->
                                    ActionMenuButton(
                                        text = course.name,
                                        onClick = {
                                            viewModel.setCourse(course.name, course.id)
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { courseInputMode = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Добавить курс")
                        }
                    }
                }

                Text("Группу:")
                if (groupInputMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newGroupName,
                            onValueChange = { newGroupName = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Название группы") }
                        )
                        IconButton(onClick = {
                            groupInputMode = false; viewModel.newGroup(
                            newGroupName
                        )
                        }) {
                            Icon(Icons.Default.Check, contentDescription = "Сохранить группу")
                        }
                        IconButton(onClick = { groupInputMode = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Отмена")
                        }
                    }
                } else {
                    var expandedGroup by remember { mutableStateOf(false) }
                    var buttonWidth by remember { mutableStateOf(0.dp) }
                    val locDen = LocalDensity.current
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { expandedGroup = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onGloballyPositioned { coordinates ->
                                        buttonWidth = with(locDen) { coordinates.size.width.toDp() }
                                    }
                            ) {
                                Text(viewModel.selectedGroupName.value ?: "Не выбрана")
                            }
                            DropdownMenu(
                                expanded = expandedGroup,
                                onDismissRequest = { expandedGroup = false },
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier
                                    .width(buttonWidth)
                                    .heightIn(max = 235.dp)
                                    .border(
                                        3.dp,
                                        MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(24.dp)
                                    )
                            ) {
                                ActionMenuButton(
                                    text = "Не выбран",
                                    onClick = {
                                        viewModel.setGroup(null, null)
                                        expandedGroup = false
                                    }
                                )
                                if (!groups.isEmpty()) {
                                    HorizontalDivider(
                                        Modifier,
                                        DividerDefaults.Thickness,
                                        DividerDefaults.color
                                    )
                                }
                                groups.forEach { group ->
                                    ActionMenuButton(
                                        text = group.name,
                                        onClick = {
                                            viewModel.setGroup(group.name, group.id)
                                            expandedGroup = false
                                        }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { groupInputMode = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Добавить группу")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
            }) { Text("ОК") }
        },
    )
}

@Composable
fun ActionMenuButton(
    text: String,
    highlight: Boolean = false,
    onClick: () -> Unit
) {
    val colors = if (highlight) ButtonDefaults.filledTonalButtonColors()
    else ButtonDefaults.textButtonColors()

    Button(
        onClick = onClick,
        colors = colors,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}

