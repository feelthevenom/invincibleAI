@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import coil.compose.AsyncImage
import com.example.GymViewModel
import com.example.data.UserProfile
import com.example.ui.theme.*

@Composable
fun MainScreen(viewModel: GymViewModel) {
    val profile by viewModel.userProfile.collectAsState()
    MainScreenContent(
        viewModel = viewModel,
        profile = profile,
        dietTab = { DietTab(viewModel) },
        workoutTab = { WorkoutTab(viewModel) },
        onPersonalDetailsSave = { td, dc, p, c, f, fi, weekly, cuisines ->
            viewModel.savePersonalDetailsEdits(td, dc, p, c, f, fi, weekly, cuisines)
        }
    )
}

@Composable
fun MainScreenContent(
    viewModel: GymViewModel,
    profile: UserProfile,
    dietTab: @Composable () -> Unit,
    workoutTab: @Composable () -> Unit,
    onPersonalDetailsSave: (Float, Int, Int, Int, Int, Int, Float?, String?) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var drawerOpen by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf(MainOverlay.None) }
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                Column {
                    HorizontalDivider(color = OutlineVariant.copy(0.2f))
                    NavigationBar(
                        containerColor = Surface.copy(alpha = 0.9f),
                        contentColor = OnSurfaceVariant,
                        tonalElevation = 0.dp
                    ) {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = { Icon(Icons.Default.Home, null) },
                            label = { Text("Home", style = Typography.labelMedium) },
                            colors = navColors(selectedTab == 0)
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = { Icon(Icons.Default.Restaurant, null) },
                            label = { Text("Diet", style = Typography.labelMedium) },
                            colors = navColors(selectedTab == 1)
                        )
                        NavigationBarItem(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            icon = { Icon(Icons.Default.FitnessCenter, null) },
                            label = { Text("Workout", style = Typography.labelMedium) },
                            colors = navColors(selectedTab == 2)
                        )
                        NavigationBarItem(
                            selected = selectedTab == 3,
                            onClick = { selectedTab = 3 },
                            icon = { Icon(Icons.Default.Psychology, null) },
                            label = { Text("Coach", style = Typography.labelMedium) },
                            colors = navColors(selectedTab == 3)
                        )
                    }
                }
            },
            topBar = {
                TopAppBar(
                    title = { Text("GYM AI", style = Typography.headlineMedium, color = Primary) },
                    navigationIcon = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            IconButton(onClick = { drawerOpen = true }) {
                                Icon(Icons.Default.Menu, "Menu", tint = OnSurface)
                            }
                            AsyncImage(
                                model = "https://lh3.googleusercontent.com/aida-public/AB6AXuDIOsswt2MZPJrY9NxjNjEGkusn6t4irxXiZNd7S9g1R8FKobj-FCLjxCO9GWjsVMoEE7BQnFJ7P9hKj6cs9WYYHH9ycIIPKS_2hQo1KfCGEK6LzTpTfXcKWs4DjdllMY50yP29c3ECZ210w6NIr0RYfFer8kUSJjoH6rkvnf6GSHb2ctOqoANY1ZlXPzHY-SGBGE9IFG4l48QPhMKJ2qHxCa3w8TYm8qYJbwlCbGXIA2Z5JYNP-CbHNey9KIxVp2iEa38YsAJN2Og2",
                                contentDescription = "Profile",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(SurfaceContainerHighest)
                                    .clickable { drawerOpen = true }
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.Notifications, "Notifications", tint = Primary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
                )
            },
            containerColor = Background
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                when (selectedTab) {
                    0 -> DashboardTab(profile)
                    1 -> dietTab()
                    2 -> workoutTab()
                    else -> DashboardTab(profile)
                }
            }
        }

        AppSidebarDrawer(
            isOpen = drawerOpen,
            onDismiss = { drawerOpen = false },
            onPersonalDetails = { overlay = MainOverlay.PersonalDetails },
            onSettings = { overlay = MainOverlay.Settings },
            onBackupRestore = {
                context.startActivity(Intent(context, com.example.BackupRestoreActivity::class.java))
            },
            modifier = Modifier.zIndex(2f)
        )

        when (overlay) {
            MainOverlay.PersonalDetails -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(3f)
                    .background(Background)
            ) {
                PersonalDetailsScreenContent(
                    profile = profile,
                    onBack = { overlay = MainOverlay.None },
                    onSave = { td, dc, p, c, f, fi, weekly, cuisines ->
                        onPersonalDetailsSave(td, dc, p, c, f, fi, weekly, cuisines)
                        overlay = MainOverlay.None
                    }
                )
            }
            MainOverlay.Settings -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(3f)
                    .background(Background)
            ) {
                SettingsScreen(viewModel = viewModel, onBack = { overlay = MainOverlay.None })
            }
            MainOverlay.None -> {}
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    // Cannot easily preview MainScreenContent without a mock ViewModel now,
    // so we'll just omit the preview or provide a mock if needed.
}

@Composable
private fun navColors(selected: Boolean) = NavigationBarItemDefaults.colors(
    selectedIconColor = Secondary,
    selectedTextColor = Secondary,
    indicatorColor = androidx.compose.ui.graphics.Color.Transparent,
    unselectedIconColor = OnSurfaceVariant.copy(0.6f),
    unselectedTextColor = OnSurfaceVariant.copy(0.6f)
)
