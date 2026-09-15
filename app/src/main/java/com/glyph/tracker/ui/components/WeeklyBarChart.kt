package com.glyph.tracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.glyph.tracker.data.DayUsage
import com.glyph.tracker.util.Formatters

@Composable
fun WeeklyBarChart(
    days: List<DayUsage>,
    modifier: Modifier = Modifier
) {
    if (days.isEmpty()) return

    var selectedDayIndex by remember {
        mutableStateOf<Int?>(days.indexOfFirst { it.isToday }.takeIf { it >= 0 } ?: (days.size - 1))
    }
    val maxMillis = (days.maxOfOrNull { it.screenTimeMillis } ?: 1L).coerceAtLeast(60_000L)
    val selectedDay = selectedDayIndex?.let { days.getOrNull(it) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("weekly_bar_chart")
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(20.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Daily activity",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Last 7 days",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (selectedDay != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = selectedDay.dayLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = Formatters.formatDuration(selectedDay.screenTimeMillis),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            val primaryColor = MaterialTheme.colorScheme.primary
            val selectedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
            val defaultColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            val slotBgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)

            // Canvas Bar Chart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .pointerInput(days) {
                            detectTapGestures { offset ->
                                val slotWidth = size.width / days.size
                                val tappedIndex = (offset.x / slotWidth).toInt().coerceIn(0, days.size - 1)
                                selectedDayIndex = tappedIndex
                            }
                        }
                ) {
                    val width = size.width
                    val height = size.height
                    val barCount = days.size
                    val slotWidth = width / barCount
                    val barWidth = (slotWidth * 0.45f).coerceIn(12f, 28f)

                    days.forEachIndexed { index, day ->
                        val centerX = slotWidth * index + slotWidth / 2
                        val barLeft = centerX - barWidth / 2
                        val ratio = (day.screenTimeMillis.toFloat() / maxMillis.toFloat()).coerceIn(0f, 1f)
                        val barHeight = (ratio * height * 0.9f).coerceAtLeast(8f)
                        val barTop = height - barHeight

                        // Background pillar
                        drawRoundRect(
                            color = slotBgColor,
                            topLeft = Offset(barLeft, 0f),
                            size = Size(barWidth, height),
                            cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
                        )

                        val barColor = when {
                            index == selectedDayIndex -> selectedColor
                            day.isToday -> primaryColor
                            else -> defaultColor
                        }

                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(barLeft, barTop),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Day labels row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                days.forEachIndexed { index, day ->
                    val isSelected = index == selectedDayIndex
                    Text(
                        text = day.dayLabel.take(1),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected || day.isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected || day.isToday) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
