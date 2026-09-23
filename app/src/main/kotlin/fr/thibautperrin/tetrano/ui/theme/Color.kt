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

/**
 * The non-ring colors of the legacy UI (`colors.xml`, `ButtonStyle.xml` and
 * the hard-coded literals of the layouts/views), unchanged.
 */
object LegacyColors {
    /** `jauneBoutonMain` / `boutonRecommencer`: every yellow text button. */
    val ButtonYellow = Color(0xBEFFFF32)

    /** `BoutonsJouer`'s panel, slightly more opaque than [ButtonYellow]. */
    val PlayModeButton = Color(0xDBFFFF32)

    /** `boutonAnnuler` / `buttonGiveUp`. */
    val ButtonCyan = Color(0xBE32FFFF)

    /** `boutonClignoter`. */
    val ButtonGreen = Color(0xBE96FF96)

    /** `infoRingsNumber`: the Challenge "rings used" counters. */
    val InfoRings = Color(0xBEB0FF80)

    /** Text of the setup screens' radio buttons and of the Play / Confirm buttons. */
    val TextBlue = Color(0xFF0000FF)

    /** `ThreadSurfaceGraphView`'s board background. */
    val BoardBackground = Color(0xFFFCFCFC)

    /** `ThreadSurfaceGraphView`'s empty ring slots. */
    val EmptyRing = Color(0xFFE6E6E6)

    /** `COLOR_VERT_CLAIR`: the saved-games list text / selection. */
    val SavedGameGreen = Color(0xFF44FF44)

    /** `Theme.AppCompat.Light.DarkActionBar`'s action bar. */
    val ActionBar = Color(0xFF212121)
    val OnActionBar = Color(0xFFFFFFFF)

    /** Plain white page behind every screen but Home and the Play menu. */
    val Page = Color(0xFFFFFFFF)
}

// Material 3 scheme: only used by the stock widgets (dialogs, text fields,
// radio buttons, sliders); red/blue echo the two default player colors.

internal val md_theme_light_primary = Color(0xFFB3261E)
internal val md_theme_light_onPrimary = Color(0xFFFFFFFF)
internal val md_theme_light_primaryContainer = Color(0xFFFFDAD4)
internal val md_theme_light_onPrimaryContainer = Color(0xFF410001)
internal val md_theme_light_secondary = Color(0xFF0B57D0)
internal val md_theme_light_onSecondary = Color(0xFFFFFFFF)
internal val md_theme_light_secondaryContainer = Color(0xFFD8E2FF)
internal val md_theme_light_onSecondaryContainer = Color(0xFF001A41)
internal val md_theme_light_background = Color(0xFFFFFFFF)
internal val md_theme_light_onBackground = Color(0xFF000000)
internal val md_theme_light_surface = Color(0xFFFFFFFF)
internal val md_theme_light_onSurface = Color(0xFF000000)
