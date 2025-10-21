package com.wllcom.quicomguide.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.wllcom.quicomguide.data.local.AppDatabase
import com.wllcom.quicomguide.ui.components.MaterialCard
import com.wllcom.quicomguide.ui.components.TopBarWithSearch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupScreen(
    navController: NavController,
    groupId: String?,
    contentPadding: PaddingValues
) {
    val context = LocalContext.current
    val dao = AppDatabase.getInstance(context).materialDao()
    var query by remember { mutableStateOf("") }

    var isEditMode by remember { mutableStateOf(false) }

    val groupMaterials by dao.getMaterialsByGroupIdFlow(groupId!!.toLong())
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val filtered = remember(groupMaterials, query) {
        if (query.isBlank()) groupMaterials
        else groupMaterials.filter { mat ->
            mat.title.contains(query, ignoreCase = true) ||
                    (mat.contentFts?.contains(query, ignoreCase = true) ?: false) ||
                    mat.xmlRaw.contains(query, ignoreCase = true)
        }
    }

    // preview xml
    val previewsById = remember(groupMaterials) {
        groupMaterials.associate { it.id to previewFromXml(it.xmlRaw) }
    }

    // Scaffold с TopAppBar (Back + Edit)
    Scaffold(
        topBar = {
            TopBarWithSearch(
                title = { if (!isEditMode) Text(text = "Группа: $groupId") else Text("Редактирование") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
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
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(8.dp)
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filtered) { mat ->
                    val preview = previewsById[mat.id] ?: ""
                    MaterialCard(
                        title = mat.title,
                        text = preview,
                        editMode = isEditMode,
                        modifier = Modifier
                            .height(105.dp)
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        onClick = { navController.navigate("material/${mat.id}") },
                        onLongClick = { isEditMode = !isEditMode },
                        onDeleteClick = {}
                    )
                }
            }
        }
    }
}

private fun previewFromXml(xmlRaw: String): String {
    val text = xmlRaw.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
    return if (text.length <= 200) text else text.substring(0, 200) + "..."
}