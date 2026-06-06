@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ExerciseGuideUiState
import com.example.GymViewModel
import com.example.data.ExerciseGuideDetail
import com.example.data.ExerciseStepFormatter
import com.example.data.GuideSource
import com.example.ui.theme.*

@Composable
fun ExerciseTutorialDialog(
    exerciseName: String,
    guideState: ExerciseGuideUiState,
    aiEnabled: Boolean,
    isOnline: Boolean,
    onDismiss: () -> Unit,
    onAiFill: () -> Unit,
    onChangeAiSteps: () -> Unit,
    onCheckOnline: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    val busy = guideState == ExerciseGuideUiState.Loading ||
        (guideState is ExerciseGuideUiState.Ready && guideState.isGeneratingAi)
    val showAiFill = aiEnabled && when (guideState) {
        is ExerciseGuideUiState.Ready -> guideState.guide.source == GuideSource.API && !guideState.isGeneratingAi
        is ExerciseGuideUiState.NoMatch, is ExerciseGuideUiState.NeedsInternet, is ExerciseGuideUiState.Error -> true
        else -> false
    }
    val showChange = aiEnabled && guideState is ExerciseGuideUiState.Ready &&
        guideState.guide.source == GuideSource.AI && !guideState.isGeneratingAi

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(20.dp),
            color = SurfaceContainer
        ) {
            Column(Modifier.fillMaxSize()) {
                TutorialHeader(
                    exerciseName = exerciseName,
                    guideState = guideState,
                    onDismiss = onDismiss
                )

                HorizontalDivider(color = OutlineVariant.copy(0.25f))

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
                                guide = guideState.guide,
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
                            showAiFill = showAiFill,
                            busy = busy,
                            onAiFill = onAiFill
                        ) {
                            NoMatchMessage(exerciseName)
                        }
                        is ExerciseGuideUiState.NeedsInternet -> EmptyGuideContent(
                            showAiFill = showAiFill,
                            busy = busy,
                            onAiFill = onAiFill
                        ) {
                            OfflineGuideMessage(exerciseName)
                        }
                        is ExerciseGuideUiState.Error -> EmptyGuideContent(
                            showAiFill = showAiFill,
                            busy = busy,
                            onAiFill = onAiFill
                        ) {
                            Text(guideState.message, color = Error, textAlign = TextAlign.Center)
                        }
                        ExerciseGuideUiState.Idle -> Unit
                    }
                }

                HorizontalDivider(color = OutlineVariant.copy(0.25f))
                TutorialActionBar(
                    guideState = guideState,
                    isOnline = isOnline,
                    onCheckOnline = onCheckOnline
                )
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
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.FitnessCenter, null, tint = Primary)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                exerciseName,
                style = Typography.titleLarge,
                color = Primary,
                fontWeight = FontWeight.Bold
            )
            when (guideState) {
                is ExerciseGuideUiState.Ready -> {
                    val subtitle = when (guideState.guide.source) {
                        GuideSource.API -> guideState.guide.apiName
                        GuideSource.AI -> "AI-generated steps"
                    }
                    Text(subtitle, style = Typography.bodySmall, color = OnSurfaceVariant)
                }
                is ExerciseGuideUiState.NoMatch -> Text(
                    "No online match found",
                    style = Typography.bodySmall,
                    color = OnSurfaceVariant
                )
                else -> Text("Exercise tutorial", style = Typography.bodySmall, color = OnSurfaceVariant)
            }
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, "Close", tint = OnSurface)
        }
    }
}

@Composable
private fun StepsSectionHeader(
    showAiFill: Boolean,
    showChange: Boolean,
    busy: Boolean,
    onAiFill: () -> Unit,
    onChangeAiSteps: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "Steps",
            style = Typography.titleMedium,
            color = Primary,
            fontWeight = FontWeight.SemiBold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showChange) {
                Button(
                    onClick = onChangeAiSteps,
                    enabled = !busy,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Change", style = Typography.labelMedium)
                }
            }
            if (showAiFill) {
                Button(
                    onClick = onAiFill,
                    enabled = !busy,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("AI Fill", style = Typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun EmptyGuideContent(
    showAiFill: Boolean,
    busy: Boolean,
    onAiFill: () -> Unit,
    message: @Composable () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StepsSectionHeader(
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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Primary)
            Spacer(Modifier.height(12.dp))
            Text("Loading tutorial…", color = OnSurfaceVariant)
        }
    }
}

@Composable
private fun GeneratingOverlay() {
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = SurfaceContainer.copy(alpha = 0.92f),
            modifier = Modifier.fillMaxSize()
        ) {}
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Primary)
            Spacer(Modifier.height(12.dp))
            Text("Generating steps with AI…", color = OnSurfaceVariant)
        }
    }
}

