package fr.thibautperrin.tetrano

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import fr.thibautperrin.tetrano.ui.components.TetranoBackground
import fr.thibautperrin.tetrano.ui.navigation.TetranoNavHost
import fr.thibautperrin.tetrano.ui.theme.TetranoTheme

/**
 * Single activity hosting the whole app; navigation between screens is
 * entirely in Compose (see docs/PLAN.md P7, [TetranoNavHost]).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TetranoApp()
        }
    }
}

@Composable
private fun TetranoApp() {
    TetranoTheme {
        TetranoBackground(modifier = Modifier.fillMaxSize()) {
            TetranoNavHost()
        }
    }
}
