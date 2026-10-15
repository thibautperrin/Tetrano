package fr.thibautperrin.tetrano.ui.playmenu

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.thibautperrin.tetrano.R
import fr.thibautperrin.tetrano.ui.components.IconTextButton
import fr.thibautperrin.tetrano.ui.components.TetranoBackground
import fr.thibautperrin.tetrano.ui.theme.LocalLegacyDimens
import fr.thibautperrin.tetrano.ui.theme.isLandscape
import fr.thibautperrin.tetrano.ui.theme.isLargeScreen

/** Turns [PlayMenuScreen]'s actions into navigation. */
@Composable
fun PlayMenuRoute(
    onNavigateToTwoPlayersSetup: () -> Unit,
    onNavigateToComputerSetup: () -> Unit,
    onNavigateToAnalyseGames: () -> Unit,
    onNavigateToChallenge: () -> Unit,
    onNavigateToPuzzles: () -> Unit,
) {
    PlayMenuScreen(
        onAction = { action ->
            when (action) {
                PlayMenuAction.TwoPlayersClicked -> onNavigateToTwoPlayersSetup()
                PlayMenuAction.OnePlayerClicked -> onNavigateToComputerSetup()
                PlayMenuAction.AnalyseGamesClicked -> onNavigateToAnalyseGames()
                PlayMenuAction.ChallengeClicked -> onNavigateToChallenge()
                PlayMenuAction.PuzzlesClicked -> onNavigateToPuzzles()
            }
        },
    )
}

/**
 * Ports `JouerActivity` (`activity_jouer.xml` and its `layout-land`,
 * `layout-large`, `layout-large-land` variants) minus the Bluetooth and
 * Online buttons: `BoutonsJouer` buttons on the `fond_inverse` collage, no
 * title bar (`Theme.Black.NoTitleBar`). "Saved games" became "Analyse
 * games" (finished games are saved automatically), and "Puzzles" is new.
 * - Portrait: one column — 2 players, 1 player, Analyse games, Challenge, Puzzles.
 * - Landscape or large screen: two columns — [1 player, 2 players, Analyse games]
 *   | [Challenge, Puzzles].
 *
 * The 2:1 buttons take the full column width when they all fit; otherwise
 * they shrink (label included) until they do, and only if that would make
 * them too small does the page scroll at full width, like the legacy
 * `ScrollView`.
 */
@Composable
fun PlayMenuScreen(onAction: (PlayMenuAction) -> Unit) {
    val dimens = LocalLegacyDimens.current
    val landscape = isLandscape()
    val large = isLargeScreen()
    val twoColumns = landscape || large
    val sidePadding = when {
        large && landscape -> dimens.largeMargin
        large -> dimens.smallMargin
        else -> dimens.mediumMargin
    }
    // layout-large-land's marginEnd/marginStart between the two columns.
    val columnGap = if (large && landscape) dimens.smallMargin * 2 else 0.dp

    val twoPlayers = PlayButton(R.drawable.bouton_2_joueurs, R.string.Two_Players, PlayMenuAction.TwoPlayersClicked)
    val onePlayer = PlayButton(R.drawable.bouton_1_joueur, R.string.One_Player, PlayMenuAction.OnePlayerClicked)
    val analyse = PlayButton(R.drawable.bouton_analyse, R.string.Analyse_Games, PlayMenuAction.AnalyseGamesClicked)
    val challenge = PlayButton(R.drawable.bouton_challenge, R.string.Challenge, PlayMenuAction.ChallengeClicked)
    val puzzles = PlayButton(R.drawable.bouton_puzzle, R.string.Puzzles, PlayMenuAction.PuzzlesClicked)
    val columns = if (twoColumns) {
        listOf(listOf(onePlayer, twoPlayers, analyse), listOf(challenge, puzzles))
    } else {
        listOf(listOf(twoPlayers, onePlayer, analyse, challenge, puzzles))
    }

    TetranoBackground {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = sidePadding),
        ) {
            val rows = columns.maxOf { it.size }
            val columnWidth = (maxWidth - columnGap * (columns.size - 1)) / columns.size
            val fullWidth = columnWidth - dimens.tinyMargin * 2
            // 1dp of slack so pixel rounding never pushes the last row out.
            val heightPerButton = (maxHeight - 1.dp) / rows - dimens.tinyMargin * 2
            val fittedWidth = minOf(fullWidth, heightPerButton * 2)
            val scroll = fittedWidth / 2 < MIN_BUTTON_HEIGHT
            val buttonWidth: Dp = if (scroll) fullWidth else fittedWidth

            Row(
                verticalAlignment = if (large) Alignment.CenterVertically else Alignment.Top,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier),
            ) {
                columns.forEachIndexed { index, column ->
                    if (index > 0) Spacer(Modifier.width(columnGap))
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f),
                    ) {
                        for (button in column) {
                            IconTextButton(
                                icon = painterResource(button.artwork),
                                text = stringResource(button.label),
                                onClick = { onAction(button.action) },
                                modifier = Modifier
                                    .padding(dimens.tinyMargin)
                                    .width(buttonWidth),
                            )
                        }
                    }
                }
            }
        }
    }
}

private class PlayButton(
    @DrawableRes val artwork: Int,
    @StringRes val label: Int,
    val action: PlayMenuAction,
)

/** Below this the label gets hard to read: scroll instead of shrinking further. */
private val MIN_BUTTON_HEIGHT = 56.dp
