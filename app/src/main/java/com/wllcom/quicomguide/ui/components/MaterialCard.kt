package com.wllcom.quicomguide.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun MaterialCard(
    title: String,
    text: String,
    editMode: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDeleteClick: () -> Unit
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

    val cardShape = RoundedCornerShape(10.dp)
    val haptic = LocalHapticFeedback.current

    var enabledDeleteMode by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
    ) {
        Card(
            shape = cardShape,
            modifier = Modifier
                .fillMaxSize()
                .clip(cardShape)
                .graphicsLayer {
                    rotationZ = angleDeg
                }
                .combinedClickable(
                    onClick = { if (!editMode) onClick() },
                    onLongClick = {
                        onLongClick()
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                )
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(title)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = text,
                    maxLines = 3,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (editMode) {
            IconButton(
                onClick = { enabledDeleteMode = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
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

    if (enabledDeleteMode) {
        AlertDialog(
            onDismissRequest = { enabledDeleteMode = false },
            title = { Text("Удалить материал?") },
            text = { Text("Это действие нельзя будет отменить.") },
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