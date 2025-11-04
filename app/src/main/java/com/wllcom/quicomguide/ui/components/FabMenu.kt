package com.wllcom.quicomguide.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wllcom.quicomguide.ui.styles.topBarStyle

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

    val topBarStyle = topBarStyle(47.dp, 0.dp)
    val backgroundColor = topBarStyle.brush
    val styleText = topBarStyle.text

    Box(modifier = modifier, contentAlignment = Alignment.BottomEnd) {

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(
                bottom = 53.dp, // 45 (height) + 8 (spacing)
                end = 0.dp
            )
        ) {
            if (expanded) {
                ExtendedFloatingActionButton(
                    containerColor = Color.Transparent,
                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                    onClick = { onAction("material"); expanded = false },
                    text = { Text("Материал", style = styleText) },
                    icon = {
                        Icon(
                            Icons.AutoMirrored.Filled.Article,
                            contentDescription = "Материал",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier
                        .height(45.dp)
                        .background(backgroundColor, RoundedCornerShape(28.dp))
                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), RoundedCornerShape(28.dp))
                )
                ExtendedFloatingActionButton(
                    containerColor = Color.Transparent,
                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                    onClick = { onAction("group"); expanded = false },
                    text = { Text("Группа", style = styleText) },
                    icon = {
                        Icon(
                            Icons.Default.CollectionsBookmark,
                            contentDescription = "Группа",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier
                        .height(45.dp)
                        .background(backgroundColor, RoundedCornerShape(28.dp))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            RoundedCornerShape(28.dp)
                        )
                )
                ExtendedFloatingActionButton(
                    containerColor = Color.Transparent,
                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                    onClick = {
                        onAction("course"); expanded = false
                    },
                    text = { Text("Курс", style = styleText) },
                    icon = {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = "Курс",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier
                        .height(45.dp)
                        .background(backgroundColor, RoundedCornerShape(28.dp))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            RoundedCornerShape(28.dp)
                        )
                )
            }
        }

        FloatingActionButton(
            containerColor = Color.Transparent,
            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
            onClick = { expanded = !expanded },
            modifier = Modifier
                .size(45.dp)
                .background(backgroundColor, RoundedCornerShape(28.dp))
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), RoundedCornerShape(28.dp))
        ) {
            Text(mainFabText)
        }
    }
}