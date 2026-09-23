package fr.thibautperrin.tetrano.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import fr.thibautperrin.tetrano.domain.model.GameMode
import fr.thibautperrin.tetrano.ui.game.GameRoute
import fr.thibautperrin.tetrano.ui.home.HomeRoute
import fr.thibautperrin.tetrano.ui.playmenu.PlayMenuRoute
import fr.thibautperrin.tetrano.ui.setup.ComputerSetupRoute
import fr.thibautperrin.tetrano.ui.setup.TwoPlayersSetupRoute

/**
 * Wires every [TetranoRoute] to its screen. [TetranoRoute.Home],
 * [TetranoRoute.PlayMenu], [TetranoRoute.TwoPlayersSetup],
 * [TetranoRoute.ComputerSetup], [TetranoRoute.TwoPlayersGame] and
 * [TetranoRoute.ComputerGame] land in this commit (see docs/PLAN.md P8);
 * the rest stay placeholders for later ones.
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
        composable<TetranoRoute.Challenge> { PlaceholderScreen("Challenge") }
        composable<TetranoRoute.SavedGames> { PlaceholderScreen("Saved games") }
        composable<TetranoRoute.SavedGameViewer> { PlaceholderScreen("Saved game viewer") }
        composable<TetranoRoute.Settings> { PlaceholderScreen("Settings") }
        composable<TetranoRoute.Rules> { PlaceholderScreen("Rules") }
        composable<TetranoRoute.WinningExamples> { PlaceholderScreen("Winning examples") }
    }
}

@Composable
private fun PlaceholderScreen(name: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "$name — coming in P8")
    }
}
