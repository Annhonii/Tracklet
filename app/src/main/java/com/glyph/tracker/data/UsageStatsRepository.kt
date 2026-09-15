package com.glyph.tracker.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.glyph.tracker.util.AppIconCache
import com.glyph.tracker.util.Formatters
import com.glyph.tracker.util.PermissionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

class UsageStatsRepository(private val context: Context) {

    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    private val packageManager: PackageManager = context.packageManager

    // In-memory caches to avoid expensive repeated IPC queries to PackageManager (no nulls stored)
    private val labelCache = ConcurrentHashMap<String, String>()
    private val iconCache = ConcurrentHashMap<String, Drawable>()

    suspend fun getTodayScreenTime(): Long = withContext(Dispatchers.IO) {
        try {
            val stats = getUsageStats(TimeFilter.TODAY)
            stats.sumOf { it.foregroundTimeMillis }
        } catch (_: Throwable) {
            0L
        }
    }

    suspend fun getUsageStats(filter: TimeFilter): List<AppUsageInfo> = withContext(Dispatchers.IO) {
        if (!PermissionUtils.hasUsageStatsPermission(context) || usageStatsManager == null) {
            // Never return mock/fake data; cleanly return empty list when permission is not granted
            return@withContext emptyList()
        }

        val (startTime, endTime) = getTimeRange(filter)
        val aggregatedMap = mutableMapOf<String, Long>()
        val lastUsedMap = mutableMapOf<String, Long>()

        // 1. Primary Strategy: queryAndAggregateUsageStats (cleanest aggregated interval representation)
        try {
            val aggregated = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
            if (!aggregated.isNullOrEmpty()) {
                for ((pkg, stats) in aggregated) {
                    val time = stats.totalTimeInForeground
                    if (time > 0) {
                        aggregatedMap[pkg] = (aggregatedMap[pkg] ?: 0L) + time
                        val last = stats.lastTimeUsed
                        val prev = lastUsedMap[pkg] ?: 0L
                        if (last > prev) lastUsedMap[pkg] = last
                    }
                }
            }
        } catch (_: Throwable) {}

        // 2. Secondary Strategy: queryUsageStats with INTERVAL_BEST if aggregated is empty
        if (aggregatedMap.isEmpty()) {
            try {
                val statsList = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_BEST,
                    startTime,
                    endTime
                )
                if (!statsList.isNullOrEmpty()) {
                    for (item in statsList) {
                        val time = item.totalTimeInForeground
                        if (time > 0) {
                            aggregatedMap[item.packageName] = (aggregatedMap[item.packageName] ?: 0L) + time
                            val last = item.lastTimeUsed
                            val prev = lastUsedMap[item.packageName] ?: 0L
                            if (last > prev) lastUsedMap[item.packageName] = last
                        }
                    }
                }
            } catch (_: Throwable) {}
        }

