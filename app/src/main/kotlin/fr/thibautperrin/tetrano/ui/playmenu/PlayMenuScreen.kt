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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.thibautperrin.tetrano.R
import fr.thibautperrin.tetrano.ui.components.IconTextButton
import fr.thibautperrin.tetrano.ui.components.TetranoBackground
import fr.thibautperrin.tetrano.ui.theme.LocalLegacyDimens
import fr.thibautperrin.tetrano.ui.theme.isLandscape
import fr.thibautperrin.tetrano.ui.theme.isLargeScreen

/** Wires [PlayMenuViewModel] to the stateless [PlayMenuScreen]. */
@Composable
fun PlayMenuRoute(
    onNavigateToTwoPlayersSetup: () -> Unit,
    onNavigateToComputerSetup: () -> Unit,
    onNavigateToSavedGames: () -> Unit,
    onNavigateToChallenge: () -> Unit,
    viewModel: PlayMenuViewModel = hiltViewModel(),
) {
    val uiModel by viewModel.uiState.collectAsState()
    PlayMenuScreen(
        uiModel = uiModel,
        onAction = { action ->
            when (action) {
                PlayMenuAction.TwoPlayersClicked -> onNavigateToTwoPlayersSetup()
                PlayMenuAction.OnePlayerClicked -> onNavigateToComputerSetup()
                PlayMenuAction.SavedGamesClicked -> onNavigateToSavedGames()
                PlayMenuAction.ChallengeClicked -> onNavigateToChallenge()
                PlayMenuAction.OnlineClicked -> Unit
            }
        },
    )
}

/**
 * Ports `JouerActivity` (`activity_jouer.xml` and its `layout-land`,
 * `layout-large`, `layout-large-land` variants) minus the Bluetooth button
 * (removed, see docs/PLAN.md §10): `BoutonsJouer` buttons on the
 * `fond_inverse` collage, no title bar (`Theme.Black.NoTitleBar`).
 * - Portrait: one column — 2 players, 1 player, Online, Saved games, Challenge.
 * - Landscape or large screen: two columns — [1 player, 2 players, Saved games]
 *   | [Online, Challenge].
 *
 * "Online" is the artwork alone (its label is part of the picture), inert
 * while [PlayMenuUiModel.onlineAvailable] is false.
 *
 * The 2:1 buttons take the full column width when they all fit; otherwise
 * they shrink (label included) until they do, and only if that would make
 * them too small does the page scroll at full width, like the legacy
 * `ScrollView`.
 */
@Composable
fun PlayMenuScreen(uiModel: PlayMenuUiModel, onAction: (PlayMenuAction) -> Unit) {
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
    val online = PlayButton(
        R.drawable.bouton_jeu_online,
        label = null,
        action = PlayMenuAction.OnlineClicked,
        enabled = uiModel.onlineAvailable,
    )
    val savedGames = PlayButton(R.drawable.bouton_partie_sauvegarde, R.string.Saved_Games, PlayMenuAction.SavedGamesClicked)
    val challenge = PlayButton(R.drawable.bouton_challenge, R.string.Challenge, PlayMenuAction.ChallengeClicked)
    val columns = if (twoColumns) {
        listOf(listOf(onePlayer, twoPlayers, savedGames), listOf(online, challenge))
    } else {
        listOf(listOf(twoPlayers, onePlayer, online, savedGames, challenge))
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
                            val label = button.label?.let { stringResource(it) }
                            IconTextButton(
                                icon = painterResource(button.artwork),
                                text = label,
                                onClick = { onAction(button.action) },
                                enabled = button.enabled,
                                contentDescription = label ?: stringResource(R.string.online_coming_soon),
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
    @StringRes val label: Int?,
    val action: PlayMenuAction,
    val enabled: Boolean = true,
)

/** Below this the label gets hard to read: scroll instead of shrinking further. */
private val MIN_BUTTON_HEIGHT = 56.dp
