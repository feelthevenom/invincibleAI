package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.SuggestedRoutine
import com.example.data.WorkoutDashboardStats
import com.example.ui.theme.*
import java.util.Locale

@Composable
fun HomeSectionToggle(
    selectedExercise: Boolean,
    onDiet: () -> Unit,
    onExercise: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceContainerLow, RoundedCornerShape(12.dp))
            .padding(4.dp)
    ) {
        Box(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(if (!selectedExercise) SurfaceContainerHigh else androidx.compose.ui.graphics.Color.Transparent)
                .clickable(onClick = onDiet)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Diet",
                style = Typography.labelMedium,
                color = if (!selectedExercise) Primary else OnSurfaceVariant.copy(0.6f)
            )
        }
        Box(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(if (selectedExercise) SurfaceContainerHigh else androidx.compose.ui.graphics.Color.Transparent)
                .clickable(onClick = onExercise)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Exercise",
                style = Typography.labelMedium,
                color = if (selectedExercise) Primary else OnSurfaceVariant.copy(0.6f)
            )
        }
    }
}

@Composable
fun SuggestedWorkoutCard(
    suggestion: SuggestedRoutine,
    onStart: () -> Unit,
    onSetReminder: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceContainer, RoundedCornerShape(16.dp))
            .border(1.dp, OutlineVariant.copy(0.25f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(suggestion.routine.name, style = Typography.titleLarge, color = OnSurface, fontWeight = FontWeight.Bold)
        if (suggestion.isAiSuggested) {
            Surface(color = Primary.copy(0.15f), shape = RoundedCornerShape(6.dp)) {
                Text(
                    "AI SUGGESTED",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = Typography.labelSmall,
                    color = Primary
                )
            }
        }
        Text("Today's Scheduled Routine", style = Typography.bodySmall, color = OnSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.FitnessCenter, null, tint = Primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "${suggestion.exerciseCount} Exercises",
                style = Typography.labelMedium,
                color = Primary
            )
        }
        Text(suggestion.reason, style = Typography.bodySmall, color = OnSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onSetReminder,
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Default.Alarm, "Set reminder", tint = Primary)
            }
            Button(
                onClick = onStart,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary)
            ) {
                Text("Start Workout", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun WorkoutStatsRow(stats: WorkoutDashboardStats) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            WorkoutStatCard(
                label = "TOTAL VOLUME",
                value = "%,.0f kg".format(Locale.US, stats.totalVolumeKg),
                subtitle = stats.volumeChangePercent?.let { if (it >= 0) "+$it% this week" else "$it% this week" },
                subtitleColor = if ((stats.volumeChangePercent ?: 0) >= 0) Secondary else Error,
                modifier = Modifier.weight(1f).heightIn(min = 132.dp)
            )
            WorkoutStatCard(
                label = "WORKOUTS",
                value = "${stats.workoutsCompleted} / ${stats.workoutsTarget}",
                progress = if (stats.workoutsTarget > 0) stats.workoutsCompleted.toFloat() / stats.workoutsTarget else 0f,
                modifier = Modifier.weight(1f).heightIn(min = 132.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            WorkoutStatCard(
                label = "CALORIES BURNED",
                value = "${stats.caloriesBurnedWeek} kcal",
                subtitle = "This week",
                subtitleColor = Secondary,
                progress = if (stats.weeklyCalorieTarget > 0) {
                    stats.caloriesBurnedWeek.toFloat() / stats.weeklyCalorieTarget
                } else 0f,
                modifier = Modifier.weight(1f).heightIn(min = 132.dp)
            )
            WorkoutStatCard(
                label = "PRS HIT",
                value = stats.prsHit.toString(),
                subtitle = if (stats.prsNewThisWeek > 0) "+${stats.prsNewThisWeek} New" else null,
                subtitleColor = Secondary,
                modifier = Modifier.weight(1f).heightIn(min = 132.dp)
            )
        }
    }
}

@Composable
private fun WorkoutStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleColor: androidx.compose.ui.graphics.Color = Secondary,
    progress: Float? = null
) {
    Column(
        modifier = modifier
            .background(SurfaceContainer, RoundedCornerShape(14.dp))
            .border(1.dp, OutlineVariant.copy(0.2f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(label, style = Typography.labelSmall, color = OnSurfaceVariant)
        Text(value, style = Typography.titleLarge, color = OnSurface, fontWeight = FontWeight.Bold)
        subtitle?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (it.contains("+")) Icon(Icons.Default.TrendingUp, null, tint = subtitleColor, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(it, style = Typography.labelSmall, color = subtitleColor)
            }
        }
        progress?.let { p ->
            LinearProgressIndicator(
                progress = { p.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = Secondary,
                trackColor = SurfaceContainerHighest
            )
        }
    }
}

@Composable
fun WeeklyActivityCard(stats: WorkoutDashboardStats, modifier: Modifier = Modifier) {
    val maxVolume = stats.weeklyActivity.maxOfOrNull { it.volumeKg }?.coerceAtLeast(1f) ?: 1f
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceContainer, RoundedCornerShape(14.dp))
            .border(1.dp, OutlineVariant.copy(0.2f), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Text("Weekly Activity", style = Typography.labelMedium, color = OnSurface)
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth().height(72.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            stats.weeklyActivity.forEach { day ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Box(
                        Modifier
                            .width(14.dp)
                            .height((day.volumeKg / maxVolume * 48f).coerceAtLeast(4f).dp)
                            .background(
                                if (day.volumeKg > 0f) Secondary else SurfaceContainerHighest,
                                RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                            )
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(day.label.take(3), style = Typography.labelSmall, color = OnSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun WeeklyActivityWideCard(stats: WorkoutDashboardStats) {
    WeeklyActivityCard(stats = stats, modifier = Modifier.fillMaxWidth())
}