        // 3. Tertiary Strategy: queryUsageStats with INTERVAL_DAILY
        if (aggregatedMap.isEmpty()) {
            try {
                val statsList = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    startTime,
                    endTime
                )
                if (!statsList.isNullOrEmpty()) {
                    for (item in statsList) {
                        val time = item.totalTimeInForeground
                        if (time > 0) {
                            aggregatedMap[item.packageName] = (aggregatedMap[item.packageName] ?: 0L) + time
                            val last = item.lastTimeUsed
                            val prev = lastUsedMap[item.packageName] ?: 0L
                            if (last > prev) lastUsedMap[item.packageName] = last
                        }
                    }
                }
            } catch (_: Throwable) {}
        }

        // 4. Raw Events Strategy: queryEvents for current day live session calculation
        if (aggregatedMap.isEmpty()) {
            try {
                val events = usageStatsManager.queryEvents(startTime, endTime)
                val event = UsageEvents.Event()
                val appOpenTime = mutableMapOf<String, Long>()
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    val pkg = event.packageName ?: continue
                    val time = event.timeStamp
                    when (event.eventType) {
                        UsageEvents.Event.ACTIVITY_RESUMED,
                        UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                            appOpenTime[pkg] = time
                        }
                        UsageEvents.Event.ACTIVITY_PAUSED,
                        UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                            val openedAt = appOpenTime.remove(pkg)
                            if (openedAt != null && time >= openedAt) {
                                val duration = time - openedAt
                                aggregatedMap[pkg] = (aggregatedMap[pkg] ?: 0L) + duration
                            }
                            val prev = lastUsedMap[pkg] ?: 0L
                            if (time > prev) lastUsedMap[pkg] = time
                        }
                    }
                }
            } catch (_: Throwable) {}
        }

        // Fetch real per-app network stats for this duration
        val networkStatsRepo = NetworkStatsRepository(context)
        val appNetworkStats = networkStatsRepo.getPerAppNetworkStats(startTime, endTime)

        val resultList = mutableListOf<AppUsageInfo>()
        for ((pkg, timeMillis) in aggregatedMap) {
            // Filter out system launchers or negligible records (< 1 sec)
            if (timeMillis < 1000L) continue

            val appName = getAppLabel(pkg)
            val icon = getAppIcon(pkg)
            val category = guessCategory(pkg)
            // FIX: icon can be null (uninstalled/restricted package) — guard before prewarm,
            // otherwise AppIconCache may reject/NPE on a null value just like the old iconCache bug did.
            if (icon != null) AppIconCache.prewarm(pkg, icon)

            val (mobBytes, wifiBytes) = appNetworkStats[pkg] ?: Pair(0L, 0L)

            resultList.add(
                AppUsageInfo(
                    packageName = pkg,
                    appName = appName,
                    icon = icon,
                    foregroundTimeMillis = timeMillis,
                    lastTimeUsed = lastUsedMap[pkg] ?: 0L,
                    mobileBytes = mobBytes,
                    wifiBytes = wifiBytes,
                    category = category
                )
            )
        }

        // Also add apps with network data usage that may have run in the background
        for ((pkg, dataUsage) in appNetworkStats) {
            if (aggregatedMap.containsKey(pkg)) continue
            val (mobBytes, wifiBytes) = dataUsage
            if (mobBytes + wifiBytes > 50 * 1024L) { // > 50 KB
                val isHotspot = (pkg == "tethering.hotspot")
                val appName = if (isHotspot) "Hotspot & tethering" else getAppLabel(pkg)
                val icon = if (isHotspot) null else getAppIcon(pkg)
                val category = if (isHotspot) AppCategory.UTILITIES else guessCategory(pkg)
                if (icon != null) AppIconCache.prewarm(pkg, icon)

                resultList.add(
                    AppUsageInfo(
                        packageName = pkg,
                        appName = appName,
                        icon = icon,
                        foregroundTimeMillis = 0L,
                        lastTimeUsed = lastUsedMap[pkg] ?: 0L,
                        mobileBytes = mobBytes,
                        wifiBytes = wifiBytes,
                        category = category
                    )
                )
            }
        }

        // Ensure strict package uniqueness to prevent duplicate key crashes in Compose LazyColumn
        resultList.distinctBy { it.packageName }.sortedByDescending { it.foregroundTimeMillis }
    }

    suspend fun getWeeklyHistory(): List<DayUsage> = withContext(Dispatchers.IO) {
        val history = mutableListOf<DayUsage>()

        for (i in 6 downTo 0) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val dayStart = cal.timeInMillis

            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            val dayEnd = cal.timeInMillis

            val isToday = (i == 0)
            val label = Formatters.formatDayOfWeek(dayStart)

            var dayMillis = 0L
            if (PermissionUtils.hasUsageStatsPermission(context) && usageStatsManager != null) {
                try {
                    val aggregated = usageStatsManager.queryAndAggregateUsageStats(dayStart, dayEnd)
                    dayMillis = aggregated?.values?.sumOf { it.totalTimeInForeground } ?: 0L
                    if (dayMillis == 0L) {
                        val dayStats = usageStatsManager.queryUsageStats(
                            UsageStatsManager.INTERVAL_DAILY,
                            dayStart,
                            dayEnd
                        )
                        dayMillis = dayStats?.sumOf { it.totalTimeInForeground } ?: 0L
                    }
                } catch (_: Throwable) {
                    dayMillis = 0L
                }
            }

            history.add(
                DayUsage(
                    dayLabel = label,
                    timestamp = dayStart,
                    screenTimeMillis = dayMillis,
                    isToday = isToday
                )
            )
        }

        history
    }

    private fun getTimeRange(filter: TimeFilter): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis

        when (filter) {
            TimeFilter.TODAY -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
            }
            TimeFilter.WEEK -> {
                calendar.add(Calendar.DAY_OF_YEAR, -7)
            }
            TimeFilter.MONTH -> {
                calendar.add(Calendar.DAY_OF_YEAR, -30)
            }
        }
        val startTime = calendar.timeInMillis
        return Pair(startTime, endTime)
    }

    private fun getAppLabel(packageName: String): String {
        labelCache[packageName]?.let { return it }
        val label = try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Throwable) {
            packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
        if (label.isNotEmpty()) {
            labelCache[packageName] = label
        }
        return label
    }

    private fun getAppIcon(packageName: String): Drawable? {
        if (iconCache.containsKey(packageName)) {
            return iconCache[packageName]
        }
        val icon = try {
            packageManager.getApplicationIcon(packageName)
        } catch (_: Throwable) {
            null
        }
        if (icon != null) {
            iconCache[packageName] = icon
        }
        return icon
    }

    private fun guessCategory(packageName: String): AppCategory {
        val lower = packageName.lowercase()
        return when {
            lower.contains("youtube") || lower.contains("netflix") || lower.contains("spotify") || lower.contains("twitch") || lower.contains("video") || lower.contains("prime") || lower.contains("hotstar") -> AppCategory.ENTERTAINMENT
            lower.contains("instagram") || lower.contains("twitter") || lower.contains("facebook") || lower.contains("whatsapp") || lower.contains("telegram") || lower.contains("tiktok") || lower.contains("reddit") || lower.contains("snapchat") || lower.contains("threads") || lower.contains("signal") -> AppCategory.SOCIAL
            lower.contains("chrome") || lower.contains("browser") || lower.contains("gmail") || lower.contains("docs") || lower.contains("sheet") || lower.contains("slack") || lower.contains("notion") || lower.contains("drive") || lower.contains("office") -> AppCategory.PRODUCTIVITY
            lower.contains("game") || lower.contains("pubg") || lower.contains("roblox") || lower.contains("unity") || lower.contains("clash") || lower.contains("freefire") || lower.contains("genshin") -> AppCategory.GAMING
            lower.contains("android") || lower.contains("system") || lower.contains("settings") || lower.contains("launcher") -> AppCategory.SYSTEM
            else -> AppCategory.UTILITIES
        }
    }
}
