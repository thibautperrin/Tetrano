package fr.thibautperrin.tetrano.ui.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.thibautperrin.tetrano.R
import fr.thibautperrin.tetrano.domain.service.AiService
import fr.thibautperrin.tetrano.ui.components.FitText
import fr.thibautperrin.tetrano.ui.components.LegacyButton
import fr.thibautperrin.tetrano.ui.components.TriangleButton
import fr.thibautperrin.tetrano.ui.theme.LocalLegacyDimens
import fr.thibautperrin.tetrano.ui.theme.TetranoPalette
import fr.thibautperrin.tetrano.ui.theme.isLandscape
import kotlin.math.roundToInt

/** Wires [ComputerSetupViewModel] to the stateless [ComputerSetupScreen]. */
@Composable
fun ComputerSetupRoute(
    onNavigateToGame: () -> Unit,
    viewModel: ComputerSetupViewModel = hiltViewModel(),
) {
    val uiModel by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.navigateToGame.collect { onNavigateToGame() }
    }
    ComputerSetupScreen(uiModel = uiModel, onAction = viewModel::onAction)
}

/**
 * Ports `JouerContreOrdiActivity` (`activity_jouer_contre_ordi.xml`), on a
 * black page titled "1 player" (the activity's manifest label, see
 * [SetupPage]): "Who begins?", the User / Computer radio buttons, "Level"
 * and its number, the ◀ level bar ▶ row (0..101, ±1 per arrow; 101 = neural network; 102 and
 * 103 = Unbeatable, only when the computer begins), and the big Play button.
 * - Portrait: all of it stacked, Play centered in the height left over.
 * - Landscape (`layout-land`): the settings on the left two thirds, Play
 *   vertically centered on the right third (the legacy `fill_parent` +
 *   `layout_weight` 50/100 pair, which splits the width 2:1).
 *
 * Texts use the legacy sizes when they fit and shrink when they don't; the
 * settings only scroll when even that can't make them fit the height.
 */
@Composable
fun ComputerSetupScreen(uiModel: ComputerSetupUiModel, onAction: (ComputerSetupAction) -> Unit) {
    val dimens = LocalLegacyDimens.current
    val play = @Composable { modifier: Modifier ->
        LegacyButton(
            text = stringResource(R.string.Play),
            onClick = { onAction(ComputerSetupAction.PlayClicked) },
            fontSize = dimens.title2,
            modifier = modifier.fillMaxWidth(),
        )
    }
    SetupPage(title = stringResource(R.string.One_Player)) {
        val minHeight = maxHeight
        if (isLandscape()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .weight(2f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                ) {
                    ComputerSettings(uiModel, onAction)
                }
                play(Modifier.weight(1f))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = minHeight),
            ) {
                ComputerSettings(uiModel, onAction)
                // Legacy FrameLayout filling the rest, Play centered in it.
                Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f).fillMaxWidth()) {
                    play(Modifier)
                }
            }
        }
    }
}

@Composable
private fun ComputerSettings(uiModel: ComputerSetupUiModel, onAction: (ComputerSetupAction) -> Unit) {
    val dimens = LocalLegacyDimens.current
    FitText(
        text = stringResource(R.string.Who_Begins),
        maxFontSize = dimens.title2,
        color = TetranoPalette.Accent,
    )
    Column(Modifier.selectableGroup()) {
        WhoBeginsRadio(
            text = stringResource(R.string.User),
            selected = uiModel.userBegins,
            onClick = { onAction(ComputerSetupAction.UserBeginsSelected) },
            startMargin = dimens.largeMargin,
        )
        WhoBeginsRadio(
            text = stringResource(R.string.Computer),
            selected = !uiModel.userBegins,
            onClick = { onAction(ComputerSetupAction.ComputerBeginsSelected) },
            startMargin = dimens.largeMargin,
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        FitText(
            text = stringResource(R.string.Level),
            maxFontSize = dimens.title2,
            color = TetranoPalette.Accent,
            modifier = Modifier.weight(1f, fill = false),
        )
        // Legacy TextNiveauPourcent: String.format(" %d", progress).
        FitText(
            text = " ${uiModel.aiLevel}",
            maxFontSize = dimens.title2,
            color = TetranoPalette.Text,
        )
    }
    val levelName = when (uiModel.aiLevel) {
        AiService.NEURAL_LEVEL -> R.string.Level_Neural
        AiService.UNBEATABLE_STRONG_LEVEL -> R.string.Level_Unbeatable_Strong
        AiService.UNBEATABLE_WEAK_LEVEL -> R.string.Level_Unbeatable_Weak
        else -> null
    }
    if (levelName != null) {
        FitText(
            text = stringResource(levelName),
            maxFontSize = dimens.title3,
            color = TetranoPalette.Text,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        TriangleButton(
            next = false,
            onClick = { onAction(ComputerSetupAction.LevelDecremented) },
            contentDescription = stringResource(R.string.level_lower),
            modifier = Modifier.widthIn(min = MIN_TOUCH_TARGET).width(dimens.mediumMargin),
        )
        Slider(
            value = uiModel.aiLevel.toFloat(),
            onValueChange = { onAction(ComputerSetupAction.LevelChanged(it.roundToInt())) },
            valueRange = ComputerSetupViewModel.MIN_LEVEL.toFloat()..ComputerSetupViewModel.maxLevel(uiModel.userBegins).toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = TetranoPalette.Accent,
                activeTrackColor = TetranoPalette.Accent,
            ),
            modifier = Modifier.weight(1f),
        )
        TriangleButton(
            next = true,
            onClick = { onAction(ComputerSetupAction.LevelIncremented) },
            contentDescription = stringResource(R.string.level_higher),
            modifier = Modifier.widthIn(min = MIN_TOUCH_TARGET).width(dimens.mediumMargin),
        )
    }
    if (uiModel.userBegins && uiModel.aiLevel == AiService.NEURAL_LEVEL) {
        FitText(
            text = stringResource(R.string.Level_Unbeatable_Hint),
            maxFontSize = dimens.title3,
            color = TetranoPalette.TextSecondary,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun WhoBeginsRadio(text: String, selected: Boolean, onClick: () -> Unit, startMargin: Dp) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = startMargin)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = TetranoPalette.Accent,
                unselectedColor = TetranoPalette.TextSecondary,
            ),
            modifier = Modifier.padding(RADIO_PADDING),
        )
        FitText(
            text = text,
            maxFontSize = LocalLegacyDimens.current.title3,
            color = TetranoPalette.Text,
        )
    }
}

/** The arrows' smallest width (their height follows), whatever the screen size. */
private val MIN_TOUCH_TARGET = 48.dp

// Room around the radio circle, like the legacy RadioButton's own padding.
private val RADIO_PADDING = 8.dp
