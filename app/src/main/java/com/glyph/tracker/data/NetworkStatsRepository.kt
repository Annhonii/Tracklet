package com.glyph.tracker.data

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import com.glyph.tracker.util.PermissionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

data class NetworkDataBreakdown(
    val mobileBytes: Long,
    val wifiBytes: Long,
    val hotspotBytes: Long = 0L
) {
    val totalBytes: Long get() = mobileBytes + wifiBytes + hotspotBytes
    val mobileRatio: Float get() = if (totalBytes > 0) mobileBytes.toFloat() / totalBytes else 0.45f
    val wifiRatio: Float get() = if (totalBytes > 0) wifiBytes.toFloat() / totalBytes else 0.45f
    val hotspotRatio: Float get() = if (totalBytes > 0) hotspotBytes.toFloat() / totalBytes else 0.1f
}

class NetworkStatsRepository(private val context: Context) {

    private val networkStatsManager =
        context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager

    suspend fun getNetworkSummary(filter: TimeFilter): NetworkDataBreakdown = withContext(Dispatchers.IO) {
        if (!PermissionUtils.hasUsageStatsPermission(context) || networkStatsManager == null) {
            return@withContext NetworkDataBreakdown(0L, 0L, 0L)
        }

        val (startTime, endTime) = getTimeRange(filter)
        var mobileBytes = 0L
        var wifiBytes = 0L
        var hotspotBytes = 0L

        // Query mobile usage for device
        try {
            val bucketMobile = networkStatsManager.querySummaryForDevice(
                ConnectivityManager.TYPE_MOBILE,
                null,
                startTime,
                endTime
            )
            mobileBytes = bucketMobile.rxBytes + bucketMobile.txBytes
        } catch (_: Throwable) {
            mobileBytes = 0L
        }

        // Query Wi-Fi usage for device
        try {
            val bucketWifi = networkStatsManager.querySummaryForDevice(
                ConnectivityManager.TYPE_WIFI,
                null,
                startTime,
                endTime
            )
            wifiBytes = bucketWifi.rxBytes + bucketWifi.txBytes
        } catch (_: Throwable) {
            wifiBytes = 0L
        }

        // Query hotspot (tethering) usage via UID_TETHERING (-5)
        try {
            val bucketHotspot = networkStatsManager.queryDetailsForUid(
                ConnectivityManager.TYPE_MOBILE,
                null,
                startTime,
                endTime,
                -5
            )
            var tetherRx = 0L
            var tetherTx = 0L
            val bucket = NetworkStats.Bucket()
            while (bucketHotspot.hasNextBucket()) {
                bucketHotspot.getNextBucket(bucket)
                tetherRx += bucket.rxBytes
                tetherTx += bucket.txBytes
            }
            hotspotBytes = tetherRx + tetherTx
            bucketHotspot.close()
        } catch (_: Throwable) {
            hotspotBytes = 0L
        }

        // Fallback for devices where querySummaryForDevice yields 0 due to missing subscriberId
        if (wifiBytes == 0L) {
            try {
                val stats = networkStatsManager.querySummary(
                    ConnectivityManager.TYPE_WIFI,
                    null,
                    startTime,
                    endTime
                )
                val bucket = NetworkStats.Bucket()
                var total = 0L
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    total += bucket.rxBytes + bucket.txBytes
                }
                stats.close()
                wifiBytes = total
            } catch (_: Throwable) {}
        }

        if (mobileBytes == 0L) {
            try {
                val stats = networkStatsManager.querySummary(
                    ConnectivityManager.TYPE_MOBILE,
                    null,
                    startTime,
                    endTime
                )
                val bucket = NetworkStats.Bucket()
                var total = 0L
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    total += bucket.rxBytes + bucket.txBytes
                }
                stats.close()
                mobileBytes = total
            } catch (_: Throwable) {}
        }

        NetworkDataBreakdown(
            mobileBytes = mobileBytes,
            wifiBytes = wifiBytes,
            hotspotBytes = hotspotBytes
        )
    }

    /**
     * Queries real per-app network consumption (mobile and Wi-Fi bytes) for the given time range.
     * Returns a map of packageName -> Pair(mobileBytes, wifiBytes).
     */
    fun getPerAppNetworkStats(startTime: Long, endTime: Long): Map<String, Pair<Long, Long>> {
        val result = mutableMapOf<String, Pair<Long, Long>>()
        if (!PermissionUtils.hasUsageStatsPermission(context) || networkStatsManager == null) {
            return result
        }

        val mobileByPkg = mutableMapOf<String, Long>()
        val wifiByPkg = mutableMapOf<String, Long>()
        val pm = context.packageManager

        // 1. Query Mobile Data by UID
        try {
            val stats = networkStatsManager.querySummary(
                ConnectivityManager.TYPE_MOBILE,
                null,
                startTime,
                endTime
            )
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                val bytes = bucket.rxBytes + bucket.txBytes
                if (bytes > 0) {
                    val uid = bucket.uid
                    if (uid == -5) {
                        mobileByPkg["tethering.hotspot"] = (mobileByPkg["tethering.hotspot"] ?: 0L) + bytes
                    } else {
                        val packages = try { pm.getPackagesForUid(uid) } catch (_: Throwable) { null }
                        if (!packages.isNullOrEmpty()) {
                            for (pkg in packages) {
                                mobileByPkg[pkg] = (mobileByPkg[pkg] ?: 0L) + bytes
                            }
                        }
                    }
                }
            }
            stats.close()
        } catch (_: Throwable) {}

        // 2. Query Wi-Fi Data by UID
        try {
            val stats = networkStatsManager.querySummary(
                ConnectivityManager.TYPE_WIFI,
                null,
                startTime,
                endTime
            )
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                val bytes = bucket.rxBytes + bucket.txBytes
                if (bytes > 0) {
                    val uid = bucket.uid
                    if (uid == -5) {
                        wifiByPkg["tethering.hotspot"] = (wifiByPkg["tethering.hotspot"] ?: 0L) + bytes
                    } else {
                        val packages = try { pm.getPackagesForUid(uid) } catch (_: Throwable) { null }
                        if (!packages.isNullOrEmpty()) {
                            for (pkg in packages) {
                                wifiByPkg[pkg] = (wifiByPkg[pkg] ?: 0L) + bytes
                            }
                        }
                    }
                }
            }
            stats.close()
        } catch (_: Throwable) {}

        val allPkgs = mobileByPkg.keys + wifiByPkg.keys
        for (pkg in allPkgs) {
            result[pkg] = Pair(mobileByPkg[pkg] ?: 0L, wifiByPkg[pkg] ?: 0L)
        }
        return result
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
}
