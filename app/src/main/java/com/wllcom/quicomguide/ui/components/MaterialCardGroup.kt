package com.wllcom.quicomguide.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.wllcom.quicomguide.data.local.entities.MaterialEntity
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun MaterialCardGroup(
    groupName: String,
    listMaterials: List<MaterialEntity>,
    editMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDeleteClick: () -> Unit,
    cardContent: @Composable (Long, String, String) -> Unit
) {
    // animation
    val infiniteTransition = rememberInfiniteTransition()
    val progress by if (editMode) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 200,
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Restart
            )
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    val maxAngleDeg = 0.5f
    val angleDeg = if (editMode) (sin(progress * 2f * PI).toFloat() * maxAngleDeg) else 0f

    val titleShape = RoundedCornerShape(8.dp)
    val haptic = LocalHapticFeedback.current

    val previewsById = remember(listMaterials) {
        listMaterials.associate { it.id to previewFromXml(it.xmlRaw) }
    }

    var enabledDeleteMode by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        Box {
            Text(
                text = "Группа: $groupName",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(titleShape)
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = {
                            onLongClick()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    )
                    .graphicsLayer {
                        rotationZ = angleDeg
                    }
                    .background(Color.DarkGray.copy(alpha = 0.3f))
                    .padding(8.dp)

            )
            if (editMode) {
                IconButton(
                    onClick = { enabledDeleteMode = true },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 2.dp)
                        .size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Удалить",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow {
            itemsIndexed(listMaterials) { _, mat ->

                val preview = previewsById[mat.id] ?: ""
                cardContent(mat.id, mat.title, preview)
            }
        }
    }

    if (enabledDeleteMode) {
        AlertDialog(
            onDismissRequest = { enabledDeleteMode = false },
            title = { Text("Удалить группу?") },
            text = { Text("Все матриалы в этой группе будут удалены. Это действие нельзя будет отменить.") },
            confirmButton = {
                TextButton(onClick = { onDeleteClick(); enabledDeleteMode = false }) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { enabledDeleteMode = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

private fun previewFromXml(xmlRaw: String): String {
    val text = xmlRaw.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
    return if (text.length <= 200) text else text.substring(0, 200) + "..."
}