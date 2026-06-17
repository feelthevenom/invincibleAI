@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.activity.compose.BackHandler
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import com.example.data.FoodMacroFormat
import com.example.data.FoodQuantityOptions
import com.example.data.FoodServingCatalog
import com.example.data.FoodServingUnit

@Composable
fun FoodImagePreview(
    imageUrl: String?,
    modifier: Modifier = Modifier
) {
    if (imageUrl.isNullOrBlank()) return
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(cs.surfaceVariant.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = "Food product image",
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            contentScale = ContentScale.Fit
        )
    }
}

/** Quantity field: tap the number to type; tap chevron/rest to open the wheel sheet. */
@Composable
fun ServingQuantityField(
    quantity: Double,
    onQuantityChange: (Double) -> Unit,
    onOpenPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    var editing by remember { mutableStateOf(false) }
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(FoodQuantityOptions.format(quantity)))
    }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val fieldPadding = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)
    val valueStyle = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.Bold,
        color = cs.onSurface
    )

    // Sync from outside when not editing
    LaunchedEffect(quantity) {
        if (!editing) {
            val formatted = FoodQuantityOptions.format(quantity)
            textFieldValue = TextFieldValue(formatted, selection = TextRange(formatted.length))
        }
    }

    // Auto-focus when editing starts
    LaunchedEffect(editing) {
        if (editing) {
            delay(100)
            focusRequester.requestFocus()
        }
    }

    fun commitAndStopEditing() {
        if (!editing) return
        editing = false
        val cleanText = textFieldValue.text.replace(',', '.')
        val parsed = cleanText.toDoubleOrNull()?.takeIf { it > 0 }
        if (parsed != null) {
            onQuantityChange(parsed)
            val formatted = FoodQuantityOptions.format(parsed)
            textFieldValue = TextFieldValue(formatted, selection = TextRange(formatted.length))
        } else {
            val formatted = FoodQuantityOptions.format(quantity)
            textFieldValue = TextFieldValue(formatted, selection = TextRange(formatted.length))
        }
        focusManager.clearFocus()
    }

    BackHandler(enabled = editing) {
        commitAndStopEditing()
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = cs.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            fieldPadding,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .widthIn(min = 40.dp, max = 100.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (!editing) {
                                textFieldValue = textFieldValue.copy(
                                    selection = TextRange(0, textFieldValue.text.length)
                                )
                                editing = true
                            }
                        }
                    )
            ) {
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { v ->
                        val s = v.text.replace(',', '.')
                        if (s.isEmpty() || s.matches(Regex("^\\d*(\\.\\d*)?$"))) {
                            textFieldValue = v.copy(text = s)
                            s.toDoubleOrNull()?.takeIf { it > 0 }?.let(onQuantityChange)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { state ->
                            if (!state.isFocused && editing) commitAndStopEditing()
                        },
                    textStyle = valueStyle,
                    singleLine = true,
                    enabled = editing,
                    cursorBrush = SolidColor(cs.primary),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { commitAndStopEditing() }
                    ),
                    decorationBox = { inner ->
                        Column {
                            Box {
                                if (!editing) {
                                    Text(textFieldValue.text, style = valueStyle)
                                } else {
                                    inner()
                                }
                            }
                            if (editing) {
                                Spacer(Modifier.height(2.dp))
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(2.dp)
                                        .background(cs.primary)
                                )
                            }
                        }
                    }
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (editing) commitAndStopEditing()
                            onOpenPicker()
                        }
                    ),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "Open quantity picker",
                    tint = cs.onSurfaceVariant
                )
            }
        }
    }
}

/** Measure field: tap label or chevron to open the wheel sheet. */
@Composable
fun ServingMeasureField(
    measureLabel: String,
    onOpenPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    Surface(
        onClick = {
            focusManager.clearFocus()
            onOpenPicker()
        },
        shape = RoundedCornerShape(12.dp),
        color = cs.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                measureLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = cs.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = cs.onSurfaceVariant
            )
        }
    }
}

