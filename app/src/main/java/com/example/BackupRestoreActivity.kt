package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.data.backup.BackupRestoreManager
import com.example.data.backup.AppRelaunch
import com.example.ui.theme.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BackupRestoreActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(color = Background) {
                    BackupRestoreScreen(
                        onBack = { finish() },
                        onExport = { uri, onResult ->
                            lifecycleScope.launch {
                                val app = application as GymApplication
                                val userProfile = app.repository.userProfile.first()
                                    ?: com.example.data.UserProfile()
                                val result = BackupRestoreManager.exportBackup(
                                    this@BackupRestoreActivity,
                                    uri,
                                    userProfile
                                )
                                onResult(result)
                            }
                        },
                        onValidate = { uri, onResult ->
                            lifecycleScope.launch {
                                val result = BackupRestoreManager.validateBackup(this@BackupRestoreActivity, uri)
                                onResult(result)
                            }
                        },
                        onImport = { uri, onResult ->
                            lifecycleScope.launch {
                                val result = BackupRestoreManager.importBackup(this@BackupRestoreActivity, uri)
                                onResult(result)
                                if (result is BackupRestoreManager.BackupResult.Success) {
                                    AppRelaunch.afterRestore(this@BackupRestoreActivity)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupRestoreScreen(
    onBack: () -> Unit,
    onExport: (android.net.Uri, (BackupRestoreManager.BackupResult) -> Unit) -> Unit,
    onValidate: (android.net.Uri, (BackupRestoreManager.BackupResult) -> Unit) -> Unit,
    onImport: (android.net.Uri, (BackupRestoreManager.BackupResult) -> Unit) -> Unit
) {
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var showRestoreConfirm by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupRestoreManager.BACKUP_MIME)
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isLoading = true
        onExport(uri) { result ->
            isLoading = false
            when (result) {
                BackupRestoreManager.BackupResult.Success -> {
                    isError = false
                    statusMessage = "Backup saved successfully as ${BackupRestoreManager.BACKUP_FILE_NAME}"
                }
                is BackupRestoreManager.BackupResult.Error -> {
                    isError = true
                    statusMessage = result.message
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isLoading = true
        onValidate(uri) { result ->
            when (result) {
                BackupRestoreManager.BackupResult.Success -> {
                    pendingRestoreUri = uri
                    showRestoreConfirm = true
                    isLoading = false
                }
                is BackupRestoreManager.BackupResult.Error -> {
                    isLoading = false
                    isError = true
                    statusMessage = result.message
                }
            }
        }
    }

    if (showRestoreConfirm && pendingRestoreUri != null) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("Restore backup?", color = OnSurface) },
            text = {
                Text(
                    "This will replace your current data with the backup. This cannot be undone.",
                    color = OnSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreConfirm = false
                    val uri = pendingRestoreUri ?: return@TextButton
                    isLoading = true
                    onImport(uri) { result ->
                        isLoading = false
                        when (result) {
                            BackupRestoreManager.BackupResult.Success -> {
                                isError = false
                                statusMessage = "Backup restored successfully. App data reloaded."
                            }
                            is BackupRestoreManager.BackupResult.Error -> {
                                isError = true
                                statusMessage = result.message
                            }
                        }
                    }
                }) { Text("Restore", color = Error) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) { Text("Cancel", color = Primary) }
            },
            containerColor = SurfaceContainer
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & Restore", style = Typography.headlineMedium, color = Primary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OnSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
            )
        },
        containerColor = Background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                "All data stays on your device. Export a backup to Google Drive, SD card, or any file manager. No cloud servers required.",
                style = Typography.bodyMedium,
                color = OnSurfaceVariant
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerHighest.copy(0.4f))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Backup contains:", style = Typography.labelMedium, color = Primary)
                    Text("• manifest.json", style = Typography.bodySmall, color = OnSurfaceVariant)
                    Text("• settings.json (profile & preferences)", style = Typography.bodySmall, color = OnSurfaceVariant)
                    Text("• database/gym_database (SQLite)", style = Typography.bodySmall, color = OnSurfaceVariant)
                    Text("• progress_photos/ (if any)", style = Typography.bodySmall, color = OnSurfaceVariant)
                }
            }

            Button(
                onClick = { exportLauncher.launch(BackupRestoreManager.BACKUP_FILE_NAME) },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary)
            ) {
                Icon(Icons.Default.Backup, null)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Export Backup", style = Typography.titleMedium)
                    Text("Save gymai_backup.zip", style = Typography.bodySmall, color = OnPrimary.copy(0.8f))
                }
            }

            Button(
                onClick = { importLauncher.launch(arrayOf(BackupRestoreManager.BACKUP_MIME, "application/octet-stream")) },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Secondary, contentColor = OnPrimary)
            ) {
                Icon(Icons.Default.Restore, null)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Restore Backup", style = Typography.titleMedium)
                    Text("Select gymai_backup.zip", style = Typography.bodySmall, color = OnPrimary.copy(0.8f))
                }
            }

            if (isLoading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            }

            statusMessage?.let { msg ->
                Text(
                    msg,
                    color = if (isError) Error else Secondary,
                    style = Typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.weight(1f))

            Text(
                "Optional: Android can back up app data if your phone uses a Google account and \"Back up to Google Drive\" is turned on in system settings. The app does not ask for Google login — your phone handles that. For a reliable copy, use Export Backup above.",
                style = Typography.bodySmall,
                color = OnSurfaceVariant.copy(0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
