package fr.thibautperrin.tetrano.ui.rules

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.hilt.navigation.compose.hiltViewModel
import fr.thibautperrin.tetrano.R
import fr.thibautperrin.tetrano.ui.components.LegacyButton
import fr.thibautperrin.tetrano.ui.components.LegacyTopBar
import fr.thibautperrin.tetrano.ui.theme.LegacyColors
import fr.thibautperrin.tetrano.ui.theme.LocalLegacyDimens

/** Wires [RulesViewModel] to the stateless [RulesScreen]. */
@Composable
fun RulesRoute(
    onNavigateToWinningExamples: () -> Unit,
    viewModel: RulesViewModel = hiltViewModel(),
) {
    val uiModel by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.navigateToWinningExamples.collect { onNavigateToWinningExamples() }
    }
    RulesScreen(uiModel = uiModel, onAction = viewModel::onAction)
}

/**
 * Ports `NoticeFormelleActivity` (see docs/GLOSSARY.md, docs/PLAN.md P8)
 * and its `activity_notice_formelle` layout, under the dark action bar
 * (`Rules_Of_Game`): the rules text (`Long_Sentence_Rules`, `Taille_Corps`)
 * scrolling in the top 9/10 of the page, and the yellow "Winning situation
 * examples" button (black bold italic text) pinned in the bottom 1/10 —
 * never shorter than a touchable height.
 */
@Composable
fun RulesScreen(uiModel: RulesUiModel, onAction: (RulesAction) -> Unit) {
    val dimens = LocalLegacyDimens.current
    Scaffold(
        containerColor = LegacyColors.Page,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { LegacyTopBar(title = stringResource(R.string.Rules_Of_Game)) },
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(dimens.screenMargin),
        ) {
            val buttonHeight = max(maxHeight / 10, MIN_BUTTON_HEIGHT)
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = stringResource(R.string.Long_Sentence_Rules),
                    color = DefaultTextColor,
                    fontSize = dimens.body,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                )
                LegacyButton(
                    text = stringResource(R.string.Winning_Situation_Example),
                    onClick = { onAction(RulesAction.WinningExamplesClicked) },
                    fontSize = dimens.examplesButton,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(buttonHeight),
                )
            }
        }
    }
}

private val MIN_BUTTON_HEIGHT = 48.dp

/** A plain legacy `TextView`'s color: Material light's `textColorSecondary`. */
private val DefaultTextColor = Color(0x8A000000)
