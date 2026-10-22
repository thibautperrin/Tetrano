package fr.thibautperrin.tetrano.ui.challenge

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.thibautperrin.tetrano.R
import fr.thibautperrin.tetrano.ui.components.BoardCanvas
import fr.thibautperrin.tetrano.ui.components.BoardOrientation
import fr.thibautperrin.tetrano.ui.components.FitText
import fr.thibautperrin.tetrano.ui.components.LegacyButton
import fr.thibautperrin.tetrano.ui.components.LegacyTopBar
import fr.thibautperrin.tetrano.ui.theme.LegacyColors
import fr.thibautperrin.tetrano.ui.theme.LocalLegacyDimens
import fr.thibautperrin.tetrano.ui.theme.TetranoPalette
import fr.thibautperrin.tetrano.ui.theme.isLandscape

/** `?android:attr/textAppearanceLarge`, the legacy rings counter's text size (an upper bound here). */
private val LargeTextSize = 22.sp

/** The narrowest the landscape side panel (counter + give-up button) may get before the board shrinks for it. */
private val MinSidePanelWidth = 120.dp

/** The narrowest the portrait give-up button may get before the counter shrinks for it (`Widget.Button`'s min width). */
private val MinGiveUpButtonWidth = 88.dp

/** Wires [ChallengeViewModel] to the stateless [ChallengeScreen]. */
@Composable
fun ChallengeRoute(
    onNavigateToSettings: () -> Unit,
    onExit: () -> Unit,
    viewModel: ChallengeViewModel = hiltViewModel(),
) {
    val uiModel by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val allLevelsPassedMessage = stringResource(R.string.All_Levels_Passed)
    LaunchedEffect(Unit) {
        viewModel.exit.collect { reason ->
            if (reason is ChallengeExit.AllLevelsPassed) {
                Toast.makeText(context, allLevelsPassedMessage, Toast.LENGTH_LONG).show()
            }
            onExit()
        }
    }
    ChallengeScreen(uiModel = uiModel, onAction = viewModel::onAction, onNavigateToSettings = onNavigateToSettings)
}

/**
 * Ports `ChallengeActivity` (see docs/GLOSSARY.md, docs/PLAN.md P8) and its
 * `activity_challenge.xml` layouts: a black page (white in 2015) under the top bar;
 * portrait: the board above, the rings counter bottom-left and the give-up
 * button bottom-right; landscape (`layout-land`): the board on the left,
 * the counter top-right and the button bottom-right. The board always takes
 * the largest size that fits beside/above them.
 */
@Composable
fun ChallengeScreen(
    uiModel: ChallengeUiModel,
    onAction: (ChallengeAction) -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    Scaffold(
        containerColor = TetranoPalette.Page,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            LegacyTopBar(
                // ChallengeActivity.updateTitle(): "%s      %s %d".
                title = "${stringResource(R.string.Challenge)}      ${stringResource(R.string.Level)} ${uiModel.level}",
                actions = { ChallengeMenu(onAction = onAction, onNavigateToSettings = onNavigateToSettings) },
            )
        },
    ) { padding ->
        val onRingDropped: (Int) -> Unit = { onAction(ChallengeAction.RingDropped(it)) }
        val onCounterClick = { onAction(ChallengeAction.RingsCounterClicked) }
        val onGiveUp = { onAction(ChallengeAction.AbandonClicked) }
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(padding)
        if (isLandscape()) {
            LandscapeContent(uiModel, onRingDropped, onCounterClick, onGiveUp, contentModifier)
        } else {
            PortraitContent(uiModel, onRingDropped, onCounterClick, onGiveUp, contentModifier)
        }
    }

    ChallengeDialogs(uiModel, onAction)
}

/** `menu_challenge.xml`: Settings, Explanations, Hint (the legacy "Use a star"). */
@Composable
private fun ChallengeMenu(onAction: (ChallengeAction) -> Unit, onNavigateToSettings: () -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    IconButton(onClick = { menuExpanded = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = null)
    }
    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_settings)) },
            onClick = {
                menuExpanded = false
                onNavigateToSettings()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.Explanations)) },
            onClick = {
                menuExpanded = false
                onAction(ChallengeAction.ExplanationsMenuClicked)
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.Hint)) },
            onClick = {
                menuExpanded = false
                onAction(ChallengeAction.HintMenuClicked)
            },
        )
    }
}

