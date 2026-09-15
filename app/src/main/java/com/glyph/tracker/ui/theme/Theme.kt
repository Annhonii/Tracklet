package com.glyph.tracker.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.glyph.tracker.data.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF042B59),
    primaryContainer = Color(0xFF1E3A5F),
    onPrimaryContainer = Color(0xFFD2E3FC),
    secondary = Color(0xFF80CBC4),
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF004D40),
    onSecondaryContainer = Color(0xFFB2DFDB),
    tertiary = Color(0xFFFDD663),
    onTertiary = Color(0xFF422E00),
    background = Color(0xFF121316),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF1E2024),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF282A30),
    onSurfaceVariant = Color(0xFFC4C7D0),
    outline = Color(0xFF44474F),
    outlineVariant = Color(0xFF353842)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1A73E8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD2E3FC),
    onPrimaryContainer = Color(0xFF042B59),
    secondary = Color(0xFF00796B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB2DFDB),
    onSecondaryContainer = Color(0xFF003731),
    tertiary = Color(0xFFE37400),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF8F9FC),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFEEF1F6),
    onSurfaceVariant = Color(0xFF44474F),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFDFE2EB)
)

@Composable
fun GlyphTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    useMonet: Boolean = true,
    content: @Composable () -> Unit
) {
    val isDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val supportsMonet = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val materialColorScheme = when {
        useMonet && supportsMonet -> {
            if (isDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDarkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val glyphColors = if (useMonet && supportsMonet) {
        if (isDarkTheme) {
            DarkGlyphColors.copy(
                primary = materialColorScheme.primary,
                primaryDim = materialColorScheme.primary.copy(alpha = 0.25f),
                primaryGlow = materialColorScheme.primary.copy(alpha = 0.12f),
                secondary = materialColorScheme.secondary,
                secondaryDim = materialColorScheme.secondary.copy(alpha = 0.25f),
                surface = materialColorScheme.surface,
                surfaceElevated = materialColorScheme.surfaceVariant.copy(alpha = 0.5f),
                background = materialColorScheme.background,
                border = materialColorScheme.outlineVariant.copy(alpha = 0.4f),
                borderSubtle = materialColorScheme.outlineVariant.copy(alpha = 0.2f),
                textPrimary = materialColorScheme.onSurface,
                textMuted = materialColorScheme.onSurfaceVariant
            )
        } else {
            LightGlyphColors.copy(
                primary = materialColorScheme.primary,
                primaryDim = materialColorScheme.primary.copy(alpha = 0.2f),
                primaryGlow = materialColorScheme.primary.copy(alpha = 0.1f),
                secondary = materialColorScheme.secondary,
                secondaryDim = materialColorScheme.secondary.copy(alpha = 0.2f),
                surface = materialColorScheme.surface,
                surfaceElevated = materialColorScheme.surfaceVariant.copy(alpha = 0.4f),
                background = materialColorScheme.background,
                border = materialColorScheme.outlineVariant.copy(alpha = 0.5f),
                borderSubtle = materialColorScheme.outlineVariant.copy(alpha = 0.25f),
                textPrimary = materialColorScheme.onSurface,
                textMuted = materialColorScheme.onSurfaceVariant
            )
        }
    } else {
        if (isDarkTheme) DarkGlyphColors else LightGlyphColors
    }

    // Set status bar icons according to light/dark theme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !isDarkTheme
                controller.isAppearanceLightNavigationBars = !isDarkTheme
            }
        }
    }

    CompositionLocalProvider(LocalGlyphColors provides glyphColors) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = GlyphTypography,
            content = content
        )
    }
}
