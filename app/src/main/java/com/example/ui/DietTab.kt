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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.GymViewModel
import com.example.data.MealEntry
import com.example.data.MealTypes
import com.example.data.UserProfile
import com.example.ui.theme.*
import java.util.Calendar

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
    val todayStart = remember { startOfTodayMillis() }
    val todayMeals = remember(meals, todayStart) { meals.filter { it.timestamp >= todayStart } }

    val dailyGoal = profile.dailyCalories.coerceAtLeast(1)
    val totalConsumed = todayMeals.sumOf { it.calories }
    val remaining = (dailyGoal - totalConsumed).coerceAtLeast(0)
    val dailyProgress = if (dailyGoal > 0) (totalConsumed.toFloat() / dailyGoal).coerceIn(0f, 1f) else 0f

    val budgets = remember(profile) { MealTypes.allBudgets(profile) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceContainerLow, RoundedCornerShape(12.dp))
                .border(1.dp, OutlineVariant.copy(0.2f), RoundedCornerShape(12.dp))
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Yesterday",
                style = Typography.bodySmall,
                color = OnSurfaceVariant.copy(0.6f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Box(Modifier.background(PrimaryContainer, RoundedCornerShape(8.dp)).padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    "Today",
                    style = Typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = OnPrimaryContainer
                )
            }
            Text(
                "Calendar",
                style = Typography.bodySmall,
                color = OnSurfaceVariant.copy(0.6f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceContainerHighest.copy(0.3f), RoundedCornerShape(16.dp))
                .border(1.dp, OutlineVariant.copy(0.2f), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column {
                Text("REMAINING", style = Typography.labelMedium, color = OnSurfaceVariant.copy(0.8f))
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "%,d".format(remaining),
                        style = Typography.displayMedium.copy(fontSize = 32.sp),
                        color = Secondary
                    )
                    Text(
                        "Goal: %,d kcal".format(dailyGoal),
                        style = Typography.labelMedium,
                        color = OnSurfaceVariant.copy(0.8f)
                    )
                }
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
            val mealEntries = todayMeals.filter { it.mealType == budget.mealType }
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

    selectedMealType?.let { mealType ->
        val budget = budgets.first { it.mealType == mealType }
        val entries = todayMeals.filter { it.mealType == mealType }
        if (viewModel != null) {
            MealDetailOverlay(
                mealType = mealType,
                budget = budget,
                entries = entries,
                viewModel = viewModel,
                onDismiss = { selectedMealType = null }
            )
        }
    }
}

fun startOfTodayMillis(): Long {
    return Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
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
                    MiniMacroBar(
                        "PRO",
                        "${protein.first}g",
                        Primary,
                        macroProgress(protein.first, protein.second),
                        Modifier.weight(1f)
                    )
                    MiniMacroBar(
                        "CAR",
                        "${carbs.first}g",
                        Tertiary,
                        macroProgress(carbs.first, carbs.second),
                        Modifier.weight(1f)
                    )
                    MiniMacroBar(
                        "FAT",
                        "${fat.first}g",
                        Error,
                        macroProgress(fat.first, fat.second),
                        Modifier.weight(1f)
                    )
                    MiniMacroBar(
                        "FIB",
                        "${fiber.first}g",
                        Secondary,
                        macroProgress(fiber.first, fiber.second),
                        Modifier.weight(1f)
                    )
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
