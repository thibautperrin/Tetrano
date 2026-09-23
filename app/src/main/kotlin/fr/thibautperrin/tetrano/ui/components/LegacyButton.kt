package fr.thibautperrin.tetrano.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.thibautperrin.tetrano.ui.theme.LegacyColors

/**
 * The legacy flat, full-bleed colored `Button` (`button_texte_style` and
 * the Play / Confirm / game-bar buttons): a translucent colored panel with
 * bold italic text. [fontSize] is the legacy size and an upper bound — the
 * label shrinks to fit (up to [maxLines] lines) instead of being cut off.
 */
@Composable
fun LegacyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = LegacyColors.ButtonYellow,
    textColor: Color = Color.Black,
    fontSize: TextUnit = 35.sp,
    enabled: Boolean = true,
    maxLines: Int = 2,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .alpha(if (enabled) 1f else 0.5f)
            .background(background)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        FitText(
            text = text,
            maxFontSize = fontSize,
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Italic,
            textAlign = TextAlign.Center,
            maxLines = maxLines,
        )
    }
}

/**
 * Text that uses [maxFontSize] when it fits and shrinks (down to
 * [minFontSize]) when it doesn't, instead of being clipped — the rewrite's
 * answer to the legacy per-screen-size `dimens.xml` text sizes.
 */
@Composable
fun FitText(
    text: String,
    maxFontSize: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
    textAlign: TextAlign = TextAlign.Start,
    maxLines: Int = 1,
    minFontSize: TextUnit = 8.sp,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            textAlign = textAlign,
        ),
        maxLines = maxLines,
        autoSize = TextAutoSize.StepBased(
            minFontSize = minFontSize,
            maxFontSize = maxFontSize,
            stepSize = 0.5.sp,
        ),
    )
}
