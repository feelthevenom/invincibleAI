@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.AppUpdateUiState
import com.example.BuildConfig
import com.example.GymViewModel

@Composable
fun AppUpdateSection(viewModel: GymViewModel) {
    val updateState by viewModel.appUpdateState.collectAsState()
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme

    Text("APP UPDATES", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = cs.surfaceContainerHigh),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = cs.primaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.SystemUpdate, null, tint = cs.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Version ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Build ${BuildConfig.VERSION_CODE}",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.35f))

            when (val state = updateState) {
                AppUpdateUiState.Idle -> {
                    Text(
                        "Check GitHub for the latest sideload release.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
                AppUpdateUiState.Checking -> {
                    GymLoadingIndicator(message = "Checking for updates…")
                }
                AppUpdateUiState.UpToDate -> {
                    Text(
                        "You're on the latest version.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.secondary
                    )
                }
                is AppUpdateUiState.Available -> {
                    Text(
                        "Update ${state.info.versionName} available",
                        style = MaterialTheme.typography.titleSmall,
                        color = cs.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (state.info.releaseNotes.isNotBlank()) {
                        Text(state.info.releaseNotes, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    if (state.downloading) {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = cs.primary,
                            trackColor = cs.surfaceContainerHighest
                        )
                        Text(
                            "Downloading… ${(state.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant
                        )
                    } else {
                        Button(
                            onClick = { viewModel.downloadAppUpdate() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Download update")
                        }
                    }
                }
                is AppUpdateUiState.ReadyToInstall -> {
                    Text(
                        "Update ${state.info.versionName} ready to install",
                        style = MaterialTheme.typography.titleSmall,
                        color = cs.secondary,
                        fontWeight = FontWeight.SemiBold
                    )
                    FilledTonalButton(
                        onClick = {
                            if (viewModel.appUpdateManager.canInstallPackages()) {
                                context.startActivity(viewModel.createAppUpdateInstallIntent())
                            } else {
                                context.startActivity(viewModel.appUpdateManager.openInstallPermissionSettings())
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Icon(Icons.Default.InstallMobile, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Install update")
                    }
                }
                is AppUpdateUiState.Error -> {
                    Text(state.message, style = MaterialTheme.typography.bodySmall, color = cs.error)
                }
            }

            val isDownloading = (updateState as? AppUpdateUiState.Available)?.downloading == true
            OutlinedButton(
                onClick = { viewModel.checkForAppUpdate(showUpToDateMessage = true) },
                enabled = updateState !is AppUpdateUiState.Checking && !isDownloading,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Text(if (updateState is AppUpdateUiState.Checking) "Checking…" else "Check for updates")
            }
        }
    }
}

@Composable
fun AppUpdatePromptDialog(viewModel: GymViewModel, onDismiss: () -> Unit) {
    val updateState by viewModel.appUpdateState.collectAsState()
    val showPrompt by viewModel.showUpdatePrompt.collectAsState()
    if (!showPrompt) return
    val context = LocalContext.current

    when (val state = updateState) {
        is AppUpdateUiState.Available -> AppUpdateAvailableDialog(
            state = state,
            viewModel = viewModel,
            context = context,
            onDismiss = onDismiss
        )
        is AppUpdateUiState.ReadyToInstall -> AppUpdateReadyDialog(
            state = state,
            viewModel = viewModel,
            context = context,
            onDismiss = onDismiss
        )
        else -> Unit
    }
}

@Composable
private fun AppUpdateAvailableDialog(
    state: AppUpdateUiState.Available,
    viewModel: GymViewModel,
    context: android.content.Context,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.SystemUpdate, null) },
        title = { Text("Update available") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Version ${state.info.versionName} is available.")
                if (state.info.releaseNotes.isNotBlank()) {
                    Text(state.info.releaseNotes, style = MaterialTheme.typography.bodySmall)
                }
                if (state.downloading) {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                }
            }
        },
        confirmButton = {
            if (!state.downloading) {
                TextButton(onClick = { viewModel.downloadAppUpdate() }) {
                    Text("Download")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Later") }
        }
    )
}

@Composable
private fun AppUpdateReadyDialog(
    state: AppUpdateUiState.ReadyToInstall,
    viewModel: GymViewModel,
    context: android.content.Context,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.InstallMobile, null) },
        title = { Text("Update ready to install") },
        text = {
            Text("Version ${state.info.versionName} has been downloaded.")
        },
        confirmButton = {
            TextButton(onClick = {
                if (viewModel.appUpdateManager.canInstallPackages()) {
                    context.startActivity(viewModel.createAppUpdateInstallIntent())
                } else {
                    context.startActivity(viewModel.appUpdateManager.openInstallPermissionSettings())
                }
            }) {
                Text("Install")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Later") }
        }
    )
}
