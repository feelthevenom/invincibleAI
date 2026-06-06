package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.CoachInsight
import com.example.ui.theme.*

@Composable
fun AiCoachInsightCard(insight: CoachInsight, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceContainer, RoundedCornerShape(16.dp))
            .border(1.dp, Primary.copy(0.35f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                insight.headline.uppercase(),
                style = Typography.labelMedium,
                color = Primary,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = buildAnnotatedString {
                val body = insight.body
                val highlight = insight.highlight
                if (highlight != null && body.contains(highlight, ignoreCase = true)) {
                    val start = body.indexOf(highlight, ignoreCase = true)
                    if (start >= 0) {
                        append(body.substring(0, start))
                        withStyle(SpanStyle(color = Secondary, fontWeight = FontWeight.Bold)) {
                            append(body.substring(start, start + highlight.length))
                        }
                        append(body.substring(start + highlight.length))
                    } else append(body)
                } else append(body)
            },
            style = Typography.bodyMedium,
            color = OnSurface
        )
    }
}
