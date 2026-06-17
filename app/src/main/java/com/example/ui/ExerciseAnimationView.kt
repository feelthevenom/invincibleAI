package com.example.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.imageLoader
import coil.request.ImageRequest
import com.example.data.WorkoutMedia

@Composable
fun ExerciseAnimationView(
    media: WorkoutMedia,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    when (media) {
        is WorkoutMedia.Gif, is WorkoutMedia.StillImage -> {
            val model = when (media) {
                is WorkoutMedia.Gif -> media.model
                is WorkoutMedia.StillImage -> media.model
                else -> return
            }
            val context = LocalContext.current
            var loadState by remember(model) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }

            Box(modifier = modifier.clip(RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                when (val state = loadState) {
                    is AsyncImagePainter.State.Loading, AsyncImagePainter.State.Empty -> {
                        ShimmerPlaceholder(modifier = Modifier.matchParentSize())
                    }
                    is AsyncImagePainter.State.Error -> {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.FitnessCenter,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Could not load exercise animation",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    else -> Unit
                }

                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(model)
                        .crossfade(true)
                        .build(),
                    imageLoader = context.imageLoader,
                    contentDescription = "Exercise animation",
                    modifier = Modifier.matchParentSize(),
                    contentScale = contentScale,
                    onState = { loadState = it }
                )
            }
        }
        is WorkoutMedia.CustomFallback -> Unit
        WorkoutMedia.None -> Unit
    }
}

@Composable
private fun ShimmerPlaceholder(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "gif_shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gif_shimmer_alpha"
    )
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha))
    )
}

@Composable
fun ExerciseMediaCard(
    workoutMedia: WorkoutMedia,
    exerciseName: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp),
        contentAlignment = Alignment.Center
    ) {
        when (workoutMedia) {
            is WorkoutMedia.Gif, is WorkoutMedia.StillImage -> {
                ExerciseAnimationView(
                    media = workoutMedia,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                )
            }
            is WorkoutMedia.CustomFallback -> {
                val context = LocalContext.current
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No matching demo found for \"$exerciseName\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(workoutMedia.searchUrl)
                            )
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Watch Video", fontWeight = FontWeight.Bold)
                    }
                }
            }
            WorkoutMedia.None -> {
                ShimmerPlaceholder(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                )
                Text(
                    "Loading animation for $exerciseName…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                )
            }
        }
    }
}
