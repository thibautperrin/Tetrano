package fr.thibautperrin.tetrano.ui.strategy

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import fr.thibautperrin.tetrano.R
import fr.thibautperrin.tetrano.ui.components.LegacyTopBar
import fr.thibautperrin.tetrano.ui.theme.TetranoPalette

/**
 * The opening guide: best first moves, the second player's best defence, the balanced game after
 * a weak first ring — drawn boards and games to step through. It is the HTML page built by
 * `tools/guide/build_guide.py` (app variant, black and yellow, in `assets/strategy/`), in French
 * for a French device and in English otherwise. Links out of the guide open in the browser.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun StrategyScreen() {
    val language = LocalConfiguration.current.locales[0].language
    val page = if (language == "fr") "strategy-fr.html" else "strategy-en.html"
    Scaffold(
        containerColor = TetranoPalette.Page,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { LegacyTopBar(title = stringResource(R.string.Strategy)) },
    ) { padding ->
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    setBackgroundColor(TetranoPalette.Page.toArgb())
                    // The guide's game steppers are scripted.
                    settings.javaScriptEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val url = request.url
                            if (url.scheme == "file") return false
                            try {
                                view.context.startActivity(Intent(Intent.ACTION_VIEW, url))
                            } catch (_: ActivityNotFoundException) {
                                // No browser: the link does nothing.
                            }
                            return true
                        }
                    }
                    loadUrl("file:///android_asset/strategy/$page")
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}
