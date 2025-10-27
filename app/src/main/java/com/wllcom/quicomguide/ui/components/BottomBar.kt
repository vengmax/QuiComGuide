package com.wllcom.quicomguide.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wllcom.quicomguide.ui.navigation.bottomNavItems
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Preview
@Composable
fun PreviewBottomBar() {
    BottomBar(navController = rememberNavController())
}

@Composable
fun BottomBar(navController: NavController, modifier: Modifier = Modifier) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val density = LocalDensity.current

    val items = bottomNavItems.take(3)

    val insets = WindowInsets.navigationBars.asPaddingValues()
    val navigationPadding = with(density) {
        insets.calculateBottomPadding()
    }

    val islandWidth = 290.dp
    val islandHeight = 60.dp + navigationPadding
    val islandRadius = 28.dp
    val islandHorizontalPadding = 0.dp
    val islandInnerHorizontalPadding = 0.dp
    val slotInnerVerticalPadding = 0.dp
    val bottomPadding = 6.dp + navigationPadding

    var islandInnerPx by remember { mutableStateOf(0) }

    var selectedIndex by remember {
        mutableStateOf(
            items.indexOfFirst { screen ->
                currentRoute == screen.route ||
                        (screen.route == "library" && (currentRoute?.contains("material") ?: false))
            }.let { if (it >= 0) it else 0 }
        )
    }

    LaunchedEffect(currentRoute, items) {
        val idx = items.indexOfFirst { screen ->
            currentRoute == screen.route ||
                    (screen.route == "library" && (currentRoute?.contains("material") ?: false))
        }.let { if (it >= 0) it else 0 }
        selectedIndex = idx
    }

    val animatedOffsetX = remember { Animatable(0f) }
    var highlightWidthPx by remember { mutableStateOf(0) }
    val highlightCorner = 28.dp
    val coroutineScope = rememberCoroutineScope()

    // rect рамки (в координатах внутренней Box) — используем для проверки начала drag
    var highlightRect by remember { mutableStateOf<Rect?>(null) }

    LaunchedEffect(selectedIndex, islandInnerPx, highlightWidthPx, items.size) {
        if (islandInnerPx > 0 && highlightWidthPx > 0 && items.isNotEmpty()) {
            val slotWidth = islandInnerPx.toFloat() / items.size
            val targetCenter = slotWidth * selectedIndex + slotWidth / 2f
            val targetX = (targetCenter - highlightWidthPx / 2f).coerceIn(
                0f,
                islandInnerPx.toFloat() - highlightWidthPx.toFloat()
            )

            // Если Animatable свежее (ещё 0f) — делать snapTo чтобы НЕ было видимого движения от 0.
            // Иначе — плавно анимировать.
            if (kotlin.math.abs(animatedOffsetX.value - 0f) < 0.5f) {
                animatedOffsetX.snapTo(targetX)
            } else {
                animatedOffsetX.animateTo(
                    targetX,
                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                )
            }

            // Обновляем rect по новой позиции
            val hPx = with(density) { islandHeight.toPx() }
            highlightRect = Rect(
                offset = Offset(animatedOffsetX.value, (hPx - hPx) / 2f),
                size = Size(highlightWidthPx.toFloat(), hPx)
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = islandHorizontalPadding, vertical = 0.dp),
//            .height(islandHeight),
        contentAlignment = Alignment.BottomCenter
    ) {

//        Spacer(modifier = Modifier.height(bottomPadding))

        Surface(
            shape = RoundedCornerShape(islandRadius),
            tonalElevation = 8.dp,
            shadowElevation = 12.dp,
            color = Color.Transparent,
//            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            modifier = Modifier
//                .fillMaxWidth()
                .height(islandHeight)
                .width(islandWidth)

                .padding(bottom = bottomPadding)
//                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), RoundedCornerShape(islandRadius))

                .align(Alignment.BottomCenter)
        ) {
            // внутренняя область: сюда повесим pointerInput
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = islandInnerHorizontalPadding)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), RoundedCornerShape(islandRadius))
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), RoundedCornerShape(islandRadius))
                    .onGloballyPositioned { coords ->
                        val w = coords.size.width
                        islandInnerPx = w
                        if (items.isNotEmpty()) {
                            highlightWidthPx = (w.toFloat() / items.size).roundToInt()
                        }
                    }
                    .pointerInput(items, islandInnerPx, highlightWidthPx) {
                        // Здесь мы вручную обрабатываем pointer события
                        if (islandInnerPx <= 0 || highlightWidthPx <= 0 || items.isEmpty()) return@pointerInput

                        while (true) {
                            // ждём первого касания (не потребляем — allow pass = false чтобы получить событие)
                            val down = awaitPointerEventScope { awaitFirstDown(false) }
                            val downPos = down.position

                            // проверяем попадает ли точка down в текущий rect рамки
                            val rect = highlightRect
                            if (rect == null || !rect.contains(downPos)) {
                                // не внутри — не перехватываем; позволяем событию пройти дальше
                                // просто продолжим цикл (следующий awaitFirstDown)
                                continue
                            }

                            // если внутри — начнём drag-gesture: будем потреблять изменения
                            // используем переменные, аккуратно ограничивая координаты
                            var pointerUp = false
                            // consume the down
                            down.consume()
                            // локальная корутина для drag
                            awaitPointerEventScope {
                                // пока не отпустили
                                while (!pointerUp) {
                                    val event = awaitPointerEvent()
                                    val moveChange = event.changes.firstOrNull() ?: continue
                                    if (moveChange.changedToUpIgnoreConsumed()) {
                                        // отпускание
                                        pointerUp = true
                                        moveChange.consume()
                                        break
                                    }
                                    // если движение есть — обновим позицию рамки
                                    if (moveChange.positionChange() != Offset.Zero) {
                                        val dx = moveChange.positionChange().x
                                        moveChange.consume()
                                        coroutineScope.launch {
                                            val newX = (animatedOffsetX.value + dx).coerceIn(
                                                0f,
                                                islandInnerPx.toFloat() - highlightWidthPx.toFloat()
                                            )
                                            animatedOffsetX.snapTo(newX)
                                            // обновим rect по новому offset
                                            highlightRect = Rect(
                                                offset = Offset(newX, (with(density) { (islandHeight.toPx() - islandHeight.toPx()) / 2f })),
                                                size = Size(highlightWidthPx.toFloat(), islandHeight.toPx())
                                            )
                                        }
                                    }
                                }
                            }

                            // pointer up или cancel: вычислим ближайший слот и анимируем к нему
                            coroutineScope.launch {
                                val slotWidth = islandInnerPx.toFloat() / items.size
                                val centerX = animatedOffsetX.value + highlightWidthPx.toFloat() / 2f
                                val nearestIndex = ((centerX) / slotWidth).toInt().coerceIn(0, items.lastIndex)
                                val targetCenterX = slotWidth * nearestIndex + slotWidth / 2f
                                val targetX = (targetCenterX - highlightWidthPx / 2f)
                                animatedOffsetX.animateTo(
                                    targetX,
                                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                                )
                                // обновим rect
                                highlightRect = Rect(
                                    offset = Offset(animatedOffsetX.value, (with(density) { (islandHeight.toPx() - islandHeight.toPx()) / 2f })),
                                    size = Size(highlightWidthPx.toFloat(), islandHeight.toPx())
                                )

                                // если индекс сменился — навигация
                                if (nearestIndex != selectedIndex) {
                                    selectedIndex = nearestIndex
                                    val screen = items[nearestIndex]
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                } else {
                                    // если тот же индекс — повторить поведение повторного нажатия
                                    val screen = items[nearestIndex]
                                    navController.navigate(screen.route) {
                                        popUpTo(screen.route) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            }
                        } // end while true
                    } // end pointerInput
            ) {
                // Плавающая рамка (overlay)
                if (islandInnerPx > 0 && highlightWidthPx > 0 && items.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = animatedOffsetX.value.roundToInt(),
                                    y = 0
                                )
                            }
                            .size(
                                width = with(density) { highlightWidthPx.toDp() },
                                height = islandHeight
                            )
                            .align(Alignment.CenterStart)
                            .onGloballyPositioned { coords ->
                                // обновляем rect при позиционировании (координаты внутри внутренней Box)
                                val pos = coords.positionInParent()
                                highlightRect = Rect(
                                    offset = Offset(pos.x, pos.y),
                                    size = Size(coords.size.width.toFloat(), coords.size.height.toFloat())
                                )
                            }
                            .border(
                                width = 2.dp,
                                brush = SolidColor(MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(highlightCorner)
                            )
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                                shape = RoundedCornerShape(highlightCorner)
                            )

                    )
                }

                // Ряд кнопок поверх (клик по ним работает как раньше)
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    items.forEachIndexed { index, screen ->
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val selected = index == selectedIndex

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(vertical = slotInnerVerticalPadding)
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null
                                ) {
                                    if (!selected) {
                                        coroutineScope.launch {
                                            if (islandInnerPx > 0 && highlightWidthPx > 0) {
                                                val slotWidth = islandInnerPx.toFloat() / items.size
                                                val targetCenterX = slotWidth * index + slotWidth / 2f
                                                val targetX = targetCenterX - highlightWidthPx / 2f
                                                animatedOffsetX.animateTo(targetX, animationSpec = tween(260, easing = FastOutSlowInEasing))
                                            }
                                            selectedIndex = index
                                            val screenTo = screen
                                            navController.navigate(screenTo.route) {
                                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    } else {
                                        navController.navigate(screen.route) {
                                            popUpTo(screen.route) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            val bgAlpha by animateFloatAsState(if (isPressed) 0.12f else 0f, animationSpec = tween(120))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = bgAlpha),
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp,
                                modifier = Modifier
                                    .padding(horizontal = 8.dp)
                                    .wrapContentSize()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    val iconSize = if (selected) 24.dp else 22.dp
                                    Icon(
                                        painter = painterResource(id = screen.iconRes),
                                        contentDescription = screen.title,
                                        modifier = Modifier.size(iconSize),
//                                        tint = Color.Unspecified
                                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 1f)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = screen.title,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 1f)
                                    )
                                }
                            }
                        }
                    }
                }
            } // end inner Box
        } // end Surface
    } // end outer Box
}


