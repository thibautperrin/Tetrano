package fr.thibautperrin.tetrano.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import fr.thibautperrin.tetrano.ui.components.LegacyTopBar
import fr.thibautperrin.tetrano.ui.theme.LegacyColors
import fr.thibautperrin.tetrano.ui.theme.LocalLegacyDimens

/**
 * The white page shared by the two setup screens (`JouerDeuxJoueurActivity`,
 * `JouerContreOrdiActivity`): the legacy `activity_*_margin` padding around
 * [content], inside the safe-drawing insets (edge-to-edge, cutouts, IME).
 *
 * Those activities had no action bar of their own, but the status bar
 * icons are light, so the page gets a dark [LegacyTopBar] titled with the
 * activity's manifest label ([title]) to keep the top edge dark.
 */
@Composable
internal fun SetupPage(title: String, content: @Composable BoxWithConstraintsScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LegacyColors.Page),
    ) {
        LegacyTopBar(title = title)
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .padding(LocalLegacyDimens.current.screenMargin),
            content = content,
        )
    }
}
