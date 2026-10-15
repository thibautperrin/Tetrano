package fr.thibautperrin.tetrano.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import fr.thibautperrin.tetrano.ui.theme.LegacyColors

/**
 * The legacy `Theme.AppCompat.Light.DarkActionBar` action bar: dark bar,
 * white title and overflow icon. [title] is a slot so screens can put a
 * [StarBadge] or a dynamic title there. Insets cover the display cutout
 * too (landscape), not just the status bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegacyTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = title,
        navigationIcon = navigationIcon,
        actions = actions,
        modifier = modifier,
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = LegacyColors.ActionBar,
            titleContentColor = LegacyColors.OnActionBar,
            actionIconContentColor = LegacyColors.OnActionBar,
            navigationIconContentColor = LegacyColors.OnActionBar,
        ),
    )
}

/** Convenience overload for a plain, single-line (ellipsized) text title. */
@Composable
fun LegacyTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    LegacyTopBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
    )
}
