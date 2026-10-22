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

    /** `ThreadSurfaceGraphView`'s board background. */
    val BoardBackground = Color(0xFFFCFCFC)

    /** `ThreadSurfaceGraphView`'s empty ring slots. */
    val EmptyRing = Color(0xFFE6E6E6)

    /** `COLOR_VERT_CLAIR`: the saved-games list text / selection. */
    val SavedGameGreen = Color(0xFF44FF44)
}

/**
 * The look of every screen since 5.0: black pages, yellow titles and accents,
 * white text — the 2015 home screen's colors (the black `fond_inverse`
 * collage and its yellow buttons) carried over to the screens that used to
 * be plain white pages. Boards keep their light 2015 artwork
 * ([LegacyColors.BoardBackground]).
 */
object TetranoPalette {
    /** Behind every screen, and the top bar. */
    val Page = Color(0xFF000000)

    /** Cards, panels and dialogs, one step above [Page]. */
    val Panel = Color(0xFF1A1A1A)

    /** A panel inside a panel (menus, text fields), or a highlighted panel. */
    val PanelRaised = Color(0xFF2A2A2A)

    /** Thin outlines: around panels, and around swatches so a black one stays visible. */
    val Outline = Color(0xFF5A5A5A)

    /** `jauneBoutonMain`, opaque: titles, selections, sliders, progress. */
    val Accent = Color(0xFFFFFF32)

    /** Text and icons on [Accent] (and on the translucent yellow buttons). */
    val OnAccent = Color(0xFF000000)

    val Text = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFB4B4B4)

    val Success = Color(0xFF6BE38A)
    val Failure = Color(0xFFFF6B6B)
}
