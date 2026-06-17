@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ExerciseGuideUiState
import com.example.GymViewModel
import com.example.data.ExerciseGuideDetail
import com.example.data.ExerciseStepFormatter
import com.example.data.GuideSource

@Composable
fun ExerciseTutorialDialog(
    exerciseName: String,
    guideState: ExerciseGuideUiState,
    aiEnabled: Boolean,
    onDismiss: () -> Unit,
    onAiFill: () -> Unit,
    onChangeAiSteps: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    val busy = guideState == ExerciseGuideUiState.Loading ||
        (guideState is ExerciseGuideUiState.Ready && guideState.isGeneratingAi)

    // Simplified AI button logic: show AI Fill if empty, or Change if non-empty (to regenerate)
    val showAiFill = aiEnabled && !busy && when (guideState) {
        is ExerciseGuideUiState.Ready -> guideState.guide.instructions.isEmpty()
        is ExerciseGuideUiState.NoMatch, is ExerciseGuideUiState.NeedsInternet,
        is ExerciseGuideUiState.Error -> true
        else -> false
    }
    
    val showChange = aiEnabled && !busy && guideState is ExerciseGuideUiState.Ready &&
        guideState.guide.instructions.isNotEmpty()

    val cs = MaterialTheme.colorScheme

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f),
            shape = MaterialTheme.shapes.extraLarge,
            color = cs.surfaceContainerHigh,
            tonalElevation = 3.dp
        ) {
            Column(Modifier.fillMaxSize()) {
                TutorialHeader(
                    exerciseName = exerciseName,
                    guideState = guideState,
                    onDismiss = onDismiss
                )

                HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.35f))

                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    when (guideState) {
                        ExerciseGuideUiState.Loading -> LoadingContent()
                        is ExerciseGuideUiState.Ready -> {
                            ExerciseGuideContent(
                                exerciseName = exerciseName,
                                guide = guideState.guide,
                                workoutMedia = guideState.workoutMedia,
                                showAiFill = showAiFill,
                                showChange = showChange,
                                busy = busy,
                                onAiFill = onAiFill,
                                onChangeAiSteps = onChangeAiSteps
                            )
                            if (guideState.isGeneratingAi) {
                                GeneratingOverlay()
                            }
                        }
                        is ExerciseGuideUiState.NoMatch -> EmptyGuideContent(
                            exerciseName = exerciseName,
                            showAiFill = showAiFill,
                            busy = busy,
                            onAiFill = onAiFill
                        ) {
                            NoMatchMessage(exerciseName)
                        }
                        is ExerciseGuideUiState.NeedsInternet -> EmptyGuideContent(
                            exerciseName = exerciseName,
                            showAiFill = showAiFill,
                            busy = busy,
                            onAiFill = onAiFill
                        ) {
                            OfflineGuideMessage(exerciseName)
                        }
                        is ExerciseGuideUiState.Error -> EmptyGuideContent(
                            exerciseName = exerciseName,
                            showAiFill = showAiFill,
                            busy = busy,
                            onAiFill = onAiFill
                        ) {
                            Text(guideState.message, color = cs.error, textAlign = TextAlign.Center)
                        }
                        is ExerciseGuideUiState.RateLimited -> EmptyGuideContent(
                            exerciseName = exerciseName,
                            showAiFill = showAiFill,
                            busy = busy,
                            onAiFill = onAiFill
                        ) {
                            Text(
                                "Tutorial service is busy. Try again in a moment or use AI Fill.",
                                color = cs.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                        ExerciseGuideUiState.Idle -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun TutorialHeader(
    exerciseName: String,
    guideState: ExerciseGuideUiState,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.FitnessCenter, null, tint = cs.primary)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                exerciseName,
                style = MaterialTheme.typography.titleLarge,
                color = cs.onSurface,
                fontWeight = FontWeight.Bold
            )
            when (guideState) {
                is ExerciseGuideUiState.Ready -> {
                    val subtitle = when (guideState.guide.source) {
                        GuideSource.BUNDLED -> if (guideState.guide.instructions.isNotEmpty()) {
                            "Curated tutorial"
                        } else {
                            "Catalog exercise"
                        }
                        GuideSource.DATASET -> "Exercise library"
                        GuideSource.AI -> "AI-generated steps"
                    }
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                is ExerciseGuideUiState.NoMatch -> Text(
                    "Exercise not in catalog",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
                else -> Text("Exercise tutorial", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, "Close", tint = cs.onSurface)
        }
    }
}

@Composable
private fun StepsSectionHeader(
    exerciseName: String,
    showAiFill: Boolean,
    showChange: Boolean,
    busy: Boolean,
    onAiFill: () -> Unit,
    onChangeAiSteps: () -> Unit
) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "Steps",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // YouTube button
            IconButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${exerciseName}+exercise+tutorial"))
                    context.startActivity(intent)
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.PlayCircle,
                    contentDescription = "YouTube tutorial",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
            }

            if (showChange) {
                FilledTonalButton(
                    onClick = onChangeAiSteps,
                    enabled = !busy,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Change", style = MaterialTheme.typography.labelMedium)
                }
            }
            if (showAiFill) {
                FilledTonalButton(
                    onClick = onAiFill,
                    enabled = !busy,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("AI Fill", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun EmptyGuideContent(
    exerciseName: String,
    showAiFill: Boolean,
    busy: Boolean,
    onAiFill: () -> Unit,
    message: @Composable (() -> Unit)
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StepsSectionHeader(
            exerciseName = exerciseName,
            showAiFill = showAiFill,
            showChange = false,
            busy = busy,
            onAiFill = onAiFill,
            onChangeAiSteps = {}
        )
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            contentAlignment = Alignment.Center
        ) {
            message()
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GymLoadingIndicator(message = "Loading tutorial…")
    }
}

@Composable
private fun GeneratingOverlay() {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center
    ) {
        ElevatedCard(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                GymLoadingIndicator()
                Spacer(Modifier.height(8.dp))
                Text("Generating steps…", color = cs.onSurface)
            }
        }
    }
}

@Composable
private fun ExerciseGuideContent(
    exerciseName: String,
    guide: ExerciseGuideDetail,
    workoutMedia: com.example.data.WorkoutMedia,
    showAiFill: Boolean,
    showChange: Boolean,
    busy: Boolean,
    onAiFill: () -> Unit,
    onChangeAiSteps: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.elevatedCardColors(containerColor = cs.surfaceContainerLowest)
        ) {
            ExerciseMediaCard(
                workoutMedia = workoutMedia,
                exerciseName = exerciseName,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
            )
        }

        // Use a Column here with smaller spacing to fix the "huge gap"
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (guide.targetMuscles.isNotEmpty() || guide.equipments.isNotEmpty() || guide.bodyParts.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    guide.bodyParts.take(3).forEach { part ->
                        MetaChip(part.replaceFirstChar { it.uppercase() })
                    }
                    guide.targetMuscles.take(5).forEach { muscle ->
                        MetaChip(muscle.replaceFirstChar { it.uppercase() })
                    }
                    guide.equipments.take(3).forEach { eq ->
                        MetaChip(eq.replaceFirstChar { it.uppercase() })
                    }
                }
            }

            StepsSectionHeader(
                exerciseName = exerciseName,
                showAiFill = showAiFill,
                showChange = showChange,
                busy = busy,
                onAiFill = onAiFill,
                onChangeAiSteps = onChangeAiSteps
            )
        }

        if (guide.instructions.isEmpty()) {
            Text(
                "No step-by-step guide bundled for this exercise yet. Tap AI Fill to generate instructions, or check back after an app update.",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                guide.instructions.forEachIndexed { index, step ->
                    val parts = ExerciseStepFormatter.parts(step, index)
                    Row(Modifier.fillMaxWidth()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = cs.primaryContainer,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                parts.tag,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = cs.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            parts.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Text(
            when (guide.source) {
                GuideSource.BUNDLED -> when {
                    guide.fromCache && guide.instructions.isNotEmpty() ->
                        "Saved offline — curated steps and muscle tags."
                    guide.instructions.isNotEmpty() ->
                        "Curated steps and muscle tags from the app catalog."
                    else ->
                        "Muscle tags from the app catalog. GIF loads from bundled assets or CDN."
                }
                GuideSource.DATASET -> if (guide.fromCache) {
                    "Saved offline — steps and GIF from the exercise library."
                } else {
                    "Steps, muscles, and GIF from the bundled exercise library."
                }
                GuideSource.AI ->
                    "AI-generated steps. Muscle tags use the app catalog when available."
            },
            style = MaterialTheme.typography.labelSmall,
            color = cs.secondary
        )
    }
}

@Composable
private fun MetaChip(label: String) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NoMatchMessage(exerciseName: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.SearchOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            "Not in exercise catalog",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "\"$exerciseName\" isn't in the built-in list. Use AI Fill to generate steps, or create it as a custom exercise with the correct muscle type.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun OfflineGuideMessage(exerciseName: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.WifiOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            "Limited offline tutorial",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Tutorial for \"$exerciseName\" isn't fully cached yet. Connect to the internet for animation diagrams, or use AI Fill for steps.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ActiveWorkoutExerciseTutorialHost(
    viewModel: GymViewModel,
    aiEnabled: Boolean,
    selectedExercise: String?,
    onDismiss: () -> Unit
) {
    val guideState by viewModel.exerciseGuideState.collectAsState()

    LaunchedEffect(selectedExercise) {
        if (selectedExercise != null) {
            viewModel.loadExerciseGuide(selectedExercise)
        } else {
            viewModel.clearExerciseGuideState()
        }
    }

    if (selectedExercise != null) {
        ExerciseTutorialDialog(
            exerciseName = selectedExercise,
            guideState = guideState,
            aiEnabled = aiEnabled,
            onDismiss = {
                viewModel.clearExerciseGuideState()
                onDismiss()
            },
            onAiFill = { viewModel.fillExerciseGuideFromAi(selectedExercise) },
            onChangeAiSteps = { viewModel.fillExerciseGuideFromAi(selectedExercise, replaceExisting = true) }
        )
    }
}
