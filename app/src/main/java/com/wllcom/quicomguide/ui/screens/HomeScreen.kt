package com.wllcom.quicomguide.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.wllcom.quicomguide.ui.components.AnimatedSearchField
import com.wllcom.quicomguide.ui.viewmodel.MaterialsViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.saveable.rememberSaveable

@SuppressLint("ViewModelConstructorInComposable")
@Preview
@Composable
fun PreviewHomeScreen() {
    HomeScreen(rememberNavController(), viewModel(), PaddingValues())
}

@OptIn(FlowPreview::class)
@Composable
fun HomeScreen(
    navController: NavController? = null,
    viewModel: MaterialsViewModel,
    contentPadding: PaddingValues
) {
    var queryState by rememberSaveable { mutableStateOf("") }
    val viewModelIsReady by viewModel.isReadyFlow.collectAsState(initial = false)

    Scaffold(
        modifier = Modifier.padding(contentPadding),
        topBar = {
            AnimatedSearchField(
                query = queryState,
                onQueryChange = { queryState = it },
            )
        }
    ) { padding ->

        // loading widget
        if (!viewModelIsReady) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val resultsState by viewModel.results

        // search
        LaunchedEffect(viewModel) {
            snapshotFlow { queryState }
                .drop(1)
                .map { it.trim() }
                .distinctUntilChanged()
                .debounce(700L)
                .collectLatest { q ->
                    viewModel.search(q)
                }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(0.dp, padding.calculateTopPadding(), 0.dp, 0.dp)
                .padding(horizontal = 16.dp, vertical = 0.dp)
        ) {

            item {
                Spacer(modifier = Modifier.height(12.dp))
                if (resultsState.conciseAnswer == null) {
                    Text("Рекомендуемые материалы", modifier = Modifier.padding(bottom = 8.dp))
                } else {

                    Card(modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Краткий ответ:", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(resultsState.conciseAnswer!!)
                        }
                    }
                    Text("Материалы", modifier = Modifier.padding(vertical = 8.dp))
                }
            }
            items(resultsState.topMaterials) { m ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(m.materialTitle, style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(m.snippet.text, style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { navController?.navigate("material/${m.materialId}") }) {
                            Text("Открыть")
                        }
                    }
                }
            }
        }
    }
}


