# Tracklet (Track Time & Data)

A privacy-focused Android application crafted with Jetpack Compose and Material Design 3 for real-time screen time analytics, network telemetry monitoring (Mobile Data & Wi-Fi), and Quick Settings integration.

## Features

- **Daily Screen Time Monitoring**: Live session tracking, daily screen-on totals, unlocked device checks, and per-app usage distribution.
- **Network Telemetry**: Detailed daily, weekly, and monthly data consumption tracking for Cellular Data, Wi-Fi, and Tethering (Hotspot) via Android's `NetworkStatsManager`.
- **Quick Settings (QS) Tile**:
  - **OFF Mode**: Shows today's Cellular Data consumption (e.g. `Data: 520 MB`).
  - **ON Mode**: Shows today's Wi-Fi consumption (e.g. `Wi-Fi: 2.15 GB`).
  - **One-Tap Toggle**: Tap in your quick settings shade to toggle between Mobile Data and Wi-Fi stats.
- **Background Telemetry Service**: Lightweight foreground service with persistent, unobtrusive status notification and zero battery drain.
- **Material 3 Design**: Dynamic themed icon, monochrome launcher icon, custom typography, dark mode optimization, and interactive charts.

## Tech Stack

- **Kotlin & Jetpack Compose**: 100% declarative UI with Material 3.
- **Architecture**: MVVM with Kotlin Coroutines & StateFlow.
- **Android APIs**:
  - `NetworkStatsManager` (`android.app.usage.NetworkStatsManager`)
  - `UsageStatsManager` (`android.app.usage.UsageStatsManager`)
  - `TileService` (`android.service.quicksettings.TileService`)
  - Foreground Service (`android.app.Service`)

## Getting Started

1. Clone or extract the project repository.
2. Open in **Android Studio** (Koala / Ladybug or newer recommended).
3. Let Gradle sync project dependencies.
4. Run on a physical Android device or emulator running **Android 9.0 (API 28)** or higher.
5. Grant the **Usage Access** permission when prompted to enable real-time tracking.

## Building via Command Line

```bash
# Build debug APK
./gradlew assembleDebug

# Run unit & Robolectric tests
./gradlew testDebugUnitTest
```
