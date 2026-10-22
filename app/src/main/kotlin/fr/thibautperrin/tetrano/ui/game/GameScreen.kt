package fr.thibautperrin.tetrano.ui.game

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.thibautperrin.tetrano.R
import fr.thibautperrin.tetrano.domain.model.GameMode
import fr.thibautperrin.tetrano.ui.components.FitText
import fr.thibautperrin.tetrano.ui.components.LegacyTopBar
import fr.thibautperrin.tetrano.ui.theme.TetranoPalette
import fr.thibautperrin.tetrano.ui.theme.isLandscape

/** Wires [GameViewModel] (created for [mode] via its assisted [GameViewModel.Factory]) to the stateless [GameScreen]. */
@Composable
fun GameRoute(
    mode: GameMode,
    savedGameName: String?,
    ringCount: Int,
    onNavigateToSettings: () -> Unit,
    onExit: () -> Unit,
    onReview: (ReviewRequest) -> Unit,
    viewModel: GameViewModel = hiltViewModel(
        creationCallback = { factory: GameViewModel.Factory -> factory.create(mode, savedGameName, ringCount) },
    ),
) {
    val uiModel by viewModel.uiState.collectAsState()
    GameScreen(
        mode = mode,
        uiModel = uiModel,
        onAction = viewModel::onAction,
        onNavigateToSettings = onNavigateToSettings,
        onExit = onExit,
        onReview = onReview,
    )
}

/** The system bars and cutouts, but not the keyboard. */
private val GameInsets: WindowInsets
    @Composable get() = WindowInsets.systemBars.union(WindowInsets.displayCutout)

/**
 * Ports `JeuActivity` (see docs/GLOSSARY.md, docs/PLAN.md P8): a white page
 * under the dark action bar showing the dynamic title and the
 * `menu_jeu.xml` overflow menu, with the board and its three buttons laid
 * out by [GameBoardAndButtons].
 */
@Composable
fun GameScreen(
    mode: GameMode,
    uiModel: GameUiModel,
    onAction: (GameAction) -> Unit,
    onNavigateToSettings: () -> Unit,
    onExit: () -> Unit,
    onReview: (ReviewRequest) -> Unit = {},
) {
    val context = LocalContext.current

    BackHandler { onAction(GameAction.BackPressed) }

    LaunchedEffect(uiModel.exitRequested) {
        if (uiModel.exitRequested) onExit()
    }

    uiModel.reviewRequest?.let { request ->
        LaunchedEffect(request) {
            onAction(GameAction.ReviewHandled)
            onReview(request)
        }
    }

    if (uiModel.showInternalError) {
        LaunchedEffect(Unit) {
            Toast.makeText(context, R.string.Internal_Error, Toast.LENGTH_LONG).show()
            onAction(GameAction.InternalErrorShown)
        }
    }

    Scaffold(
        containerColor = TetranoPalette.Page,
        contentWindowInsets = GameInsets,
        topBar = {
            // The dark bar also runs under a landscape cutout; its title and
            // menu stay clear of it.
            Box(
                Modifier
                    .background(TetranoPalette.Page)
                    .windowInsetsPadding(GameInsets.only(WindowInsetsSides.Horizontal)),
            ) {
                LegacyTopBar(
                    title = {
                        FitText(
                            text = if (uiModel.isLoading) "" else gameTitle(mode, uiModel),
                            maxFontSize = TitleFontSize,
                            maxLines = 2,
                        )
                    },
                    actions = {
                        GameMenu(mode = mode, onAction = onAction, onNavigateToSettings = onNavigateToSettings)
                    },
                )
            }
        },
    ) { padding ->
        GameBoardAndButtons(
            board = uiModel.board,
            finished = uiModel.title.isFinished,
            landscape = isLandscape(),
            onAction = onAction,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        )
    }

}

private val TitleFontSize = 20.sp

/**
 * `menu_jeu.xml`: Cancel, Play again, Blink, Settings (hidden in 2-player mode). No "Save the
 * game": a finished game is saved automatically.
 */
@Composable
private fun GameMenu(
    mode: GameMode,
    onAction: (GameAction) -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = null)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        @Composable
        fun item(label: Int, onClick: () -> Unit) {
            DropdownMenuItem(
                text = { Text(stringResource(label)) },
                onClick = {
                    expanded = false
                    onClick()
                },
            )
        }
        item(R.string.Cancel) { onAction(GameAction.CancelClicked) }
        item(R.string.Play_Again) { onAction(GameAction.PlayAgainClicked) }
        item(R.string.Mot_Clignoter) { onAction(GameAction.BlinkClicked) }
        if (mode == GameMode.VS_COMPUTER) item(R.string.action_settings, onNavigateToSettings)
    }
}

// GestionJeu2Joueurs.mettreLeBonTitre / GestionJeuContreOrdinateur.mettreLeBonTitre, ported.
@Composable
private fun gameTitle(mode: GameMode, uiModel: GameUiModel): String {
    val title = uiModel.title
    // ApplicationTetrano's preference defaults: Joueur1/Joueur2, and User against the computer.
    val player1Name = uiModel.player1Name.ifBlank {
        stringResource(if (mode == GameMode.VS_COMPUTER) R.string.User else R.string.Joueur1)
    }
    val player2Name = uiModel.player2Name.ifBlank { stringResource(R.string.Joueur2) }
    if (title.isFinished) {
        return when {
            title.isDraw || title.winner == null -> stringResource(R.string.Sentence_End_Null)
            mode == GameMode.VS_COMPUTER -> {
                if (title.winner == 1) stringResource(R.string.Sentence_End_Win) else stringResource(R.string.Sentence_End_Lost)
            }
            else -> {
                val winnerName = if (title.winner == 1) player1Name else player2Name
                "${stringResource(R.string.Winner)}: $winnerName"
            }
        }
    }
    return when (mode) {
        GameMode.VS_COMPUTER -> when {
            title.playedCount == 0 && title.nextPlayer == 1 -> stringResource(R.string.Sentence_Begin_Start)
            title.nextPlayer == 1 -> "$player1Name ${stringResource(R.string.Your_Turn)}"
            else -> stringResource(R.string.Sentence_Computer_Work)
        }
        else -> {
            val name = if (title.nextPlayer == 1) player1Name else player2Name
            if (title.playedCount == 0) {
                "$name${stringResource(R.string.You_Can_Start)}"
            } else {
                "$name, ${stringResource(R.string.Your_Turn)}"
            }
        }
    }
}
