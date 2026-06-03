package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.example.data.UserProfile
import com.example.ui.theme.*

@Composable
fun DashboardTab(profile: UserProfile? = null) {
    val dailyCalories = profile?.dailyCalories ?: 2800
    val protein = profile?.protein ?: 180
    val carbs = profile?.carbs ?: 300
    val fat = profile?.fat ?: 75
    val fiber = profile?.fiber ?: 35
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Toggle
        Row(
            modifier = Modifier.fillMaxWidth().background(SurfaceContainerLow, RoundedCornerShape(12.dp)).padding(4.dp)
        ) {
            Box(Modifier.weight(1f).background(SurfaceContainerHigh, RoundedCornerShape(8.dp)).padding(8.dp), contentAlignment = Alignment.Center) {
                Text("Diet", style = Typography.labelMedium, color = Primary)
            }
            Box(Modifier.weight(1f).padding(8.dp), contentAlignment = Alignment.Center) {
                Text("Exercise", style = Typography.labelMedium, color = OnSurfaceVariant.copy(0.6f))
            }
        }

        // Calorie Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceContainerHighest.copy(0.3f), RoundedCornerShape(16.dp))
                .border(1.dp, OutlineVariant.copy(0.2f), RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("REMAINING", style = Typography.labelMedium, color = OnSurfaceVariant.copy(0.7f))
                Text("%,d".format(dailyCalories), style = Typography.displayMedium, color = Secondary)
                Text("of %,d kcal goal".format(dailyCalories), style = Typography.bodySmall, color = OnSurfaceVariant.copy(0.6f))
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MacroCol("PROTEIN", "0g/${protein}g", Secondary, 0f, Modifier.weight(1f))
                    MacroCol("CARBS", "0g/${carbs}g", Primary, 0f, Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MacroCol("FAT", "0g/${fat}g", Tertiary, 0.60f, Modifier.weight(1f))
                    MacroCol("FIBER", "0g/${fiber}g", OnSurface, 0f, Modifier.weight(1f))
                }
            }
        }

        // AI Insight
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PrimaryContainer.copy(0.1f), RoundedCornerShape(16.dp))
                .border(1.dp, Primary.copy(0.2f), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Box(Modifier.size(40.dp).background(Primary.copy(0.2f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Psychology, null, tint = Primary)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("AI COACH INSIGHT", style = Typography.labelMedium, color = Primary)
                Spacer(modifier = Modifier.height(4.dp))
                Text("You're 20g short of your protein goal. Consider a shake before bed to optimize muscle recovery.", style = Typography.bodyMedium, color = OnPrimaryContainer)
            }
        }

        // Widgets
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth().height(160.dp)) {
            WidgetCard(title = "Water", icon = Icons.Default.WaterDrop, tint = Primary, Modifier.weight(1f)) {
                Text("1.8", style = Typography.displayMedium.copy(fontSize = 28.sp), color = OnSurface)
                Button(onClick = {}, modifier = Modifier.fillMaxWidth().height(40.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainer)) {
                    Text("+ Add 250ml", style = Typography.labelMedium, color = OnSurfaceVariant)
                }
            }
            WidgetCard(title = "Weight", icon = Icons.Default.MonitorWeight, tint = Secondary, Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        profile?.currentWeight?.let { String.format("%.1f", it) } ?: "—",
                        style = Typography.displayMedium.copy(fontSize = 28.sp),
                        color = OnSurface
                    )
                    Text("kg", style = Typography.bodySmall, color = OnSurfaceVariant)
                }
                Text(
                    "Target: ${profile?.targetWeight?.let { String.format("%.1f", it) } ?: "—"}kg",
                    style = Typography.labelMedium,
                    color = OnSurfaceVariant.copy(0.6f)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DashboardTabPreview() {
    MyApplicationTheme {
        DashboardTab()
    }
}

@Composable
fun MacroCol(label: String, value: String, color: Color, progress: Float, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = Typography.labelMedium, color = OnSurfaceVariant.copy(0.8f))
            Text(value, style = Typography.labelMedium, color = color)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).background(SurfaceContainerHighest, RoundedCornerShape(50))) {
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(color, RoundedCornerShape(50)))
        }
    }
}

@Composable
fun WidgetCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(SurfaceContainerHighest.copy(0.3f), RoundedCornerShape(16.dp))
            .border(1.dp, OutlineVariant.copy(0.2f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, null, tint = tint)
            Text(title, style = Typography.labelMedium, color = OnSurfaceVariant.copy(0.6f))
        }
        content()
    }
}
