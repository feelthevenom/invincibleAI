package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlin.math.abs

private val WheelItemHeight: Dp = 44.dp
private val WheelVisibleRows = 5
private val WheelHeight: Dp = WheelItemHeight * WheelVisibleRows

private fun LazyListState.centeredItemIndex(): Int {
    val layoutInfo = layoutInfo
    if (layoutInfo.visibleItemsInfo.isEmpty()) return firstVisibleItemIndex
    val viewportCenter = layoutInfo.viewportStartOffset + layoutInfo.viewportSize.height / 2
    return layoutInfo.visibleItemsInfo
        .minByOrNull { item -> abs((item.offset + item.size / 2) - viewportCenter) }
        ?.index
        ?: firstVisibleItemIndex
}

@Composable
fun WheelPickerField(
    label: String,
    items: List<String>,
    selectedIndex: Int,
    onConfirm: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val safeIndex = selectedIndex.coerceIn(0, items.lastIndex)
    var showDialog by remember { mutableStateOf(false) }
    var draftIndex by remember(safeIndex) { mutableIntStateOf(safeIndex) }

    Column(modifier = modifier) {
        Text(
            label,
            style = Typography.bodySmall,
            color = OnSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceContainer, RoundedCornerShape(12.dp))
                .clickable {
                    draftIndex = safeIndex
                    showDialog = true
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(items[safeIndex], style = Typography.bodyLarge, color = OnSurface, modifier = Modifier.weight(1f))
            Text("Change", style = Typography.labelMedium, color = Primary)
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(label, color = Primary) },
            text = {
                WheelPicker(
                    items = items,
                    selectedIndex = draftIndex,
                    onSelected = { draftIndex = it },
                    label = null
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onConfirm(draftIndex)
                    showDialog = false
                }) { Text("Select") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun InlineTimeWheelPicker(
    hour12: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val hourItems = remember { (1..12).map { String.format("%02d", it) } }
    val minuteItems = remember { (0..59).map { String.format("%02d", it) } }
    val hourIndex = (hour12 - 1).coerceIn(0, 11)
    val minuteIndex = minute.coerceIn(0, 59)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        WheelPicker(
            items = hourItems,
            selectedIndex = hourIndex,
            onSelected = { onHourChange(it + 1) },
            modifier = Modifier.weight(1f),
            label = null,
            compact = true
        )
        Text(
            ":",
            style = Typography.headlineMedium,
            color = Primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
        WheelPicker(
            items = minuteItems,
            selectedIndex = minuteIndex,
            onSelected = onMinuteChange,
            modifier = Modifier.weight(1f),
            label = null,
            compact = true
        )
    }
}

@Composable
fun WheelPicker(
    items: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    compact: Boolean = false
) {
    if (items.isEmpty()) return
    val safeIndex = selectedIndex.coerceIn(0, items.lastIndex)
    val itemHeight = if (compact) 40.dp else WheelItemHeight
    val wheelHeight = if (compact) itemHeight * WheelVisibleRows else WheelHeight
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = safeIndex)
    val flingBehavior = rememberSnapFlingBehavior(
        lazyListState = listState,
        snapPosition = SnapPosition.Center
    )
    val centeredIndex by remember {
        derivedStateOf { listState.centeredItemIndex().coerceIn(0, items.lastIndex) }
    }
    val contentPadding = PaddingValues(vertical = (wheelHeight - itemHeight) / 2)

    LaunchedEffect(safeIndex, items.size) {
        if (listState.centeredItemIndex() != safeIndex) {
            listState.animateScrollToItem(safeIndex)
        }
    }

    LaunchedEffect(listState, items.size) {
        snapshotFlow { listState.centeredItemIndex() }
            .distinctUntilChanged()
            .collect { index ->
                if (index in items.indices) onSelected(index)
            }
    }

    LaunchedEffect(listState, items.size) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .filter { !it }
            .map { listState.centeredItemIndex() }
            .distinctUntilChanged()
            .collect { index ->
                if (index in items.indices) onSelected(index)
            }
    }

    Column(modifier = modifier) {
        label?.let {
            Text(
                it,
                style = Typography.bodySmall,
                color = OnSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(wheelHeight)
                .then(if (compact) Modifier else Modifier.background(SurfaceContainer, RoundedCornerShape(12.dp))),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
                    .background(Primary.copy(if (compact) 0.08f else 0.12f), RoundedCornerShape(8.dp))
            )
            if (compact) {
                Column(Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = OutlineVariant.copy(0.45f), thickness = 1.dp)
                    Spacer(Modifier.height(itemHeight - 2.dp))
                    HorizontalDivider(color = OutlineVariant.copy(0.45f), thickness = 1.dp)
                }
            }
            LazyColumn(
                state = listState,
                flingBehavior = flingBehavior,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding
            ) {
                items(items.size) { index ->
                    val isSelected = index == centeredIndex
                    Text(
                        text = items[index],
                        style = Typography.bodyLarge.copy(
                            fontSize = if (isSelected) if (compact) 22.sp else 20.sp else if (compact) 15.sp else 16.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (isSelected) Primary else OnSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(itemHeight)
                            .wrapContentHeight(Alignment.CenterVertically)
                            .alpha(if (isSelected) 1f else 0.45f)
                    )
                }
            }
        }
    }
}

@Composable
fun HeightUnitToggle(useMetric: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceContainerLow, RoundedCornerShape(10.dp))
            .padding(4.dp)
    ) {
        listOf(true to "cm", false to "ft/in").forEach { (metric, label) ->
            val selected = useMetric == metric
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (selected) Primary.copy(0.2f) else androidx.compose.ui.graphics.Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(vertical = 10.dp)
                    .clickable { onToggle(metric) },
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (selected) Primary else OnSurfaceVariant, style = Typography.labelMedium)
            }
        }
    }
}

fun ageItems(): List<String> = (com.example.data.ProfileValidation.MIN_AGE..com.example.data.ProfileValidation.MAX_AGE).map { "$it yrs" }

fun cmHeightItems(): List<String> =
    (com.example.data.ProfileValidation.MIN_HEIGHT_CM..com.example.data.ProfileValidation.MAX_HEIGHT_CM).map { "$it cm" }

fun feetItems(): List<String> = (3..8).map { "$it ft" }
fun inchItems(): List<String> = (0..11).map { "$it in" }

fun weightKgItems(min: Float = com.example.data.ProfileValidation.MIN_WEIGHT_KG, max: Float = com.example.data.ProfileValidation.MAX_WEIGHT_KG): List<String> {
    val minInt = min.toInt()
    val maxInt = max.toInt()
    return (minInt..maxInt).map { "$it kg" }
}

fun weeklyChangeItems(min: Float, max: Float): List<String> {
    if (max <= 0f) return listOf("0.0 kg/wk")
    val steps = ((max - min) / 0.1f).toInt().coerceAtLeast(0)
    return (0..steps).map { i ->
        val v = min + i * 0.1f
        String.format("%.1f kg/wk", v)
    }
}
