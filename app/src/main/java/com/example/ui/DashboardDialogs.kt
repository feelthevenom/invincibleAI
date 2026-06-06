package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.WaterGoalCalculator
import com.example.ui.theme.*

@Composable
fun WaterGoalEditDialog(
    currentGoalMl: Int,
    suggestedMl: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    validate: (Int) -> String?
) {
    var goalText by remember { mutableStateOf(WaterGoalCalculator.formatLiters(currentGoalMl)) }
    var warning by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = SurfaceContainerHigh) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Daily Water Goal", style = Typography.titleMedium, color = OnSurface)
                Text(
                    "Suggested for you: ${WaterGoalCalculator.formatLiters(suggestedMl)}L based on weight & activity",
                    style = Typography.bodySmall,
                    color = OnSurfaceVariant
                )
                OutlinedTextField(
                    value = goalText,
                    onValueChange = {
                        if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d*$"))) {
                            goalText = it
                            warning = null
                        }
                    },
                    label = { Text("Max liters / day") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                warning?.let { Text(it, color = Error, style = Typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(
                        onClick = {
                            val liters = goalText.toFloatOrNull()
                            if (liters == null) {
                                warning = "Enter a valid number"
                                return@Button
                            }
                            val ml = (liters * 1000).toInt()
                            val err = validate(ml)
                            if (err != null) {
                                warning = err
                            } else {
                                onConfirm(ml)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Confirm") }
                }
            }
        }
    }
}

@Composable
fun LogWeightDialog(
    currentKg: Float,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit
) {
    var weightText by remember {
        mutableStateOf(if (currentKg > 0f) String.format("%.1f", currentKg) else "")
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = SurfaceContainerHigh) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Log Today's Weight", style = Typography.titleMedium, color = OnSurface)
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { v ->
                        if (v.isEmpty() || v.matches(Regex("^\\d*\\.?\\d*$"))) weightText = v
                    },
                    label = { Text("Weight (kg)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(
                        onClick = {
                            weightText.toFloatOrNull()?.let { if (it > 0f) onConfirm(it) }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Save") }
                }
            }
        }
    }
}

@Composable
fun LogMeasurementDialog(
    typeLabel: String,
    useMetric: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit,
    parseInput: (String) -> Float?
) {
    var valueText by remember { mutableStateOf("") }
    val unit = if (useMetric) "cm" else "in"
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = SurfaceContainerHigh) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Log $typeLabel", style = Typography.titleMedium, color = OnSurface)
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { v ->
                        if (v.isEmpty() || v.matches(Regex("^\\d*\\.?\\d*$"))) valueText = v
                    },
                    label = { Text("Size ($unit)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(
                        onClick = { parseInput(valueText)?.let { onConfirm(it) } },
                        modifier = Modifier.weight(1f)
                    ) { Text("Save") }
                }
            }
        }
    }
}

@Composable
fun HydrationAlertDialog(
    progressPercent: Int,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
    onLogWater: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = SurfaceContainerHigh) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WaterDrop, null, tint = Primary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Hydration Alert", style = Typography.titleMedium, color = Primary, fontWeight = FontWeight.Bold)
                        Text("GOAL PROGRESS: $progressPercent%", style = Typography.labelMedium, color = OnSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close", tint = OnSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Stay fueled! Time to hit your water goal. Keep that momentum going.",
                    style = Typography.bodyMedium,
                    color = OnSurface
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onSnooze, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Snooze, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Snooze")
                    }
                    Button(onClick = onLogWater, modifier = Modifier.weight(1f)) {
                        Text("+ Log 250ml")
                    }
                }
            }
        }
    }
}

@Composable
fun WaterReminderPermissionDialog(
    onDismiss: () -> Unit,
    onEnable: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enable Hydration Reminders") },
        text = {
            Text("Allow notifications so Gym AI can remind you to drink water throughout the day.")
        },
        confirmButton = { TextButton(onClick = onEnable) { Text("Allow") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } }
    )
}
