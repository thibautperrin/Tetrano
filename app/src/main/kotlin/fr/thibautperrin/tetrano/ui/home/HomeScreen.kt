package fr.thibautperrin.tetrano.ui.home

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.thibautperrin.tetrano.R
import fr.thibautperrin.tetrano.ui.components.LegacyButton
import fr.thibautperrin.tetrano.ui.components.LegacyTopBar
import fr.thibautperrin.tetrano.ui.components.TetranoBackground
import fr.thibautperrin.tetrano.ui.theme.LegacyColors
import fr.thibautperrin.tetrano.ui.theme.LocalLegacyDimens
import kotlinx.coroutines.delay

/** Turns [HomeScreen]'s actions into navigation. */
@Composable
fun HomeRoute(
    onNavigateToPlayMenu: () -> Unit,
    onNavigateToRules: () -> Unit,
    onNavigateToStrategy: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAbout: () -> Unit,
) {
    HomeScreen(
        onAction = { action ->
            when (action) {
                HomeAction.PlayClicked -> onNavigateToPlayMenu()
                HomeAction.RulesClicked -> onNavigateToRules()
                HomeAction.StrategyClicked -> onNavigateToStrategy()
                HomeAction.SettingsClicked -> onNavigateToSettings()
                HomeAction.AboutClicked -> onNavigateToAbout()
            }
        },
    )
}

/**
 * Ports `MainActivity` (`activity_main.xml`): the dark action bar titled
 * "Tetrano", and five `button_texte_style`
 * buttons stacked at the top of the `fond_inverse` collage — Play (which
 * flashes blue for 500 ms when tapped, as in the legacy listener), Rules,
 * Strategy (the opening guide, since 5.0),
 * Settings and About (a screen of its own since 5.0, where the 2015 app
 * showed a one-line dialog).
 *
 * The buttons keep their legacy size whenever it fits; on a short screen
 * (landscape) they share the height and their labels shrink, and only when
 * even that would make them too small does the page scroll.
 */
@Composable
fun HomeScreen(onAction: (HomeAction) -> Unit) {
    val dimens = LocalLegacyDimens.current
    var playFlashing by remember { mutableStateOf(false) }
    LaunchedEffect(playFlashing) {
        if (playFlashing) {
            delay(PLAY_FLASH_MILLIS)
            playFlashing = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LegacyTopBar(title = stringResource(R.string.app_name))
        TetranoBackground(modifier = Modifier.weight(1f)) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                    .padding(dimens.screenMargin),
            ) {
                val buttonSlot = MIN_BUTTON_HEIGHT + dimens.tinyMargin * 2
                val fits = maxHeight >= buttonSlot * BUTTON_COUNT
                val columnModifier = if (fits) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                }
                Column(modifier = columnModifier) {
                    // In the fitting case each button gets at most a quarter of
                    // the height (weight without fill): its natural height when
                    // there's room, a shrunk one (label included) when there isn't.
                    val buttonModifier = (if (fits) Modifier.weight(1f, fill = false) else Modifier)
                        .padding(dimens.tinyMargin)
                        .fillMaxWidth()
                    LegacyButton(
                        text = stringResource(R.string.Play),
                        onClick = {
                            playFlashing = true
                            onAction(HomeAction.PlayClicked)
                        },
                        background = if (playFlashing) Color.Blue else LegacyColors.ButtonYellow,
                        fontSize = dimens.title2,
                        modifier = buttonModifier,
                    )
                    LegacyButton(
                        text = stringResource(R.string.Rules_Of_Game),
                        onClick = { onAction(HomeAction.RulesClicked) },
                        fontSize = dimens.title2,
                        modifier = buttonModifier,
                    )
                    LegacyButton(
                        text = stringResource(R.string.Strategy),
                        onClick = { onAction(HomeAction.StrategyClicked) },
                        fontSize = dimens.title2,
                        modifier = buttonModifier,
                    )
                    LegacyButton(
                        text = stringResource(R.string.action_settings),
                        onClick = { onAction(HomeAction.SettingsClicked) },
                        fontSize = dimens.title2,
                        modifier = buttonModifier,
                    )
                    LegacyButton(
                        text = stringResource(R.string.About),
                        onClick = { onAction(HomeAction.AboutClicked) },
                        fontSize = dimens.title2,
                        modifier = buttonModifier,
                    )
                }
            }
        }
    }
}

private const val BUTTON_COUNT = 5
private const val PLAY_FLASH_MILLIS = 500L
private val MIN_BUTTON_HEIGHT = 40.dp
