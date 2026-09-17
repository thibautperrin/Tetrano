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

/**
 * Wires every [TetranoRoute] to its screen. Real screens land in P8 (see
 * docs/PLAN.md) — every destination is a placeholder for now, which
 * exercises the navigation graph end to end (including argument passing
 * for the routes that carry one) ahead of that.
 */
@Composable
fun TetranoNavHost(
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier,
) {
    NavHost(navController = navController, startDestination = TetranoRoute.Home, modifier = modifier) {
        composable<TetranoRoute.Home> { PlaceholderScreen("Home") }
        composable<TetranoRoute.PlayMenu> { PlaceholderScreen("Play menu") }
        composable<TetranoRoute.TwoPlayersSetup> { PlaceholderScreen("Two players setup") }
        composable<TetranoRoute.ComputerSetup> { PlaceholderScreen("Computer setup") }
        composable<TetranoRoute.TwoPlayersGame> { PlaceholderScreen("Two players game") }
        composable<TetranoRoute.ComputerGame> { PlaceholderScreen("Computer game") }
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
