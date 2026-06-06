@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.GymViewModel
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.example.data.DietDateUtils
import com.example.data.MealEntry
import com.example.data.MealTypes
import com.example.data.UserProfile
import com.example.ui.theme.*

private enum class DietDateTab { Yesterday, Today, Calendar }

@Composable
fun DietTab(viewModel: GymViewModel) {
    val meals by viewModel.allMeals.collectAsState()
    val profile by viewModel.userProfile.collectAsState()
    DietTabContent(meals = meals, profile = profile, viewModel = viewModel)
}

@Composable
fun DietTabContent(
    meals: List<MealEntry> = emptyList(),
    profile: UserProfile = UserProfile(),
    viewModel: GymViewModel? = null
) {
    var selectedMealType by remember { mutableStateOf<String?>(null) }
    var dateTab by remember { mutableStateOf(DietDateTab.Today) }
    var selectedDayStart by remember { mutableLongStateOf(DietDateUtils.startOfTodayMillis()) }
    var showCalendar by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val emptySnapshots = remember { emptyList<com.example.data.DailyGoalSnapshot>() }
    val goalSnapshots = if (viewModel != null) {
        val gs by viewModel.allGoalSnapshots.collectAsState()
        gs
    } else emptySnapshots

    LaunchedEffect(dateTab) {
        selectedDayStart = when (dateTab) {
            DietDateTab.Yesterday -> DietDateUtils.startOfYesterdayMillis()
            DietDateTab.Today -> DietDateUtils.startOfTodayMillis()
            DietDateTab.Calendar -> selectedDayStart
        }
    }

    val dayMeals = remember(meals, selectedDayStart) {
        DietDateUtils.mealsForDay(meals, selectedDayStart)
    }
    val daySummaries = remember(meals, goalSnapshots, profile.dailyCalories) {
        if (viewModel != null) {
            DietDateUtils.summarizeDays(meals) { day -> viewModel.dailyCaloriesForDay(day, goalSnapshots) }
        } else {
            DietDateUtils.summarizeDays(meals, profile.dailyCalories.coerceAtLeast(1))
        }
    }
    val isToday = DietDateUtils.isToday(selectedDayStart)

    val dayProfile = remember(selectedDayStart, goalSnapshots, profile) {
        viewModel?.profileForDay(selectedDayStart, goalSnapshots) ?: profile
    }
    val dailyGoal = dayProfile.dailyCalories.coerceAtLeast(1)
    val totalConsumed = dayMeals.sumOf { it.calories }
    val rawRemaining = dailyGoal - totalConsumed
    val dailyProgress = if (dailyGoal > 0) (totalConsumed.toFloat() / dailyGoal).coerceIn(0f, 1f) else 0f

    val budgets = remember(dayProfile) { MealTypes.allBudgets(dayProfile) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DateNavigationTabs(
            yesterdaySelected = dateTab == DietDateTab.Yesterday,
            todaySelected = dateTab == DietDateTab.Today,
            calendarSelected = dateTab == DietDateTab.Calendar,
            onYesterday = { dateTab = DietDateTab.Yesterday },
            onToday = { dateTab = DietDateTab.Today },
            onCalendar = {
                dateTab = DietDateTab.Calendar
                showCalendar = true
            }
        )

        Text(
            DietDateUtils.formatDisplayDate(selectedDayStart),
            style = MaterialTheme.typography.titleLarge,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("DAILY CALORIE INTAKE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "%,d".format(totalConsumed),
                        style = MaterialTheme.typography.displayMedium,
                        color = if (rawRemaining < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        " / %,d kcal".format(dailyGoal),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f),
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )
                }
                
                LinearProgressIndicator(
                    progress = { dailyProgress },
                    modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                    color = if (rawRemaining < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    if (rawRemaining >= 0) {
                        "%,d kcal remaining".format(rawRemaining)
                    } else {
                        "%,d kcal over goal".format(-rawRemaining)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (rawRemaining >= 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        Text("MEALS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        budgets.forEach { budget ->
            val mealEntries = dayMeals.filter { it.mealType == budget.mealType }
            val kcal = mealEntries.sumOf { it.calories }
            val pro = mealEntries.sumOf { it.protein }
            val carb = mealEntries.sumOf { it.carbs }
            val fat = mealEntries.sumOf { it.fat }
            val fib = mealEntries.sumOf { it.fiber }
            val isOver = kcal > budget.calories
            val status = when {
                kcal == 0 -> "Empty"
                isOver -> "Over target"
                else -> "Within limit"
            }
            val statusColor = when {
                kcal == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
                isOver -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.secondary
            }

            MealCard(
                title = budget.mealType,
                kcal = kcal,
                target = budget.calories,
                status = status,
                statusColor = statusColor,
                isError = isOver,
                protein = pro to budget.protein,
                carbs = carb to budget.carbs,
                fat = fat to budget.fat,
                fiber = fib to budget.fiber,
                onClick = { if (viewModel != null) selectedMealType = budget.mealType }
            )
        }
    }

    if (showCalendar) {
        DietCalendarDialog(
            selectedDayStart = selectedDayStart,
            daySummaries = daySummaries,
            onDismiss = { showCalendar = false },
            onDaySelected = { day ->
                selectedDayStart = day
                dateTab = DietDateTab.Calendar
                showCalendar = false
            },
            onFutureDayBlocked = {
                Toast.makeText(context, "You can't log entries for future dates.", Toast.LENGTH_SHORT).show()
            }
        )
    }

    selectedMealType?.let { mealType ->
        val budget = budgets.first { it.mealType == mealType }
        val entries = dayMeals.filter { it.mealType == mealType }
        if (viewModel != null) {
            MealDetailOverlay(
                mealType = mealType,
                budget = budget,
                entries = entries,
                logDayStart = selectedDayStart,
                viewModel = viewModel,
                onDismiss = { selectedMealType = null }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DietTabPreview() {
    MyApplicationTheme {
        DietTabContent(
            profile = UserProfile(
                dailyCalories = 2500,
                protein = 180,
                carbs = 300,
                fat = 75,
                fiber = 35,
                onboardingComplete = true
            )
        )
    }
}

// MealCard and MiniMacroBar below

@Composable
fun MealCard(
    title: String,
    kcal: Int,
    target: Int,
    status: String,
    statusColor: Color,
    isError: Boolean = false,
    protein: Pair<Int, Int> = 0 to 1,
    carbs: Pair<Int, Int> = 0 to 1,
    fat: Pair<Int, Int> = 0 to 1,
    fiber: Pair<Int, Int> = 0 to 1,
    onClick: () -> Unit = {}
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(0.12f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.RestaurantMenu, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "Target: $target kcal",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "$kcal kcal",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = statusColor
                    )
                    Text(status, style = MaterialTheme.typography.labelSmall, color = statusColor)
                }
            }
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { if (target > 0) (kcal.toFloat() / target).coerceIn(0f, 1.2f) else 0f },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = statusColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiniMacroBar("PRO", "${protein.first}g", MaterialTheme.colorScheme.secondary, macroProgress(protein.first, protein.second), Modifier.weight(1f))
                    MiniMacroBar("CAR", "${carbs.first}g", MaterialTheme.colorScheme.tertiary, macroProgress(carbs.first, carbs.second), Modifier.weight(1f))
                    MiniMacroBar("FAT", "${fat.first}g", MaterialTheme.colorScheme.error, macroProgress(fat.first, fat.second), Modifier.weight(1f))
                }
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
            }
        }
    }
}

private fun macroProgress(consumed: Int, target: Int): Float =
    if (target > 0) (consumed.toFloat() / target).coerceIn(0f, 1f) else 0f

@Composable
fun MiniMacroBar(label: String, value: String, color: Color, progress: Float, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f))
            Text(value, style = MaterialTheme.typography.labelSmall, color = color)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))) {
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(color, RoundedCornerShape(50)))
        }
    }
}
