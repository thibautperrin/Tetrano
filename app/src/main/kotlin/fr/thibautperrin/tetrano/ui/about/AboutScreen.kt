package fr.thibautperrin.tetrano.ui.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.thibautperrin.tetrano.R
import fr.thibautperrin.tetrano.ui.components.LegacyButton
import fr.thibautperrin.tetrano.ui.components.LegacyTopBar
import fr.thibautperrin.tetrano.ui.theme.TetranoPalette

/**
 * What Qubic is, how Maxime Vial's Tetrano draws it with rings, who wrote
 * the app and where its source code lives. Replaces the 2015 one-line
 * "About" dialog.
 */
@Composable
fun AboutScreen() {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val versionName = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    Scaffold(
        containerColor = TetranoPalette.Page,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { LegacyTopBar(title = stringResource(R.string.About)) },
    ) { padding ->
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon()
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            stringResource(R.string.app_name),
                            color = TetranoPalette.Accent,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(stringResource(R.string.about_version, versionName), color = TetranoPalette.TextSecondary)
                    }
                }
                Section(stringResource(R.string.about_qubic_title), stringResource(R.string.about_qubic))
                Section(stringResource(R.string.about_tetrano_title), stringResource(R.string.about_tetrano))
                Section(stringResource(R.string.about_app_title), stringResource(R.string.about_app))
                Section(stringResource(R.string.about_open_source_title), stringResource(R.string.about_open_source))
                LegacyButton(
                    text = stringResource(R.string.about_github),
                    onClick = { runCatching { uriHandler.openUri(GITHUB_URL) } },
                    fontSize = 20.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { runCatching { uriHandler.openUri(LICENSE_URL) } }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.about_license), color = TetranoPalette.Accent)
                }
            }
        }
    }
}

/** The adaptive launcher icon's two layers (a `mipmap` adaptive icon can't be painted directly). */
@Composable
private fun AppIcon() {
    Box(Modifier.size(72.dp).clip(RoundedCornerShape(16.dp))) {
        Image(painterResource(R.drawable.ic_launcher_background), contentDescription = null, modifier = Modifier.fillMaxSize())
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun Section(title: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = TetranoPalette.Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(text, color = TetranoPalette.Text, fontSize = 16.sp, lineHeight = 23.sp)
    }
}

private const val GITHUB_URL = "https://github.com/thibautperrin/Tetrano"
private const val LICENSE_URL = "https://github.com/thibautperrin/Tetrano/blob/main/LICENSE"
