package fr.thibautperrin.tetrano

import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import fr.thibautperrin.tetrano.ui.navigation.TetranoNavHost
import fr.thibautperrin.tetrano.ui.theme.TetranoTheme

/**
 * Single activity hosting the whole app; navigation between screens is
 * entirely in Compose (see docs/PLAN.md P7, [TetranoNavHost]).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate()/setContent (see docs/PLAN.md P9):
        // it reads the Theme.Tetrano splash attributes and keeps the splash
        // visible until the first Compose frame, per the core-splashscreen
        // integration contract.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Light status-bar icons: every screen's top edge is dark (the legacy
        // dark action bar, or the black fond_inverse collage).
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        setContent {
            TetranoApp()
        }
    }
}

@Composable
private fun TetranoApp() {
    TetranoTheme {
        TetranoNavHost()
    }
}
