package fr.thibautperrin.tetrano.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import fr.thibautperrin.tetrano.domain.model.GameMode
import fr.thibautperrin.tetrano.ui.about.AboutScreen
import fr.thibautperrin.tetrano.ui.analysis.AnalyseGamesRoute
import fr.thibautperrin.tetrano.ui.analysis.AnalysisRoute
import fr.thibautperrin.tetrano.ui.challenge.ChallengeRoute
import fr.thibautperrin.tetrano.ui.game.GameRoute
import fr.thibautperrin.tetrano.ui.game.ReviewRequest
import fr.thibautperrin.tetrano.ui.home.HomeRoute
import fr.thibautperrin.tetrano.ui.playmenu.PlayMenuRoute
import fr.thibautperrin.tetrano.ui.puzzles.PuzzleRoute
import fr.thibautperrin.tetrano.ui.rules.RulesRoute
import fr.thibautperrin.tetrano.ui.rules.WinningExamplesRoute
import fr.thibautperrin.tetrano.ui.settings.SettingsRoute
import fr.thibautperrin.tetrano.ui.setup.ComputerSetupRoute
import fr.thibautperrin.tetrano.ui.setup.TwoPlayersSetupRoute

/**
 * Wires every [TetranoRoute] to its screen (see docs/PLAN.md P8), with the
 * legacy back stack:
 * - a fresh game keeps its setup screen underneath (back from the game
 *   returns to it, like the legacy `startActivity` chain);
 * - a game resumed from a save returns straight to the Play menu (the
 *   legacy `RESULT_CODE_LEAVE` chain closed the saved-games list, the
 *   viewer and the setup screen).
 */
/** The review screen for a game that just ended. */
private fun ReviewRequest.toRoute() = TetranoRoute.Analysis(
    rings = rings,
    userBegins = userBegins,
    userColor = userColor,
    adversaryColor = adversaryColor,
)

@Composable
fun TetranoNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = TetranoRoute.Home, modifier = modifier) {
        composable<TetranoRoute.Home> { entry ->
            HomeRoute(
                onNavigateToPlayMenu = { navController.navigateFrom(entry, TetranoRoute.PlayMenu) },
                onNavigateToRules = { navController.navigateFrom(entry, TetranoRoute.Rules) },
                onNavigateToSettings = { navController.navigateFrom(entry, TetranoRoute.Settings) },
                onNavigateToAbout = { navController.navigateFrom(entry, TetranoRoute.About) },
            )
        }
        composable<TetranoRoute.About> { AboutScreen() }
        composable<TetranoRoute.PlayMenu> { entry ->
            PlayMenuRoute(
                onNavigateToTwoPlayersSetup = { navController.navigateFrom(entry, TetranoRoute.TwoPlayersSetup()) },
                onNavigateToComputerSetup = { navController.navigateFrom(entry, TetranoRoute.ComputerSetup()) },
                onNavigateToAnalyseGames = { navController.navigateFrom(entry, TetranoRoute.AnalyseGames) },
                onNavigateToChallenge = { navController.navigateFrom(entry, TetranoRoute.Challenge) },
                onNavigateToPuzzles = { navController.navigateFrom(entry, TetranoRoute.Puzzles) },
            )
        }
        composable<TetranoRoute.TwoPlayersSetup> { entry ->
            val route = entry.toRoute<TetranoRoute.TwoPlayersSetup>()
            TwoPlayersSetupRoute(
                onNavigateToGame = {
                    navController.navigateFrom(entry, TetranoRoute.TwoPlayersGame(route.savedGameName, route.ringCount)) {
                        if (route.savedGameName != null) popUpTo(TetranoRoute.PlayMenu)
                    }
                },
            )
        }
        composable<TetranoRoute.ComputerSetup> { entry ->
            val route = entry.toRoute<TetranoRoute.ComputerSetup>()
            ComputerSetupRoute(
                onNavigateToGame = {
                    navController.navigateFrom(entry, TetranoRoute.ComputerGame(route.savedGameName, route.ringCount)) {
                        if (route.savedGameName != null) popUpTo(TetranoRoute.PlayMenu)
                    }
                },
            )
        }
        composable<TetranoRoute.TwoPlayersGame> { entry ->
            val route = entry.toRoute<TetranoRoute.TwoPlayersGame>()
            GameRoute(
                mode = GameMode.TWO_PLAYERS,
                savedGameName = route.savedGameName,
                ringCount = route.ringCount,
                onNavigateToSettings = { navController.navigateFrom(entry, TetranoRoute.Settings) },
                onExit = { navController.popFrom(entry) },
                onReview = { navController.navigateFrom(entry, it.toRoute()) },
            )
        }
        composable<TetranoRoute.ComputerGame> { entry ->
            val route = entry.toRoute<TetranoRoute.ComputerGame>()
            GameRoute(
                mode = GameMode.VS_COMPUTER,
                savedGameName = route.savedGameName,
                ringCount = route.ringCount,
                onNavigateToSettings = { navController.navigateFrom(entry, TetranoRoute.Settings) },
                onExit = { navController.popFrom(entry) },
                onReview = { navController.navigateFrom(entry, it.toRoute()) },
            )
        }
        composable<TetranoRoute.Challenge> { entry ->
            ChallengeRoute(
                onNavigateToSettings = { navController.navigateFrom(entry, TetranoRoute.Settings) },
                onExit = { navController.popFrom(entry) },
            )
        }
        composable<TetranoRoute.AnalyseGames> { entry ->
            AnalyseGamesRoute(
                onOpenGame = { name -> navController.navigateFrom(entry, TetranoRoute.Analysis(savedGameName = name)) },
                onOpenEmptyBoard = { navController.navigateFrom(entry, TetranoRoute.Analysis()) },
            )
        }
        composable<TetranoRoute.Analysis> { entry ->
            AnalysisRoute(
                onContinueTwoPlayers = { name, ringCount ->
                    navController.navigateFrom(entry, TetranoRoute.TwoPlayersSetup(name, ringCount))
                },
                onContinueAgainstComputer = { name, ringCount ->
                    navController.navigateFrom(entry, TetranoRoute.ComputerSetup(name, ringCount))
                },
            )
        }
        composable<TetranoRoute.Puzzles> { PuzzleRoute() }
        composable<TetranoRoute.Settings> { entry -> SettingsRoute(onDone = { navController.popFrom(entry) }) }
        composable<TetranoRoute.Rules> { entry ->
            RulesRoute(
                onNavigateToWinningExamples = { navController.navigateFrom(entry, TetranoRoute.WinningExamples) },
            )
        }
        composable<TetranoRoute.WinningExamples> { WinningExamplesRoute() }
    }
}

/**
 * Navigates only while [from] is the screen on display, so that a second tap landing during the
 * transition (or a late event from the screen being left) can't push the destination twice.
 */
private fun NavController.navigateFrom(from: NavBackStackEntry, route: TetranoRoute, builder: NavOptionsBuilder.() -> Unit = {}) {
    if (from.lifecycle.currentState == Lifecycle.State.RESUMED) navigate(route, builder)
}

/** Leaves [from], unless it is already being left (a Quit then a Back during the exit animation). */
private fun NavController.popFrom(from: NavBackStackEntry) {
    if (from.lifecycle.currentState == Lifecycle.State.RESUMED) popBackStack()
}
