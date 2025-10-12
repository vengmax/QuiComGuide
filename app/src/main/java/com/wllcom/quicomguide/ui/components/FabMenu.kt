package com.wllcom.quicomguide.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wllcom.quicomguide.data.local.AppDatabase
import com.wllcom.quicomguide.data.local.entities.CourseEntity
import com.wllcom.quicomguide.data.local.entities.MaterialGroupEntity
import kotlinx.coroutines.launch

@Preview
@Composable
fun PreviewFabMenu() {
    FabMenu(onAction = { action ->
        when (action) {
            "material" -> {}
            "group" -> {}
            "course" -> {}
        }
    })
}

@Composable
fun FabMenu(
    modifier: Modifier = Modifier,
    mainFabText: String = "+",
    onAction: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val db = AppDatabase.getInstance(ctx)
    val groupDao = db.groupDao()
    val courseDao = db.courseDao()
    val scope = rememberCoroutineScope()

    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showCreateCourseDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    if (expanded) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    expanded = false
                }
        )
    }

    if (expanded) {
        BackHandler {
            expanded = false
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.BottomEnd) {

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(
                bottom = 53.dp, // 45 (height) + 8 (spacing)
                end = 0.dp
            )
        ) {
            AnimatedVisibility(visible = expanded, enter = fadeIn(), exit = fadeOut()) {
                ExtendedFloatingActionButton(
                    onClick = { onAction("material"); expanded = false },
                    text = { Text("Материал") },
                    icon = { Icon(Icons.Default.Create, contentDescription = "Материал") },
                    modifier = Modifier.height(45.dp)
                )
            }
            AnimatedVisibility(visible = expanded, enter = fadeIn(), exit = fadeOut()) {
                ExtendedFloatingActionButton(
                    onClick = { onAction("group"); showCreateGroupDialog = true; expanded = false },
                    text = { Text("Группа") },
                    icon = { Icon(Icons.Default.Menu, contentDescription = "Группа") },
                    modifier = Modifier.height(45.dp)
                )
            }
            AnimatedVisibility(visible = expanded, enter = fadeIn(), exit = fadeOut()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        onAction("course"); showCreateCourseDialog = true; expanded = false
                    },
                    text = { Text("Курс") },
                    icon = { Icon(Icons.Default.Star, contentDescription = "Курс") },
                    modifier = Modifier.height(45.dp)
                )
            }
        }

        FloatingActionButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.size(45.dp)
        ) {
            Text(mainFabText)
        }
    }

    if (showCreateGroupDialog) {
        AlertDialog(
            onDismissRequest = { showCreateGroupDialog = false; newName = "" },
            confirmButton = {
                TextButton(onClick = {
                    val name = newName.trim()
                    if (name.isNotEmpty()) {
                        scope.launch {
                            groupDao.insert(MaterialGroupEntity(name = name))
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
                            courseDao.insert(CourseEntity(name = name))
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