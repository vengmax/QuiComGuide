package com.wllcom.quicomguide.ui.screens

import android.webkit.WebView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.wllcom.quicomguide.data.local.AppDatabase
import com.wllcom.quicomguide.data.local.entities.MaterialEntity
import com.wllcom.quicomguide.data.parser.ParsedMaterial
import com.wllcom.quicomguide.data.parser.XmlMaterialParser
import com.wllcom.quicomguide.ui.components.HighlightedWebView
import com.wllcom.quicomguide.ui.components.TopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialDetailScreen(
    systemPadding: PaddingValues,
    materialId: String?,
    navController: NavController,
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

    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
//                .padding(padding)
        ) {
            when {
                parsed == null -> Text("Загрузка или материал не найден")
                else -> HighlightedWebView(
                    materialTitle = material!!.title,
                    parsedMaterial = parsed,
                    supportZoom = true,
                    fontSize = 12,
                    sharedWebView = sharedWebView,
                )
            }
        }
        TopBar(
            title = "Материал",
            back = true,
            onBack = { navController.popBackStack() },
            customButtons = true,
            composeCustomButtons = { modifier ->
                Box(contentAlignment = Alignment.Center,modifier = modifier
                    .width(44.dp)
                    .clickable { navController.navigate("editMaterial/${material?.id ?: 0}") })
                {
                    Icon(Icons.Default.Edit, contentDescription = "Редактировать")
                }
            },
        )
    }
}

