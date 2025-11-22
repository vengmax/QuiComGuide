package com.wllcom.quicomguide.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.wllcom.quicomguide.R
import com.wllcom.quicomguide.data.local.AppDatabase
import com.wllcom.quicomguide.data.local.crossref.MaterialCourseCrossRef
import com.wllcom.quicomguide.data.local.crossref.MaterialGroupCrossRef
import com.wllcom.quicomguide.data.local.entities.CourseEntity
import com.wllcom.quicomguide.data.local.entities.MaterialGroupEntity
import com.wllcom.quicomguide.data.source.cloud.AuthService
import com.wllcom.quicomguide.ui.components.TopBar
import com.wllcom.quicomguide.ui.styles.topBarStyle
import com.wllcom.quicomguide.ui.viewmodel.AddEditMaterialViewModel
import com.wllcom.quicomguide.ui.viewmodel.AuthViewModel
import com.wllcom.quicomguide.ui.viewmodel.MaterialsViewModel
import com.wllcom.quicomguide.ui.viewmodel.SettingsViewModel
import com.wllcom.quicomguide.ui.viewmodel.StorageViewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

fun readTextFromUri(context: Context, uri: Uri): String {
    val stringBuilder = StringBuilder()
    context.contentResolver.openInputStream(uri)?.use { inputStream ->
        BufferedReader(InputStreamReader(inputStream)).use { reader ->
            var line = reader.readLine()
            while (line != null) {
                stringBuilder.append(line).append("\n")
                line = reader.readLine()
            }
        }
    }
    return stringBuilder.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditMaterialScreen(
    systemPadding: PaddingValues,
    navController: NavController,
    viewModel: MaterialsViewModel,
    materialId: String? = null,
) {
    val context = LocalContext.current
    val db = AppDatabase.getInstance(context)
    val materialDao = db.materialDao()
    val groupDao = db.groupDao()
    val courseDao = db.courseDao()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var updatedMaterialId by remember { mutableStateOf<Long?>(null) }

    var showSettingsDialog by remember { mutableStateOf(false) }

    var allMaterialNameList by remember { mutableStateOf<List<String>>(emptyList()) }
    var receivedAllMaterialNameList by remember {mutableStateOf(false)}
    LaunchedEffect(receivedAllMaterialNameList){
        if(materialId == null) {
            allMaterialNameList = materialDao.getAllMaterialName().map { it.lowercase() }
            receivedAllMaterialNameList = true
        }
    }

    var materialTitle by remember { mutableStateOf("") }
    var xmlValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = "<material>\n\n" +
                        "<section>\n" +
                        "<title>Новый раздел</title>\n" +
                        "<content>\n" +
                        "Пример втроенного кода: <inline-code language=\"cpp\">int main()</inline-code><br/>\n" +
                        "Пример матрицы:\n" +
                        "<tex>\n" +
                        "\\begin{bmatrix}\n" +
                        "a_{11} & a_{12} & a_{13} \\\\\n" +
                        "a_{21} & a_{22} & a_{23} \\\\\n" +
                        "a_{31} & a_{32} & a_{33}\n" +
                        "\\end{bmatrix}\n" +
                        "</tex>\n" +
                        "Пример таблицы через тэг tex:\n" +
                        "<tex>\n" +
                        "\\begin{array}{l l r}\n" +
                        "\\textsf{\\textbf{№}} & \\textsf{\\textbf{Имя}} & \\textsf{\\textbf{Возраст}} \\\\[3pt]\n" +
                        "\\hline\n" +
                        "1 & Анна & 25 \\\\\n" +
                        "2 & Борис & 30 \\\\\n" +
                        "3 & Виктор & 28 \\\\\n" +
                        "\\end{array}\n" +
                        "</tex>\n" +
                        "Пример таблицы:\n" +
                        "<table>\n" +
                        "| № | Имя | Пример |\n" +
                        "|---|-----|--------|\n" +
                        "| 1 | std::vector | <inline-code>std::vector<int></inline-code> |\n" +
                        "| 2 | move | <inline-code language=\"cpp\">std::move(x)</inline-code> |\n" +
                        "| 3 | main | <inline-code language=\"cpp\">int main()</inline-code> |\n" +
                        "</table>\n" +
                        "</content>\n" +
                        "<example>\n" +
                        "Пример кода:\n" +
                        "<code language=\"cpp\">\n" +
                        "int main() {\n" +
                        "   cout << \"Hello world\" << endl;\n" +
                        "}\n" +
                        "</code>\n" +
                        "</example>\n" +
                        "</section>\n\n" +
                        "</material>\n\n\n\n\n",
                selection = TextRange(11)
            )
        )
    }
    val darkMode = isSystemInDarkTheme()
    val highlightedText = remember(xmlValue) {
        highlightXML(xmlValue.text, darkMode)
    }

    data class EditAction(
        val start: Int,
        val oldPart: String,
        val newPart: String
    )
    val undoStack = remember { mutableStateListOf<EditAction>() }
    val redoStack = remember { mutableStateListOf<EditAction>() }
    var lastText by remember { mutableStateOf(xmlValue.text) }
    var lastSelection by remember { mutableStateOf(TextRange(0,0)) }
    var debounceJob by remember { mutableStateOf<Job?>(null) }

    fun saveDebounceEditAction(
        oldText: String,
        newText: String,
        oldSelection: TextRange,
        newSelection: TextRange,
        debounceMs: Long = 1000L,
    ) {

        fun saveEditAction(
            oldText: String,
            newText: String,
            oldSelection: TextRange,
            newSelection: TextRange
        ) {
            val start = minOf(oldSelection.start, newSelection.start)
            val oldPart = oldText.substring(start, oldSelection.end)
            val newPart = newText.substring(start, newSelection.end)

            if (redoStack.isNotEmpty()) {
                if (redoStack.last().oldPart == newPart && redoStack.last().start == start)
                    return
            }
            if (undoStack.isNotEmpty()) {
                if (undoStack.last().newPart == newPart && undoStack.last().start == start)
                    return
            }

            if (oldPart != newPart) {
                undoStack.add(EditAction(start, oldPart, newPart))
                redoStack.clear()
            }
        }

        if(debounceMs == 0L){
            saveEditAction(oldText, newText, oldSelection, newSelection)
            lastText = newText
            lastSelection = newSelection
        }
        else{
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(debounceMs)
                saveEditAction(oldText, newText, oldSelection, newSelection)

                lastText = newText
                lastSelection = newSelection
            }
        }
    }

    fun undo() {

        if(debounceJob != null) {
            if (debounceJob!!.isActive) {
                debounceJob!!.cancel()
                saveDebounceEditAction(
                    lastText,
                    xmlValue.text,
                    lastSelection,
                    xmlValue.selection,
                    0L
                )
            }
        }

        if (undoStack.isNotEmpty()) {
            val action = undoStack.removeAt(undoStack.lastIndex)
            val current = xmlValue.text
            val newText = current.replaceRange(
                action.start,
                action.start + action.newPart.length,
                action.oldPart
            )
            xmlValue = xmlValue.copy(
                text = newText,
                selection = TextRange(action.start + action.oldPart.length)
            )
            lastText = xmlValue.text
            lastSelection = xmlValue.selection
            redoStack.add(action)
        }
    }

    fun redo() {

        if(debounceJob != null) {
            if (debounceJob!!.isActive) {
                debounceJob!!.cancel()
                saveDebounceEditAction(
                    lastText,
                    xmlValue.text,
                    lastSelection,
                    xmlValue.selection,
                    0L
                )
            }
        }

        if (redoStack.isNotEmpty()) {
            val action = redoStack.removeAt(redoStack.lastIndex)
            val current = xmlValue.text
            val newText = current.replaceRange(
                action.start,
                action.start + action.oldPart.length,
                action.newPart
            )
            xmlValue = xmlValue.copy(
                text = newText,
                selection = TextRange(action.start + action.newPart.length)
            )
            lastText = xmlValue.text
            lastSelection = xmlValue.selection
            undoStack.add(action)
        }
    }

    // Лаунчер для выбора XML-файла
    var expanded by remember { mutableStateOf(false) }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            val text = readTextFromUri(context, selectedUri) + "\n\n\n\n\n"
            xmlValue = TextFieldValue(text)
            undoStack.clear()
            redoStack.clear()
            lastText = xmlValue.text
            lastSelection = TextRange(0, 0)
        }
    }

    val vmAddEditMaterial: AddEditMaterialViewModel = hiltViewModel()
    val selectedCourseName by vmAddEditMaterial.selectedCourseName.collectAsState()
    val selectedCourseId by vmAddEditMaterial.selectedCourseId.collectAsState()
    val selectedGroupName by vmAddEditMaterial.selectedGroupName.collectAsState()
    val selectedGroupId by vmAddEditMaterial.selectedGroupId.collectAsState()

    var oldCourseId by remember { mutableStateOf<Long?>(null) }
    var oldGroupId by remember { mutableStateOf<Long?>(null) }
    var oldCourseName by remember { mutableStateOf<String?>(null) }
    var oldGroupName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(materialDao) {
        materialId?.toLongOrNull()?.let { id ->
            val mat = materialDao.getMaterialById(id)
            materialTitle = mat?.title?: "Материал не найден!"
            val xmlText = mat?.xmlRaw ?: "Материал не найден!"
            xmlValue = TextFieldValue(resetFiltrationSpecialCharactersXml(xmlText) + "\n\n\n\n\n")
            undoStack.clear()
            redoStack.clear()
            lastText = xmlValue.text
            lastSelection = TextRange(0, 0)

            // find group and course
            val groupsWithThisMat = materialDao.getGroupIdsByMaterialId(id)
            if(groupsWithThisMat.isNotEmpty()){
                val group = groupDao.getGroupById(groupsWithThisMat.first())!!
                vmAddEditMaterial.setGroup(group.name, group.id)
                oldGroupId = group.id
                oldGroupName = group.name
                oldCourseId = group.courseId
                if(group.courseId != null) {
                    val course = courseDao.getCourseById(group.courseId)!!
                    oldCourseName = course.name
                    vmAddEditMaterial.setCourse(course.name, course.id)
                }
            }
            else{
                val coursesWithThisMat = materialDao.getCourseIdsByMaterialId(id)
                if(coursesWithThisMat.isNotEmpty()){
                    val course = courseDao.getCourseById(coursesWithThisMat.first())!!
                    vmAddEditMaterial.setCourse(course.name, course.id)
                    oldCourseId = course.id
                }
            }
        }
    }

    var showTagsInfoDialog by remember { mutableStateOf(false) }

    // online
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val isAutoSync by settingsViewModel.isAutoSync.collectAsState()

    val authViewMode: AuthViewModel = hiltViewModel()
    val storageViewMode: StorageViewModel = hiltViewModel()

    // upload material
    val statusUploadMaterial by storageViewMode.statusUploadMaterial.collectAsState()
    var uploadingMaterial by remember { mutableStateOf(false) }
    var uploadingMaterialState by remember { mutableStateOf(false) }
    var jobCreateOrUpdateMaterial by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(statusUploadMaterial) {
        if (statusUploadMaterial == true && uploadingMaterial) {
            jobCreateOrUpdateMaterial!!.start()
        }
        else if (statusUploadMaterial == false && uploadingMaterial){
            snackbarHostState.showSnackbar(
                "Ошибка операции",
                withDismissAction = true
            )
            uploadingMaterial = false
        }
    }

    val statusDeleteMaterial by storageViewMode.statusDeleteMaterial.collectAsState()
    var jobUploadMaterial by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(statusDeleteMaterial) {
        if (statusDeleteMaterial == true && uploadingMaterial) {
            jobUploadMaterial!!.start()
        }
        else if (statusDeleteMaterial == false && uploadingMaterial){
            snackbarHostState.showSnackbar(
                "Ошибка операции",
                withDismissAction = true
            )
            uploadingMaterial = false
        }
    }

    BackHandler(enabled = uploadingMaterial) {}
    if (!uploadingMaterial && uploadingMaterialState &&
        (statusUploadMaterial == true || authViewMode.authState.collectAsState().value !is AuthService.AuthState.Authenticated
                 || !isAutoSync)) {
        navController.previousBackStackEntry
            ?.savedStateHandle
            ?.set("updatedMaterialId", updatedMaterialId)

        navController.popBackStack()
    }

    var toolHeightDp by remember { mutableStateOf(44.dp) }

    Scaffold(
        snackbarHost = {
            Box( modifier = Modifier.fillMaxSize()) {
                SnackbarHost(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .navigationBarsPadding()
                        .padding(top = 75.dp),
                    hostState = snackbarHostState,
                )
            }
        },
        topBar = {

            TopBar(
                title = materialTitle,
                editTitle = !(uploadingMaterial || materialId != null),
                onEditTitle = { newTitle ->
                    if (newTitle.length <= 128)
                        materialTitle = newTitle
                    else {
                        Toast.makeText(
                            context,
                            "Заголовок материала не может быть больше 128 символов!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                back = true,
                onBack = {navController.popBackStack()},
                customButtons = true,
                composeCustomButtons = { modifier ->
                    if (uploadingMaterial) {
                        CircularProgressIndicator(modifier = modifier
                            .padding(10.dp)
                            .size(24.dp)
                        )
                    } else {
                        Row (modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                modifier = Modifier
                                    .padding(horizontal = 6.dp)
                                    .size(35.dp),
                                onClick = { expanded = !expanded }) {
                                Icon(
                                    if(expanded) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "Группа/Курс")
                            }
                            VerticalDivider(modifier = Modifier
                                .width(3.dp)
                                .padding(vertical = 4.dp))
                            if(expanded){
                                IconButton(
                                    modifier = Modifier
                                        .padding(horizontal = 6.dp)
                                        .size(35.dp),
                                    onClick = { filePickerLauncher.launch("text/xml") }) {
                                    Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = "Группа/Курс")
                                }
                                IconButton(
                                    modifier = Modifier
                                        .padding(horizontal = 6.dp)
                                        .size(35.dp),
                                    onClick = { showSettingsDialog = true }) {
                                    Icon(Icons.Filled.Settings, contentDescription = "Группа/Курс")
                                }
                            }

                            IconButton(
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .size(35.dp),
                                onClick = {

                                    if (materialTitle.isBlank()) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                "Название материала не может быть пустым",
                                                withDismissAction = true
                                            )
                                        }
                                        return@IconButton
                                    }
                                    else if (materialId == null){
                                        if(!receivedAllMaterialNameList){
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    "Не удалось выполнить проверку заголовка, попробуйте повторить позже",
                                                    withDismissAction = true
                                                )
                                            }
                                            return@IconButton
                                        }
                                        else {
                                            if (materialTitle.lowercase().trim() in allMaterialNameList){
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        "Такой заголовок уже существует. Введите уникальный заголовок",
                                                        withDismissAction = true
                                                    )
                                                }
                                                return@IconButton
                                            }
                                            receivedAllMaterialNameList = false
                                        }
                                    }

                                    val escapeXmlValue = xmlValue.copy(filtrationSpecialCharactersXml(xmlValue.text))

                                    uploadingMaterial = true
                                    uploadingMaterialState = false

                                    jobCreateOrUpdateMaterial?.cancel()
                                    jobCreateOrUpdateMaterial = scope.launch(start = CoroutineStart.LAZY) {
                                        if (materialId == null) {
                                            val idNewMaterial = viewModel.addMaterial(materialTitle.trim(), escapeXmlValue.text)
                                            if (idNewMaterial != null) {
                                                uploadingMaterialState = true

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
                                                viewModel.updateMaterial(materialId.toLong(), escapeXmlValue.text)
                                            if (newId != null) {
                                                uploadingMaterialState = true

                                                // update course
                                                if(selectedCourseId != oldCourseId){
                                                    if(selectedCourseId == null){
                                                        materialDao.deleteMaterialCourseCrossRefByMaterial(
                                                            newId,
                                                            oldCourseId!!
                                                        )
                                                    }
                                                    else if (oldCourseId == null) {
                                                        val crossRef = MaterialCourseCrossRef(
                                                            newId,
                                                            selectedCourseId!!
                                                        )
                                                        materialDao.insertMaterialCourseCrossRefs(
                                                            listOf(
                                                                crossRef
                                                            )
                                                        )
                                                    }
                                                    else{
                                                        materialDao.updateCourseForMaterial(
                                                            newId,
                                                            oldCourseId!!,
                                                            selectedCourseId!!
                                                        )
                                                    }
                                                }

                                                // update group
                                                if(selectedGroupId != oldGroupId){
                                                    if(selectedGroupId == null){
                                                        materialDao.deleteMaterialGroupCrossRefByMaterial(
                                                            newId,
                                                            oldGroupId!!
                                                        )
                                                    }
                                                    else if (oldGroupId == null) {
                                                        val crossRef = MaterialGroupCrossRef(
                                                            newId,
                                                            selectedGroupId!!
                                                        )
                                                        materialDao.insertMaterialGroupCrossRefs(
                                                            listOf(
                                                                crossRef
                                                            )
                                                        )
                                                    }
                                                    else{
                                                        materialDao.updateGroupForMaterial(
                                                            newId,
                                                            oldGroupId!!,
                                                            selectedGroupId!!
                                                        )
                                                    }
                                                }

                                                updatedMaterialId = newId
                                            }
                                        }
                                        uploadingMaterial = false
                                    }


                                    // sync with storage
                                    if (authViewMode.authState.value is AuthService.AuthState.Authenticated && isAutoSync)
                                    {
                                        val au = authViewMode.authState.value as AuthService.AuthState.Authenticated
                                        if (au.accessToken != null) {
                                            if(materialId == null) {
                                                val courseName = selectedCourseName
                                                val groupName = selectedGroupName
                                                val xml = escapeXmlValue.text
                                                storageViewMode.uploadMaterial(
                                                    accessToken = au.accessToken,
                                                    uniqueFileName = materialTitle.trim(),
                                                    xml = xml,
                                                    uniqueCourseName = courseName,
                                                    uniqueGroupName = groupName
                                                )
                                            }
                                            else{
                                                val courseName = selectedCourseName
                                                val groupName = selectedGroupName
                                                val xml = escapeXmlValue.text
                                                jobUploadMaterial?.cancel()
                                                jobUploadMaterial = scope.launch(start = CoroutineStart.LAZY) {
                                                    storageViewMode.uploadMaterial(
                                                        accessToken = au.accessToken,
                                                        uniqueFileName = materialTitle.trim(),
                                                        xml = xml,
                                                        uniqueCourseName = courseName,
                                                        uniqueGroupName = groupName
                                                    )
                                                }

                                                val oldCourseName = oldCourseName
                                                val oldGroupName = oldGroupName
                                                storageViewMode.deleteMaterial(
                                                    accessToken = au.accessToken,
                                                    uniqueFileName = materialTitle.trim(),
                                                    uniqueCourseName = oldCourseName,
                                                    uniqueGroupName = oldGroupName
                                                )
                                            }
                                        }
                                    } else {
                                        jobCreateOrUpdateMaterial!!.start()
                                    }
                                }) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Сохранить")
                            }
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
                .padding(start = 3.dp, top = 0.dp, end = 3.dp, bottom = 0.dp)
                .imePadding()
        ) {
            val bg = MaterialTheme.colorScheme.outlineVariant
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(bg, shape = RoundedCornerShape(8.dp))
//                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                            .background(
                                MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
//                            .padding(8.dp)
                    ) {

                        TextField(
                            enabled = !uploadingMaterial,
                            value = xmlValue,
                            onValueChange = {

                                var text = it.text
                                if (text.takeLast(5) != "\n\n\n\n\n")
                                    text += "\n\n\n\n\n"
                                val newXmlValue = it.copy(text)

                                if (xmlValue.text != newXmlValue.text) {
                                    saveDebounceEditAction(
                                        lastText,
                                        newXmlValue.text,
                                        lastSelection,
                                        newXmlValue.selection
                                    )
                                }
                                else {
                                    if(debounceJob != null) {
                                        if (debounceJob!!.isActive) {
                                            debounceJob!!.cancel()
                                            saveDebounceEditAction(
                                                lastText,
                                                xmlValue.text,
                                                lastSelection,
                                                xmlValue.selection,
                                                0L
                                            )
                                        }
                                    }

                                    lastSelection = newXmlValue.selection
                                }

                                xmlValue = newXmlValue
                            },
                            textStyle = TextStyle(
                                color = Color.Transparent,
                                fontSize = 12.sp,
                                lineHeight = 15.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            modifier = Modifier.matchParentSize(),
                            singleLine = false,
                            maxLines = Int.MAX_VALUE,
//                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
//                            decorationBox = { innerTextField ->
//                                innerTextField()
//                            }
                        )
                        Text(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                            ,
                            text = highlightedText,
                            style = TextStyle(
                                fontSize = 12.sp,
                                lineHeight = 15.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                }
            }

            // small floating toolbar
            if(!uploadingMaterial) {
                Box(modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .background(
                                    topBarStyle(toolHeightDp, 0.dp).brush,
                                    RoundedCornerShape(28.dp)
                                )
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    RoundedCornerShape(28.dp)
                                )
                                .padding(horizontal = 4.dp)
                                .height(toolHeightDp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                val insert = "<section>\n" +
                                        "<title>Новый раздел</title>\n" +
                                        "<content>\nСодержимое...\n</content>\n" +
                                        "<example>\nПример...\n</example>\n" +
                                        "</section>\n"
                                xmlValue = insertAtCursor(xmlValue, insert)
                                saveDebounceEditAction(
                                    lastText,
                                    xmlValue.text,
                                    lastSelection,
                                    xmlValue.selection,
                                    0L
                                )

                                val offset = ("Новый раздел</title>\n" +
                                        "<content>\nСодержимое...\n</content>\n" +
                                        "<example>\nПример...\n</example>\n" +
                                        "</section>\n").length
                                val selectionSize = "Новый раздел".length
                                xmlValue = xmlValue.copy(
                                    selection = TextRange(
                                        xmlValue.selection.start - offset,
                                        xmlValue.selection.end - offset + selectionSize
                                    )
                                )
                                lastSelection = xmlValue.selection
                            },
                                modifier = Modifier.size(36.dp)) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_section),
                                    contentDescription = "Добавить параграф",
                                    modifier = Modifier.size(15.dp),
                                )
                            }

                            Box(modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .combinedClickable(
                                    onClick = {
                                        xmlValue = insertAtCursor(xmlValue, "<tex>\nФормула в формате KaTeX\n</tex>")
                                        saveDebounceEditAction(
                                            lastText,
                                            xmlValue.text,
                                            lastSelection,
                                            xmlValue.selection,
                                            0L
                                        )

                                        val offset = "Формула в формате KaTeX\n</tex>".length
                                        val selectionSize = "Формула в формате KaTeX".length
                                        xmlValue = xmlValue.copy(
                                            selection = TextRange(
                                                xmlValue.selection.start - offset,
                                                xmlValue.selection.end - offset + selectionSize
                                            )
                                        )
                                        lastSelection = xmlValue.selection
                                    },
                                    onLongClick = {
                                        xmlValue = insertAtCursor(xmlValue, "<inline-tex>Формула в формате KaTeX</inline-tex>")
                                        saveDebounceEditAction(
                                            lastText,
                                            xmlValue.text,
                                            lastSelection,
                                            xmlValue.selection,
                                            0L
                                        )

                                        val offset = "Формула в формате KaTeX</inline-tex>".length
                                        val selectionSize = "Формула в формате KaTeX".length
                                        xmlValue = xmlValue.copy(
                                            selection = TextRange(
                                                xmlValue.selection.start - offset,
                                                xmlValue.selection.end - offset + selectionSize
                                            )
                                        )
                                        lastSelection = xmlValue.selection
                                    },
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = LocalIndication.current
                                ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_tex),
                                    contentDescription = "Добавить формулу",
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Box(modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .combinedClickable(
                                    onClick = {
                                        xmlValue = insertAtCursor(xmlValue, "<code language=\"cpp\">\nКод...\n</code>")
                                        saveDebounceEditAction(
                                            lastText,
                                            xmlValue.text,
                                            lastSelection,
                                            xmlValue.selection,
                                            0L
                                        )

                                        val offset = "Код...\n</code>".length
                                        val selectionSize = "Код...".length
                                        xmlValue = xmlValue.copy(
                                            selection = TextRange(
                                                xmlValue.selection.start - offset,
                                                xmlValue.selection.end - offset + selectionSize
                                            )
                                        )
                                        lastSelection = xmlValue.selection
                                    },
                                    onLongClick = {
                                        xmlValue = insertAtCursor(xmlValue, "<inline-code language=\"cpp\">Код...</inline-code>")
                                        saveDebounceEditAction(
                                            lastText,
                                            xmlValue.text,
                                            lastSelection,
                                            xmlValue.selection,
                                            0L
                                        )

                                        val offset = "Код...</inline-code>".length
                                        val selectionSize = "Код...".length
                                        xmlValue = xmlValue.copy(
                                            selection = TextRange(
                                                xmlValue.selection.start - offset,
                                                xmlValue.selection.end - offset + selectionSize
                                            )
                                        )
                                        lastSelection = xmlValue.selection
                                    },
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = LocalIndication.current
                                ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_code),
                                    contentDescription = "Добавить код",
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(onClick = {
                                val insertText = "<table>\n" +
                                        "| № | Имя  | Фамилия |\n" +
                                        "|---|------|---------|\n" +
                                        "| 1 | Иван | Иванов  |\n" +
                                        "| 2 | Петр | Петров  |\n" +
                                        "| 3 | Рома | Романов |\n" +
                                        "</table>\n"
                                xmlValue = insertAtCursor(xmlValue, insertText)
                                saveDebounceEditAction(
                                    lastText,
                                    xmlValue.text,
                                    lastSelection,
                                    xmlValue.selection,
                                    0L
                                )
                            },
                                modifier = Modifier.size(36.dp)) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_table),
                                    contentDescription = "Добавить code",
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            IconButton(
                                enabled = undoStack.isNotEmpty(),
                                onClick = {
                                    undo()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = "Отменить",
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                enabled = redoStack.isNotEmpty(),
                                onClick = {
                                    redo()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Redo,
                                    contentDescription = "Вернуть отмену",
                                    modifier = Modifier.size(18.dp)
                                )
                            }

//                            IconButton(
//                                onClick = {
//                                    xmlValue = indentSelection(xmlValue)
//                                },
//                                modifier = Modifier.size(36.dp)
//                            ) {
//                                Icon(
//                                    Icons.AutoMirrored.Filled.FormatIndentIncrease,
//                                    contentDescription = "Отступ",
//                                    modifier = Modifier.size(18.dp)
//                                )
//                            }
//
//                            IconButton(
//                                onClick = {
//                                    xmlValue = unindentSelection(xmlValue)
//                                },
//                                modifier = Modifier.size(36.dp)
//                            ) {
//                                Icon(
//                                    Icons.AutoMirrored.Filled.FormatIndentDecrease,
//                                    contentDescription = "Убрать отступ",
//                                    modifier = Modifier.size(18.dp)
//                                )
//                            }

                            VerticalDivider(
                                modifier = Modifier.height(36.dp),
                                thickness = DividerDefaults.Thickness,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
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

    val inlineContent = mapOf(
        "sectionIcon" to InlineTextContent(
            placeholder = Placeholder(
                width = 20.sp,
                height = 20.sp,
                placeholderVerticalAlign = PlaceholderVerticalAlign.Center
            )
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_section),
                contentDescription = null,
                modifier = Modifier.size(15.dp)
            )
        },
        "texIcon" to InlineTextContent(
            placeholder = Placeholder(
                width = 20.sp,
                height = 20.sp,
                placeholderVerticalAlign = PlaceholderVerticalAlign.Center
            )
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_tex),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        "codeIcon" to InlineTextContent(
            placeholder = Placeholder(
                width = 20.sp,
                height = 20.sp,
                placeholderVerticalAlign = PlaceholderVerticalAlign.Center
            )
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_code),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        "tableIcon" to InlineTextContent(
            placeholder = Placeholder(
                width = 20.sp,
                height = 20.sp,
                placeholderVerticalAlign = PlaceholderVerticalAlign.Center
            )
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_table),
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        },
    )

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
                        append("(нажать: ")
                        appendInlineContent("sectionIcon", "icon")
                        append(") ")
                        append("- секция(параграф) материала. Количество: неограниченно")
                    }, fontSize = 14.sp, inlineContent = inlineContent)
                }
                item {
                    Text(buildAnnotatedString {
                        pushStyle(SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        append("3) <title> ")
                        pop()
                        append("(нажать: ")
                        appendInlineContent("sectionIcon", "icon")
                        append(") ")
                        append("- заголовок материала или секции. Количество: по одному для каждого")
                    }, fontSize = 14.sp, inlineContent = inlineContent)
                }
                item {
                    Text(buildAnnotatedString {
                        pushStyle(SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        append("4) <content> ")
                        pop()
                        append("(нажать: ")
                        appendInlineContent("sectionIcon", "icon")
                        append(") ")
                        append("- основное содержимое секции, можно встраивать в текст inline тэги. Количество: неограниченно")
                    }, fontSize = 14.sp, inlineContent = inlineContent)
                }
                item {
                    Text(buildAnnotatedString {
                        pushStyle(SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        append("5) <example> ")
                        pop()
                        append("(нажать: ")
                        appendInlineContent("sectionIcon", "icon")
                        append(") ")
                        append("- пример в секции, можно встраивать в текст inline тэги. Количество: неограниченно")
                    }, fontSize = 14.sp, inlineContent = inlineContent)
                }
                item {
                    Text(buildAnnotatedString {
                        pushStyle(SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        append("6) <code> ")
                        pop()
                        append("(нажать: ")
                        appendInlineContent("codeIcon", "icon")
                        append(") ")
                        append(
                            "- блок с кодом в основном содержимом или примере, имеет необязательный атрибут language" +
                                    " для указания языка программирования. Код пишется от начала строки! Количество: неограниченно"
                        )
                    }, fontSize = 14.sp, inlineContent = inlineContent)
                }
                item {
                    Text(buildAnnotatedString {
                        pushStyle(SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        append("7) <inline-code> ")
                        pop()
                        append("(удерживать: ")
                        appendInlineContent("codeIcon", "icon")
                        append(") ")
                        append(
                            "- код в тексте основного содержимого или примера, имеет необязательный атрибут language" +
                                    "для указания языка программирования. Количество: неограниченно"
                        )
                    }, fontSize = 14.sp, inlineContent = inlineContent)
                }
                item {
                    Text(buildAnnotatedString {
                        pushStyle(SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        append("8) <tex> ")
                        pop()
                        append("(нажать: ")
                        appendInlineContent("texIcon", "icon")
                        append(") ")
                        append("- формула в формате KaTeX в основном содержимом или примере. Количество: неограниченно")
                    }, fontSize = 14.sp, inlineContent = inlineContent)
                }
                item {
                    Text(buildAnnotatedString {
                        pushStyle(SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        append("9) <inline-tex> ")
                        pop()
                        append("(удерживать: ")
                        appendInlineContent("texIcon", "icon")
                        append(") ")
                        append("- инлайн формула в формате KaTeX в основном содержимом или примере. Количество: неограниченно")
                    }, fontSize = 14.sp, inlineContent = inlineContent)
                }
                item {
                    Text(buildAnnotatedString {
                        pushStyle(SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        append("10) <table> ")
                        pop()
                        append("(нажать: ")
                        appendInlineContent("tableIcon", "icon")
                        append(") ")
                        append("- текстовый формат таблицы, Markdown-подобный синтаксис. Количество: неограниченно")
                    }, fontSize = 14.sp, inlineContent = inlineContent)
                }
                item {
                    Text(buildAnnotatedString {
                        pushStyle(SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        append("11) <br/> ")
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
            groups = db.groupDao().getGroupsByCourseId(viewModel.selectedCourseId.value)
            if (viewModel.selectedGroupId.value !in groups.map { it.id })
                viewModel.setGroup(null, null)
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
//                        IconButton(onClick = { courseInputMode = true }) {
//                            Icon(Icons.Default.Add, contentDescription = "Добавить курс")
//                        }
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
//                        IconButton(onClick = { groupInputMode = true }) {
//                            Icon(Icons.Default.Add, contentDescription = "Добавить группу")
//                        }
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


fun filtrationSpecialCharactersXml(input: String): String {
    // Основные теги, в которых нужно обрабатывать содержимое (с атрибутами)
    val outerTagRegex = Regex(
        "<(content|example)(\\s+[^>]*)?>(.*?)</\\1>",
        RegexOption.DOT_MATCHES_ALL
    )

    // Теги, внутри которых ничего не меняем
    val protectedTagRegex = Regex(
        "<(code|tex|inline-code|inline-tex)(\\s+[^>]*)?>.*?</\\1>",
        RegexOption.DOT_MATCHES_ALL
    )

    // Универсальный регекс для любых XML-тегов
    val anyTagRegex = Regex("<[^>]+>")

    // Замена служебных символов XML
    fun escapeXml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    // Обработка текста вне <code> и т.п., но с сохранением всех тегов
    fun processOutsideProtected(text: String): String {
        val sb = StringBuilder()
        var lastIndex = 0

        for (tagMatch in anyTagRegex.findAll(text)) {
            val before = text.substring(lastIndex, tagMatch.range.first)
            sb.append(escapeXml(before)) // экранируем только чистый текст
            sb.append(tagMatch.value)    // теги не трогаем
            lastIndex = tagMatch.range.last + 1
        }

        // остаток после последнего тега
        sb.append(escapeXml(text.substring(lastIndex)))
        return sb.toString()
    }

    // Обработка содержимого внутри content/example
    fun processContent(text: String): String {
        val sb = StringBuilder()
        var lastIndex = 0

        // Пропускаем участки, находящиеся внутри защищённых тегов
        for (protectedMatch in protectedTagRegex.findAll(text)) {
            // участок ДО защищённого тега
            val before = text.substring(lastIndex, protectedMatch.range.first)
            sb.append(processOutsideProtected(before))
            // вставляем защищённый тег без изменений
            sb.append(protectedMatch.value)
            lastIndex = protectedMatch.range.last + 1
        }

        // остаток после последнего защищённого участка
        sb.append(processOutsideProtected(text.substring(lastIndex)))
        return sb.toString()
    }

    val result = StringBuilder()
    var lastIndex = 0

    // Ищем все <content ...>...</content> и <example ...>...</example>
    for (outerMatch in outerTagRegex.findAll(input)) {
        val before = input.substring(lastIndex, outerMatch.range.first)
        result.append(before) // часть вне целевых тегов — без изменений

        val tagName = outerMatch.groupValues[1]
        val tagAttrs = outerMatch.groupValues[2] // может быть пустым
        val innerText = outerMatch.groupValues[3]

        val processed = processContent(innerText)

        result.append("<$tagName${tagAttrs}>")
        result.append(processed)
        result.append("</$tagName>")

        lastIndex = outerMatch.range.last + 1
    }

    // Добавляем хвост (если после последнего блока что-то осталось)
    result.append(input.substring(lastIndex))

    return result.toString()
}

fun resetFiltrationSpecialCharactersXml(input: String): String {
    // Основные теги, в которых нужно обрабатывать содержимое (с атрибутами)
    val outerTagRegex = Regex(
        "<(content|example)(\\s+[^>]*)?>(.*?)</\\1>",
        RegexOption.DOT_MATCHES_ALL
    )

    // Теги, внутри которых ничего не меняем
    val protectedTagRegex = Regex(
        "<(code|tex|inline-code|inline-tex)(\\s+[^>]*)?>.*?</\\1>",
        RegexOption.DOT_MATCHES_ALL
    )

    // Универсальный регекс для любых XML-тегов
    val anyTagRegex = Regex("<[^>]+>")

    // Замена служебных символов XML
    fun escapeXml(text: String): String = text
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;","'")

    // Обработка текста вне <code> и т.п., но с сохранением всех тегов
    fun processOutsideProtected(text: String): String {
        val sb = StringBuilder()
        var lastIndex = 0

        for (tagMatch in anyTagRegex.findAll(text)) {
            val before = text.substring(lastIndex, tagMatch.range.first)
            sb.append(escapeXml(before)) // экранируем только чистый текст
            sb.append(tagMatch.value)    // теги не трогаем
            lastIndex = tagMatch.range.last + 1
        }

        // остаток после последнего тега
        sb.append(escapeXml(text.substring(lastIndex)))
        return sb.toString()
    }

    // Обработка содержимого внутри content/example
    fun processContent(text: String): String {
        val sb = StringBuilder()
        var lastIndex = 0

        // Пропускаем участки, находящиеся внутри защищённых тегов
        for (protectedMatch in protectedTagRegex.findAll(text)) {
            // участок ДО защищённого тега
            val before = text.substring(lastIndex, protectedMatch.range.first)
            sb.append(processOutsideProtected(before))
            // вставляем защищённый тег без изменений
            sb.append(protectedMatch.value)
            lastIndex = protectedMatch.range.last + 1
        }

        // остаток после последнего защищённого участка
        sb.append(processOutsideProtected(text.substring(lastIndex)))
        return sb.toString()
    }

    val result = StringBuilder()
    var lastIndex = 0

    // Ищем все <content ...>...</content> и <example ...>...</example>
    for (outerMatch in outerTagRegex.findAll(input)) {
        val before = input.substring(lastIndex, outerMatch.range.first)
        result.append(before) // часть вне целевых тегов — без изменений

        val tagName = outerMatch.groupValues[1]
        val tagAttrs = outerMatch.groupValues[2] // может быть пустым
        val innerText = outerMatch.groupValues[3]

        val processed = processContent(innerText)

        result.append("<$tagName${tagAttrs}>")
        result.append(processed)
        result.append("</$tagName>")

        lastIndex = outerMatch.range.last + 1
    }

    // Добавляем хвост (если после последнего блока что-то осталось)
    result.append(input.substring(lastIndex))

    return result.toString()
}

fun collectTagEvents(input: String): List<Pair<MatchResult, Boolean>> {
    // returns list of (position, isOpen, tagName) ordered by position
    val events = mutableListOf<Pair<MatchResult, Boolean>>()
    val openRe = Regex("""<(material|section|title|content|example|code|inline-code|table|inline-tex|tex)(\b[^\r\n>]*)?>""",
        setOf(RegexOption.IGNORE_CASE))
    val closeRe = Regex("""</(material|section|title|content|example|code|inline-code|table|inline-tex|tex)>""",
        setOf(RegexOption.IGNORE_CASE))
    openRe.findAll(input).forEach { events.add(Pair(it, true)) }
    closeRe.findAll(input).forEach { events.add(Pair(it, false)) }
    return events.sortedBy { it.first.range.first }
}

fun getIncorrectTags(text: String): List<Triple<IntRange, String, Boolean>> {
    val stackName = ArrayDeque<String>()
    val stack = ArrayDeque<Triple<IntRange, String, Boolean>>()
    val incor = mutableListOf<Triple<IntRange, String, Boolean>>()
    for ((matchResult, isOpen) in collectTagEvents(text)) {
        if (isOpen) {
            stackName.addLast(matchResult.groupValues[1])
            stack.addLast(Triple(matchResult.range, matchResult.value, true))
        } else {
            if (stack.isNotEmpty() && stackName.last() == matchResult.groupValues[1]) {
                stackName.removeLast()
                stack.removeLast()
            } else {
                while(stack.isNotEmpty()) {
                    if (matchResult.groupValues[1] !in stackName) {
                        incor.add(Triple(matchResult.range, matchResult.value, true))
                        break
                    }
                    else {
                        incor.add(stack.last())
                        stackName.removeLast()
                        stack.removeLast()
                        if (stack.isNotEmpty() && stackName.last() == matchResult.groupValues[1]) {
                            stackName.removeLast()
                            stack.removeLast()
                            break
                        }
                    }
                }
            }
        }
    }
    return incor // opened but not closed
}

fun highlightXML(input: String, darkMode: Boolean = false): AnnotatedString {
    val builder = AnnotatedString.Builder()

    // Цвета для разных элементов
    val defaultTagColor = if(darkMode) Color(0xFF4FC3F7) else Color(0xFF0017DA)
    val attrColor = if(darkMode) Color(0xFF81C784) else Color(0xFF077709)
    val valueColor = if(darkMode) Color(0xFFFFB74D) else Color(0xFFC97802)
    val errorBg = if(darkMode) Color(0xFFA64249) else Color(0xFFFFCDD2)

    var lastIndex = 0
    val tagsRegex = Regex("""</?(material|section|title|content|example|code|inline-code|table|inline-tex|tex|br)(\b[^\r\n>]*)?>""",
        setOf(RegexOption.IGNORE_CASE))

    val matches = mutableListOf<MatchResult>()
    matches += tagsRegex.findAll(input)
    matches.sortBy { it.range.first }
    var matchesList = matches.map { Triple(it.range, it.value, false) }.toMutableList()
    matchesList += getIncorrectTags(input)
    matchesList.sortBy { it.first.first }
    val grouped = matchesList.groupBy { it.first.first }
    matchesList = grouped.map { (_, group) ->
        group.find { it.third } ?: group.first()
    }.toMutableList()

    var prevTag = ""
    for (match in matchesList) {
        val range = match.first
        if (lastIndex < range.first) {

            val spanStyle = if(Regex("""<title(\b[^\r\n>]*)?>""").matches(prevTag))
                SpanStyle(
                    color = if(darkMode) Color(0xFFd6d8ff) else Color(0xFF555aaf),
//                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Bold
                )
            else if (Regex("""<(code|inline-code)(\b[^\r\n>]*)?>""").matches(prevTag))
                SpanStyle(
                    color = if(darkMode) Color(0xFFdbadff) else Color(0xFFA12CFF),
//                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Bold
                )
            else if (Regex("""<(tex|inline-tex)(\b[^\r\n>]*)?>""").matches(prevTag))
                SpanStyle(
                    color = if(darkMode) Color(0xFFfdb581) else Color(0xFFc26c2e),
//                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Bold
                )
            else {
                SpanStyle(color = Color.Unspecified)
            }

            builder.withStyle(spanStyle) {
                builder.append(input.substring(lastIndex, range.first))
            }
        }
        prevTag = match.second

        val customTagColor = if(Regex("""</?(content|example)(\b[^\r\n>]*)?>""").matches(match.second))
            if(darkMode) Color(0xFF4ff939) else Color(0xFF10A600)
        else if(Regex("""</?section(\b[^\r\n>]*)?>""").matches(match.second))
            if(darkMode) Color(0xFF4ff939) else Color(0xFF10A600)
        else if(Regex("""</?title(\b[^\r\n>]*)?>""").matches(match.second))
            if(darkMode) Color(0xFF4ff939) else Color(0xFF10A600)
        else
            defaultTagColor


        val indexSpace = match.second.indexOf(' ')
        if(indexSpace != -1) {
            val tagStartText = match.second.take(indexSpace)
            builder.withStyle(SpanStyle(
                color = customTagColor,
                background = if (match.third) errorBg else Color.Unspecified,
                fontWeight = FontWeight.ExtraBold
            )) {
                append(tagStartText)
            }
            val indexEqual = match.second.indexOf('=', indexSpace)
            if(indexEqual != -1){
                val attrText = match.second.substring(indexSpace, indexEqual + 1)
                builder.withStyle(SpanStyle(
                    color = attrColor,
                    background = if (match.third) errorBg else Color.Unspecified,
                    fontWeight = FontWeight.ExtraBold
                )) {
                    append(attrText)
                }
                val indexCloseTag = match.second.indexOf('>', indexSpace)
                if(indexCloseTag != -1){
                    val valueAttrText = match.second.substring(indexEqual + 1, indexCloseTag)
                    builder.withStyle(SpanStyle(
                        color = valueColor,
                        background = if (match.third) errorBg else Color.Unspecified,
                        fontWeight = FontWeight.ExtraBold
                    )) {
                        append(valueAttrText)
                    }

                    val closeTagText = match.second.substring(indexCloseTag)
                    builder.withStyle(SpanStyle(
                        color = customTagColor,
                        background = if (match.third) errorBg else Color.Unspecified,
                        fontWeight = FontWeight.ExtraBold
                    )) {
                        append(closeTagText)
                    }
                }
            }
        }
        else{
            builder.withStyle(SpanStyle(
                color = customTagColor,
                background = if (match.third) errorBg else Color.Unspecified,
                fontWeight = FontWeight.ExtraBold
            )) {
                append(match.second)
            }
        }

        lastIndex = range.last + 1
    }

    if (lastIndex < input.length) {
        builder.append(input.substring(lastIndex))
    }

    return builder.toAnnotatedString()
}