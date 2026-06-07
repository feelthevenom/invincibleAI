@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ModelDownloadManager

@Composable
fun OfflineModelsSettingCard(
    installedCount: Int,
    activeModelName: String?,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = cs.surfaceContainer
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(cs.primary.copy(0.12f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Memory, null, tint = cs.primary, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Text("Offline Models", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Download, import, or remove on-device LiteRT-LM models for private Coach AI.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            Surface(shape = RoundedCornerShape(20.dp), color = cs.primary.copy(0.14f)) {
                Text(
                    when {
                        activeModelName != null -> "Active: $activeModelName"
                        installedCount > 0 -> "$installedCount installed — tap to manage"
                        else -> "No models installed"
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = cs.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun OfflineModelsBottomSheet(
    installedModels: List<ModelDownloadManager.InstalledOfflineModel>,
    builtInStates: Map<String, BuiltInModelUiState>,
    activeModelId: String?,
    onDismiss: () -> Unit,
    onSelectModel: (String) -> Unit,
    onDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onDelete: (String) -> Unit,
    onImport: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = cs.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                "Offline Models",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                "Select a model for Coach AI. Vision-capable models support meal photo analysis.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            listOf("offline_2b", "offline_4b").forEach { modelId ->
                val state = builtInStates[modelId] ?: BuiltInModelUiState()
                val title = if (modelId == "offline_2b") "Gemma 4 E2B-it (~2.4 GB)" else "Gemma 4 E4B-it (~3.4 GB)"
                val installed = state.isDownloaded
                val selected = activeModelId == modelId && installed
                OfflineModelSheetRow(
                    title = title,
                    subtitle = "Text + Vision · ${if (modelId == "offline_2b") "4 GB" else "5 GB"} RAM min",
                    status = when {
                        state.isDownloading -> "Downloading…"
                        installed && selected -> "Ready · Active"
                        installed -> "Ready"
                        state.isCompatible -> "Tap download"
                        else -> "Needs more RAM"
                    },
                    isSelected = selected,
                    isDownloading = state.isDownloading,
                    progress = state.progress,
                    showDownload = !installed && !state.isDownloading && state.isCompatible,
                    showDelete = installed,
                    showCancel = state.isDownloading,
                    onSelect = { if (installed) onSelectModel(modelId) },
                    onDownload = { onDownload(modelId) },
                    onDelete = { onDelete(modelId) },
                    onCancel = { onCancelDownload(modelId) }
                )
            }

            installedModels.filter { !it.isBuiltIn }.forEach { model ->
                val selected = activeModelId == model.id
                OfflineModelSheetRow(
                    title = model.displayName,
                    subtitle = "${model.capabilityLabel} · ${model.minRamGb} GB RAM min · Imported",
                    status = if (selected) "Ready · Active" else "Ready",
                    isSelected = selected,
                    isDownloading = false,
                    progress = 0f,
                    showDownload = false,
                    showDelete = true,
                    showCancel = false,
                    onSelect = { onSelectModel(model.id) },
                    onDownload = {},
                    onDelete = { onDelete(model.id) },
                    onCancel = {}
                )
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.FileUpload, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Import .litertlm model")
            }
        }
    }
}

data class BuiltInModelUiState(
    val isDownloaded: Boolean = false,
    val isDownloading: Boolean = false,
    val progress: Float = 0f,
    val isCompatible: Boolean = true
)

@Composable
private fun OfflineModelSheetRow(
    title: String,
    subtitle: String,
    status: String,
    isSelected: Boolean,
    isDownloading: Boolean,
    progress: Float,
    showDownload: Boolean,
    showDelete: Boolean,
    showCancel: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onSelect,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) cs.primary else cs.surfaceContainer
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) cs.onPrimary else cs.onSurface
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) cs.onPrimary.copy(0.85f) else cs.onSurfaceVariant
                    )
                }
                if (isSelected) {
                    Icon(Icons.Default.Check, null, tint = cs.onPrimary)
                } else if (showCancel) {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, "Cancel download", tint = cs.onSurfaceVariant)
                    }
                } else if (showDownload) {
                    IconButton(onClick = onDownload) {
                        Icon(Icons.Default.Download, "Download", tint = cs.primary)
                    }
                } else if (showDelete) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, "Delete", tint = cs.error)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                status,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) cs.onPrimary.copy(0.9f) else cs.secondary
            )
            if (isDownloading) {
                Spacer(Modifier.height(8.dp))
                GymModelDownloadProgress(progress = progress)
                Text(
                    "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) cs.onPrimary else cs.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
fun UnsupportedModelDialog(message: String, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, null, tint = cs.error) },
        title = { Text("Unsupported model") },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