/** `layout/activity_challenge.xml`: the board centered above a bottom bar, no margins. */
@Composable
private fun PortraitContent(
    uiModel: ChallengeUiModel,
    onRingDropped: (Int) -> Unit,
    onCounterClick: () -> Unit,
    onGiveUp: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            // No size modifier: BoardCanvas's aspect ratio picks the largest 4:5 box that fits.
            BoardCanvas(state = uiModel.board, orientation = BoardOrientation.PORTRAIT, onRingDropped = onRingDropped)
        }
        PortraitBottomBar(uiModel, onCounterClick, onGiveUp)
    }
}

/**
 * The counter wraps its text on the left; the button takes the counter's
 * height (legacy `alignTop` + `alignParentBottom`) and wraps its text on
 * the right, never narrower than [MinGiveUpButtonWidth].
 */
@Composable
private fun PortraitBottomBar(uiModel: ChallengeUiModel, onCounterClick: () -> Unit, onGiveUp: () -> Unit) {
    Layout(
        content = {
            RingsCounter(uiModel.usedRings, uiModel.authorizedRings, onCounterClick)
            GiveUpButton(visible = uiModel.canAbandon, onClick = onGiveUp)
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val reservedForButton = MinGiveUpButtonWidth.roundToPx().coerceAtMost(width / 2)
        val counter = measurables[0].measure(Constraints(maxWidth = width - reservedForButton))
        val height = counter.height
        val remaining = width - counter.width
        val button = measurables[1].measure(
            Constraints(minWidth = reservedForButton, maxWidth = remaining, minHeight = height, maxHeight = height),
        )
        layout(width, height) {
            counter.place(0, 0)
            button.place(width - button.width, 0)
        }
    }
}

/** `layout-land/activity_challenge.xml`: the board top-left, a side panel with 10 dp margins on its right. */
@Composable
private fun LandscapeContent(
    uiModel: ChallengeUiModel,
    onRingDropped: (Int) -> Unit,
    onCounterClick: () -> Unit,
    onGiveUp: () -> Unit,
    modifier: Modifier,
) {
    val margin = LocalLegacyDimens.current.tinyMargin
    BoxWithConstraints(modifier) {
        val boardWidth = min(maxHeight * 5f / 4f, maxWidth - MinSidePanelWidth).coerceAtLeast(0.dp)
        Row(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .width(boardWidth)
                    .fillMaxHeight(),
                contentAlignment = Alignment.TopStart,
            ) {
                BoardCanvas(state = uiModel.board, orientation = BoardOrientation.LANDSCAPE, onRingDropped = onRingDropped)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(margin),
            ) {
                RingsCounter(uiModel.usedRings, uiModel.authorizedRings, onCounterClick, Modifier.fillMaxWidth())
                Spacer(Modifier.weight(1f))
                GiveUpButton(visible = uiModel.canAbandon, onClick = onGiveUp, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * `textViewRingsUsed` ("Rings used:") above `textViewRingsUsed2` ("x / y",
 * centered), on the InfoRings background; both open the rings info dialog
 * (`onClickNbRings`).
 */
@Composable
private fun RingsCounter(used: Int, authorized: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(LegacyColors.InfoRings)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FitText(
            text = stringResource(R.string.Rings_used),
            maxFontSize = LargeTextSize,
            color = Color.Black,
            textAlign = TextAlign.Center,
        )
        // updateTextAndButton(): "%d / %d".
        FitText(
            text = "$used / $authorized",
            maxFontSize = LargeTextSize,
            color = Color.Black,
            textAlign = TextAlign.Center,
        )
    }
}

/** `buttonRestartChallenge`: INVISIBLE (space kept) when giving up isn't allowed. */
@Composable
private fun GiveUpButton(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    LegacyButton(
        text = stringResource(R.string.Abandon_Challenge),
        onClick = onClick,
        modifier = if (visible) modifier else modifier.alpha(0f).clearAndSetSemantics {},
        background = LegacyColors.ButtonCyan,
        textColor = Color.Black,
        fontSize = LocalLegacyDimens.current.bottomButtons,
        enabled = visible,
    )
}

@Composable
private fun ChallengeDialogs(uiModel: ChallengeUiModel, onAction: (ChallengeAction) -> Unit) {
    if (uiModel.showRingsInfoDialog) {
        // onClickNbRings (legacy: no button, cancelable).
        AlertDialog(
            onDismissRequest = { onAction(ChallengeAction.RingsInfoDismissed) },
            text = {
                DialogText(
                    stringResource(R.string.Explanation_Rings_Number) + "\n\n" +
                        stringResource(R.string.In_This_Level) + "\n\t" +
                        stringResource(R.string.Number_Of_Rings_Allowed) + " ${uiModel.authorizedRings}" + "\n\t" +
                        stringResource(R.string.Rings_used) + " ${uiModel.usedRings}",
                )
            },
            confirmButton = {
                TextButton(onClick = { onAction(ChallengeAction.RingsInfoDismissed) }) { Text(stringResource(R.string.OK)) }
            },
        )
    }

    when (uiModel.showExplanationsPage) {
        1 -> AlertDialog(
            onDismissRequest = {},
            title = { Text("${stringResource(R.string.Explanations)} 1/2") },
            text = { DialogText(stringResource(R.string.Explanations_Challenge1)) },
            confirmButton = {
                TextButton(onClick = { onAction(ChallengeAction.ExplanationsNextClicked) }) { Text(stringResource(R.string.Next)) }
            },
        )
        2 -> AlertDialog(
            onDismissRequest = { onAction(ChallengeAction.ExplanationsDismissed) },
            title = { Text("${stringResource(R.string.Explanations)} 2/2") },
            text = { DialogText(stringResource(R.string.Explanations_Challenge2)) },
            confirmButton = {
                TextButton(onClick = { onAction(ChallengeAction.ExplanationsDismissed) }) { Text(stringResource(R.string.Play)) }
            },
        )
    }

    uiModel.endDialog?.let { dialog ->
        val (title, message, positiveLabel) = endDialogTexts(dialog)
        AlertDialog(
            onDismissRequest = { onAction(ChallengeAction.EndDialogQuitClicked) },
            title = { Text(title) },
            text = { DialogText(message) },
            confirmButton = {
                TextButton(onClick = { onAction(ChallengeAction.EndDialogContinueClicked) }) { Text(positiveLabel) }
            },
            dismissButton = {
                TextButton(onClick = { onAction(ChallengeAction.EndDialogQuitClicked) }) { Text(stringResource(R.string.Quit)) }
            },
        )
    }
}

// displayDialogEnd(level, situation), ported.
@Composable
private fun endDialogTexts(dialog: ChallengeEndDialog): Triple<String, String, String> = when (dialog.situation) {
    ChallengeEndSituation.USER_WON -> Triple(
        stringResource(R.string.Sentence_End_Win),
        "${stringResource(R.string.You_Passed_Level)} ${dialog.level}",
        stringResource(R.string.Go_To_Next_Level),
    )
    ChallengeEndSituation.COMPUTER_WON -> Triple(
        stringResource(R.string.Sentence_End_Lost),
        "${stringResource(R.string.You_Lost_At_Level)} ${dialog.level}\n${stringResource(R.string.Computer_Won_Before)}",
        stringResource(R.string.Try_Again),
    )
    ChallengeEndSituation.RAN_OUT_OF_RINGS -> Triple(
        stringResource(R.string.Sentence_End_Lost),
        "${stringResource(R.string.You_Lost_At_Level)} ${dialog.level}\n${stringResource(R.string.Sentence_Number_Rings)}",
        stringResource(R.string.Try_Again),
    )
    ChallengeEndSituation.ABANDONED -> Triple(
        stringResource(R.string.Abandoned),
        "${stringResource(R.string.You_Lost_At_Level)} ${dialog.level}",
        stringResource(R.string.Try_Again),
    )
}

/** A dialog message that scrolls instead of being cut off (long explanations, landscape phones). */
@Composable
private fun DialogText(text: String) {
    Text(text, modifier = Modifier.verticalScroll(rememberScrollState()))
}
