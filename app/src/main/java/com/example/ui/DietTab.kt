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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceContainerLow, RoundedCornerShape(12.dp))
                .border(1.dp, OutlineVariant.copy(0.2f), RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabModifier = @Composable { tab: DietDateTab, label: String ->
                val isSelected = dateTab == tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) PrimaryContainer else Color.Transparent)
                        .clickable {
                            dateTab = tab
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        style = Typography.bodyMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (isSelected) OnPrimaryContainer else OnSurfaceVariant.copy(0.6f)
                    )
                }
            }

            tabModifier(DietDateTab.Yesterday, "Yesterday")
            tabModifier(DietDateTab.Today, "Today")

            IconButton(
                onClick = {
                    dateTab = DietDateTab.Calendar
                    showCalendar = true
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Default.CalendarMonth,
                    contentDescription = "Calendar",
                    tint = if (dateTab == DietDateTab.Calendar) Primary else OnSurfaceVariant.copy(0.6f)
                )
            }
        }

        Text(
            DietDateUtils.formatDisplayDate(selectedDayStart),
            style = Typography.titleMedium,
            color = if (isToday) OnSurface else Error,
            modifier = Modifier.padding(start = 4.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceContainerHighest.copy(0.3f), RoundedCornerShape(16.dp))
                .border(1.dp, OutlineVariant.copy(0.2f), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column {
                Text("TODAY'S INTAKE", style = Typography.labelMedium, color = OnSurfaceVariant.copy(0.8f))
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "%,d".format(totalConsumed),
                            style = Typography.displayMedium.copy(fontSize = 32.sp),
                            color = if (rawRemaining < 0) Error else Secondary
                        )
                        Text(
                            " / %,d kcal".format(dailyGoal),
                            style = Typography.titleMedium,
                            color = OnSurfaceVariant.copy(0.8f),
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                        )
                    }
                }
                Text(
                    if (rawRemaining >= 0) {
                        "%,d kcal remaining".format(rawRemaining)
                    } else {
                        "%,d kcal over goal".format(-rawRemaining)
                    },
                    style = Typography.labelMedium,
                    color = if (rawRemaining >= 0) OnSurfaceVariant.copy(0.7f) else Error,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(OutlineVariant.copy(0.3f), RoundedCornerShape(50))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(dailyProgress)
                            .fillMaxHeight()
                            .background(Secondary, RoundedCornerShape(50))
                    )
                }
            }
        }

        Text("MEALS", style = Typography.labelMedium, color = OnSurfaceVariant)

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
                kcal == 0 -> OnSurfaceVariant
                isOver -> Error
                else -> Secondary
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceContainerHighest.copy(0.3f), RoundedCornerShape(16.dp))
            .border(1.dp, if (isError) Error else OutlineVariant.copy(0.2f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .background(SurfaceContainerHigh, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.RestaurantMenu, null, tint = Primary)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(title, style = Typography.headlineMedium.copy(fontSize = 18.sp), color = OnSurface)
                        Text(
                            "Target: $target kcal",
                            style = Typography.bodySmall,
                            color = OnSurfaceVariant.copy(0.6f)
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "$kcal",
                            style = Typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = statusColor
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("kcal", style = Typography.bodySmall, color = OnSurfaceVariant.copy(0.6f))
                    }
                    Text(status, style = Typography.labelMedium, color = statusColor)
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).background(OutlineVariant.copy(0.2f), RoundedCornerShape(50))) {
                val calProgress = if (target > 0) (kcal.toFloat() / target).coerceIn(0f, 1f) else 0f
                Box(
                    Modifier
                        .fillMaxWidth(calProgress)
                        .fillMaxHeight()
                        .background(statusColor, RoundedCornerShape(50))
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniMacroBar("PRO", "${protein.first}g", Primary, macroProgress(protein.first, protein.second), Modifier.weight(1f))
                    MiniMacroBar("CAR", "${carbs.first}g", Tertiary, macroProgress(carbs.first, carbs.second), Modifier.weight(1f))
                    MiniMacroBar("FAT", "${fat.first}g", Error, macroProgress(fat.first, fat.second), Modifier.weight(1f))
                    MiniMacroBar("FIB", "${fiber.first}g", Secondary, macroProgress(fiber.first, fiber.second), Modifier.weight(1f))
                }
                Spacer(Modifier.width(16.dp))
                Box(
                    Modifier
                        .size(32.dp)
                        .background(SurfaceContainerHighest, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ChevronRight, null, tint = OnSurface, modifier = Modifier.size(18.dp))
                }
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
            Text(label, style = Typography.labelMedium.copy(fontSize = 10.sp), color = OnSurfaceVariant.copy(0.4f))
            Text(value, style = Typography.labelMedium.copy(fontSize = 10.sp), color = OnSurfaceVariant.copy(0.4f))
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).background(OutlineVariant.copy(0.2f), RoundedCornerShape(50))) {
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(color, RoundedCornerShape(50)))
        }
    }
}
