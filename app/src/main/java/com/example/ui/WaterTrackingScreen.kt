package com.example.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.GymViewModel
import com.example.data.DietDateUtils
import com.example.data.WaterGoalCalculator
import com.example.data.WaterTips
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaterTrackingScreen(
    viewModel: GymViewModel,
    onBack: () -> Unit,
    onOpenReminderSettings: () -> Unit
) {
    val profile by viewModel.userProfile.collectAsState()
    val waterLogs by viewModel.allWaterLogs.collectAsState()
    var selectedDayStart by remember { mutableLongStateOf(DietDateUtils.startOfTodayMillis()) }
    var showCalendar by remember { mutableStateOf(false) }
    var showGoalDialog by remember { mutableStateOf(false) }
    var showDateMenu by remember { mutableStateOf(false) }

    val goalGlasses = WaterGoalCalculator.effectiveGoalGlasses(profile)
    val glasses = viewModel.waterGlassesForDay(waterLogs, selectedDayStart)
    val progress = viewModel.waterProgress(waterLogs, selectedDayStart).coerceIn(0f, 1f)
    val aiEnabled = viewModel.isAiConfigured()
    val motivation = viewModel.waterMotivationMessage(glasses, goalGlasses, aiEnabled)
    val weeklyData = viewModel.weeklyWaterGlasses(waterLogs, DietDateUtils.startOfTodayMillis())
    val isToday = DietDateUtils.isToday(selectedDayStart)
    val canEdit = DietDateUtils.isPastOrToday(selectedDayStart)

    if (showCalendar) {
        DietCalendarDialog(
            selectedDayStart = selectedDayStart,
            daySummaries = DietDateUtils.summarizeWaterDays(waterLogs, goalGlasses),
            onDismiss = { showCalendar = false },
            onDaySelected = { day ->
                selectedDayStart = day
                showCalendar = false
            },
            onFutureDayBlocked = { showCalendar = false }
        )
    }

    if (showGoalDialog) {
        WaterGoalGlassesDialog(
            currentGlasses = goalGlasses,
            suggestedGlasses = viewModel.suggestedWaterGoalGlasses(),
            onDismiss = { showGoalDialog = false },
            onConfirm = { g ->
                viewModel.updateDailyWaterGoalGlasses(g) { err ->
                    if (err == null) showGoalDialog = false
                }
            },
            validate = viewModel::validateWaterGoalGlasses
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box {
                        TextButton(onClick = { showDateMenu = true }) {
                            Text(
                                if (isToday) "Today" else DietDateUtils.formatDisplayDate(selectedDayStart),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(Icons.Default.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurface)
                        }
                        DropdownMenu(expanded = showDateMenu, onDismissRequest = { showDateMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Today") },
                                onClick = {
                                    selectedDayStart = DietDateUtils.startOfTodayMillis()
                                    showDateMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Yesterday") },
                                onClick = {
                                    selectedDayStart = DietDateUtils.startOfYesterdayMillis()
                                    showDateMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Calendar") },
                                onClick = {
                                    showDateMenu = false
                                    showCalendar = true
                                }
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    IconButton(onClick = { showCalendar = true }) {
                        Icon(Icons.Default.CalendarMonth, "Calendar", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$glasses of $goalGlasses Glasses",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showGoalDialog = true }) {
                    Icon(Icons.Default.Edit, "Edit goal", tint = MaterialTheme.colorScheme.primary)
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(50)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(Modifier.height(24.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledIconButton(
                    onClick = { if (canEdit) viewModel.decrementWaterGlass(selectedDayStart) },
                    enabled = canEdit && glasses > 0,
                    modifier = Modifier.size(52.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Remove, null, tint = MaterialTheme.colorScheme.onPrimary)
                }
                Spacer(Modifier.width(24.dp))
                WaterGlassAnimation(progress = progress, modifier = Modifier.size(180.dp))
                Spacer(Modifier.width(24.dp))
                FilledIconButton(
                    onClick = { if (canEdit) viewModel.incrementWaterGlass(selectedDayStart) },
                    enabled = canEdit,
                    modifier = Modifier.size(52.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.onPrimary)
                }
            }

            Column(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "$glasses Glass (${WaterGoalCalculator.ML_PER_GLASS} ml)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                motivation?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
                if (profile.waterReminderEnabled) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        reminderHintText(profile),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f))

            Row(
                Modifier
                    .fillMaxWidth()
                    .clickableRow(onOpenReminderSettings)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Reminder", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text("EDIT", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f))

            Column(Modifier.padding(20.dp)) {
                Text("Today's Tip", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Default.WaterDrop, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        WaterTips.tipForDay(selectedDayStart),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Daily Water Intake", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    Text("Last 7 days", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(16.dp))
                WaterWeeklyBarChart(weeklyData = weeklyData, goalGlasses = goalGlasses)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun WaterGlassAnimation(progress: Float, modifier: Modifier = Modifier) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(600),
        label = "waterFill"
    )
    val primaryColor = MaterialTheme.colorScheme.primary
    Box(modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val fillH = h * animatedProgress
            if (fillH > 0f) {
                val wavePath = Path().apply {
                    moveTo(0f, h - fillH)
                    cubicTo(w * 0.25f, h - fillH - 8f, w * 0.75f, h - fillH + 8f, w, h - fillH)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                clipPath(Path().apply { addOval(androidx.compose.ui.geometry.Rect(0f, 0f, w, h)) }) {
                    drawPath(wavePath, primaryColor.copy(alpha = 0.55f))
                    drawRect(
                        primaryColor.copy(alpha = 0.35f),
                        topLeft = Offset(0f, h - fillH),
                        size = Size(w, fillH)
                    )
                }
            }
        }
        Icon(Icons.Default.LocalDrink, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.85f), modifier = Modifier.size(48.dp))
    }
}

@Composable
fun WaterWeeklyBarChart(
    weeklyData: List<Pair<Long, Int>>,
    goalGlasses: Int,
    modifier: Modifier = Modifier
) {
    val maxVal = (weeklyData.maxOfOrNull { it.second } ?: 0).coerceAtLeast(goalGlasses).coerceAtLeast(1)
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().height(140.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            weeklyData.forEach { (dayStart, glasses) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(
                        if (glasses > 0) "$glasses" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 10.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    val barFraction = glasses.toFloat() / maxVal
                    Box(
                        Modifier
                            .width(18.dp)
                            .height((100 * barFraction).coerceAtLeast(if (glasses > 0) 8f else 0f).dp)
                            .background(
                                if (DietDateUtils.isToday(dayStart)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(0.35f),
                                RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                            )
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        DietDateUtils.dayOfMonth(dayStart).toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            weeklyData.forEach { (dayStart, _) ->
                Text(
                    DietDateUtils.formatShortDay(dayStart).substringBefore(" "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }
        if (goalGlasses > 0) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(24.dp).height(2.dp).background(MaterialTheme.colorScheme.tertiary))
                Spacer(Modifier.width(8.dp))
                Text("Goal: $goalGlasses glasses", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@Composable
fun WaterGoalGlassesDialog(
    currentGlasses: Int,
    suggestedGlasses: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    validate: (Int) -> String?
) {
    var glasses by remember { mutableIntStateOf(currentGlasses) }
    var warning by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily Water Goal") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Suggested: $suggestedGlasses glasses based on your weight",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FilledIconButton(
                        onClick = { if (glasses > 4) glasses-- },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) { Icon(Icons.Default.Remove, null) }
                    Text("$glasses", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface)
                    FilledIconButton(
                        onClick = { if (glasses < 20) glasses++ },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.onPrimary) }
                }
                Text("glasses (${glasses * WaterGoalCalculator.ML_PER_GLASS} ml)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                warning?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val err = validate(glasses)
                if (err != null) warning = err else onConfirm(glasses)
            }) { Text("Confirm") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun reminderHintText(profile: com.example.data.UserProfile): String {
    return when (profile.waterReminderMode) {
        com.example.data.WaterReminderModes.INTERVAL ->
            "Next reminder about every ${profile.waterReminderIntervalMinutes} minutes during your active window."
        com.example.data.WaterReminderModes.TIMES ->
            "We’ll remind you ${profile.waterReminderTimesPerDay} times today between your set hours."
        com.example.data.WaterReminderModes.DAILY ->
            "Next reminder at ${DietDateUtils.formatTimeFromMinute(profile.waterReminderDailyTimeMinute)}."
        com.example.data.WaterReminderModes.WEEKLY ->
            "Weekly reminder on ${java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault()).format(
                java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.DAY_OF_WEEK, profile.waterReminderWeeklyDay)
                }.time
            )}."
        else -> "Hydration reminders are active."
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))
