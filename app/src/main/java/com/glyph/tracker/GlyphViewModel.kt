package com.glyph.tracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.glyph.tracker.data.AppUsageInfo
import com.glyph.tracker.data.DataFilter
import com.glyph.tracker.data.DayUsage
import com.glyph.tracker.data.NetworkStatsRepository
import com.glyph.tracker.data.ThemeMode
import com.glyph.tracker.data.TimeFilter
import com.glyph.tracker.data.UsageStatsRepository
import com.glyph.tracker.data.UsageSummary
import com.glyph.tracker.service.MonitoringService
import com.glyph.tracker.service.UsageTileService
import com.glyph.tracker.ui.components.GlyphTab
import com.glyph.tracker.util.PermissionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GlyphUiState(
    val selectedTab: GlyphTab = GlyphTab.SCREEN_TIME,
    val timeFilter: TimeFilter = TimeFilter.TODAY,
    val dataFilter: DataFilter = DataFilter.ALL,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val isMonitoringServiceRunning: Boolean = false,
    val isSettingsOpen: Boolean = false,
    val hasUsagePermission: Boolean = false,
    val searchQuery: String = "",
    val selectedAppDetail: AppUsageInfo? = null,
    val screenTimeApps: List<AppUsageInfo> = emptyList(),
    val weeklyHistory: List<DayUsage> = emptyList(),
    val usageSummary: UsageSummary = UsageSummary(),
    val isLoading: Boolean = false
)

class GlyphViewModel(application: Application) : AndroidViewModel(application) {

    private val usageRepository = UsageStatsRepository(application)
    private val networkRepository = NetworkStatsRepository(application)

    private val _uiState = MutableStateFlow(GlyphUiState())
    val uiState: StateFlow<GlyphUiState> = _uiState.asStateFlow()

    private var loadJob: kotlinx.coroutines.Job? = null

    init {
        checkPermission()
        loadData()
    }

    fun checkPermission() {
        val hasPermission = PermissionUtils.hasUsageStatsPermission(getApplication())
        _uiState.update { it.copy(hasUsagePermission = hasPermission) }
    }

    fun loadData() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val hasPermission = PermissionUtils.hasUsageStatsPermission(getApplication())
                val currentFilter = _uiState.value.timeFilter
                val apps = usageRepository.getUsageStats(currentFilter)
                val weekly = usageRepository.getWeeklyHistory()
                val network = networkRepository.getNetworkSummary(currentFilter)

                val totalScreenTime = apps.sumOf { it.foregroundTimeMillis }

                val summary = UsageSummary(
                    totalScreenTimeMillis = totalScreenTime,
                    totalMobileBytes = network.mobileBytes,
                    totalWifiBytes = network.wifiBytes,
                    totalHotspotBytes = network.hotspotBytes,
                    totalAppCount = apps.size,
                    mostUsedApp = apps.firstOrNull()
                )

                _uiState.update {
                    it.copy(
                        hasUsagePermission = hasPermission,
                        screenTimeApps = apps,
                        weeklyHistory = weekly,
                        usageSummary = summary,
                        isLoading = false
                    )
                }

                UsageTileService.requestTileUpdate(getApplication())
            } catch (t: Throwable) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onTabSelected(tab: GlyphTab) {
        _uiState.update { it.copy(selectedTab = tab, searchQuery = "") }
    }

    fun onTimeFilterSelected(filter: TimeFilter) {
        _uiState.update { it.copy(timeFilter = filter) }
        loadData()
    }

    fun onDataFilterSelected(filter: DataFilter) {
        _uiState.update { it.copy(dataFilter = filter) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun openSettings(open: Boolean = true) {
        _uiState.update { it.copy(isSettingsOpen = open) }
    }

    fun closeSettings() {
        _uiState.update { it.copy(isSettingsOpen = false) }
    }

    fun setThemeMode(mode: ThemeMode) {
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun toggleMonitoringService(enabled: Boolean) {
        val context = getApplication<Application>()
        if (enabled) {
            MonitoringService.startService(context)
        } else {
            MonitoringService.stopService(context)
        }
        _uiState.update { it.copy(isMonitoringServiceRunning = enabled) }
    }

    fun selectAppDetail(app: AppUsageInfo?) {
        _uiState.update { it.copy(selectedAppDetail = app) }
    }
}
