@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ModelDownloadManager

private const val HF_LITERTLM_URL = "https://huggingface.co/models?library=litert-lm"

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
                "Download, import, or remove on-device LiteRT-LM models. Select the active model above in AI Settings.",
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
    onDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onDelete: (String) -> Unit,
    onImport: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
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
                "Manage Offline Models",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                "Download or remove models here. Choose the active model in the On-device model picker above.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            listOf("offline_2b", "offline_4b").forEach { modelId ->
                val state = builtInStates[modelId] ?: BuiltInModelUiState()
                val title = if (modelId == "offline_2b") "Gemma 4 E2B-it (~2.4 GB)" else "Gemma 4 E4B-it (~3.4 GB)"
                val installed = state.isDownloaded
                val isActive = activeModelId == modelId && installed
                OfflineModelManageRow(
                    title = title,
                    subtitle = "Text + Vision · ${if (modelId == "offline_2b") "4 GB" else "5.5 GB"} RAM min",
                    status = when {
                        state.isDownloading -> "Downloading…"
                        installed && isActive -> "Installed · Currently active"
                        installed -> "Installed"
                        state.isCompatible -> "Not installed — tap download"
                        else -> "Device RAM too low for this model"
                    },
                    isDownloading = state.isDownloading,
                    progress = state.progress,
                    showDownload = !installed && !state.isDownloading && state.isCompatible,
                    showDelete = installed && !state.isDownloading,
                    showCancel = state.isDownloading,
                    onDownload = { onDownload(modelId) },
                    onDelete = { onDelete(modelId) },
                    onCancel = { onCancelDownload(modelId) }
                )
            }

            installedModels.filter { !it.isBuiltIn }.forEach { model ->
                val isActive = activeModelId == model.id
                OfflineModelManageRow(
                    title = model.displayName,
                    subtitle = "${model.capabilityLabel} · ${model.minRamGb} GB RAM min · Imported",
                    status = if (isActive) "Imported · Currently active" else "Imported",
                    isDownloading = false,
                    progress = 0f,
                    showDownload = false,
                    showDelete = true,
                    showCancel = false,
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
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(HF_LITERTLM_URL))
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Browse LiteRT-LM models on Hugging Face")
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
private fun OfflineModelManageRow(
    title: String,
    subtitle: String,
    status: String,
    isDownloading: Boolean,
    progress: Float,
    showDownload: Boolean,
    showDelete: Boolean,
    showCancel: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        color = cs.surfaceContainer
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (showCancel) {
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, "Cancel download", tint = cs.onSurfaceVariant)
                        }
                    }
                    if (showDownload) {
                        IconButton(onClick = onDownload) {
                            Icon(Icons.Default.Download, "Download", tint = cs.primary)
                        }
                    }
                    if (showDelete) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, "Delete", tint = cs.error)
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(status, style = MaterialTheme.typography.labelMedium, color = cs.secondary)
            if (isDownloading) {
                Spacer(Modifier.height(8.dp))
                GymModelDownloadProgress(progress = progress)
                Text(
                    "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
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
