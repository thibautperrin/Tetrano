package fr.thibautperrin.tetrano.ui.theme

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The legacy `dimens.xml` values, per screen-size bucket (`values-small`,
 * default, `values-large`, `values-xlarge`), under their original names
 * translated to English. Text sizes are upper bounds: screens pair them
 * with auto-sizing so a label shrinks instead of being cut off.
 */
@Immutable
data class LegacyDimens(
    /** `activity_horizontal_margin` / `activity_vertical_margin`. */
    val screenMargin: Dp,
    /** `Grosse_marge`. */
    val largeMargin: Dp,
    /** `Moyenne_marge`. */
    val mediumMargin: Dp,
    /** `Petite_marge`. */
    val smallMargin: Dp,
    /** `Tres_petite_marge`. */
    val tinyMargin: Dp,
    /** `Taille_titre_2`. */
    val title2: TextUnit,
    /** `Taille_titre_3`. */
    val title3: TextUnit,
    /** `Taille_Corps`. */
    val body: TextUnit,
    /** `three_bottom_buttons`. */
    val bottomButtons: TextUnit,
    /** `winning_situation_examples_button`. */
    val examplesButton: TextUnit,
)

private val Small = LegacyDimens(12.dp, 45.dp, 28.dp, 12.dp, 6.dp, 23.sp, 20.sp, 16.sp, 13.sp, 20.sp)
private val Normal = LegacyDimens(16.dp, 70.dp, 45.dp, 20.dp, 10.dp, 35.sp, 28.sp, 20.sp, 15.sp, 20.sp)
private val Large = LegacyDimens(20.dp, 100.dp, 56.dp, 25.dp, 12.dp, 45.sp, 34.sp, 25.sp, 20.sp, 28.sp)
private val XLarge = LegacyDimens(24.dp, 135.dp, 67.dp, 30.dp, 15.dp, 54.sp, 40.sp, 30.sp, 23.sp, 28.sp)

val LocalLegacyDimens = staticCompositionLocalOf { Normal }

@Composable
internal fun rememberLegacyDimens(): LegacyDimens {
    val configuration = LocalConfiguration.current
    val sizeBucket = configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK
    return remember(sizeBucket) {
        when (sizeBucket) {
            Configuration.SCREENLAYOUT_SIZE_SMALL -> Small
            Configuration.SCREENLAYOUT_SIZE_LARGE -> Large
            Configuration.SCREENLAYOUT_SIZE_XLARGE -> XLarge
            else -> Normal
        }
    }
}

/** True in landscape — the legacy `layout-land` qualifier. */
@Composable
fun isLandscape(): Boolean =
    LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

/** True on `large`/`xlarge` screens — the legacy `layout-large` qualifier. */
@Composable
fun isLargeScreen(): Boolean {
    val bucket = LocalConfiguration.current.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK
    return bucket >= Configuration.SCREENLAYOUT_SIZE_LARGE
}
