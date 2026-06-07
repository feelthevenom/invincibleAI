package com.example.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import kotlinx.coroutines.delay

/**
 * Reveals [targetText] with a typewriter effect. When the target changes, erases from the end
 * then re-types the new content — used for AI insight cards on the home dashboard.
 */
@Composable
fun TypewriterInsightText(
    targetText: String,
    highlight: String?,
    modifier: Modifier = Modifier,
    eraseDelayMs: Long = 6L,
    typeDelayMs: Long = 14L
) {
    var displayed by remember { mutableStateOf("") }
    var lastTarget by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(targetText) {
        if (targetText.isBlank()) {
            displayed = ""
            lastTarget = targetText
            return@LaunchedEffect
        }
        if (lastTarget == targetText && displayed == targetText) return@LaunchedEffect

        if (displayed.isNotEmpty()) {
            while (displayed.isNotEmpty()) {
                displayed = displayed.dropLast(1)
                delay(eraseDelayMs)
            }
        }
        lastTarget = targetText
        for (i in targetText.indices) {
            displayed = targetText.substring(0, i + 1)
            delay(typeDelayMs)
        }
    }

    Text(
        text = buildAnnotatedString {
            val body = displayed
            val hl = highlight
            if (hl != null && body.contains(hl, ignoreCase = true)) {
                val start = body.indexOf(hl, ignoreCase = true)
                if (start >= 0) {
                    append(body.substring(0, start))
                    withStyle(
                        SpanStyle(
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold
                        )
                    ) {
                        append(body.substring(start, start + hl.length))
                    }
                    append(body.substring(start + hl.length))
                } else append(body)
            } else append(body)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    )
}
