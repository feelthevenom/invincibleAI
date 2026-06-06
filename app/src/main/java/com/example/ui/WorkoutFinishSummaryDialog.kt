package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.WorkoutFinishSummary
import com.example.ui.theme.*
import java.util.concurrent.TimeUnit

@Composable
fun WorkoutFinishSummaryDialog(
    summary: WorkoutFinishSummary,
    onConfirm: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit
) {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(summary.durationMs)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(summary.durationMs) % 60

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainer)
        ) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Workout Complete!", style = Typography.headlineMedium, color = Primary)
                Text(
                    summary.motivationalMessage,
                    style = Typography.bodyMedium,
                    color = OnSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SummaryStat("Duration", "%d:%02d".format(minutes, seconds))
                    SummaryStat("Volume", "%.0f kg".format(summary.totalVolumeKg))
                    SummaryStat("Sets", "${summary.setsCompleted}/${summary.totalSets}")
                }

                Button(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save & Close")
                }
                OutlinedButton(
                    onClick = onDiscard,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Error)
                ) {
                    Text("Discard Workout")
                }
                TextButton(onClick = onDismiss) {
                    Text("Back to Workout", color = OnSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = Typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = OnSurface)
        Text(label, style = Typography.labelMedium, color = OnSurfaceVariant)
    }
}
