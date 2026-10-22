package fr.thibautperrin.tetrano.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Always dark, whatever the system setting: [TetranoPalette] mapped onto
 * Material 3 so the stock widgets (dialogs, menus, text fields, radio
 * buttons, sliders) follow it — yellow on black, like the home screen.
 */
private val TetranoColors = darkColorScheme(
    primary = TetranoPalette.Accent,
    onPrimary = TetranoPalette.OnAccent,
    primaryContainer = TetranoPalette.Accent,
    onPrimaryContainer = TetranoPalette.OnAccent,
    secondary = TetranoPalette.Accent,
    onSecondary = TetranoPalette.OnAccent,
    secondaryContainer = TetranoPalette.PanelRaised,
    onSecondaryContainer = TetranoPalette.Text,
    background = TetranoPalette.Page,
    onBackground = TetranoPalette.Text,
    surface = TetranoPalette.Page,
    onSurface = TetranoPalette.Text,
    surfaceVariant = TetranoPalette.PanelRaised,
    onSurfaceVariant = TetranoPalette.TextSecondary,
    surfaceContainerLowest = TetranoPalette.Page,
    surfaceContainerLow = TetranoPalette.Panel,
    surfaceContainer = TetranoPalette.Panel,
    surfaceContainerHigh = TetranoPalette.Panel,
    surfaceContainerHighest = TetranoPalette.PanelRaised,
    outline = TetranoPalette.Outline,
    outlineVariant = TetranoPalette.Outline,
    error = TetranoPalette.Failure,
)

@Composable
fun TetranoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TetranoColors,
        typography = TetranoTypography,
    ) {
        CompositionLocalProvider(LocalLegacyDimens provides rememberLegacyDimens(), content = content)
    }
}
