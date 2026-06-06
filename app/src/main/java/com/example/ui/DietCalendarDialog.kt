package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.DietDateUtils
import com.example.data.DietDateUtils.DaySummary
import com.example.ui.theme.*
import java.util.Calendar

@Composable
fun DietCalendarDialog(
    selectedDayStart: Long,
    daySummaries: Map<Long, DaySummary>,
    onDismiss: () -> Unit,
    onDaySelected: (Long) -> Unit,
    onFutureDayBlocked: (() -> Unit)? = null
) {
    val initialCal = Calendar.getInstance().apply { timeInMillis = selectedDayStart }
    var viewYear by remember { mutableIntStateOf(initialCal.get(Calendar.YEAR)) }
    var viewMonth by remember { mutableIntStateOf(initialCal.get(Calendar.MONTH)) }
    
    // UI state for switching between month view and year selection view
    var isYearSelectionMode by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(Modifier.padding(20.dp)) {
                // Header: Navigate Month or Toggle Year Selection
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        if (isYearSelectionMode) {
                            viewYear -= 12 // Page back 12 years
                        } else {
                            if (viewMonth == Calendar.JANUARY) {
                                viewMonth = Calendar.DECEMBER
                                viewYear--
                            } else viewMonth--
                        }
                    }) {
                        Icon(Icons.Default.ChevronLeft, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    
                    Text(
                        text = if (isYearSelectionMode) {
                            "$viewYear"
                        } else {
                            DietDateUtils.formatMonthYear(
                                Calendar.getInstance().apply {
                                    set(Calendar.YEAR, viewYear)
                                    set(Calendar.MONTH, viewMonth)
                                    set(Calendar.DAY_OF_MONTH, 1)
                                }.timeInMillis
                            )
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { isYearSelectionMode = !isYearSelectionMode }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                    
                    IconButton(onClick = {
                        if (isYearSelectionMode) {
                            viewYear += 12 // Page forward 12 years
                        } else {
                            if (viewMonth == Calendar.DECEMBER) {
                                viewMonth = Calendar.JANUARY
                                viewYear++
                            } else viewMonth++
                        }
                    }) {
                        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(Modifier.height(12.dp))

                AnimatedContent(
                    targetState = isYearSelectionMode,
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    },
                    label = "calendar_content"
                ) { isYearMode ->
                    if (isYearMode) {
                        YearSelectionGrid(
                            currentYear = viewYear,
                            onYearSelected = {
                                viewYear = it
                                isYearSelectionMode = false
                            }
                        )
                    } else {
                        MonthViewGrid(
                            viewYear = viewYear,
                            viewMonth = viewMonth,
                            selectedDayStart = selectedDayStart,
                            daySummaries = daySummaries,
                            onDaySelected = onDaySelected,
                            onFutureDayBlocked = onFutureDayBlocked
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

@Composable
private fun MonthViewGrid(
    viewYear: Int,
    viewMonth: Int,
    selectedDayStart: Long,
    daySummaries: Map<Long, DaySummary>,
    onDaySelected: (Long) -> Unit,
    onFutureDayBlocked: (() -> Unit)? = null
) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { name ->
                Text(
                    name,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        val cells = remember(viewYear, viewMonth) {
            DietDateUtils.daysInMonthGrid(viewYear, viewMonth)
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.height(260.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(cells) { cell ->
                when (cell) {
                    DietDateUtils.CalendarDayCell.Empty -> Spacer(Modifier.size(36.dp))
                    is DietDateUtils.CalendarDayCell.Day -> {
                        val summary = daySummaries[cell.dayStart]
                        val isSelected = cell.dayStart == selectedDayStart
                        val isFuture = DietDateUtils.isFuture(cell.dayStart)
                        val hasData = summary?.hasData == true
                        val goalMet = summary?.goalMet == true
                        val bg = when {
                            isFuture -> MaterialTheme.colorScheme.surfaceVariant.copy(0.15f)
                            isSelected -> MaterialTheme.colorScheme.primary
                            goalMet -> MaterialTheme.colorScheme.secondary.copy(0.85f)
                            hasData -> MaterialTheme.colorScheme.secondary.copy(0.18f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(0.35f)
                        }
                        val textColor = when {
                            isFuture -> MaterialTheme.colorScheme.onSurfaceVariant.copy(0.35f)
                            isSelected || goalMet -> MaterialTheme.colorScheme.onPrimary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(bg)
                                .then(
                                    if (isSelected) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                    else Modifier
                                )
                                .clickable {
                                    if (isFuture) onFutureDayBlocked?.invoke()
                                    else onDaySelected(cell.dayStart)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${cell.dayNumber}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = if (isSelected || goalMet) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = textColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YearSelectionGrid(
    currentYear: Int,
    onYearSelected: (Int) -> Unit
) {
    // Show a 3x4 grid of years centered around the current viewYear
    val years = remember(currentYear) {
        val startYear = currentYear - 5
        (startYear until startYear + 12).toList()
    }

    Column(Modifier.height(280.dp)) {
        Text(
            "Select Year",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(years) { year ->
                val isSelected = year == Calendar.getInstance().get(Calendar.YEAR)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(0.3f))
                        .border(
                            width = if (isSelected) 1.dp else 0.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onYearSelected(year) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$year",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
