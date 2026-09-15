package com.glyph.tracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.glyph.tracker.data.AppUsageInfo
import com.glyph.tracker.data.DataFilter
import com.glyph.tracker.data.DayUsage
import com.glyph.tracker.data.TimeFilter
import com.glyph.tracker.data.UsageSummary
import com.glyph.tracker.ui.components.AppUsageRow
import com.glyph.tracker.ui.components.DataDonutChart
import com.glyph.tracker.ui.components.WeeklyBarChart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataUsageScreen(
    summary: UsageSummary,
    apps: List<AppUsageInfo>,
    weeklyHistory: List<DayUsage>,
    selectedTimeFilter: TimeFilter,
    onTimeFilterSelect: (TimeFilter) -> Unit,
    selectedDataFilter: DataFilter,
    onDataFilterSelect: (DataFilter) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onAppClick: (AppUsageInfo) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val filteredApps = remember(apps, selectedDataFilter, searchQuery) {
        apps.filter { app ->
            val hasData = when (selectedDataFilter) {
                DataFilter.ALL -> app.totalBytes > 0
                DataFilter.MOBILE -> app.mobileBytes > 0
                DataFilter.WIFI -> app.wifiBytes > 0
            }
            val matchesQuery = searchQuery.isEmpty() ||
                    app.appName.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)
            hasData && matchesQuery
        }.sortedByDescending { app ->
            when (selectedDataFilter) {
                DataFilter.ALL -> app.totalBytes
                DataFilter.MOBILE -> app.mobileBytes
                DataFilter.WIFI -> app.wifiBytes
            }
        }
    }

    val maxDataBytes = remember(filteredApps, selectedDataFilter) {
        filteredApps.maxOfOrNull { app ->
            when (selectedDataFilter) {
                DataFilter.ALL -> app.totalBytes
                DataFilter.MOBILE -> app.mobileBytes
                DataFilter.WIFI -> app.wifiBytes
            }
        } ?: 1L
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("data_usage_screen")
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(10.dp))

            // Clean Pixel Top Header: Title and Settings Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Data usage",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .testTag("header_settings_button_data")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Minimal Pill Time Filter (Today / 7 Days / 30 Days)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TimeFilter.values().forEach { filter ->
                    val isSelected = selectedTimeFilter == filter
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.surface
                                else androidx.compose.ui.graphics.Color.Transparent
                            )
                            .clickable { onTimeFilterSelect(filter) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = filter.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Network Consumption Donut
        item {
            DataDonutChart(
                mobileBytes = summary.totalMobileBytes,
                wifiBytes = summary.totalWifiBytes,
                hotspotBytes = summary.totalHotspotBytes
            )
        }

        // Screen-on-time history — same daily bars as the Screen Time tab, shown here too so you can
        // see the SOT trend alongside data usage. Resets automatically each day (backed by weeklyHistory,
        // which is recomputed against the current day's date range on every refresh).
        if (weeklyHistory.isNotEmpty()) {
            item {
                WeeklyBarChart(days = weeklyHistory)
            }
        }

        // Data Filter Chips (All Data / Mobile / Wi-Fi)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DataFilter.values().forEach { filter ->
                    val isSelected = selectedDataFilter == filter
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.surface
                                else androidx.compose.ui.graphics.Color.Transparent
                            )
                            .clickable { onDataFilterSelect(filter) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = filter.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Search Field
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_data_apps_field"),
                placeholder = {
                    Text(
                        "Search apps",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }

        // Apps List
        if (filteredApps.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No apps match your search" else "No network activity recorded",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(
                items = filteredApps,
                key = { app -> "${app.packageName}_${app.lastTimeUsed}" },
                contentType = { "app_usage_row" }
            ) { appInfo ->
                AppUsageRow(
                    app = appInfo,
                    maxMetricValue = maxDataBytes,
                    isDataMode = true,
                    dataFilter = selectedDataFilter,
                    onClick = { onAppClick(appInfo) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
