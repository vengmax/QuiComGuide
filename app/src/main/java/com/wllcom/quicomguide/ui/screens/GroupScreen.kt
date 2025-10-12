package com.wllcom.quicomguide.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.wllcom.quicomguide.data.local.AppDatabase
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

    val materials by dao.getAllMaterialsFlow().collectAsState(initial = emptyList())

    val groupMaterials by dao.getMaterialsByGroupIdFlow(groupId!!.toLong())
        .collectAsState(initial = emptyList())
    val filtered = remember(groupMaterials, query) {
        if (query.isBlank()) groupMaterials
        else groupMaterials.filter { mat ->
            mat.title.contains(query, ignoreCase = true) ||
                    (mat.contentFts?.contains(query, ignoreCase = true) ?: false) ||
                    mat.xmlRaw.contains(query, ignoreCase = true)
        }
    }

    // Scaffold с TopAppBar (Back + Edit)
    Scaffold(
        topBar = {
            TopBarWithSearch(
                title = { Text(text = "Группа: ${groupId ?: ""}") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { /* edit group */ }) {
                        Icon(Icons.Default.Edit, contentDescription = "Редактировать")
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
                .padding(16.dp)
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filtered) { mat ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
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
            }
        }
    }
}

private fun previewFromXml(xmlRaw: String): String {
    val text = xmlRaw.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
    return if (text.length <= 200) text else text.substring(0, 200) + "..."
}