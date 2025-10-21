package com.wllcom.quicomguide.ui.screens

import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.wllcom.quicomguide.data.local.AppDatabase
import com.wllcom.quicomguide.data.local.entities.MaterialEntity
import com.wllcom.quicomguide.data.parser.ParsedMaterial
import com.wllcom.quicomguide.data.parser.XmlMaterialParser
import com.wllcom.quicomguide.ui.components.HighlightedWebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialDetailScreen(
    materialId: String?,
    navController: NavController,
    contentPadding: PaddingValues,
    sharedWebView: WebView
) {
    val context = LocalContext.current
    val dao = AppDatabase.getInstance(context).materialDao()
    var currentMaterialId = materialId?.toLongOrNull()

    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    val updatedMaterialId = savedStateHandle?.getLiveData<Long?>("updatedMaterialId")
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        updatedMaterialId?.observe(lifecycleOwner) { id ->
            currentMaterialId = id
        }
    }

    val loaded by produceState<Pair<MaterialEntity?, ParsedMaterial?>>(
        initialValue = null to null,
        currentMaterialId
    ) {
        var entity: MaterialEntity?
        var parsed: ParsedMaterial?
        val id = currentMaterialId ?: run { value = null to null; return@produceState }
        try {
            entity = dao.getMaterialById(id)
            val xml = entity?.xmlRaw ?: run { value = null to null; return@produceState }
            try {
                parsed = withContext(Dispatchers.Default) {
                    XmlMaterialParser.parse(xml, false)
                }
                value = entity to parsed
            } catch (e: Exception) {
                value = null to null
            }
        } catch (e: Exception) {
            value = null to null
        }

    }
    val material = loaded.first
    val parsed = loaded.second

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
                .padding(padding)
        ) {
            when {
                parsed == null -> Text("Загрузка или материал не найден")
                else -> HighlightedWebView(
                    parsedMaterial = parsed,
                    supportZoom = true,
                    fontSize = 12,
                    sharedWebView = sharedWebView,
                )
            }
        }
    }
}

