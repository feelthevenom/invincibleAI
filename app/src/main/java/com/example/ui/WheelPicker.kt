package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlin.math.abs
import com.example.data.FoodQuantityOptions

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
fun EditableQuantityWheel(
    items: List<String>,
    selectedIndex: Int,
    editText: String,
    onEditTextChange: (String) -> Unit,
    onSelectedIndex: (Int) -> Unit,
    onFocusChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val safeIndex = selectedIndex.coerceIn(0, items.lastIndex)
    val itemHeight = 40.dp
    val wheelHeight = itemHeight * 5
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = safeIndex)
    val flingBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(
        lazyListState = listState,
        snapPosition = androidx.compose.foundation.gestures.snapping.SnapPosition.Center
    )
    val centeredIndex by remember {
        derivedStateOf { listState.centeredItemIndex().coerceIn(0, items.lastIndex) }
    }
    val contentPadding = PaddingValues(vertical = (wheelHeight - itemHeight) / 2)
    val cs = MaterialTheme.colorScheme
    var editing by remember { mutableStateOf(false) }
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(editText))
    }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // Sync from outside when not editing
    LaunchedEffect(editText) {
        if (!editing) {
            textFieldValue = TextFieldValue(editText, selection = TextRange(editText.length))
        }
    }

    LaunchedEffect(safeIndex, items.size) {
        if (!editing && listState.centeredItemIndex() != safeIndex) {
            listState.animateScrollToItem(safeIndex)
        }
    }

    LaunchedEffect(listState, items.size) {
        snapshotFlow { listState.centeredItemIndex() }
            .distinctUntilChanged()
            .collect { index ->
                if (index in items.indices && !editing) {
                    onSelectedIndex(index)
                }
            }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (scrolling && editing) {
                    focusManager.clearFocus()
                    editing = false
                }
            }
    }

    LaunchedEffect(editing) {
        onFocusChange(editing)
        if (editing) {
            delay(100)
            focusRequester.requestFocus()
        }
    }

    fun commitEdit() {
        if (!editing) return
        editing = false
        val cleanText = textFieldValue.text.replace(',', '.')
        val parsed = cleanText.toDoubleOrNull()?.takeIf { it > 0 }
        if (parsed != null) {
            val formatted = FoodQuantityOptions.format(parsed)
            onEditTextChange(formatted)
            textFieldValue = TextFieldValue(formatted, selection = TextRange(formatted.length))
        } else {
            val fallback = items.getOrElse(centeredIndex) { textFieldValue.text }
            onEditTextChange(fallback)
            textFieldValue = TextFieldValue(fallback, selection = TextRange(fallback.length))
        }
        focusManager.clearFocus()
    }

    BackHandler(enabled = editing) {
        commitEdit()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(wheelHeight),
        contentAlignment = Alignment.Center
    ) {
        Column(Modifier.fillMaxWidth()) {
            HorizontalDivider(color = cs.outlineVariant.copy(0.35f), thickness = 1.dp)
            Spacer(Modifier.height(itemHeight - 2.dp))
            HorizontalDivider(color = cs.outlineVariant.copy(0.35f), thickness = 1.dp)
        }
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            userScrollEnabled = !editing
        ) {
            items(items.size) { index ->
                val isSelected = index == centeredIndex
                Text(
                    text = items[index],
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = if (isSelected) 28.sp else 15.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = if (isSelected) cs.onSurface else cs.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .wrapContentHeight(Alignment.CenterVertically)
                        .alpha(if (isSelected && editing) 0f else if (isSelected) 1f else 0.38f)
                        .then(
                            if (isSelected && !editing) {
                                Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        textFieldValue = TextFieldValue(
                                            items[index], 
                                            selection = TextRange(0, items[index].length)
                                        )
                                        editing = true
                                    }
                                )
                            } else Modifier
                        )
                )
            }
        }
        if (editing) {
            BasicTextField(
                value = textFieldValue,
                onValueChange = { v ->
                    val s = v.text.replace(',', '.')
                    if (s.isEmpty() || s.matches(Regex("^\\d*(\\.\\d*)?$"))) {
                        textFieldValue = v.copy(text = s)
                        s.toDoubleOrNull()?.takeIf { it > 0 }?.let { onEditTextChange(s) }
                    }
                },
                modifier = Modifier
                    .widthIn(min = 48.dp, max = 100.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { state ->
                        if (!state.isFocused && editing) commitEdit()
                    },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = cs.onSurface
                ),
                singleLine = true,
                cursorBrush = SolidColor(cs.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { commitEdit() }),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            inner()
                            Spacer(Modifier.height(2.dp))
                            Box(Modifier.width(60.dp).height(2.dp).background(cs.primary))
                        }
                    }
                }
            )
        }
    }
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
    val cs = MaterialTheme.colorScheme

    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    showDialog = true
                },
            shape = RoundedCornerShape(12.dp),
            color = cs.surfaceContainerLow,
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    items[safeIndex],
                    style = MaterialTheme.typography.bodyLarge,
                    color = cs.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text("Change", style = MaterialTheme.typography.labelMedium, color = cs.primary)
            }
        }
    }

    if (showDialog) {
        WheelPickerDialog(
            title = label,
            items = items,
            selectedIndex = safeIndex,
            onDismiss = { showDialog = false },
            onConfirm = { index ->
                onConfirm(index)
                showDialog = false
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
    val cs = MaterialTheme.colorScheme

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
            style = MaterialTheme.typography.headlineMedium,
            color = cs.primary,
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
    val flingBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(
        lazyListState = listState,
        snapPosition = androidx.compose.foundation.gestures.snapping.SnapPosition.Center
    )
    val centeredIndex by remember {
        derivedStateOf { listState.centeredItemIndex().coerceIn(0, items.lastIndex) }
    }
    val contentPadding = PaddingValues(vertical = (wheelHeight - itemHeight) / 2)
    val cs = MaterialTheme.colorScheme

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
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(wheelHeight)
                .then(
                    if (compact) Modifier
                    else Modifier.background(cs.surfaceContainerLow, RoundedCornerShape(12.dp))
                ),
            contentAlignment = Alignment.Center
        ) {
            if (compact) {
                Column(Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = cs.outlineVariant.copy(0.35f), thickness = 1.dp)
                    Spacer(Modifier.height(itemHeight - 2.dp))
                    HorizontalDivider(color = cs.outlineVariant.copy(0.35f), thickness = 1.dp)
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
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = when {
                                isSelected && compact -> 28.sp
                                isSelected -> 24.sp
                                compact -> 15.sp
                                else -> 16.sp
                            },
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        color = if (isSelected) cs.onSurface else cs.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(itemHeight)
                            .wrapContentHeight(Alignment.CenterVertically)
                            .alpha(if (isSelected) 1f else 0.38f)
                    )
                }
            }
        }
    }
}

@Composable
fun HeightUnitToggle(useMetric: Boolean, onToggle: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = useMetric,
            onClick = { onToggle(true) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            label = { Text("cm") }
        )
        SegmentedButton(
            selected = !useMetric,
            onClick = { onToggle(false) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            label = { Text("ft/in") }
        )
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
