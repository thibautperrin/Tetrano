package fr.thibautperrin.tetrano.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.thibautperrin.tetrano.R
import fr.thibautperrin.tetrano.ui.components.ColorBlobRow
import fr.thibautperrin.tetrano.ui.components.FitText
import fr.thibautperrin.tetrano.ui.components.LegacyButton
import fr.thibautperrin.tetrano.ui.theme.LegacyColors
import fr.thibautperrin.tetrano.ui.theme.LocalLegacyDimens
import fr.thibautperrin.tetrano.ui.theme.isLandscape

/** Wires [TwoPlayersSetupViewModel] to the stateless [TwoPlayersSetupScreen]. */
@Composable
fun TwoPlayersSetupRoute(
    onNavigateToGame: () -> Unit,
    viewModel: TwoPlayersSetupViewModel = hiltViewModel(),
) {
    val uiModel by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.navigateToGame.collect { onNavigateToGame() }
    }
    TwoPlayersSetupScreen(uiModel = uiModel, onAction = viewModel::onAction)
}

/**
 * Ports `JouerDeuxJoueurActivity` (`activity_jouer_deux_joueur.xml`), on a
 * white page titled "2 players" (the activity's manifest label, see
 * [SetupPage]). For each player: "Joueur N name ?" and a name field, then
 * the 8 color blobs; the label, the typed name and the hint (the last name
 * used) all take the player's current color. Then the Play button.
 * - Portrait: label and field side by side, the blob row below, Play
 *   centered in the height left over.
 * - Landscape (`layout-land`): label with the field under it, the blob row
 *   to their right, Play pinned to the bottom.
 *
 * Only scrolls when the content can't fit the height (small landscape
 * screen with the keyboard up).
 */
@Composable
fun TwoPlayersSetupScreen(uiModel: TwoPlayersSetupUiModel, onAction: (TwoPlayersSetupAction) -> Unit) {
    val dimens = LocalLegacyDimens.current
    val player1Hint = uiModel.player1LastName.ifBlank { stringResource(R.string.Joueur1) }
    val player2Hint = uiModel.player2LastName.ifBlank { stringResource(R.string.Joueur2) }
    val players = listOf(
        PlayerSetup(
            label = stringResource(R.string.Joueur1_Name),
            name = uiModel.player1Name,
            hint = player1Hint,
            color = Color(uiModel.player1Color),
            imeAction = ImeAction.Next,
            onNameChanged = { onAction(TwoPlayersSetupAction.Player1NameChanged(it)) },
            onColorSelected = { onAction(TwoPlayersSetupAction.Player1ColorSelected(it.toArgb())) },
        ),
        PlayerSetup(
            label = stringResource(R.string.Joueur2_Name),
            name = uiModel.player2Name,
            hint = player2Hint,
            color = Color(uiModel.player2Color),
            imeAction = ImeAction.Done,
            onNameChanged = { onAction(TwoPlayersSetupAction.Player2NameChanged(it)) },
            onColorSelected = { onAction(TwoPlayersSetupAction.Player2ColorSelected(it.toArgb())) },
        ),
    )
    val play = @Composable {
        LegacyButton(
            text = stringResource(R.string.Play),
            onClick = {
                onAction(
                    TwoPlayersSetupAction.PlayClicked(
                        player1Name = uiModel.player1Name.ifBlank { player1Hint },
                        player2Name = uiModel.player2Name.ifBlank { player2Hint },
                    ),
                )
            },
            textColor = LegacyColors.TextBlue,
            fontSize = dimens.title3,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    SetupPage(title = stringResource(R.string.Two_Players)) {
        val landscape = isLandscape()
        val pageWidth = maxWidth
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight),
        ) {
            if (landscape) {
                players.forEachIndexed { index, player ->
                    if (index > 0) Spacer(Modifier.height(dimens.smallMargin))
                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(LANDSCAPE_NAME_WEIGHT)) {
                            PlayerLabel(player)
                            NameField(player, Modifier.fillMaxWidth())
                        }
                        ColorBlobRow(
                            onColorSelected = player.onColorSelected,
                            modifier = Modifier
                                .weight(1f - LANDSCAPE_NAME_WEIGHT)
                                .padding(start = dimens.tinyMargin),
                        )
                    }
                }
                Spacer(Modifier.height(dimens.smallMargin))
                Spacer(Modifier.weight(1f))
                play()
            } else {
                for (player in players) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        PlayerLabel(player, Modifier.widthIn(max = pageWidth * PORTRAIT_LABEL_MAX_WIDTH))
                        NameField(player, Modifier.weight(1f))
                    }
                    ColorBlobRow(onColorSelected = player.onColorSelected)
                }
                // Legacy FrameLayout filling the rest, Play centered in it.
                Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f).fillMaxWidth()) {
                    play()
                }
            }
        }
    }
}

private class PlayerSetup(
    val label: String,
    val name: String,
    val hint: String,
    val color: Color,
    val imeAction: ImeAction,
    val onNameChanged: (String) -> Unit,
    val onColorSelected: (Color) -> Unit,
)

@Composable
private fun PlayerLabel(player: PlayerSetup, modifier: Modifier = Modifier) {
    FitText(
        text = player.label,
        maxFontSize = LocalLegacyDimens.current.body,
        color = player.color,
        maxLines = 2,
        modifier = modifier,
    )
}

/**
 * The legacy `EditText` (`textPersonName`): underlined single-line field
 * whose text, hint and underline are all in the player's color.
 */
@Composable
private fun NameField(player: PlayerSetup, modifier: Modifier = Modifier) {
    val textStyle = TextStyle(color = player.color, fontSize = LocalLegacyDimens.current.body)
    BasicTextField(
        value = player.name,
        onValueChange = player.onNameChanged,
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(player.color),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            keyboardType = KeyboardType.Text,
            imeAction = player.imeAction,
        ),
        modifier = modifier,
        decorationBox = { innerTextField ->
            Column {
                Box(Modifier.padding(horizontal = FIELD_PADDING, vertical = FIELD_PADDING)) {
                    if (player.name.isEmpty()) {
                        Text(
                            text = player.hint,
                            style = textStyle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FIELD_UNDERLINE_INSET)
                        .height(1.dp)
                        .background(player.color),
                )
            }
        },
    )
}

/** Share of the width left to the label and name field in landscape. */
private const val LANDSCAPE_NAME_WEIGHT = 0.4f

/** Longest the portrait label may get before wrapping / shrinking, so the field keeps room. */
private const val PORTRAIT_LABEL_MAX_WIDTH = 0.55f

private val FIELD_PADDING: Dp = 8.dp
private val FIELD_UNDERLINE_INSET: Dp = 4.dp
