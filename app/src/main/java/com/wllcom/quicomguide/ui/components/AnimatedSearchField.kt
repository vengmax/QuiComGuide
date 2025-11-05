package com.wllcom.quicomguide.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wllcom.quicomguide.data.source.EnumSearchMode

@Preview
@Composable
fun PreviewAnimatedSearchField() {
    AnimatedSearchField("", { }){}
}

@Composable
fun AnimatedSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 1.dp,
    cornerRadius: Dp = 24.dp,
    alphaGlow: Float = 0.5f,
    fallbackMode: (EnumSearchMode) -> Unit
) {

    // variables
    val borderPadding = 8.dp
    val searchPadding = 16.dp
    val innerColor = MaterialTheme.colorScheme.background
    var expanded by rememberSaveable { mutableStateOf(false) }
    var selectedSearchMode by rememberSaveable { mutableStateOf(EnumSearchMode.EMBEDDING) }

    val icon = if (expanded) {
        Icons.Default.AutoFixHigh
    } else {
        Icons.Default.AutoFixHigh
    }
    val placeholderText = if (selectedSearchMode == EnumSearchMode.EMBEDDING)
        "AI поиск..."
    else if (selectedSearchMode == EnumSearchMode.BOTH)
        "Поиск со стеммингом + AI..."
    else
        "Поиск со стеммингом..."

    val colors = listOf(
        Color(0xFFFDD835), Color(0xFFFD9900), Color(0xFFFF5D93),
        Color(0xFFFF60EA), Color(0xFFFF5D93), Color(0xFFFD9900)
    )

    // infinity color animation
    val infiniteTransition = rememberInfiniteTransition()
    val colorIndex by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (colors.size).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    // color interpolation
    val currentColor: Color = run {
        val i = colorIndex.toInt() % colors.size
        val next = (i + 1) % colors.size
        val fraction = colorIndex - colorIndex.toInt()
        lerp(colors[i], colors[next], fraction)
    }

    Box(
        modifier = modifier
            .background(Color.Transparent)
            .then(
                if (selectedSearchMode == EnumSearchMode.EMBEDDING || selectedSearchMode == EnumSearchMode.BOTH) {
                    modifier
                        .glowBorder(
                            stroke = strokeWidth,
                            cornerRadius = cornerRadius,
                            color = currentColor,
                            innerColor = innerColor,
                            alphaGlow = alphaGlow,
                            borderPadding = borderPadding
                        )
                        .padding(borderPadding)
                        .border(strokeWidth, currentColor, RoundedCornerShape(cornerRadius))
                } else {
                    modifier
                        .padding(borderPadding)
                        .border(
                            width = strokeWidth,
                            color = MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(cornerRadius)
                        )
                }
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Column {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Normal
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.padding(start = searchPadding),
            ) { innerTextField ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                text = placeholderText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                        }
                        innerTextField()
                    }
                    IconButton(
                        onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = icon,
                            contentDescription = "Меню поиска",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }

                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp, 0.dp, 18.dp, 10.dp)
                ) {
                    MenuItem(text = "AI поиск", selectedSearchMode == EnumSearchMode.EMBEDDING) {
                        expanded = false
                        selectedSearchMode = EnumSearchMode.EMBEDDING
                        fallbackMode(selectedSearchMode)
                    }
                    MenuItem(text = "Поиск со стеммингом", selectedSearchMode == EnumSearchMode.FTS) {
                        expanded = false
                        selectedSearchMode = EnumSearchMode.FTS
                        fallbackMode(selectedSearchMode)
                    }
                    MenuItem(text = "Поиск со стеммингом + AI поиск", selectedSearchMode == EnumSearchMode.BOTH) {
                        expanded = false
                        selectedSearchMode = EnumSearchMode.BOTH
                        fallbackMode(selectedSearchMode)
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuItem(
    text: String,
    checked: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = if (checked) 4.dp else 6.dp)
    ) {
        val icon = if (checked)
            Icons.Default.Check
        else
            ImageVector.Builder(
                defaultWidth = 0.dp,
                defaultHeight = 0.dp,
                viewportWidth = 0f,
                viewportHeight = 0f
            ).build() // Empty image

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = "Меню поиска",
                tint = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}


/**
 * glow effect
 */
fun Modifier.glowBorder(
    stroke: Dp = 1.dp,
    cornerRadius: Dp = 8.dp,
    color: Color = Color.Cyan,
    innerColor: Color = Color.Transparent,
    alphaGlow: Float = 0.35f,
    borderPadding: Dp = 8.dp
): Modifier = this.then(
    Modifier.drawBehind {

        val strokePx = stroke.toPx()
        val borderPaddingPx = borderPadding.toPx()
        val cornerRadiusPx = cornerRadius.toPx()

        // center glow (vertical gradient)
        val offsetRounding = borderPaddingPx + strokePx + cornerRadiusPx
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    color.copy(alpha = alphaGlow),
                    color.copy(alpha = alphaGlow),
                    color.copy(alpha = alphaGlow),
                    Color.Transparent
                ),
            ),
            topLeft = Offset(offsetRounding, 0f),
            size = Size(
                size.width - offsetRounding * 2,
                size.height
            )
        )

        // center internal fill
        drawRect(
            color = innerColor,
            topLeft = Offset(offsetRounding, borderPaddingPx),
            size = Size(
                size.width - offsetRounding * 2,
                size.height - borderPaddingPx * 2
            )
        )

        // left glow
        clipRect(
            left = 0f,
            top = 0f,
            right = offsetRounding,
            bottom = size.height
        ) {
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = alphaGlow),
                        color.copy(alpha = alphaGlow),
                        Color.Transparent
                    ),
                    center = Offset(offsetRounding, size.height / 2f)
                )
            )
        }

        // left internal fill
        clipRect(
            left = 0f,
            top = 0f,
            right = offsetRounding,
            bottom = size.height
        ) {
            drawRoundRect(
                color = innerColor,
                topLeft = Offset(borderPaddingPx, borderPaddingPx),
                size = Size(offsetRounding * 2, size.height - borderPaddingPx * 2),
                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
            )
        }

        // right glow
        clipRect(
            left = size.width - offsetRounding,
            top = 0f,
            right = size.width,
            bottom = size.height
        ) {
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = alphaGlow),
                        color.copy(alpha = alphaGlow),
                        Color.Transparent
                    ),
                    center = Offset(size.width - offsetRounding, size.height / 2f)
                )
            )
        }

        // right internal fill
        clipRect(
            left = size.width - offsetRounding,
            top = 0f,
            right = size.width,
            bottom = size.height
        ) {
            drawRoundRect(
                color = innerColor,
                topLeft = Offset(
                    size.width - offsetRounding * 2 - borderPadding.toPx(),
                    borderPadding.toPx()
                ),
                size = Size(offsetRounding * 2, size.height - borderPaddingPx * 2),
                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
            )
        }
    }
)