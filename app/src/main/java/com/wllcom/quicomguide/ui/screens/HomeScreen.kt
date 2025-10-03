package com.wllcom.quicomguide.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.wllcom.quicomguide.ui.components.AnimatedSearchField
import com.wllcom.quicomguide.ui.components.FabMenu

@Composable
fun HomeScreen(navController: NavController? = null, contentPadding: PaddingValues) {
    var query by remember { mutableStateOf("") }

    // демонстрационные элементы — ленивый список
    val recommended = remember {
        (1..20).map { "Рекомендованный материал #$it" }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .padding(contentPadding)) {
        Scaffold(
            topBar = {
                AnimatedSearchField(
                    query = query,
                    onQueryChange = { query = it },
                    modifier = Modifier.padding(8.dp, 12.dp, 8.dp, 6.dp)
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(0.dp, padding.calculateTopPadding(), 0.dp, 0.dp)
                    .padding(horizontal = 16.dp, vertical = 0.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Рекомендуемые материалы", modifier = Modifier.padding(bottom = 8.dp))
                }
                items(recommended) { item ->
                    Text(
                        item, modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                }
            }
        }

        FabMenu(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) { action ->
            when (action) {
                "material" -> {
                    navController?.navigate("library") {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }

                "group" -> {
                    navController?.navigate("library") {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }

                "course" -> {
                    navController?.navigate("library") {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun PreviewHomeScreen() {
    HomeScreen(rememberNavController(), PaddingValues())
}