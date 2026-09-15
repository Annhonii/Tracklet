package com.glyph.tracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glyph.tracker.data.AppCategory
import com.glyph.tracker.data.AppUsageInfo
import com.glyph.tracker.data.DataFilter
import com.glyph.tracker.util.AppIconCache
import com.glyph.tracker.util.Formatters

/** Gives each usage row a distinct accent color based on the app's category — breaks up the flat list visually. */
@Composable
private fun categoryAccent(category: AppCategory) = when (category) {
    AppCategory.ENTERTAINMENT -> MaterialTheme.colorScheme.tertiary
    AppCategory.SOCIAL -> MaterialTheme.colorScheme.primary
    AppCategory.PRODUCTIVITY -> MaterialTheme.colorScheme.secondary
    AppCategory.GAMING -> MaterialTheme.colorScheme.error
    AppCategory.SYSTEM -> MaterialTheme.colorScheme.outline
    AppCategory.UTILITIES -> MaterialTheme.colorScheme.secondary
    AppCategory.OTHER -> MaterialTheme.colorScheme.outline
}

@Composable
fun AppUsageRow(
    app: AppUsageInfo,
    maxMetricValue: Long,
    isDataMode: Boolean = false,
    dataFilter: DataFilter = DataFilter.ALL,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Respect the active Wi-Fi / Mobile / All tab instead of always showing combined total bytes
    val metricValue = if (isDataMode) {
        when (dataFilter) {
            DataFilter.ALL -> app.totalBytes
            DataFilter.MOBILE -> app.mobileBytes
            DataFilter.WIFI -> app.wifiBytes
        }
    } else {
        app.foregroundTimeMillis
    }
    val targetProgress = if (maxMetricValue > 0) (metricValue.toFloat() / maxMetricValue.toFloat()).coerceIn(0f, 1f) else 0f
    val isHotspotItem = app.packageName == "tethering.hotspot"
    val accent = if (isHotspotItem) MaterialTheme.colorScheme.tertiary else categoryAccent(app.category)

    // Animate the bar filling in instead of snapping straight to its value
    val progressRatio by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 500),
        label = "usageProgress"
    )

    val appBitmap = remember(app.packageName, app.icon) {
        if (!isHotspotItem) {
            AppIconCache.getOrCreate(app.packageName, app.icon)
        } else {
            null
        }
    }

    Box(
        modifier = modifier
            .testTag("app_usage_row_${app.packageName}")
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .border(1.dp, accent.copy(alpha = 0.14f), RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App Icon or clean initial / Hotspot icon placeholder — ringed with the category accent
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.14f))
                        .border(1.5.dp, accent.copy(alpha = 0.3f), CircleShape)
                ) {
                    if (isHotspotItem) {
                        Icon(
                            imageVector = Icons.Outlined.WifiTethering,
                            contentDescription = "Hotspot & tethering",
                            tint = accent,
                            modifier = Modifier.size(22.dp)
                        )
                    } else if (appBitmap != null) {
                        Image(
                            bitmap = appBitmap,
                            contentDescription = app.appName,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Text(
                            text = app.appName.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                // App Name and Category
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = app.category.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = accent
                    )
                }

                // Metric Value Text
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (isDataMode) {
                            Formatters.formatBytes(metricValue)
                        } else {
                            Formatters.formatDuration(metricValue)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    // Only show the Wi-Fi/Mobile breakdown line on the "All Data" tab — on the Wi-Fi or
                    // Mobile tab the main value above is already filtered to that type, so repeating a
                    // combined breakdown here would be confusing/redundant.
                    if (isDataMode && dataFilter == DataFilter.ALL) {
                        val subtitleText = when {
                            app.mobileBytes > 0 && app.wifiBytes > 0 ->
                                "Wi-Fi: ${Formatters.formatBytes(app.wifiBytes)} • Mob: ${Formatters.formatBytes(app.mobileBytes)}"
                            app.wifiBytes > 0 -> "Wi-Fi: ${Formatters.formatBytes(app.wifiBytes)}"
                            app.mobileBytes > 0 -> "Mobile: ${Formatters.formatBytes(app.mobileBytes)}"
                            else -> ""
                        }
                        if (subtitleText.isNotEmpty()) {
                            Text(
                                text = subtitleText,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Animated gradient progress bar, tinted per-category instead of one flat color for every row
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressRatio)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(accent.copy(alpha = 0.6f), accent)
                            )
                        )
                )
            }
        }
    }
}
