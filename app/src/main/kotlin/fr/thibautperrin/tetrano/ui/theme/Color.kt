package fr.thibautperrin.tetrano.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Ring colors, unchanged from the legacy `colors.xml`, and the 8-color
 * palette [SettingsActivity][fr.thibautperrin.tetrano.data] let a player
 * choose from (the `tacheXxx` views — see docs/GLOSSARY.md, `ColorBlob`).
 */
object TetranoRingColors {
    val Black = Color(0xFF000000)
    val Gray = Color(0xFF777777)
    val Red = Color(0xFFFF0000)
    val Green = Color(0xFF00CC00)
    val Blue = Color(0xFF0000FF)
    val LightGreen = Color(0xFF44FF44)
    val Purple = Color(0xFFAA00DD)
    val Brown = Color(0xFFA05000)
    val Orange = Color(0xFFFF9900)

    /** In the legacy settings screen's layout order (grid: grey/black/brown/orange/red/purple/blue/green). */
    val selectable: List<Color> = listOf(Gray, Black, Brown, Orange, Red, Purple, Blue, Green)
}

// Material 3 seed colors: the app's own identity, echoing the two default
// player colors (red, blue) rather than reusing a legacy Android theme
// color literally (there wasn't a real "app theme" to port — the legacy
// AppTheme was a bare ActionBar style).
internal val SeedRed = Color(0xFFB3261E)
internal val SeedBlue = Color(0xFF0B57D0)

internal val md_theme_light_primary = Color(0xFFB3261E)
internal val md_theme_light_onPrimary = Color(0xFFFFFFFF)
internal val md_theme_light_primaryContainer = Color(0xFFFFDAD4)
internal val md_theme_light_onPrimaryContainer = Color(0xFF410001)
internal val md_theme_light_secondary = Color(0xFF0B57D0)
internal val md_theme_light_onSecondary = Color(0xFFFFFFFF)
internal val md_theme_light_secondaryContainer = Color(0xFFD8E2FF)
internal val md_theme_light_onSecondaryContainer = Color(0xFF001A41)
internal val md_theme_light_background = Color(0xFFFFFBFF)
internal val md_theme_light_onBackground = Color(0xFF201A19)
internal val md_theme_light_surface = Color(0xFFFFFBFF)
internal val md_theme_light_onSurface = Color(0xFF201A19)

internal val md_theme_dark_primary = Color(0xFFFFB4A9)
internal val md_theme_dark_onPrimary = Color(0xFF690002)
internal val md_theme_dark_primaryContainer = Color(0xFF930006)
internal val md_theme_dark_onPrimaryContainer = Color(0xFFFFDAD4)
internal val md_theme_dark_secondary = Color(0xFFAEC6FF)
internal val md_theme_dark_onSecondary = Color(0xFF002E69)
internal val md_theme_dark_secondaryContainer = Color(0xFF004494)
internal val md_theme_dark_onSecondaryContainer = Color(0xFFD8E2FF)
internal val md_theme_dark_background = Color(0xFF201A19)
internal val md_theme_dark_onBackground = Color(0xFFEDE0DE)
internal val md_theme_dark_surface = Color(0xFF201A19)
internal val md_theme_dark_onSurface = Color(0xFFEDE0DE)
