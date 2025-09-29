package com.wllcom.quicomguide.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import com.wllcom.quicomguide.ui.components.FabMenu

@Composable
fun LibraryScreen(navController: NavController, contentPadding: androidx.compose.foundation.layout.PaddingValues) {
    var query by remember { mutableStateOf("") }
    val allMaterials = remember {
        listOf(
            Pair("m1", "Kotlin: основное"),
            Pair("m2", "Jetpack Compose: основы"),
            Pair("m3", "Алгоритмы: структуры данных"),
            Pair("m4", "Android: архитектура"),
            Pair("m5", "SQL и Room")
        )
    }

    val filtered = remember(query) {
        if (query.isBlank()) allMaterials
        else allMaterials.filter { it.second.contains(query, ignoreCase = true) }
    }

    var fabExpanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier
        .fillMaxSize()
        .padding(contentPadding)
    ) {
        LazyColumn(modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Поиск по материалам") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("Материалы", modifier = Modifier.padding(bottom = 8.dp))
            }
            items(filtered) { (id, title) ->
                Card(modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clickable {
                        navController.navigate("material/$id")
                        // при навигации фокус на BottomBar снимется, т.к. меняется currentRoute
                        fabExpanded = false
                    }
                ) {
                    Row(modifier = Modifier.padding(12.dp)) {
                        Text(title)
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }

        FabMenu(
            modifier = Modifier.fillMaxSize(),
        ) { action ->
            when (action) {
                "material" -> {
                    // пробросим навигацию на экран добавления материала (позже)
                }
                "group" -> { /* добавить группу */ }
                "course" -> { /* добавить курс */ }
            }
        }
    }
}