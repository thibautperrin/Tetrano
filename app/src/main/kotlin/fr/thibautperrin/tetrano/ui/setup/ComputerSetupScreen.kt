package fr.thibautperrin.tetrano.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.thibautperrin.tetrano.R

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

/** Ports `JouerContreOrdiActivity` (see docs/GLOSSARY.md, docs/PLAN.md P8). */
@Composable
fun ComputerSetupScreen(uiModel: ComputerSetupUiModel, onAction: (ComputerSetupAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${stringResource(R.string.Level)}: ${uiModel.aiLevel}")
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onAction(ComputerSetupAction.LevelDecremented) }) {
                    androidx.compose.material3.Icon(Icons.Default.Remove, contentDescription = null)
                }
                Slider(
                    value = uiModel.aiLevel.toFloat(),
                    onValueChange = { onAction(ComputerSetupAction.LevelChanged(it.toInt())) },
                    valueRange = 0f..100f,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onAction(ComputerSetupAction.LevelIncremented) }) {
                    androidx.compose.material3.Icon(Icons.Default.Add, contentDescription = null)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.Who_Begins))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = uiModel.userBegins,
                        onClick = { onAction(ComputerSetupAction.UserBeginsSelected) },
                    ),
            ) {
                RadioButton(selected = uiModel.userBegins, onClick = { onAction(ComputerSetupAction.UserBeginsSelected) })
                Text(stringResource(R.string.i_begin))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = !uiModel.userBegins,
                        onClick = { onAction(ComputerSetupAction.ComputerBeginsSelected) },
                    ),
            ) {
                RadioButton(selected = !uiModel.userBegins, onClick = { onAction(ComputerSetupAction.ComputerBeginsSelected) })
                Text(stringResource(R.string.computer_begins))
            }
        }

        Button(
            onClick = { onAction(ComputerSetupAction.PlayClicked) },
            enabled = !uiModel.isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.Play))
        }
    }
}
