@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.example.ui.theme.*

@Composable
fun AppUpdateSection(viewModel: GymViewModel) {
    val updateState by viewModel.appUpdateState.collectAsState()
    val context = LocalContext.current

    Text("APP UPDATES", style = Typography.labelMedium, color = OnSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerHighest.copy(0.3f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Version ${BuildConfig.VERSION_NAME}",
                        style = Typography.titleMedium,
                        color = OnSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Build ${BuildConfig.VERSION_CODE}",
                        style = Typography.bodySmall,
                        color = OnSurfaceVariant
                    )
                }
                Icon(Icons.Default.SystemUpdate, null, tint = Primary)
            }

            when (val state = updateState) {
                AppUpdateUiState.Idle -> Unit
                AppUpdateUiState.Checking -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("Checking for updates…", style = Typography.bodySmall, color = OnSurfaceVariant)
                }
                AppUpdateUiState.UpToDate -> {
                    Text("You're on the latest version.", style = Typography.bodySmall, color = Secondary)
                }
                is AppUpdateUiState.Available -> {
                    Text(
                        "Update ${state.info.versionName} available",
                        style = Typography.bodyMedium,
                        color = Primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (state.info.releaseNotes.isNotBlank()) {
                        Text(state.info.releaseNotes, style = Typography.bodySmall, color = OnSurfaceVariant)
                    }
                    if (state.downloading) {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "Downloading… ${(state.progress * 100).toInt()}%",
                            style = Typography.bodySmall,
                            color = OnSurfaceVariant
                        )
                    } else {
                        Button(
                            onClick = { viewModel.downloadAppUpdate() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Download update")
                        }
                    }
                }
                is AppUpdateUiState.ReadyToInstall -> {
                    Text(
                        "Update ${state.info.versionName} ready to install",
                        style = Typography.bodyMedium,
                        color = Secondary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Button(
                        onClick = {
                            if (viewModel.appUpdateManager.canInstallPackages()) {
                                context.startActivity(viewModel.createAppUpdateInstallIntent())
                            } else {
                                context.startActivity(viewModel.appUpdateManager.openInstallPermissionSettings())
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Install update")
                    }
                }
                is AppUpdateUiState.Error -> {
                    Text(state.message, style = Typography.bodySmall, color = Error)
                }
            }

            val isDownloading = (updateState as? AppUpdateUiState.Available)?.downloading == true
            OutlinedButton(
                onClick = { viewModel.checkForAppUpdate(showUpToDateMessage = true) },
                enabled = updateState !is AppUpdateUiState.Checking && !isDownloading,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    if (updateState is AppUpdateUiState.Checking) "Checking…" else "Check for updates",
                    color = Primary
                )
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
        title = { Text("Update available") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Version ${state.info.versionName} is available.")
                if (state.info.releaseNotes.isNotBlank()) {
                    Text(state.info.releaseNotes, style = Typography.bodySmall, color = OnSurfaceVariant)
                }
                if (state.downloading) {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth()
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
