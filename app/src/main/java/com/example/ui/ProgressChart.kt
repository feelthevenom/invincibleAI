package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.ProgressChartPoint
import com.example.data.DietDateUtils
import com.example.ui.theme.OnSurfaceVariant
import com.example.ui.theme.Secondary
import com.example.ui.theme.SurfaceContainerHighest
import com.example.ui.theme.Typography

@Composable
fun ProgressSplineChart(
    points: List<ProgressChartPoint>,
    lineColor: Color = Secondary,
    modifier: Modifier = Modifier.height(160.dp)
) {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    if (points.size < 2) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                if (points.isEmpty()) "Log at least 2 entries to see your progress curve"
                else "Add one more entry to plot the curve",
                style = Typography.bodySmall,
                color = OnSurfaceVariant
            )
        }
        return
    }

    val minVal = points.minOf { it.value }
    val maxVal = points.maxOf { it.value }
    val valueRange = (maxVal - minVal).coerceAtLeast(0.1f)

    Column(modifier = modifier.fillMaxWidth()) {
        selectedIndex?.let { idx ->
            points.getOrNull(idx)?.let { pt ->
                Text(
                    "${DietDateUtils.formatLogTimestamp(pt.timestamp)} · ${pt.label}",
                    style = Typography.labelMedium,
                    color = lineColor,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        val step = size.width.toFloat() / (points.size - 1).coerceAtLeast(1)
                        val index = (offset.x / step).toInt().coerceIn(0, points.lastIndex)
                        selectedIndex = if (selectedIndex == index) null else index
                    }
                }
        ) {
            val padding = 16f
            val chartW = size.width - padding * 2
            val chartH = size.height - padding * 2

            fun xAt(i: Int) = padding + chartW * i / (points.size - 1).coerceAtLeast(1)
            fun yAt(v: Float) = padding + chartH - ((v - minVal) / valueRange) * chartH

            val coords = points.mapIndexed { i, p -> Offset(xAt(i), yAt(p.value)) }

            val path = Path().apply {
                moveTo(coords.first().x, coords.first().y)
                for (i in 0 until coords.lastIndex) {
                    val p0 = coords[i]
                    val p1 = coords[i + 1]
                    val cx = (p0.x + p1.x) / 2f
                    cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                }
            }
            drawPath(path, lineColor, style = Stroke(width = 4f, cap = StrokeCap.Round))

            coords.forEachIndexed { i, c ->
                val isSelected = selectedIndex == i
                drawCircle(
                    color = if (isSelected) lineColor else SurfaceContainerHighest,
                    radius = if (isSelected) 10f else 7f,
                    center = c
                )
                drawCircle(color = lineColor, radius = if (isSelected) 6f else 4f, center = c)
            }
        }
    }
}

@Composable
fun WaterSegmentBar(progress: Float, modifier: Modifier = Modifier) {
    val segments = 4
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(segments) { index ->
            val filled = progress >= (index + 1) / segments.toFloat()
            Box(
                Modifier
                    .weight(1f)
                    .height(6.dp)
                    .background(
                        if (filled) Secondary else SurfaceContainerHighest,
                        RoundedCornerShape(50)
                    )
            )
        }
    }
}
