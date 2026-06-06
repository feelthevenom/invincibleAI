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
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.notifications.WaterNotificationHelper
import com.example.notifications.WorkoutNotificationHelper
import androidx.compose.ui.tooling.preview.Preview
import coil.compose.AsyncImage
import com.example.GymViewModel
import com.example.data.UserProfile
import com.example.ui.theme.*

@Composable
fun MainScreen(viewModel: GymViewModel) {
    val profile by viewModel.userProfile.collectAsState()
    val modelsRevision by viewModel.modelsRevision.collectAsState()
    val aiEnabled = remember(profile, profile.aiSplitModels, profile.aiTextProvider, profile.aiProvider, profile.offlineModelId, modelsRevision) {
        viewModel.isAiConfigured()
    }
    MainScreenContent(
        viewModel = viewModel,
        profile = profile,
        aiEnabled = aiEnabled,
        dietTab = { DietTab(viewModel) },
        onPersonalDetailsSave = { td, dc, p, c, f, fi, weekly, cuisines ->
            viewModel.savePersonalDetailsEdits(td, dc, p, c, f, fi, weekly, cuisines)
        }
    )
}

@Composable
fun MainScreenContent(
    viewModel: GymViewModel,
    profile: UserProfile,
    aiEnabled: Boolean,
    dietTab: @Composable () -> Unit,
    onPersonalDetailsSave: (Float, Int, Int, Int, Int, Int, Float?, String?) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var drawerOpen by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf(MainOverlay.None) }
    val context = LocalContext.current
    var lastBackPress by remember { mutableLongStateOf(0L) }
    var showWaterReminderDialog by remember { mutableStateOf(false) }

    LaunchedEffect(
        profile.workoutReminderEnabled,
        profile.workoutReminderTimeMinute,
        profile.workoutReminderRepeat,
        profile.notificationsEnabled
    ) {
        if (profile.workoutReminderEnabled) {
            WorkoutNotificationHelper.scheduleReminder(context, profile)
        } else {
            WorkoutNotificationHelper.cancelReminder(context)
        }
    }

    LaunchedEffect(
        profile.waterReminderEnabled,
        profile.waterReminderMode,
        profile.waterReminderIntervalMinutes,
        profile.waterReminderTimesPerDay,
        profile.waterReminderDailyTimeMinute,
        profile.waterReminderWeeklyDay,
        profile.waterReminderWindowStartMinute,
        profile.waterReminderWindowEndMinute,
        profile.notificationsEnabled
    ) {
        if (profile.waterReminderEnabled) {
            WaterNotificationHelper.scheduleReminders(context, profile)
        } else {
            WaterNotificationHelper.scheduleReminders(context, false)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setNotificationsEnabled(true)
            Toast.makeText(context, "Notifications enabled", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Notifications are required for hydration reminders", Toast.LENGTH_SHORT).show()
        }
        showWaterReminderDialog = false
    }

    val workoutHandlesBack by viewModel.workoutHandlesBack.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.checkForAppUpdate(promptIfAvailable = true)
    }

    BackHandler(enabled = !workoutHandlesBack) {
        when {
            overlay == MainOverlay.WaterReminderSettings -> overlay = MainOverlay.WaterTracking
            overlay == MainOverlay.WaterTracking -> overlay = MainOverlay.None
            overlay == MainOverlay.WorkoutReminder -> overlay = MainOverlay.None
            overlay == MainOverlay.NotificationHistory -> overlay = MainOverlay.None
            overlay != MainOverlay.None -> overlay = MainOverlay.None
            drawerOpen -> drawerOpen = false
            selectedTab != 0 -> selectedTab = 0
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackPress < 2000L) {
                    (context as? Activity)?.finish()
                } else {
                    lastBackPress = now
                    Toast.makeText(context, "Tap again to exit", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val isKeyboardVisible = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                if (!isKeyboardVisible || selectedTab != 3) {
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
                }
            },
            topBar = {
                if (selectedTab != 3) {
                    TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("GYM AI", style = Typography.headlineMedium, color = Primary)
                            if (aiEnabled) {
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = "AI mode active",
                                    tint = Primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
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
                        IconButton(onClick = {
                            if (!profile.notificationsEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                                PackageManager.PERMISSION_GRANTED
                            ) {
                                showWaterReminderDialog = true
                            } else {
                                overlay = MainOverlay.NotificationHistory
                            }
                        }) {
                            Icon(Icons.Default.Notifications, "Notifications", tint = Primary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
                )
                }
            },
            containerColor = Background
        ) { padding ->
            Box(modifier = Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize()) {
                when (selectedTab) {
                    0 -> DashboardTab(
                        viewModel = viewModel,
                        onOpenWaterTracking = { overlay = MainOverlay.WaterTracking },
                        onNavigateToDiet = { selectedTab = 1 },
                        onNavigateToWorkout = { selectedTab = 2 },
                        onOpenWorkoutReminder = { overlay = MainOverlay.WorkoutReminder }
                    )
                    1 -> dietTab()
                    2 -> WorkoutTab(
                        viewModel = viewModel,
                        onOpenWorkoutReminder = { overlay = MainOverlay.WorkoutReminder },
                        onNavigateToCoach = { selectedTab = 3 },
                        aiEnabled = aiEnabled
                    )
                    3 -> CoachChatScreen(
                        viewModel = viewModel,
                        onOpenSettings = { overlay = MainOverlay.Settings }
                    )
                    else -> CoachChatScreen(
                        viewModel = viewModel,
                        onOpenSettings = { overlay = MainOverlay.Settings }
                    )
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
                    },
                    onSaveWorkout = { fl, skipped, squat, bench, dead, days, weekStart, equip, gym ->
                        viewModel.saveWorkoutPreferencesEdits(fl, skipped, squat, bench, dead, days, weekStart, equip, gym)
                    },
                    onSavePhysical = { age, gender, height ->
                        viewModel.savePhysicalMetricsEdits(age, gender, height)
                    },
                    onSaveActivityGoal = { activity, goal ->
                        viewModel.saveActivityGoalEdits(activity, goal)
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
            MainOverlay.WaterTracking -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(3f)
                    .background(Background)
            ) {
                WaterTrackingScreen(
                    viewModel = viewModel,
                    onBack = { overlay = MainOverlay.None },
                    onOpenReminderSettings = { overlay = MainOverlay.WaterReminderSettings }
                )
            }
            MainOverlay.WaterReminderSettings -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(4f)
                    .background(Background)
            ) {
                WaterReminderSettingsScreen(
                    viewModel = viewModel,
                    onBack = { overlay = MainOverlay.WaterTracking }
                )
            }
            MainOverlay.NotificationHistory -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(3f)
                    .background(Background)
            ) {
                NotificationHistoryScreen(
                    viewModel = viewModel,
                    onBack = { overlay = MainOverlay.None }
                )
            }
            MainOverlay.WorkoutReminder -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(3f)
                    .background(Background)
            ) {
                WorkoutReminderScreen(
                    viewModel = viewModel,
                    onBack = { overlay = MainOverlay.None }
                )
            }
            MainOverlay.None -> {}
        }

        if (showWaterReminderDialog) {
            WaterReminderPermissionDialog(
                onDismiss = { showWaterReminderDialog = false },
                onEnable = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        when {
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                                PackageManager.PERMISSION_GRANTED -> {
                                viewModel.setNotificationsEnabled(true)
                                showWaterReminderDialog = false
                            }
                            else -> notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    } else {
                        viewModel.setNotificationsEnabled(true)
                        showWaterReminderDialog = false
                    }
                }
            )
        }

        AppUpdatePromptDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.dismissUpdatePrompt() }
        )
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
