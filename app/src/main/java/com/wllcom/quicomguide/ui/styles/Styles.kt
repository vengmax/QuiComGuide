package com.wllcom.quicomguide.ui.styles

import android.content.ContentValues.TAG
import android.util.Log
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class StyleResult(
    val brush: Brush,
    val text: TextStyle
)

@Composable
fun topBarStyle(height: Dp, width: Dp): StyleResult {
    return glassStyle(height)
}

@Composable
fun buttonStyle(height: Dp, width: Dp): StyleResult {
    return glassStyle(height)
}

@Composable
fun bottomBarStyle(height: Dp, width: Dp): StyleResult {
    return glassStyle(height)
}

@Composable
fun dropDownMenuStyle(height: Dp, width: Dp): StyleResult{
    return halfGlassStyle(height)
}

@Composable
fun glassStyle(heightElemnet: Dp): StyleResult {
    val isDarkTheme = isSystemInDarkTheme()
    Log.d(TAG, heightElemnet.toString())
    val localtextStyle = LocalTextStyle.current
    val colorOutline = MaterialTheme.colorScheme.outline
    val colorOutlineVariant = MaterialTheme.colorScheme.outlineVariant
    val colorContainerHigh = MaterialTheme.colorScheme.surfaceContainerHigh

    return remember(heightElemnet, isDarkTheme, MaterialTheme.colorScheme) {
        val fixSizeFirstColor = 2.dp
        val fixSizeSecondColor = 6.dp
        val offset1 = fixSizeFirstColor / heightElemnet
        val offset2 = fixSizeSecondColor / heightElemnet
        val offset3 = (heightElemnet - fixSizeSecondColor) / heightElemnet
        val offset4 = (heightElemnet - fixSizeFirstColor) / heightElemnet

        val colorGlass = Brush.verticalGradient(
            colorStops = arrayOf(
                0f to colorOutline.copy(alpha = 0.9f),
                offset1 to colorOutline.copy(alpha = 0.6f),
                offset2 to colorOutlineVariant.copy(alpha = 0.9f),
                0.5f to colorContainerHigh.copy(alpha = 0.8f),
                offset3 to colorOutlineVariant.copy(alpha = 0.9f),
                offset4 to colorOutline.copy(alpha = 0.6f),
                1f to colorOutline.copy(alpha = 0.9f)
            )
        )

        val styleGlassText = localtextStyle.copy(
//            shadow = Shadow(
//                color = if (isDarkTheme) Color.Black else Color.White,
//                offset = Offset(4f, 4f),
//                blurRadius = 15f
//            )
        )

        StyleResult(colorGlass, styleGlassText)
    }
}

@Composable
fun halfGlassStyle(heightElemnet: Dp): StyleResult {
    val isDarkTheme = isSystemInDarkTheme()
    Log.d(TAG, heightElemnet.toString())
    val localtextStyle = LocalTextStyle.current
    val colorOutline = MaterialTheme.colorScheme.outlineVariant
    val colorOutlineVariant = MaterialTheme.colorScheme.surfaceContainerHigh
    val density = LocalDensity.current

    return remember(heightElemnet, isDarkTheme, MaterialTheme.colorScheme) {
        val topOffsetGlass = 12.dp / heightElemnet
        val mainOffsetGlass = (heightElemnet - 54.dp) / heightElemnet
        val bottomOffsetGlass = (heightElemnet - 42.dp) / heightElemnet
        val listColorGlass = Brush.verticalGradient(startY = 0f, endY = with(density){heightElemnet.toPx()},colorStops = arrayOf(
            0f to Color.Transparent,
            topOffsetGlass to colorOutlineVariant.copy(alpha = 0.9f),
            mainOffsetGlass to colorOutlineVariant.copy(alpha = 0.9f),
            bottomOffsetGlass to colorOutline.copy(alpha = 0.4f),
            1f to colorOutline.copy(alpha = 0.9f))
        )

        val styleGlassText = localtextStyle.copy(
            shadow = Shadow(
                color = if (isDarkTheme) Color.Black else Color.White,
                offset = Offset(4f, 4f),
                blurRadius = 15f
            )
        )

        StyleResult(listColorGlass, styleGlassText)
    }
}