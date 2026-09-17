package fr.thibautperrin.tetrano.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * A full-width play-menu button: a translucent yellow panel with a
 * full-bleed icon and an optional centered label. Ports `BoutonsJouer`
 * (see docs/GLOSSARY.md) — [icon] replaces the legacy PNG (`bouton_*`,
 * see docs/PLAN.md P9) with a vector drawable/icon.
 *
 * Text auto-sizing-to-fit-width isn't reproduced (Compose has no direct
 * equivalent, and it isn't worth one for a menu label) — a fixed style
 * achieves the same look closely enough.
 */
@Composable
fun IconTextButton(
    icon: Painter,
    text: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2f)
            .background(BUTTON_BACKGROUND)
            .clickable(onClick = onClick),
    ) {
        Image(
            painter = icon,
            contentDescription = text,
            modifier = Modifier.fillMaxSize(),
        )
        if (text != null) {
            Text(
                text = text,
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp)
                    .fillMaxWidth(),
            )
        }
    }
}

// 0xdbffff32, unchanged.
private val BUTTON_BACKGROUND = Color(0xDBFFFF32)
