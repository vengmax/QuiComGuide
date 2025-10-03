package com.wllcom.quicomguide.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.wllcom.quicomguide.data.local.AppDatabase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialDetailScreen(
    materialId: String?,
    navController: NavController,
    contentPadding: androidx.compose.foundation.layout.PaddingValues
) {
    val context = LocalContext.current
    val dao = AppDatabase.getInstance(context).materialDao()

    var material by remember {
        mutableStateOf<com.wllcom.quicomguide.data.model.MaterialEntity?>(
            null
        )
    }

    LaunchedEffect(materialId) {
        materialId?.toLongOrNull()?.let { id ->
            try {
                material = dao.getById(id)
            } catch (e: Exception) {
                material = null
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = material?.title ?: "Материал") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate("editMaterial/${material?.id ?: 0}") }) {
                        Icon(Icons.Default.Edit, contentDescription = "Редактировать")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(16.dp)
        ) {
            val scrollState = rememberScrollState()
            Column(modifier = Modifier.verticalScroll(scrollState)) {
                Text(
                    material?.xmlContent ?: "Материал не найден",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(24.dp))
                // possible sections / additional info...
            }
        }
    }
}