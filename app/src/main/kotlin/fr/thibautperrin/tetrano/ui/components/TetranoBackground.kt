package fr.thibautperrin.tetrano.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import fr.thibautperrin.tetrano.R

/**
 * The 2015 `fond_inverse` collage (tilted 4x4 grids and colored rings on
 * black) behind Home and the Play menu — the only two screens that had it.
 *
 * Both hand-made originals are kept pixel for pixel (lossless WebP): the
 * portrait one (`drawable/`) and the landscape one (`drawable-land/`),
 * picked here from the available space rather than a resource qualifier.
 * The artwork is a raster collage of thin 1px lines, which does not survive
 * vectorization faithfully (see docs/PLAN.md P9). [ContentScale.Crop]
 * keeps its proportions where the legacy `android:background` stretched it.
 */
@Composable
fun TetranoBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(Color.Black)) {
        val landscape = maxWidth > maxHeight
        Image(
            painter = painterResource(if (landscape) R.drawable.fond_inverse_landscape else R.drawable.fond_inverse_portrait),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize()) { content() }
    }
}
