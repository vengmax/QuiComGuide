package com.wllcom.quicomguide.ui.components

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import com.wllcom.quicomguide.ui.styles.dropDownMenuStyle
import com.wllcom.quicomguide.ui.styles.topBarStyle

@Preview
@Composable
fun PreviewTopBarWithSearch() {
    TopBar("")
}

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun TopBar(
    title: String,
    editTitle: Boolean = false,
    onEditTitle: (String) -> Unit = {},
    back: Boolean = false,
    onBack: () -> Unit = {},
    dropDown: Boolean = false,
    generalDropDownItem: String = "",
    onGeneralDropDownItem: () -> Unit = {},
    dropDownList: List<String> = emptyList(),
    onDropDownList: (Int) -> Unit = {},
    deleteModeDropDownList: Boolean = false,
    onDeleteDropDownList: (Int) -> Unit = {},
    search: Boolean = false,
    query: String = "",
    onQueryChange: (String) -> Unit = {},
    onDebouncedQuery: (String) -> Unit = {},
    debounceMs: Long = 700L,
    customButtons: Boolean = false,
    composeCustomButtons: @Composable (Modifier) -> Unit = {},
) {
    val density = LocalDensity.current
    var heightTopBarDp by remember { mutableStateOf(44.dp) }
    var isSearching by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(query, debounceMs) {
        snapshotFlow { query }
            .map { it.trim() }
            .distinctUntilChanged()
            .debounce(debounceMs)
            .collectLatest { debounced ->
                onDebouncedQuery(debounced)
            }
    }

    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isDarkTheme = isSystemInDarkTheme()

    var selectedCourse by remember { mutableStateOf<Int?>(null) }

    var expanded by remember { mutableStateOf(false) }

    val swipeHandleAreaDp = 40.dp
    val startOffsetDp = 0.dp
    val screenHeightDp = configuration.screenHeightDp.dp
    val bottomBarHeight = if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
        170.dp
    else
        130.dp
    val systemBarsPadding = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

    val panelHeightDp = screenHeightDp - systemBarsPadding - bottomBarHeight
    val panelMaxHeightPx = with(density) { panelHeightDp.toPx() }
    val heightPx = remember { Animatable(0f) }

    var dropDownMenuWidthDp by remember { mutableStateOf(0.dp) }

    LaunchedEffect(expanded) {
        val target = if (expanded) panelMaxHeightPx else 0f
        heightPx.animateTo(target, animationSpec = tween(350))
    }

    fun snapByDeltaPx(deltaPx: Float) {
        val new = (heightPx.value + deltaPx).coerceIn(with(density) { 50.dp.toPx() }, panelMaxHeightPx)
        scope.launch { heightPx.snapTo(new) }
    }

    fun settleAfterRelease() {
        scope.launch {
            val halfway = panelMaxHeightPx * 0.5f
            if (heightPx.value < halfway) {
                expanded = false
                heightPx.animateTo(0f, tween(300))
            } else {
                expanded = true
                heightPx.animateTo(panelMaxHeightPx, tween(300))
            }
        }
    }

    val topBarStyle = topBarStyle(heightTopBarDp, 0.dp)
    val backgroundColor = topBarStyle.brush
    val styleText = topBarStyle.text
    val dropDownMenuStyle = dropDownMenuStyle(panelHeightDp ,dropDownMenuWidthDp)
    val backgroundColorDropDownMenu = dropDownMenuStyle.brush

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

    if (isSearching) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(vertical = 4.dp, horizontal = 4.dp)
                .height(heightTopBarDp)
        ){
            Row (verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(backgroundColor, RoundedCornerShape(14.dp))
                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            isSearching = false
                        }
                        .padding(10.dp)
                        .size(24.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Spacer(Modifier.width(5.dp))
                Box(
                    modifier = Modifier
                        .background(backgroundColor, RoundedCornerShape(14.dp))
                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .padding(10.dp)
                        .size(24.dp)
                        .weight(1f)
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
                        modifier = Modifier
                            .fillMaxWidth()
                    ) { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (query.isEmpty()) {
                                Text(
                                    text = "Поиск...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onBackground.copy(
                                        alpha = 0.5f
                                    )
                                )
                            }
                            innerTextField()
                        }
                    }
                }
            }
        }

    } else {
        if(dropDown) {
            Box(
                modifier = Modifier
                    .width(dropDownMenuWidthDp + 23.dp)
                    .statusBarsPadding()
                    .padding(top = 39.dp)
                    .padding(start = 4.dp + 1.dp)
                    .alpha(if (heightPx.value < with(density) { 50.dp.toPx() }) 0f else 1f)
            ) {

                val currentHeightDp = with(density) { heightPx.value.toDp() }

                Box(
                    modifier = Modifier

                        .offset(y = startOffsetDp)
                        .height(currentHeightDp)
                        .align(Alignment.TopCenter)
                        .clipToBounds()
                        .background(Color.Transparent)
                        .clip(RoundedCornerShape(0.dp, 0.dp, 14.dp, 14.dp))
                ) {

                    val colorBorder = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = swipeHandleAreaDp)
                            .clip(RoundedCornerShape(0.dp, 0.dp, 14.dp, 14.dp))
                            .background(backgroundColorDropDownMenu)
//                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), RoundedCornerShape(0.dp, 0.dp, 14.dp, 14.dp))
                            .drawBehind {
                                val strokeWidth = 2.dp.toPx()
                                val cornerRadius = 14.dp.toPx()
                                val w = size.width
                                val h = size.height

                                val path = Path().apply {
                                    moveTo(0f, 0f)
                                    lineTo(0f, h - cornerRadius)

                                    arcTo(
                                        rect = Rect(0f, h - 2 * cornerRadius, 2 * cornerRadius, h),
                                        startAngleDegrees = 90f,
                                        sweepAngleDegrees = 90f,
                                        forceMoveTo = true
                                    )
                                    moveTo(cornerRadius, h)
                                    lineTo(w - cornerRadius, h)
                                    arcTo(
                                        rect = Rect(w - 2 * cornerRadius, h - 2 * cornerRadius, w, h),
                                        startAngleDegrees = 0f,
                                        sweepAngleDegrees = 90f,
                                        forceMoveTo = true
                                    )
                                    moveTo(w, h - cornerRadius)
                                    lineTo(w, 0f)
                                }

                                drawPath(
                                    path = path,
                                    color = colorBorder,
                                    style = Stroke(width = strokeWidth)
                                )
                            }
                    ) {
                        item {
                            Spacer(Modifier.height(4.dp))
                        }
                        item {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                headlineContent = { Text(generalDropDownItem,style = styleText) },
                                supportingContent = { if (selectedCourse == null) Text("Фильтр отключён", style = styleText) },
                                modifier = Modifier
//                                    .graphicsLayer {
//                                        shadowElevation = if (isDarkTheme) 16.dp.toPx() else 36.dp.toPx()
//                                        shape = CircleShape
//                                        clip = false
//                                    }
                                    .clickable {
                                        selectedCourse = null
                                        expanded = false
                                        onGeneralDropDownItem()
                                    }
                            )
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = if(isDarkTheme) Color.LightGray.copy(alpha = 0.4f) else Color.DarkGray.copy(alpha = 0.4f)
                            )
                        }
                        itemsIndexed(dropDownList) { index, text ->
                            Box(modifier = Modifier.fillMaxWidth()) {
                                ListItem(
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    headlineContent = { Text(text, style = styleText) },
                                    supportingContent = { if (selectedCourse == index) Text("Выбран", style = styleText) },
                                    modifier = Modifier
//                                        .graphicsLayer {
//                                            shadowElevation = if (isDarkTheme) 16.dp.toPx() else 36.dp.toPx()
//                                            shape = CircleShape
//                                            clip = false
//                                        }
                                        .clickable {
                                            selectedCourse = index
                                            expanded = false
                                            onDropDownList(index)
                                        }
                                        .padding(end = 22.dp)
                                )
                                if (deleteModeDropDownList) {
                                    IconButton(
                                        onClick = {
                                            onDeleteDropDownList(index)
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Удалить",
                                            tint = MaterialTheme.colorScheme.onBackground,
                                            modifier = Modifier.size(23.dp)
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = if(isDarkTheme) Color.LightGray.copy(alpha = 0.4f) else Color.DarkGray.copy(alpha = 0.4f)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(swipeHandleAreaDp)
                            .align(Alignment.BottomCenter)
                            .draggable(
                                state = rememberDraggableState { delta ->
                                    snapByDeltaPx(delta)
                                },
                                orientation = Orientation.Vertical,
                                onDragStopped = { settleAfterRelease() }
                            )
                    ) {}
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(vertical = 4.dp, horizontal = 4.dp)
                .height(heightTopBarDp)
        ){
            Row (verticalAlignment = Alignment.CenterVertically) {
                if(back){
                    Box(
                        modifier = Modifier
                            .background(backgroundColor, RoundedCornerShape(14.dp))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                RoundedCornerShape(14.dp)
                            )
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                onBack()
                            }
                            .padding(10.dp)
                            .size(24.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                    Spacer(Modifier.width(5.dp))
                }
                Box(
                    modifier = Modifier
                        .background(backgroundColor, RoundedCornerShape(14.dp))
                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            if(dropDown)
                                expanded = !expanded
                        }
                        .padding(10.dp)
                        .size(24.dp)
                        .weight(1f)
                        .onGloballyPositioned { coordinates ->
                            dropDownMenuWidthDp = with(density) { coordinates.size.width.toDp() }
                        }
                        .draggable(
                            state = rememberDraggableState { delta ->
                                snapByDeltaPx(delta)
                            },
                            orientation = Orientation.Vertical,
                            onDragStopped = { settleAfterRelease() }
                        )

                ) {
                    Row (verticalAlignment = Alignment.CenterVertically) {
                        if(!editTitle) {
                            Text(
                                title,
                                modifier = Modifier.weight(1f), MaterialTheme.colorScheme.onBackground,
                                style = styleText
                            )
                        }
                        else{
                            BasicTextField(
                                value = title,
                                onValueChange = onEditTitle,
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .weight(1f)
                            ) { innerTextField ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (title.isEmpty()) {
                                        Text(
                                            "Название материала",
                                            style = LocalTextStyle.current.copy(
                                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                                                fontSize = 16.sp,
                                            )
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        }
                        if(dropDown) {
                            if (!expanded)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "DropDown")
                            else
                                Icon(Icons.Default.ArrowDropUp, contentDescription = "DropDown")
                        }
                    }
                }
                if(search) {
                    Spacer(Modifier.width(5.dp))
                    Box(
                        modifier = Modifier
                            .background(backgroundColor, RoundedCornerShape(14.dp))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                RoundedCornerShape(14.dp)
                            )
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                isSearching = true
                            }
                            .padding(10.dp)
                            .size(24.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                }
                if(customButtons) {
                    Spacer(Modifier.width(5.dp))
                    val modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(backgroundColor, RoundedCornerShape(14.dp))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            RoundedCornerShape(14.dp)
                        )
                        .height(heightTopBarDp)
                    composeCustomButtons(modifier)

                }
            }
        }
    }
}