package com.glyph.tracker.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class GlyphColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceSelected: Color,
    val border: Color,
    val borderSubtle: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val textDisabled: Color,
    val primary: Color,
    val primaryDim: Color,
    val primaryGlow: Color,
    val secondary: Color,
    val secondaryDim: Color,
    val amber: Color,
    val green: Color,
    val orange: Color = amber,
    val purple: Color = Color(0xFFAF52DE)
)

val DarkGlyphColors = GlyphColors(
    isDark = true,
    background = Color(0xFF121316),
    surface = Color(0xFF1E2024),
    surfaceElevated = Color(0xFF282A30),
    surfaceSelected = Color(0xFF33363E),
    border = Color(0xFF353842),
    borderSubtle = Color(0xFF23262D),
    textPrimary = Color(0xFFE2E2E6),
    textMuted = Color(0xFF90939E),
    textDisabled = Color(0xFF5E616B),
    primary = Color(0xFF8AB4F8), // Pixel signature soft blue/coral
    primaryDim = Color(0x338AB4F8),
    primaryGlow = Color(0x1A8AB4F8),
    secondary = Color(0xFF80CBC4),
    secondaryDim = Color(0x3380CBC4),
    amber = Color(0xFFFDD663),
    green = Color(0xFF81C995)
)

val LightGlyphColors = GlyphColors(
    isDark = false,
    background = Color(0xFFF8F9FC),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFEEF1F6),
    surfaceSelected = Color(0xFFE1E5ED),
    border = Color(0xFFDFE2EB),
    borderSubtle = Color(0xFFEDEFF5),
    textPrimary = Color(0xFF1A1C1E),
    textMuted = Color(0xFF5E626B),
    textDisabled = Color(0xFF989CA6),
    primary = Color(0xFF1A73E8),
    primaryDim = Color(0x221A73E8),
    primaryGlow = Color(0x101A73E8),
    secondary = Color(0xFF00796B),
    secondaryDim = Color(0x2200796B),
    amber = Color(0xFFE37400),
    green = Color(0xFF1E8E3E)
)

val LocalGlyphColors = staticCompositionLocalOf { DarkGlyphColors }

// Dynamic properties delegating to LocalGlyphColors for seamless theming throughout all composables
val GlyphBlack: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalGlyphColors.current.background

val GlyphSurface: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalGlyphColors.current.surface

val GlyphSurfaceElevated: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalGlyphColors.current.surfaceElevated

val GlyphSurfaceSelected: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalGlyphColors.current.surfaceSelected

val GlyphBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalGlyphColors.current.border

val GlyphBorderSubtle: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalGlyphColors.current.borderSubtle

val GlyphRed: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalGlyphColors.current.primary

val GlyphRedDim: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalGlyphColors.current.primaryDim

val GlyphRedGlow: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalGlyphColors.current.primaryGlow

val GlyphCyan: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalGlyphColors.current.secondary

val GlyphCyanDim: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalGlyphColors.current.secondaryDim

val GlyphAmber: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalGlyphColors.current.amber

val GlyphGreen: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalGlyphColors.current.green

val GlyphWhite: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalGlyphColors.current.textPrimary

val GlyphWhiteDim: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalGlyphColors.current.textMuted

val GlyphTextMuted: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalGlyphColors.current.textMuted

val GlyphTextDisabled: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalGlyphColors.current.textDisabled

val GlyphGray: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalGlyphColors.current.textMuted

val GlyphGrayDark: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalGlyphColors.current.border

val GlyphOrange: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalGlyphColors.current.orange

val GlyphPurple: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalGlyphColors.current.purple

val GlyphBackground: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalGlyphColors.current.background

val GlyphSurfaceBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalGlyphColors.current.border
