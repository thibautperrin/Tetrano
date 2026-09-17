package fr.thibautperrin.tetrano

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single activity hosting the whole app. Navigation between screens is
 * handled entirely in Compose (see docs/PLAN.md P7 for the navigation
 * graph); this bootstrap placeholder is replaced once that graph exists.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TetranoBootstrapScreen()
        }
    }
}

@Composable
private fun TetranoBootstrapScreen() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Text(text = "Hello Tetrano")
        }
    }
}
