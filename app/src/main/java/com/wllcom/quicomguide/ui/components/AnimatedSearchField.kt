package com.wllcom.quicomguide.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

@Composable
fun AnimatedSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    heightDp: Int = 52,
    placeholderText: String = "Умный поиск по материалам",
) {
    // ваш список цветов
    val colors = listOf(
        Color(0xFFFDD835), Color(0xFFFD9900), Color(0xFFFF5D93),
        Color(0xFFFF60EA), Color(0xFFFF5D93), Color(0xFFFD9900)
    )

    // бесконечная анимация индекса цвета (0..colors.size)
    val infiniteTransition = rememberInfiniteTransition()
    val colorIndex by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (colors.size).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    val strokeDp = 4.dp
    val cornerRadius = 12.dp

    // вычисляем интерполированный текущий цвет
    val currentColor: Color = run {
        val i = colorIndex.toInt() % colors.size
        val next = (i + 1) % colors.size
        val fraction = colorIndex - colorIndex.toInt()
        lerp(colors[i], colors[next], fraction)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .background(Color.Transparent)
            .drawBehind {
                val strokePx = strokeDp.toPx()
                val glowRadius = 12.dp.toPx()
                val centerY = size.height / 2f
                val startY = centerY - size.height / 2f - glowRadius
                val endY = centerY + size.height / 2f + glowRadius

                // Основное свечение
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            currentColor.copy(alpha = 0.3f),
                            Color.Transparent
                        ),
                        startY = startY,
                        endY = endY
                    ),
                    topLeft = Offset(max(cornerRadius.toPx(), size.height / 2f), -glowRadius),
                    size = Size(
                        size.width - glowRadius * 2 - max(
                            cornerRadius.toPx(),
                            size.height / 2f
                        ) - 0.5f,
                        size.height + glowRadius * 2
                    ),
                )

                // Левое свечение
                clipRect(
                    left = -glowRadius,
                    top = -glowRadius,
                    right = size.height / 2f,
                    bottom = size.height + glowRadius
                ) {
                    drawRoundRect(
                        brush = Brush.radialGradient(
                            colors = listOf(currentColor.copy(alpha = 0.3f), Color.Transparent),
                            center = Offset(size.height / 2f, size.height / 2f),
                            radius = (size.height / 2f) + glowRadius
                        ),
                        topLeft = Offset(-glowRadius, -glowRadius),
                        size = Size(size.height + glowRadius * 2, size.height + glowRadius * 2),
                        cornerRadius = CornerRadius(
                            cornerRadius.toPx() + glowRadius,
                            cornerRadius.toPx() + glowRadius
                        )
                    )
                }

                // Правое свечение
                translate(
                    size.width - glowRadius * 2 - max(cornerRadius.toPx(), size.height / 2f),
                    0f
                ) {
                    clipRect(
                        left = size.height / 2f - 0.5f,
                        top = -glowRadius,
                        right = size.height + glowRadius,
                        bottom = size.height + glowRadius
                    ) {
                        drawRoundRect(
                            brush = Brush.radialGradient(
                                colors = listOf(currentColor.copy(alpha = 0.3f), Color.Transparent),
                                center = Offset(size.height / 2f, size.height / 2f),
                                radius = (size.height / 2f) + glowRadius
                            ),
                            topLeft = Offset(-glowRadius, -glowRadius),
                            size = Size(size.height + glowRadius * 2, size.height + glowRadius * 2),
                            cornerRadius = CornerRadius(
                                cornerRadius.toPx() + glowRadius,
                                cornerRadius.toPx() + glowRadius
                            )
                        )
                    }
                }

                // Рамка
                drawRoundRect(
                    color = currentColor,
                    topLeft = Offset.Zero,
                    size = Size(size.width, size.height),
                    cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
                    style = Stroke(width = strokePx)
                )
            }
            .clip(RoundedCornerShape(cornerRadius))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.Normal
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth()
        ) { innerTextField ->
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholderText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
                innerTextField()
            }
        }
    }
}


@Preview
@Composable
fun PreviewAnimatedSearchField() {
    AnimatedSearchField("", { })
}