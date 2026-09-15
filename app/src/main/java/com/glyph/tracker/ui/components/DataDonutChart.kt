package com.glyph.tracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.glyph.tracker.util.Formatters

@Composable
fun DataDonutChart(
    mobileBytes: Long,
    wifiBytes: Long,
    hotspotBytes: Long = 0L,
    modifier: Modifier = Modifier
) {
    val totalBytes = mobileBytes + wifiBytes

    val wifiRatio = if (totalBytes > 0) wifiBytes.toFloat() / totalBytes else 0.5f
    val mobileRatio = if (totalBytes > 0) mobileBytes.toFloat() / totalBytes else 0.5f

    val animatedWifiSweep by animateFloatAsState(
        targetValue = wifiRatio * 360f,
        animationSpec = tween(600),
        label = "wifi_sweep"
    )

    val animatedMobileSweep by animateFloatAsState(
        targetValue = mobileRatio * 360f,
        animationSpec = tween(600),
        label = "mobile_sweep"
    )

    // Vibrant/High-contrast color resolution to ensure segments remain distinct
    // even under Monochrome Monet / grayscale dynamic color schemes:
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    // Calculate perceptual contrast between primary and secondary in monochrome/dark modes
    val isMonochromeOrLowContrast = kotlin.math.abs(primaryColor.red - secondaryColor.red) < 0.05f &&
            kotlin.math.abs(primaryColor.green - secondaryColor.green) < 0.05f &&
            kotlin.math.abs(primaryColor.blue - secondaryColor.blue) < 0.05f

    val resolvedWifiColor = primaryColor
    val resolvedMobileColor = if (isMonochromeOrLowContrast) {
        primaryColor.copy(alpha = 0.65f)
    } else {
        secondaryColor
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("data_donut_chart")
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Network usage",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Donut Chart with Center Text
            Box(
                modifier = Modifier.size(170.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(170.dp)) {
                    val strokeWidth = 18.dp.toPx()
                    val diameter = size.minDimension - strokeWidth
                    val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                    val arcSize = Size(diameter, diameter)

                    // Background track
                    drawArc(
                        color = trackColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Wi-Fi segment (starts from top -90 deg)
                    if (animatedWifiSweep > 0) {
                        drawArc(
                            color = resolvedWifiColor,
                            startAngle = -90f,
                            sweepAngle = (animatedWifiSweep - 4f).coerceAtLeast(0f),
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    // Mobile data segment
                    if (animatedMobileSweep > 0) {
                        drawArc(
                            color = resolvedMobileColor,
                            startAngle = -90f + animatedWifiSweep,
                            sweepAngle = (animatedMobileSweep - 4f).coerceAtLeast(0f),
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }

                // Center Readout
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = Formatters.formatBytes(totalBytes),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Total used",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Legend indicators with Wi-Fi and Mobile
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Wi-Fi Legend Item
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(resolvedWifiColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Wi-Fi",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = Formatters.formatBytes(wifiBytes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Mobile Data Legend Item
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(resolvedMobileColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Mobile",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = Formatters.formatBytes(mobileBytes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
