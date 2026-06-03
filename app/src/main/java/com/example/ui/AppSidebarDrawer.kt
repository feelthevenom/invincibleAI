package com.example.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*

enum class MainOverlay { None, PersonalDetails, Settings }

@Composable
fun AppSidebarDrawer(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onPersonalDetails: () -> Unit,
    onSettings: () -> Unit,
    onBackupRestore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrimAlpha by animateFloatAsState(
        targetValue = if (isOpen) 0.5f else 0f,
        animationSpec = tween(300),
        label = "scrim"
    )
    val offsetFraction by animateFloatAsState(
        targetValue = if (isOpen) 0f else -1f,
        animationSpec = tween(350),
        label = "drawer"
    )

    if (isOpen || scrimAlpha > 0f) {
        Box(modifier = modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(scrimAlpha)
                    .background(Color.Black)
                    .clickable(onClick = onDismiss)
            )

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.78f)
                    .offset(x = (offsetFraction * 300).dp)
                    .background(Surface, RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Menu", style = Typography.headlineMedium, color = Primary)
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "Close", tint = OnSurface)
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                    SidebarMenuItem(
                        icon = { Icon(Icons.Default.Person, null, tint = Primary) },
                        title = "Personal Details",
                        subtitle = "View and edit your profile",
                        onClick = {
                            onDismiss()
                            onPersonalDetails()
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SidebarMenuItem(
                        icon = { Icon(Icons.Default.Settings, null, tint = Secondary) },
                        title = "Settings",
                        subtitle = "App preferences",
                        onClick = {
                            onDismiss()
                            onSettings()
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SidebarMenuItem(
                        icon = { Icon(Icons.Default.Backup, null, tint = Primary) },
                        title = "Backup & Restore",
                        subtitle = "Export or import gymai_backup.zip",
                        onClick = {
                            onDismiss()
                            onBackupRestore()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SidebarMenuItem(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceContainer, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(SurfaceContainerHigh, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, style = Typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = OnSurface)
            Text(subtitle, style = Typography.bodySmall, color = OnSurfaceVariant)
        }
    }
}
