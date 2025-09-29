package com.wllcom.quicomguide.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialDetailScreen(materialId: String?, navController: NavController, contentPadding: PaddingValues) {
    // моковые темы (динамически могут приходить из БД)
    val topics = remember {
        listOf(
            Pair("t1", "Введение"),
            Pair("t2", "Синтаксис"),
            Pair("t3", "Практика"),
            Pair("t4", "Упражнения"),
            Pair("t5", "Частые ошибки")
        )
    }
    var selectedTopic by remember { mutableStateOf(topics.first().first) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Материал: ${materialId ?: ""}") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding) // учитываем глобальные паддинги (в т.ч. от BottomBar)
//                .padding(start = 8.dp, end = 8.dp)
        ) {
            // NavigationRail слева
            NavigationRail {
                topics.forEach { (id, title) ->
                    NavigationRailItem(
                        icon = { },
                        label = { Text(title) },
                        selected = selectedTopic == id,
                        onClick = { selectedTopic = id }
                    )
                }
            }

            // основной прокручиваемый контент (LazyColumn)
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp)
            ) {
                item {
                    Text("Тема: ${topics.first { it.first == selectedTopic }.second}", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                items(8) { idx ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Секция ${idx + 1}", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Описание секции — здесь будет XML-контент материала, код и текст. Позже подключим подсветку кода.")
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}