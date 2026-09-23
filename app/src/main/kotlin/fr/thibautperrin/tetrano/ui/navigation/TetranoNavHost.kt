package fr.thibautperrin.tetrano.ui.navigation

import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import fr.thibautperrin.tetrano.domain.model.GameMode
import fr.thibautperrin.tetrano.ui.challenge.ChallengeRoute
import fr.thibautperrin.tetrano.ui.game.GameRoute
import fr.thibautperrin.tetrano.ui.home.HomeRoute
import fr.thibautperrin.tetrano.ui.playmenu.PlayMenuRoute
import fr.thibautperrin.tetrano.ui.rules.RulesRoute
import fr.thibautperrin.tetrano.ui.rules.WinningExamplesRoute
import fr.thibautperrin.tetrano.ui.savedgames.SavedGameViewerRoute
import fr.thibautperrin.tetrano.ui.savedgames.SavedGamesRoute
import fr.thibautperrin.tetrano.ui.settings.SettingsRoute
import fr.thibautperrin.tetrano.ui.setup.ComputerSetupRoute
import fr.thibautperrin.tetrano.ui.setup.TwoPlayersSetupRoute

/**
 * Wires every [TetranoRoute] to its screen (see docs/PLAN.md P8) — the
 * last of the P8 screens, [TetranoRoute.Settings], [TetranoRoute.Rules]
 * and [TetranoRoute.WinningExamples], land in this commit.
 */
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
                onNavigateToTwoPlayersSetup = { navController.navigate(TetranoRoute.TwoPlayersSetup) },
                onNavigateToComputerSetup = { navController.navigate(TetranoRoute.ComputerSetup()) },
                onNavigateToSavedGames = { navController.navigate(TetranoRoute.SavedGames) },
                onNavigateToChallenge = { navController.navigate(TetranoRoute.Challenge) },
            )
        }
        composable<TetranoRoute.TwoPlayersSetup> {
            TwoPlayersSetupRoute(
                onNavigateToGame = {
                    navController.navigate(TetranoRoute.TwoPlayersGame()) {
                        popUpTo(TetranoRoute.TwoPlayersSetup) { inclusive = true }
                    }
                },
            )
        }
        composable<TetranoRoute.ComputerSetup> { backStackEntry ->
            val route = backStackEntry.toRoute<TetranoRoute.ComputerSetup>()
            ComputerSetupRoute(
                onNavigateToGame = {
                    navController.navigate(TetranoRoute.ComputerGame(route.savedGameName)) {
                        popUpTo(TetranoRoute.ComputerSetup(route.savedGameName)) { inclusive = true }
                    }
                },
            )
        }
        composable<TetranoRoute.TwoPlayersGame> { backStackEntry ->
            val route = backStackEntry.toRoute<TetranoRoute.TwoPlayersGame>()
            GameRoute(
                mode = GameMode.TWO_PLAYERS,
                savedGameName = route.savedGameName,
                onNavigateToSettings = { navController.navigate(TetranoRoute.Settings) },
                onExit = { navController.popBackStack() },
            )
        }
        composable<TetranoRoute.ComputerGame> { backStackEntry ->
            val route = backStackEntry.toRoute<TetranoRoute.ComputerGame>()
            GameRoute(
                mode = GameMode.VS_COMPUTER,
                savedGameName = route.savedGameName,
                onNavigateToSettings = { navController.navigate(TetranoRoute.Settings) },
                onExit = { navController.popBackStack() },
            )
        }
        composable<TetranoRoute.Challenge> {
            ChallengeRoute(
                onNavigateToSettings = { navController.navigate(TetranoRoute.Settings) },
                onExit = { navController.popBackStack() },
            )
        }
        composable<TetranoRoute.SavedGames> {
            SavedGamesRoute(
                onWatch = { name -> navController.navigate(TetranoRoute.SavedGameViewer(name)) },
                onContinueTwoPlayers = { name -> navController.navigate(TetranoRoute.TwoPlayersGame(name)) },
                onContinueAgainstComputer = { name -> navController.navigate(TetranoRoute.ComputerSetup(name)) },
            )
        }
        composable<TetranoRoute.SavedGameViewer> { SavedGameViewerRoute() }
        composable<TetranoRoute.Settings> { SettingsRoute() }
        composable<TetranoRoute.Rules> {
            RulesRoute(
                onNavigateToWinningExamples = { navController.navigate(TetranoRoute.WinningExamples) },
            )
        }
        composable<TetranoRoute.WinningExamples> { WinningExamplesRoute() }
    }
}
