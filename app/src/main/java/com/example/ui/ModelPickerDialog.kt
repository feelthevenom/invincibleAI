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
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp
        ) {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
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
                        placeholder = { Text("Search models…") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        colors = m3OutlinedTextFieldColors()
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
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                WheelPicker(
                                    items = wheelLabels,
                                    selectedIndex = wheelIndex.coerceIn(0, wheelLabels.lastIndex),
                                    onSelected = { index ->
                                        draftModelId = filteredModels[index.coerceIn(0, filteredModels.lastIndex)].id
                                    },
                                    label = null,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
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
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            filteredModels.find { it.id == draftModelId }?.let(onSelect)
                            onDismiss()
                        },
                        enabled = filteredModels.isNotEmpty(),
                        shape = MaterialTheme.shapes.large
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
    val cs = MaterialTheme.colorScheme
    FilterChip(
        selected = enabled,
        onClick = onToggle,
        label = { Text("Free", fontWeight = FontWeight.SemiBold) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = cs.primaryContainer,
            selectedLabelColor = cs.onPrimaryContainer
        )
    )
}

@Composable
private fun ModelPickerListItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    ListItem(
        headlineContent = {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) cs.primary else cs.onSurface
            )
        },
        trailingContent = {
            if (selected) {
                Icon(Icons.Default.Check, null, tint = cs.primary, modifier = Modifier.size(20.dp))
            }
        },
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (selected) cs.primaryContainer.copy(alpha = 0.35f)
                else cs.surfaceContainerHighest.copy(alpha = 0.35f)
            )
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
    )
}
