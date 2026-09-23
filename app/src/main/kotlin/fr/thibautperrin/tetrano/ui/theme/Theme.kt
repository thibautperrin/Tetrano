package fr.thibautperrin.tetrano.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * The legacy app only ever had a light look (`Theme.AppCompat.Light.DarkActionBar`:
 * white pages, dark action bar) — the rewrite keeps exactly that instead of
 * following the system dark mode, which would repaint every screen and the
 * board differently from the 2015 artwork.
 */
private val LightColors = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,
    background = md_theme_light_background,
    onBackground = md_theme_light_onBackground,
    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,
)

@Composable
fun TetranoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = TetranoTypography,
    ) {
        CompositionLocalProvider(LocalLegacyDimens provides rememberLegacyDimens(), content = content)
    }
}
