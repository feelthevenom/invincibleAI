package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.GymViewModel
import com.example.data.AppNotification
import com.example.data.DietDateUtils
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationHistoryScreen(viewModel: GymViewModel, onBack: () -> Unit) {
    val notifications by viewModel.allNotifications.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("All", "Hydration", "General")

    val filtered = remember(notifications, selectedTab) {
        when (selectedTab) {
            1 -> notifications.filter { it.category == "hydration" }
            2 -> notifications.filter { it.category != "hydration" }
            else -> notifications
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications", style = Typography.titleMedium) },
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
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = SurfaceContainerLow,
                contentColor = Primary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, style = Typography.labelMedium) }
                    )
                }
            }
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No notifications yet", style = Typography.bodyMedium, color = OnSurfaceVariant)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filtered, key = { it.id }) { item ->
                        NotificationHistoryCard(
                            notification = item,
                            onDismiss = { viewModel.deleteNotification(item.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationHistoryCard(notification: AppNotification, onDismiss: () -> Unit) {
    val timeFmt = remember { SimpleDateFormat("MMM d, yyyy · hh:mm a", Locale.getDefault()) }
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                if (notification.category == "hydration") Icons.Default.WaterDrop else Icons.Default.Notifications,
                null,
                tint = Primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(notification.title, style = Typography.titleSmall, fontWeight = FontWeight.Bold, color = OnSurface)
                Spacer(Modifier.height(4.dp))
                Text(notification.body, style = Typography.bodyMedium, color = OnSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Text(timeFmt.format(Date(notification.timestamp)), style = Typography.labelSmall, color = OnSurfaceVariant.copy(0.7f))
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, "Remove", tint = OnSurfaceVariant)
            }
        }
    }
}