@Composable
private fun ExerciseGuideContent(
    guide: ExerciseGuideDetail,
    showAiFill: Boolean,
    showChange: Boolean,
    busy: Boolean,
    onAiFill: () -> Unit,
    onChangeAiSteps: () -> Unit
) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        guide.gifModel?.let { model ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Background)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(model)
                        .crossfade(true)
                        .build(),
                    contentDescription = "${guide.displayName} demonstration",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 260.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Fit
                )
            }
        }

        if (guide.targetMuscles.isNotEmpty() || guide.equipments.isNotEmpty() || guide.bodyParts.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                guide.bodyParts.take(2).forEach { part ->
                    MetaChip(part.replaceFirstChar { it.uppercase() })
                }
                guide.targetMuscles.take(2).forEach { muscle ->
                    MetaChip(muscle.replaceFirstChar { it.uppercase() })
                }
                guide.equipments.take(2).forEach { eq ->
                    MetaChip(eq.replaceFirstChar { it.uppercase() })
                }
            }
        }

        StepsSectionHeader(
            showAiFill = showAiFill,
            showChange = showChange,
            busy = busy,
            onAiFill = onAiFill,
            onChangeAiSteps = onChangeAiSteps
        )
        guide.instructions.forEachIndexed { index, step ->
            val parts = ExerciseStepFormatter.parts(step, index)
            Row(Modifier.fillMaxWidth()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Primary.copy(0.15f),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        parts.tag,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = Typography.labelMedium,
                        color = Primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    parts.body,
                    style = Typography.bodyMedium,
                    color = OnSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Text(
            when (guide.source) {
                GuideSource.API -> if (guide.fromCache) "Saved offline — available without internet."
                else "Loaded from ExerciseDB."
                GuideSource.AI -> "AI-generated — saved locally."
            },
            style = Typography.labelSmall,
            color = Secondary
        )
    }
}

@Composable
private fun MetaChip(label: String) {
    Surface(shape = RoundedCornerShape(20.dp), color = SurfaceContainerHigh) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = Typography.labelSmall,
            color = OnSurfaceVariant
        )
    }
}

@Composable
private fun NoMatchMessage(exerciseName: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.SearchOff, null, tint = OnSurfaceVariant, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            "No exact match online",
            style = Typography.titleMedium,
            color = OnSurface,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "\"$exerciseName\" wasn't found in ExerciseDB. Use AI Fill to generate steps, or Check Online after correcting the name.",
            style = Typography.bodyMedium,
            color = OnSurfaceVariant,
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
        Icon(Icons.Default.WifiOff, null, tint = OnSurfaceVariant, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            "Turn on internet",
            style = Typography.titleMedium,
            color = OnSurface,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Tutorial for \"$exerciseName\" isn't cached yet. Connect to load GIF and steps from ExerciseDB, or use AI Fill offline.",
            style = Typography.bodyMedium,
            color = OnSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TutorialActionBar(
    guideState: ExerciseGuideUiState,
    isOnline: Boolean,
    onCheckOnline: () -> Unit
) {
    val showCheckOnline = when (guideState) {
        is ExerciseGuideUiState.Ready -> !guideState.isGeneratingAi
        is ExerciseGuideUiState.NoMatch, is ExerciseGuideUiState.NeedsInternet, is ExerciseGuideUiState.Error -> true
        else -> false
    }
    val busy = guideState == ExerciseGuideUiState.Loading ||
        (guideState is ExerciseGuideUiState.Ready && guideState.isGeneratingAi)

    if (!showCheckOnline) return

    Row(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onCheckOnline,
            shape = RoundedCornerShape(12.dp),
            enabled = isOnline && !busy
        ) {
            Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Check Online")
        }
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
    val isOnline = viewModel.exerciseGuideIsOnline()

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
            isOnline = isOnline,
            onDismiss = {
                viewModel.clearExerciseGuideState()
                onDismiss()
            },
            onAiFill = { viewModel.fillExerciseGuideFromAi(selectedExercise) },
            onChangeAiSteps = { viewModel.fillExerciseGuideFromAi(selectedExercise, replaceExisting = true) },
            onCheckOnline = { viewModel.fetchExerciseGuideOnline(selectedExercise) }
        )
    }
}
