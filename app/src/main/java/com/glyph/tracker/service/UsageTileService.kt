package com.glyph.tracker.service

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.R
import com.glyph.tracker.data.NetworkStatsRepository
import com.glyph.tracker.data.TimeFilter
import com.glyph.tracker.util.Formatters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Quick Settings Tile:
 * - When OFF (Inactive): Shows Mobile Data usage (e.g. "Data: 640 MB")
 * - When ON (Active): Shows Wi-Fi usage (e.g. "Wi-Fi: 2.15 GB")
 *
 * Tapping toggles between OFF (Mobile Data) and ON (Wi-Fi).
 */
class UsageTileService : TileService() {

    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + serviceJob)
    private lateinit var networkStatsRepository: NetworkStatsRepository

    private var cachedMobileBytes: Long = 0L
    private var cachedWifiBytes: Long = 0L

    override fun onCreate() {
        super.onCreate()
        networkStatsRepository = NetworkStatsRepository(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        val isWifi = isWifiMode()
        updateTileState(isWifi, cachedMobileBytes, cachedWifiBytes)
        refreshData(isWifi)
    }

    override fun onClick() {
        super.onClick()
        val currentIsWifi = isWifiMode()
        val nextIsWifi = !currentIsWifi
        setWifiMode(nextIsWifi)

        // Instantly reflect state change with cached or fallback stats
        updateTileState(nextIsWifi, cachedMobileBytes, cachedWifiBytes)

        // Query fresh stats asynchronously
        refreshData(nextIsWifi)
    }

    private fun refreshData(isWifi: Boolean) {
        scope.launch {
            try {
                val summary = withContext(Dispatchers.IO) {
                    networkStatsRepository.getNetworkSummary(TimeFilter.TODAY)
                }
                cachedMobileBytes = summary.mobileBytes
                cachedWifiBytes = summary.wifiBytes
                updateTileState(isWifi, summary.mobileBytes, summary.wifiBytes)
            } catch (_: Exception) {
                // Keep existing display if query fails
            }
        }
    }

    private fun updateTileState(isWifi: Boolean, mobileBytes: Long, wifiBytes: Long) {
        val tile = qsTile ?: return

        if (isWifi) {
            // ON / ACTIVE state -> Displays Wi-Fi usage
            tile.state = Tile.STATE_ACTIVE
            val formattedWifi = Formatters.formatBytes(wifiBytes)
            tile.label = "Wi-Fi: $formattedWifi"
            tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_wifi)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                tile.stateDescription = "Active, showing Wi-Fi usage: $formattedWifi"
            }
            tile.contentDescription = "Wi-Fi Usage: $formattedWifi. On. Tap to show Mobile Data."
        } else {
            // OFF / INACTIVE state -> Displays Data (Mobile) usage
            tile.state = Tile.STATE_INACTIVE
            val formattedData = Formatters.formatBytes(mobileBytes)
            tile.label = "Data: $formattedData"
            tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_data)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                tile.stateDescription = "Inactive, showing Mobile Data: $formattedData"
            }
            tile.contentDescription = "Mobile Data Usage: $formattedData. Off. Tap to show Wi-Fi."
        }

        tile.updateTile()
    }

    private fun isWifiMode(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_IS_WIFI_MODE, false)
    }

    private fun setWifiMode(isWifi: Boolean) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_IS_WIFI_MODE, isWifi).apply()
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    companion object {
        private const val PREFS_NAME = "tracklet_qs_prefs"
        private const val KEY_IS_WIFI_MODE = "is_wifi_mode"

        /**
         * Requests the system to refresh the tile state if active in the user's shade.
         */
        fun requestTileUpdate(context: Context) {
            try {
                requestListeningState(
                    context,
                    ComponentName(context, UsageTileService::class.java)
                )
            } catch (_: Exception) {
                // Ignore if not supported or not added
            }
        }
    }
}
