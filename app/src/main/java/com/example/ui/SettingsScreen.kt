@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.GymViewModel
import com.example.ui.theme.*

@Composable
fun SettingsScreen(
    viewModel: GymViewModel,
    onBack: () -> Unit,
    onOpenWaterReminder: () -> Unit = {},
    onOpenWorkoutReminder: () -> Unit = {}
) {
    val profile by viewModel.userProfile.collectAsState()
    val context = LocalContext.current
    var showThemeSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = Typography.headlineMedium, color = MaterialTheme.colorScheme.primary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Text("THEME", style = Typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                AppThemeSettingCard(
                    currentMode = profile.themeMode,
                    onClick = { showThemeSheet = true }
                )
            }

            item {
                Text("REMINDERS", style = Typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                SettingsActionCard(
                    title = "Drink Water Reminder",
                    subtitle = "Hydration alerts and schedule",
                    icon = Icons.Default.WaterDrop,
                    onClick = onOpenWaterReminder
                )
                Spacer(Modifier.height(10.dp))
                SettingsActionCard(
                    title = "Workout Reminder",
                    subtitle = "Gym alarm and daily schedule",
                    icon = Icons.Default.FitnessCenter,
                    onClick = onOpenWorkoutReminder
                )
            }

            item {
                Text("DATA MANAGEMENT", style = Typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                SettingsActionCard(
                    title = "Backup & Restore",
                    subtitle = "Export or import your data (gym-ai_backup.zip)",
                    icon = Icons.Default.Backup,
                    onClick = {
                        context.startActivity(Intent(context, com.example.BackupRestoreActivity::class.java))
                    }
                )
            }

            item {
                Text("SYSTEM", style = Typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                AppUpdateSection(viewModel = viewModel)
            }
        }
    }

    if (showThemeSheet) {
        AppThemeBottomSheet(
            currentMode = profile.themeMode,
            onDismiss = { showThemeSheet = false },
            onModeSelected = {
                viewModel.updateThemeMode(it)
                showThemeSheet = false
            }
        )
    }
}

@Composable
private fun AppThemeSettingCard(currentMode: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val label = themeModeLabel(currentMode)
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
                    Icon(Icons.Default.LightMode, null, tint = cs.primary, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    "App Theme",
                    style = Typography.titleMedium,
                    color = cs.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Switch between light, dark, or follow system appearance.",
                style = Typography.bodySmall,
                color = cs.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = cs.primary.copy(0.14f)
            ) {
                Text(
                    label,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = Typography.labelLarge,
                    color = cs.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun AppThemeBottomSheet(
    currentMode: String,
    onDismiss: () -> Unit,
    onModeSelected: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val options = listOf(
        "light" to "Light Theme",
        "dark" to "Dark Theme",
        "system" to "Follow System"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = cs.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                "App Theme",
                style = Typography.titleLarge,
                color = cs.onSurface,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            options.forEach { (mode, label) ->
                val selected = currentMode == mode
                Surface(
                    onClick = { onModeSelected(mode) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) cs.primary else cs.surfaceContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            label,
                            modifier = Modifier.weight(1f),
                            style = Typography.bodyLarge,
                            color = if (selected) cs.onPrimary else cs.onSurface,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                        if (selected) {
                            Icon(Icons.Default.Check, null, tint = cs.onPrimary)
                        }
                    }
                }
            }
        }
    }
}

private fun themeModeLabel(mode: String): String = when (mode) {
    "light" -> "Light Theme"
    "dark" -> "Dark Theme"
    else -> "Follow System"
}

@Composable
private fun SettingsActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(0.12f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = Typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = Typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
        }
    }
}
