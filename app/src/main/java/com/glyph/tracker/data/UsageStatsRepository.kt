package com.glyph.tracker.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
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

    // The device's home launcher spends real foreground time on the home screen / recents / app switching
    // gestures. Android counts that as "usage" too, which inflates total screen time by a fluctuating
    // amount (worse on MIUI/Redmi) that has nothing to do with actual app usage — so it's excluded below.
    private val launcherPackage: String? by lazy {
        try {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        } catch (_: Throwable) {
            null
        }
    }

    private fun isExcludedFromScreenTime(packageName: String): Boolean {
        if (packageName == launcherPackage) return true
        val lower = packageName.lowercase()
        return lower.contains("systemui") || lower == "android"
    }

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

        // 1. Primary Strategy: raw UsageEvents reconstruction, clipped to screen-ON periods.
        // This is the accurate approach — queryAndAggregateUsageStats()/queryUsageStats() often
        // over-count by several hours because totalTimeInForeground keeps accruing for an app that
        // was left "resumed" (e.g. screen locked without switching away) until Android eventually
        // emits a pause event. Here we explicitly close every open session the moment the screen
        // turns off, so screen-off time never gets counted as screen-on time.
        try {
            val events = usageStatsManager.queryEvents(startTime, endTime)
            val event = UsageEvents.Event()
            val appOpenTime = mutableMapOf<String, Long>()

            fun closeAllOpenSessions(atTime: Long) {
                for ((pkg, openedAt) in appOpenTime) {
                    if (atTime > openedAt) {
                        aggregatedMap[pkg] = (aggregatedMap[pkg] ?: 0L) + (atTime - openedAt)
                        val prev = lastUsedMap[pkg] ?: 0L
                        if (atTime > prev) lastUsedMap[pkg] = atTime
                    }
                }
                appOpenTime.clear()
            }

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val time = event.timeStamp
                when (event.eventType) {
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                        // Screen just turned off — stop counting every currently-open app session.
                        closeAllOpenSessions(time)
                    }
                    UsageEvents.Event.ACTIVITY_RESUMED,
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        val pkg = event.packageName ?: continue
                        appOpenTime[pkg] = time
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        val pkg = event.packageName ?: continue
                        val openedAt = appOpenTime.remove(pkg)
                        if (openedAt != null && time >= openedAt) {
                            aggregatedMap[pkg] = (aggregatedMap[pkg] ?: 0L) + (time - openedAt)
                        }
                        val prev = lastUsedMap[pkg] ?: 0L
                        if (time > prev) lastUsedMap[pkg] = time
                    }
                }
            }

            // Close any sessions still open at range end (e.g. the app currently on screen "today"),
            // capped at "now" so we never count time that hasn't happened yet.
            closeAllOpenSessions(minOf(endTime, System.currentTimeMillis()))
        } catch (_: Throwable) {}

        // 2. Fallback Strategy: queryAndAggregateUsageStats, only used if raw events were unavailable
        // (some OEMs restrict queryEvents). Note this can slightly over-count vs. Strategy 1.
        if (aggregatedMap.isEmpty()) {
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
        }

        // 3. Fallback Strategy: queryUsageStats with INTERVAL_BEST
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

        // 4. Fallback Strategy: queryUsageStats with INTERVAL_DAILY
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

        // Fetch real per-app network stats for this duration
        val networkStatsRepo = NetworkStatsRepository(context)
        val appNetworkStats = networkStatsRepo.getPerAppNetworkStats(startTime, endTime)

        val resultList = mutableListOf<AppUsageInfo>()
        for ((pkg, timeMillis) in aggregatedMap) {
            // Filter out the home launcher / SystemUI (see isExcludedFromScreenTime) and negligible records (< 1 sec)
            if (timeMillis < 1000L || isExcludedFromScreenTime(pkg)) continue

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
            if (aggregatedMap.containsKey(pkg) || isExcludedFromScreenTime(pkg)) continue
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
                    dayMillis = aggregated
                        ?.entries
                        ?.filterNot { isExcludedFromScreenTime(it.key) }
                        ?.sumOf { it.value.totalTimeInForeground } ?: 0L
                    if (dayMillis == 0L) {
                        val dayStats = usageStatsManager.queryUsageStats(
                            UsageStatsManager.INTERVAL_DAILY,
                            dayStart,
                            dayEnd
                        )
                        dayMillis = dayStats
                            ?.filterNot { isExcludedFromScreenTime(it.packageName) }
                            ?.sumOf { it.totalTimeInForeground } ?: 0L
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
