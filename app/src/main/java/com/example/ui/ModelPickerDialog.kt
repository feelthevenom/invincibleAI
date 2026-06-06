package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.AiProviderConfig
import com.example.data.ModelSearchUtils
import com.example.ui.theme.*

@Composable
fun ModelPickerDialog(
    title: String,
    provider: String,
    models: List<AiProviderConfig.AiModel>,
    selectedModelId: String,
    onDismiss: () -> Unit,
    onSelect: (AiProviderConfig.AiModel) -> Unit
) {
    var freeOnly by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var draftModelId by remember(selectedModelId) { mutableStateOf(selectedModelId) }

    val showSearch = models.size > 6
    val useSearchList = showSearch

    val filteredModels = remember(models, freeOnly, searchQuery, provider) {
        var list = models
        if (freeOnly) {
            list = list.filter { AiProviderConfig.isFreeModel(provider, it) }
        }
        if (useSearchList && searchQuery.isNotBlank()) {
            list = ModelSearchUtils.filterAndRank(list, searchQuery)
        }
        list
    }

    LaunchedEffect(filteredModels, draftModelId) {
        if (filteredModels.isNotEmpty() && filteredModels.none { it.id == draftModelId }) {
            draftModelId = filteredModels.first().id
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(if (useSearchList) 0.78f else 0.55f),
            shape = RoundedCornerShape(20.dp),
            color = SurfaceContainer
        ) {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        title,
                        style = Typography.titleMedium,
                        color = Primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    FreeFilterChip(enabled = freeOnly, onToggle = { freeOnly = !freeOnly })
                }

                if (showSearch) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search models…", color = OnSurfaceVariant) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = Primary) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = OutlineVariant.copy(0.4f),
                            focusedTextColor = OnSurface,
                            unfocusedTextColor = OnSurface
                        )
                    )
                }

                Spacer(Modifier.height(12.dp))

                when {
                    filteredModels.isEmpty() -> {
                        Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (freeOnly) "No free models match your filter." else "No models match your search.",
                                style = Typography.bodyMedium,
                                color = OnSurfaceVariant
                            )
                        }
                    }
                    useSearchList -> {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredModels, key = { it.id }) { model ->
                                ModelPickerListItem(
                                    label = AiProviderConfig.modelWheelLabel(provider, model),
                                    selected = model.id == draftModelId,
                                    onClick = { draftModelId = model.id }
                                )
                            }
                        }
                    }
                    else -> {
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            val wheelLabels = filteredModels.map {
                                AiProviderConfig.modelWheelLabel(provider, it)
                            }
                            val wheelIndex = filteredModels.indexOfFirst { it.id == draftModelId }
                                .coerceAtLeast(0)
                            WheelPicker(
                                items = wheelLabels,
                                selectedIndex = wheelIndex.coerceIn(0, wheelLabels.lastIndex),
                                onSelected = { index ->
                                    draftModelId = filteredModels[index.coerceIn(0, filteredModels.lastIndex)].id
                                },
                                label = null
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = OnSurfaceVariant)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            filteredModels.find { it.id == draftModelId }?.let(onSelect)
                            onDismiss()
                        },
                        enabled = filteredModels.isNotEmpty(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Select")
                    }
                }
            }
        }
    }
}

@Composable
private fun FreeFilterChip(enabled: Boolean, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .then(
                if (enabled) {
                    Modifier.background(Primary, shape)
                } else {
                    Modifier
                        .border(1.dp, Primary, shape)
                        .background(androidx.compose.ui.graphics.Color.Transparent, shape)
                }
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Free",
            style = Typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) OnPrimary else Primary
        )
    }
}

@Composable
private fun ModelPickerListItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (selected) Primary.copy(alpha = 0.12f) else SurfaceContainerHighest.copy(0.35f),
                shape
            )
            .border(
                1.dp,
                if (selected) Primary.copy(0.5f) else OutlineVariant.copy(0.25f),
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = Typography.bodyMedium,
            color = if (selected) Primary else OnSurface,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(Icons.Default.Check, null, tint = Primary, modifier = Modifier.size(20.dp))
        }
    }
}