/** Pill button like HealthifyMe "Net wt: 100.0 g" — opens quantity/measure sheet. */
@Composable
fun FoodNetWeightButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = cs.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(1.dp, cs.outlineVariant.copy(0.4f))
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = cs.onSurfaceVariant
        )
    }
}

@Composable
fun FoodQuantityMeasureSheet(
    quantity: Double,
    units: List<FoodServingUnit>,
    selectedUnit: FoodServingUnit,
    onQuantityChange: (Double) -> Unit,
    onUnitChange: (FoodServingUnit) -> Unit,
    onDismiss: () -> Unit,
    onDone: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val quantityStrings = remember { FoodQuantityOptions.values().map { FoodQuantityOptions.format(it) } }
    val unitLabels = remember(units) { units.map { it.label } }

    var draftQuantity by remember(quantity) { mutableDoubleStateOf(quantity) }
    var quantityText by remember(quantity) { mutableStateOf(FoodQuantityOptions.format(quantity)) }

    val quantityIndex = remember(draftQuantity, quantityStrings) {
        quantityStrings.indexOf(FoodQuantityOptions.format(draftQuantity)).let { idx ->
            if (idx >= 0) idx else {
                quantityStrings.minByOrNull { kotlin.math.abs(it.toDoubleOrNull()?.minus(draftQuantity) ?: Double.MAX_VALUE) }
                    ?.let { quantityStrings.indexOf(it) } ?: 0
            }
        }
    }
    val unitIndex = remember(selectedUnit, units) {
        units.indexOfFirst { it.id == selectedUnit.id }.coerceAtLeast(0)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = cs.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Text(
                    "Quantity",
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    color = cs.onSurfaceVariant
                )
                Text(
                    "Measure",
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    color = cs.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) {
                    EditableQuantityWheel(
                        items = quantityStrings,
                        selectedIndex = quantityIndex,
                        editText = quantityText,
                        onEditTextChange = { text ->
                            quantityText = text
                            text.toDoubleOrNull()?.takeIf { it > 0 }?.let { value ->
                                draftQuantity = value
                                onQuantityChange(value)
                            }
                        },
                        onSelectedIndex = { index ->
                            val value = quantityStrings.getOrNull(index)?.toDoubleOrNull() ?: return@EditableQuantityWheel
                            draftQuantity = value
                            quantityText = FoodQuantityOptions.format(value)
                            onQuantityChange(value)
                        }
                    )
                }
                Box(Modifier.weight(1f)) {
                    WheelPicker(
                        items = unitLabels,
                        selectedIndex = unitIndex,
                        onSelected = { index ->
                            units.getOrNull(index)?.let(onUnitChange)
                        },
                        compact = true
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun FoodMacroBreakdownCard(
    calories: Int,
    protein: Float,
    carbs: Float,
    fat: Float,
    fiber: Float,
    netWeightLabel: String,
    onNetWeightClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "Macronutrients Breakdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface
            )
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Calories", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    Text(
                        "$calories Cal",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = cs.onSurface
                    )
                }
                FoodNetWeightButton(label = netWeightLabel, onClick = onNetWeightClick)
            }
            HorizontalDivider(Modifier.padding(vertical = 16.dp), color = cs.outlineVariant.copy(0.35f))
            MacroRow("Proteins", "${FoodMacroFormat.grams(protein)} g", cs.primary)
            Spacer(Modifier.height(10.dp))
            MacroRow("Fats", "${FoodMacroFormat.grams(fat)} g", cs.error)
            Spacer(Modifier.height(10.dp))
            MacroRow("Carbs", "${FoodMacroFormat.grams(carbs)} g", cs.tertiary)
            Spacer(Modifier.height(10.dp))
            MacroRow("Fiber", "${FoodMacroFormat.grams(fiber)} g", cs.secondary)
        }
    }
}

@Composable
private fun MacroRow(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = color)
    }
}
