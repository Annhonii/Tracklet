package com.glyph.tracker.data

import android.graphics.drawable.Drawable

enum class AppCategory(val displayName: String) {
    SOCIAL("Social"),
    ENTERTAINMENT("Entertainment"),
    PRODUCTIVITY("Productivity"),
    GAMING("Games"),
    UTILITIES("Utilities"),
    SYSTEM("System"),
    OTHER("Other")
}

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null,
    val foregroundTimeMillis: Long = 0L,
    val lastTimeUsed: Long = 0L,
    val launchCount: Int = 0,
    val mobileBytes: Long = 0L,
    val wifiBytes: Long = 0L,
    val category: AppCategory = AppCategory.OTHER
) {
    val totalBytes: Long
        get() = mobileBytes + wifiBytes
}

data class DayUsage(
    val dayLabel: String,
    val timestamp: Long,
    val screenTimeMillis: Long,
    val isToday: Boolean = false
)

enum class TimeFilter(val label: String) {
    TODAY("Today"),
    WEEK("7 Days"),
    MONTH("30 Days")
}

enum class DataFilter(val label: String) {
    ALL("All Data"),
    MOBILE("Mobile"),
    WIFI("Wi-Fi")
}

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

data class UsageSummary(
    val totalScreenTimeMillis: Long = 0L,
    val totalMobileBytes: Long = 0L,
    val totalWifiBytes: Long = 0L,
    val totalHotspotBytes: Long = 0L,
    val totalAppCount: Int = 0,
    val mostUsedApp: AppUsageInfo? = null
) {
    val totalBytes: Long
        get() = totalMobileBytes + totalWifiBytes + totalHotspotBytes
}
