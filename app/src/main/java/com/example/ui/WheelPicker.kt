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
import androidx.compose.material3.Text
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
fun WheelPicker(
    items: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    if (items.isEmpty()) return
    val safeIndex = selectedIndex.coerceIn(0, items.lastIndex)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = safeIndex)
    val flingBehavior = rememberSnapFlingBehavior(
        lazyListState = listState,
        snapPosition = SnapPosition.Center
    )
    val centeredIndex by remember {
        derivedStateOf { listState.centeredItemIndex().coerceIn(0, items.lastIndex) }
    }
    val contentPadding = PaddingValues(vertical = (WheelHeight - WheelItemHeight) / 2)

    LaunchedEffect(safeIndex, items.size) {
        if (listState.centeredItemIndex() != safeIndex) {
            listState.animateScrollToItem(safeIndex)
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
                .height(WheelHeight)
                .background(SurfaceContainer, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WheelItemHeight)
                    .background(Primary.copy(0.12f), RoundedCornerShape(8.dp))
            )
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
                            fontSize = if (isSelected) 20.sp else 16.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (isSelected) Primary else OnSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(WheelItemHeight)
                            .wrapContentHeight(Alignment.CenterVertically)
                            .alpha(if (isSelected) 1f else 0.5f)
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
