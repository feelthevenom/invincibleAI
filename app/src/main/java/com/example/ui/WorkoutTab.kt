package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.example.GymViewModel
import com.example.data.ExerciseSet
import com.example.ui.theme.*

@Composable
fun WorkoutTab(viewModel: GymViewModel) {
    val sets by viewModel.allSets.collectAsState()
    WorkoutTabContent(sets)
}

@Composable
fun WorkoutTabContent(sets: List<ExerciseSet> = emptyList()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .padding(bottom = 80.dp), // For nav bar
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Header
        Column {
            Text("Push Day (Hypertrophy)", style = Typography.headlineLarge.copy(fontSize = 28.sp), color = OnSurface)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarToday, null, modifier = Modifier.size(14.dp), tint = OnSurfaceVariant.copy(0.6f))
                Spacer(Modifier.width(8.dp))
                Text("OCT 24 • SESSION #42", style = Typography.labelMedium, color = OnSurfaceVariant.copy(0.6f))
            }
        }

        // AI Tip
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceContainerHighest.copy(0.3f), RoundedCornerShape(12.dp))
                .border(2.dp, Primary, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.AutoAwesome, null, tint = Primary)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("AI PROGRESSIVE OVERLOAD TIP", style = Typography.labelMedium, color = Primary)
                    Spacer(Modifier.height(4.dp))
                    Text("Last week you did 60kg x 8. Suggesting ", style = Typography.bodySmall, color = OnSurface)
                    Text("62.5kg x 8 ", style = Typography.bodySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = Secondary)
                    Text("for Set 1.", style = Typography.bodySmall, color = OnSurface)
                }
            }
        }

        // Exercises
        ExerciseCard("Bench Press", "CHEST")
        ExerciseCard("Incline DB Press", "CHEST")

        // Actions
        Button(
            onClick = {},
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerHigh, contentColor = Primary)
        ) {
            Text("+ ADD EXERCISE", style = Typography.labelMedium)
        }
        
        Button(
            onClick = {},
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ErrorContainer.copy(0.2f), contentColor = Error)
        ) {
            Text("CANCEL WORKOUT", style = Typography.labelMedium)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WorkoutTabPreview() {
    MyApplicationTheme {
        WorkoutTabContent()
    }
}

@Composable
fun ExerciseCard(name: String, category: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceContainerHighest.copy(0.3f), RoundedCornerShape(12.dp))
            .border(1.dp, OutlineVariant.copy(0.3f), RoundedCornerShape(12.dp))
    ) {
        Row(
            Modifier.fillMaxWidth().border(1.dp, OutlineVariant.copy(0.2f)).padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(name, style = Typography.headlineMedium.copy(fontSize = 18.sp), color = Primary)
                Box(Modifier.background(SurfaceContainerHighest, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text(category, style = Typography.labelMedium.copy(fontSize = 10.sp), color = OnSurfaceVariant)
                }
            }
            Icon(Icons.Default.MoreHoriz, null, tint = OnSurfaceVariant.copy(0.4f))
        }
        
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Text("SET", style = Typography.labelMedium.copy(fontSize = 10.sp), color = OnSurfaceVariant.copy(0.6f), modifier = Modifier.weight(0.5f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text("WEIGHT (KG)", style = Typography.labelMedium.copy(fontSize = 10.sp), color = OnSurfaceVariant.copy(0.6f), modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text("REPS", style = Typography.labelMedium.copy(fontSize = 10.sp), color = OnSurfaceVariant.copy(0.6f), modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text("DONE", style = Typography.labelMedium.copy(fontSize = 10.sp), color = OnSurfaceVariant.copy(0.6f), modifier = Modifier.weight(0.5f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            // Set 1
            SetRow(1, "62.5", "8", true)
            Spacer(Modifier.height(8.dp))
            // Set 2
            SetRow(2, "62.5", "8", false)
            
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().border(1.dp, OutlineVariant.copy(0.3f), RoundedCornerShape(8.dp)).padding(12.dp), contentAlignment = Alignment.Center) {
                Text("+ ADD SET", style = Typography.labelMedium, color = OnSurfaceVariant.copy(0.6f))
            }
        }
    }
}

@Composable
fun SetRow(number: Int, weight: String, reps: String, done: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("$number", style = Typography.displayMedium.copy(fontSize = 14.sp), color = OnSurfaceVariant.copy(0.4f), modifier = Modifier.weight(0.5f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Box(Modifier.weight(1f).padding(horizontal = 4.dp).height(40.dp).background(SurfaceContainerLow, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Text(weight, style = Typography.displayMedium.copy(fontSize = 16.sp), color = PrimaryFixed)
        }
        Box(Modifier.weight(1f).padding(horizontal = 4.dp).height(40.dp).background(SurfaceContainerLow, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Text(reps, style = Typography.displayMedium.copy(fontSize = 16.sp), color = OnSurface)
        }
        Box(Modifier.weight(0.5f), contentAlignment = Alignment.Center) {
            Box(Modifier.size(40.dp).background(if(done) Secondary else SurfaceContainerHighest, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, null, tint = if(done) OnSecondary else OnSurfaceVariant.copy(0.2f))
            }
        }
    }
}
