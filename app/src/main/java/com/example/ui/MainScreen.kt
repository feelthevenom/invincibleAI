@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
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
    var coachSubNav by remember { mutableStateOf(CoachSubNav.Chat) }
    var coachHistoryId by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    var lastBackPress by remember { mutableLongStateOf(0L) }
    var showWaterReminderDialog by remember { mutableStateOf(false) }
    val notifications by viewModel.allNotifications.collectAsState()
    val hasUnreadNotifications = remember(notifications, profile.notificationsLastViewedAt) {
        notifications.any { it.timestamp > profile.notificationsLastViewedAt }
    }

    LaunchedEffect(
        profile.workoutReminderEnabled,
        profile.workoutReminderTimeMinute,
        profile.workoutReminderRepeat
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
        profile.waterReminderWindowEndMinute
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
            if (profile.waterReminderEnabled) {
                WaterNotificationHelper.scheduleReminders(context, profile)
            }
            if (profile.workoutReminderEnabled) {
                WorkoutNotificationHelper.scheduleReminder(context, profile)
            }
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

    LaunchedEffect(selectedTab) {
        if (selectedTab == 3) {
            viewModel.onCoachTabSelected()
        } else {
            viewModel.onCoachTabHidden()
            coachSubNav = CoachSubNav.Chat
            coachHistoryId = null
        }
    }

    BackHandler(enabled = !workoutHandlesBack) {
        when {
            overlay == MainOverlay.WaterReminderSettings -> overlay = MainOverlay.WaterTracking
            overlay == MainOverlay.WaterTracking -> overlay = MainOverlay.None
            overlay == MainOverlay.WorkoutReminder -> overlay = MainOverlay.None
            overlay == MainOverlay.NotificationHistory -> overlay = MainOverlay.None
            overlay == MainOverlay.AiSettings -> overlay = MainOverlay.None
            overlay == MainOverlay.Settings -> overlay = MainOverlay.None
            overlay != MainOverlay.None -> overlay = MainOverlay.None
            drawerOpen -> drawerOpen = false
            selectedTab == 3 && coachSubNav == CoachSubNav.HistoryDetail -> {
                coachHistoryId = null
                coachSubNav = CoachSubNav.HistoryList
            }
            selectedTab == 3 && coachSubNav == CoachSubNav.HistoryList -> {
                coachSubNav = CoachSubNav.Chat
            }
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
    AppSidebarDrawer(
        isOpen = drawerOpen,
        onDismiss = { drawerOpen = false },
        onPersonalDetails = { overlay = MainOverlay.PersonalDetails },
        onAiSettings = { overlay = MainOverlay.AiSettings },
        onSettings = { overlay = MainOverlay.Settings }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                bottomBar = {
                    if (!isKeyboardVisible) {
                        Column {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.2f))
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                                tonalElevation = 0.dp
                            ) {
                                NavigationBarItem(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    icon = { Icon(Icons.Default.Home, null) },
                                    label = { Text("Home") },
                                    colors = navColors(selectedTab == 0)
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    icon = { Icon(Icons.Default.Restaurant, null) },
                                    label = { Text("Diet") },
                                    colors = navColors(selectedTab == 1)
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 2,
                                    onClick = { selectedTab = 2 },
                                    icon = { Icon(Icons.Default.FitnessCenter, null) },
                                    label = { Text("Workout") },
                                    colors = navColors(selectedTab == 2)
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 3,
                                    onClick = { selectedTab = 3 },
                                    icon = { Icon(Icons.Default.Psychology, null) },
                                    label = { Text("Coach") },
                                    colors = navColors(selectedTab == 3)
                                )
                            }
                        }
                    }
                },
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            when {
                                selectedTab == 3 && coachSubNav == CoachSubNav.HistoryList -> {
                                    Text(
                                        "Chat History",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                selectedTab == 3 && coachSubNav == CoachSubNav.HistoryDetail -> {
                                    Text(
                                        "Past conversation",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "GYM AI",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (aiEnabled) {
                                        Spacer(Modifier.width(6.dp))
                                        Icon(
                                            Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        },
                        navigationIcon = {
                            if (selectedTab == 3 && coachSubNav != CoachSubNav.Chat) {
                                IconButton(onClick = {
                                    when (coachSubNav) {
                                        CoachSubNav.HistoryDetail -> {
                                            coachHistoryId = null
                                            coachSubNav = CoachSubNav.HistoryList
                                        }
                                        CoachSubNav.HistoryList -> coachSubNav = CoachSubNav.Chat
                                        else -> coachSubNav = CoachSubNav.Chat
                                    }
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                                }
                            } else {
                                IconButton(onClick = { drawerOpen = true }) {
                                    Icon(Icons.Default.Menu, "Menu")
                                }
                            }
                        },
                        actions = {
                            if (selectedTab == 3 && coachSubNav == CoachSubNav.Chat) {
                                IconButton(onClick = {
                                    viewModel.refreshCoachHistory()
                                    coachSubNav = CoachSubNav.HistoryList
                                }) {
                                    Icon(Icons.Default.History, "Chat history")
                                }
                                IconButton(onClick = { overlay = MainOverlay.AiSettings }) {
                                    Icon(Icons.Default.Settings, "AI settings")
                                }
                            }
                            IconButton(onClick = {
                                if (!profile.notificationsEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                                    PackageManager.PERMISSION_GRANTED
                                ) {
                                    showWaterReminderDialog = true
                                } else {
                                    viewModel.markNotificationsViewed()
                                    overlay = MainOverlay.NotificationHistory
                                }
                            }) {
                                BadgedBox(
                                    badge = {
                                        if (hasUnreadNotifications) {
                                            Badge()
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Notifications, "Notifications")
                                }
                            }
                        }
                    )
                },
                containerColor = MaterialTheme.colorScheme.background
            ) { padding ->
                Box(modifier = Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize()) {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            if (targetState > initialState) {
                                slideInHorizontally { it } + fadeIn() togetherWith
                                    slideOutHorizontally { -it / 2 } + fadeOut()
                            } else {
                                slideInHorizontally { -it } + fadeIn() togetherWith
                                    slideOutHorizontally { it / 2 } + fadeOut()
                            }.using(
                                SizeTransform(clip = false)
                            )
                        },
                        label = "tab_transition"
                    ) { targetTab ->
                        when (targetTab) {
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
                                onOpenSettings = { overlay = MainOverlay.AiSettings },
                                subNav = coachSubNav,
                                onSubNavChange = { coachSubNav = it },
                                selectedHistoryId = coachHistoryId,
                                onSelectedHistoryIdChange = { coachHistoryId = it },
                                embedded = true
                            )
                        }
                    }
                }
            }

            AnimatedContent(
                targetState = overlay,
                transitionSpec = {
                    if (targetState != MainOverlay.None) {
                        slideInVertically { it } + fadeIn() togetherWith
                            fadeOut(animationSpec = tween(150))
                    } else {
                        fadeIn() togetherWith
                            slideOutVertically { it } + fadeOut()
                    }
                },
                label = "overlay_transition",
                modifier = Modifier.fillMaxSize()
            ) { currentOverlay ->
                when (currentOverlay) {
                    MainOverlay.PersonalDetails -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(3f)
                            .background(MaterialTheme.colorScheme.background)
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
                    MainOverlay.AiSettings -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(3f)
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        AiSettingsScreen(viewModel = viewModel, onBack = { overlay = MainOverlay.None })
                    }
                    MainOverlay.Settings -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(3f)
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        SettingsScreen(viewModel = viewModel, onBack = { overlay = MainOverlay.None })
                    }
                    MainOverlay.WaterTracking -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(3f)
                            .background(MaterialTheme.colorScheme.background)
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
                            .background(MaterialTheme.colorScheme.background)
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
                            .background(MaterialTheme.colorScheme.background)
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
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        WorkoutReminderScreen(
                            viewModel = viewModel,
                            onBack = { overlay = MainOverlay.None }
                        )
                    }
                    MainOverlay.None -> Spacer(Modifier.fillMaxSize())
                }
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
        }
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
    selectedIconColor = MaterialTheme.colorScheme.secondary,
    selectedTextColor = MaterialTheme.colorScheme.secondary,
    indicatorColor = androidx.compose.ui.graphics.Color.Transparent,
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f),
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f)
)
