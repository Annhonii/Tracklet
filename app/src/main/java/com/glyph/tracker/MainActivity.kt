package com.glyph.tracker

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Launch
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.glyph.tracker.data.AppUsageInfo
import com.glyph.tracker.ui.components.AppIconDisplay
import com.glyph.tracker.ui.components.GlyphBottomNav
import com.glyph.tracker.ui.components.GlyphTab
import com.glyph.tracker.ui.components.SettingsSheet
import com.glyph.tracker.ui.screens.DataUsageScreen
import com.glyph.tracker.ui.screens.ScreenTimeScreen
import com.glyph.tracker.ui.theme.GlyphTheme
import com.glyph.tracker.util.Formatters
import com.glyph.tracker.util.PermissionUtils
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var viewModelRef: GlyphViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val vm: GlyphViewModel = viewModel()
            viewModelRef = vm
            val uiState by vm.uiState.collectAsStateWithLifecycle()

            GlyphTheme(themeMode = uiState.themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val pagerState = rememberPagerState(
                        initialPage = if (uiState.selectedTab == GlyphTab.SCREEN_TIME) 0 else 1,
                        pageCount = { 2 }
                    )
                    val coroutineScope = rememberCoroutineScope()

                    // Sync tab selection from VM/BottomNav to Pager
                    LaunchedEffect(uiState.selectedTab) {
                        val targetPage = if (uiState.selectedTab == GlyphTab.SCREEN_TIME) 0 else 1
                        if (pagerState.currentPage != targetPage) {
                            pagerState.animateScrollToPage(targetPage)
                        }
                    }

                    // Sync tab selection from Pager swipe to VM/BottomNav
                    LaunchedEffect(pagerState) {
                        snapshotFlow { pagerState.currentPage }.collect { page ->
                            val targetTab = if (page == 0) GlyphTab.SCREEN_TIME else GlyphTab.DATA_USAGE
                            if (uiState.selectedTab != targetTab) {
                                vm.onTabSelected(targetTab)
                            }
                        }
                    }

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = MaterialTheme.colorScheme.background,
                        bottomBar = {
                            GlyphBottomNav(
                                selectedTab = uiState.selectedTab,
                                onTabSelected = { tab ->
                                    vm.onTabSelected(tab)
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(if (tab == GlyphTab.SCREEN_TIME) 0 else 1)
                                    }
                                },
                                onOpenSettings = {
                                    vm.openSettings()
                                }
                            )
                        }
                    ) { innerPadding ->
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .windowInsetsPadding(WindowInsets.statusBars)
                        ) { page ->
                            when (page) {
                                0 -> {
                                    ScreenTimeScreen(
                                        summary = uiState.usageSummary,
                                        apps = uiState.screenTimeApps,
                                        weeklyDays = uiState.weeklyHistory,
                                        selectedFilter = uiState.timeFilter,
                                        onFilterSelect = { vm.onTimeFilterSelected(it) },
                                        searchQuery = uiState.searchQuery,
                                        onSearchQueryChange = { vm.setSearchQuery(it) },
                                        onAppClick = { app -> vm.selectAppDetail(app) },
                                        onOpenSettings = { vm.openSettings() },
                                        hasUsagePermission = uiState.hasUsagePermission,
                                        onRequestUsagePermission = {
                                            PermissionUtils.openUsageAccessSettings(this@MainActivity)
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                1 -> {
                                    DataUsageScreen(
                                        summary = uiState.usageSummary,
                                        apps = uiState.screenTimeApps,
                                        weeklyHistory = uiState.weeklyHistory,
                                        selectedTimeFilter = uiState.timeFilter,
                                        onTimeFilterSelect = { vm.onTimeFilterSelected(it) },
                                        selectedDataFilter = uiState.dataFilter,
                                        onDataFilterSelect = { vm.onDataFilterSelected(it) },
                                        searchQuery = uiState.searchQuery,
                                        onSearchQueryChange = { vm.setSearchQuery(it) },
                                        onAppClick = { app -> vm.selectAppDetail(app) },
                                        onOpenSettings = { vm.openSettings() },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }

                        // Settings Bottom Sheet
                        SettingsSheet(
                            isOpen = uiState.isSettingsOpen,
                            onDismiss = { vm.closeSettings() },
                            isMonitoringServiceRunning = uiState.isMonitoringServiceRunning,
                            onToggleMonitoringService = { vm.toggleMonitoringService(it) }
                        )

                        // Clean Pixel App Detail Bottom Sheet
                        AppDetailSheet(
                            appInfo = uiState.selectedAppDetail,
                            onDismiss = { vm.selectAppDetail(null) },
                            onLaunchApp = { pkg ->
                                try {
                                    if (pkg == "tethering.hotspot") {
                                        val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        startActivity(intent)
                                    } else {
                                        val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                                        if (launchIntent != null) {
                                            startActivity(launchIntent)
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModelRef?.let { vm ->
            vm.checkPermission()
            vm.loadData()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailSheet(
    appInfo: AppUsageInfo?,
    onDismiss: () -> Unit,
    onLaunchApp: (String) -> Unit
) {
    if (appInfo == null) return

    val isHotspot = appInfo.packageName == "tethering.hotspot"
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            )
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIconDisplay(
                    icon = appInfo.icon,
                    fallbackInitial = appInfo.appName.firstOrNull()?.uppercase() ?: "A",
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(52.dp),
                    packageName = appInfo.packageName
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appInfo.appName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isHotspot) "System tethering service" else appInfo.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Metrics Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!isHotspot) {
                    PixelMetricCard(
                        title = "Screen time",
                        value = Formatters.formatDuration(appInfo.foregroundTimeMillis),
                        modifier = Modifier.weight(1f)
                    )
                }

                PixelMetricCard(
                    title = "Total data",
                    value = Formatters.formatBytes(appInfo.totalBytes),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PixelMetricCard(
                    title = "Wi-Fi",
                    value = Formatters.formatBytes(appInfo.wifiBytes),
                    modifier = Modifier.weight(1f)
                )

                PixelMetricCard(
                    title = "Mobile",
                    value = Formatters.formatBytes(appInfo.mobileBytes),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Launch App / Settings Button
            Button(
                onClick = {
                    onLaunchApp(appInfo.packageName)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("button_launch_app_${appInfo.packageName}")
            ) {
                Icon(
                    imageVector = if (isHotspot) Icons.Outlined.Settings else Icons.Outlined.Launch,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isHotspot) "Hotspot settings" else "Open app",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PixelMetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
