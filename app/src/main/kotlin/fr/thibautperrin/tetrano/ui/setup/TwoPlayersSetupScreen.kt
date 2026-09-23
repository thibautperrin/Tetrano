package fr.thibautperrin.tetrano.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.thibautperrin.tetrano.R
import fr.thibautperrin.tetrano.ui.components.ColorBlob
import fr.thibautperrin.tetrano.ui.theme.TetranoRingColors

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

/** Ports `JouerDeuxJoueurActivity` (see docs/GLOSSARY.md, docs/PLAN.md P8). */
@Composable
fun TwoPlayersSetupScreen(uiModel: TwoPlayersSetupUiModel, onAction: (TwoPlayersSetupAction) -> Unit) {
    val player1Hint = stringResource(R.string.Joueur1)
    val player2Hint = stringResource(R.string.Joueur2)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        PlayerSetup(
            label = stringResource(R.string.player1_name_label),
            name = uiModel.player1Name,
            hint = player1Hint,
            color = Color(uiModel.player1Color),
            onNameChanged = { onAction(TwoPlayersSetupAction.Player1NameChanged(it)) },
            onColorSelected = { onAction(TwoPlayersSetupAction.Player1ColorSelected(it)) },
        )
        PlayerSetup(
            label = stringResource(R.string.player2_name_label),
            name = uiModel.player2Name,
            hint = player2Hint,
            color = Color(uiModel.player2Color),
            onNameChanged = { onAction(TwoPlayersSetupAction.Player2NameChanged(it)) },
            onColorSelected = { onAction(TwoPlayersSetupAction.Player2ColorSelected(it)) },
        )
        Button(
            onClick = {
                onAction(
                    TwoPlayersSetupAction.PlayClicked(
                        player1Name = uiModel.player1Name.ifBlank { player1Hint },
                        player2Name = uiModel.player2Name.ifBlank { player2Hint },
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.Play))
        }
    }
}

@Composable
private fun PlayerSetup(
    label: String,
    name: String,
    hint: String,
    color: Color,
    onNameChanged: (String) -> Unit,
    onColorSelected: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChanged,
            label = { Text(label) },
            placeholder = { Text(hint) },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedTextColor = color,
                unfocusedTextColor = color,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (option in TetranoRingColors.selectable) {
                ColorBlob(
                    color = option,
                    modifier = Modifier
                        .clickable { onColorSelected(option.toArgb()) },
                )
            }
        }
    }
}
