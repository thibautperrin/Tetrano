package fr.thibautperrin.tetrano.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import fr.thibautperrin.tetrano.domain.model.GameMode
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
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier,
) {
    NavHost(navController = navController, startDestination = TetranoRoute.Home, modifier = modifier) {
        composable<TetranoRoute.Home> {
            HomeRoute(
                onNavigateToPlayMenu = { navController.navigate(TetranoRoute.PlayMenu) },
                onNavigateToRules = { navController.navigate(TetranoRoute.Rules) },
                onNavigateToSettings = { navController.navigate(TetranoRoute.Settings) },
            )
        }
        composable<TetranoRoute.PlayMenu> {
            PlayMenuRoute(
                onNavigateToTwoPlayersSetup = { navController.navigate(TetranoRoute.TwoPlayersSetup()) },
                onNavigateToComputerSetup = { navController.navigate(TetranoRoute.ComputerSetup()) },
                onNavigateToAnalyseGames = { navController.navigate(TetranoRoute.AnalyseGames) },
                onNavigateToChallenge = { navController.navigate(TetranoRoute.Challenge) },
                onNavigateToPuzzles = { navController.navigate(TetranoRoute.Puzzles) },
            )
        }
        composable<TetranoRoute.TwoPlayersSetup> { backStackEntry ->
            val route = backStackEntry.toRoute<TetranoRoute.TwoPlayersSetup>()
            TwoPlayersSetupRoute(
                onNavigateToGame = {
                    navController.navigate(TetranoRoute.TwoPlayersGame(route.savedGameName, route.ringCount)) {
                        if (route.savedGameName != null) popUpTo(TetranoRoute.PlayMenu)
                    }
                },
            )
        }
        composable<TetranoRoute.ComputerSetup> { backStackEntry ->
            val route = backStackEntry.toRoute<TetranoRoute.ComputerSetup>()
            ComputerSetupRoute(
                onNavigateToGame = {
                    navController.navigate(TetranoRoute.ComputerGame(route.savedGameName, route.ringCount)) {
                        if (route.savedGameName != null) popUpTo(TetranoRoute.PlayMenu)
                    }
                },
            )
        }
        composable<TetranoRoute.TwoPlayersGame> { backStackEntry ->
            val route = backStackEntry.toRoute<TetranoRoute.TwoPlayersGame>()
            GameRoute(
                mode = GameMode.TWO_PLAYERS,
                savedGameName = route.savedGameName,
                ringCount = route.ringCount,
                onNavigateToSettings = { navController.navigate(TetranoRoute.Settings) },
                onExit = { navController.popBackStack() },
                onReview = { navController.navigate(it.toRoute()) },
            )
        }
        composable<TetranoRoute.ComputerGame> { backStackEntry ->
            val route = backStackEntry.toRoute<TetranoRoute.ComputerGame>()
            GameRoute(
                mode = GameMode.VS_COMPUTER,
                savedGameName = route.savedGameName,
                ringCount = route.ringCount,
                onNavigateToSettings = { navController.navigate(TetranoRoute.Settings) },
                onExit = { navController.popBackStack() },
                onReview = { navController.navigate(it.toRoute()) },
            )
        }
        composable<TetranoRoute.Challenge> {
            ChallengeRoute(
                onNavigateToSettings = { navController.navigate(TetranoRoute.Settings) },
                onExit = { navController.popBackStack() },
            )
        }
        composable<TetranoRoute.AnalyseGames> {
            AnalyseGamesRoute(
                onOpenGame = { name -> navController.navigate(TetranoRoute.Analysis(savedGameName = name)) },
                onOpenEmptyBoard = { navController.navigate(TetranoRoute.Analysis()) },
            )
        }
        composable<TetranoRoute.Analysis> {
            AnalysisRoute(
                onContinueTwoPlayers = { name, ringCount ->
                    navController.navigate(TetranoRoute.TwoPlayersSetup(name, ringCount))
                },
                onContinueAgainstComputer = { name, ringCount ->
                    navController.navigate(TetranoRoute.ComputerSetup(name, ringCount))
                },
            )
        }
        composable<TetranoRoute.Puzzles> { PuzzleRoute() }
        composable<TetranoRoute.Settings> { SettingsRoute(onDone = { navController.popBackStack() }) }
        composable<TetranoRoute.Rules> {
            RulesRoute(
                onNavigateToWinningExamples = { navController.navigate(TetranoRoute.WinningExamples) },
            )
        }
        composable<TetranoRoute.WinningExamples> { WinningExamplesRoute() }
    }
}
