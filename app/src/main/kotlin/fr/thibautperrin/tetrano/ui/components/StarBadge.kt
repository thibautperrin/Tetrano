package fr.thibautperrin.tetrano.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The Challenge-mode hint currency: a count next to a star glyph. Ports
 * `ApplicationTetrano.getSpannableForTitle` (which embedded `star.png`
 * into a title's `SpannableStringBuilder`) as a plain Composable — a
 * Unicode glyph instead of a bitmap needs no asset at all (see
 * docs/PLAN.md P9).
 */
@Composable
fun StarBadge(count: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(text = count.toString(), color = LocalContentColor.current)
        Spacer(Modifier.width(4.dp))
        Text(text = "★", color = STAR_COLOR)
    }
}

// A warm gold, close to the legacy star.png's color.
private val STAR_COLOR = Color(0xFFFFC107)
