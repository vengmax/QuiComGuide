package com.wllcom.quicomguide.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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

@Composable
fun FabMenu(
    modifier: Modifier = Modifier,
    mainFabText: String = "+",
    onAction: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    // Если меню открыто → рисуем прозрачный оверлей
    if (expanded) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f)) // затемнение (можно убрать)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    expanded = false // скрываем меню по клику
                }
        )
    }

    // Обработка возвращения
    if (expanded) {
        BackHandler {
            expanded = false
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.BottomEnd) {
        // Колонка с дочерними кнопками, появляющимися сверху над основной FAB
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(
                bottom = 53.dp, // 45 (height) + 8 (spacing)
                end = 0.dp
            )
        ) {
            AnimatedVisibility(visible = expanded, enter = fadeIn(), exit = fadeOut()) {
                ExtendedFloatingActionButton(
                    onClick = { onAction("material"); expanded = false },
                    text = { Text("Материал") },
                    icon = { Icon(Icons.Default.Create, contentDescription = "Материал") },
                    modifier = Modifier.height(45.dp)
                )
            }
            AnimatedVisibility(visible = expanded, enter = fadeIn(), exit = fadeOut()) {
                ExtendedFloatingActionButton(
                    onClick = { onAction("group"); expanded = false },
                    text = { Text("Группа") },
                    icon = { Icon(Icons.Default.Menu, contentDescription = "Группа") },
                    modifier = Modifier.height(45.dp)
                )
            }
            AnimatedVisibility(visible = expanded, enter = fadeIn(), exit = fadeOut()) {
                ExtendedFloatingActionButton(
                    onClick = { onAction("course"); expanded = false },
                    text = { Text("Курс") },
                    icon = { Icon(Icons.Default.Star, contentDescription = "Курс") },
                    modifier = Modifier.height(45.dp)
                )
            }
        }

        // Основной FAB в правом нижнем углу
        FloatingActionButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.size(45.dp)
        ) {
            Text(mainFabText)
        }
    }
}

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