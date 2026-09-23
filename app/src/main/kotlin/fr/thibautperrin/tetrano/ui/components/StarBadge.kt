package fr.thibautperrin.tetrano.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.thibautperrin.tetrano.R

/**
 * The Challenge-mode hint currency: a count followed by the star. Ports
 * `ApplicationTetrano.getSpannableForTitle` (`"<count>     "` with
 * `star.png` spanned in) — used as the title of every star dialog; the
 * star is `R.drawable.star`, the vector redraw of `star.png`.
 */
@Composable
fun StarBadge(
    count: Int,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    fontSize: TextUnit = 22.sp,
) {
    val starSize = with(LocalDensity.current) { (fontSize * 1.3f).toDp() }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(text = count.toString(), color = color, fontSize = fontSize)
        Spacer(Modifier.width(12.dp))
        Image(painter = painterResource(R.drawable.star), contentDescription = null, modifier = Modifier.size(starSize))
    }
}
