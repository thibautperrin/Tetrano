package fr.thibautperrin.tetrano.ui.settings

import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.thibautperrin.tetrano.R
import fr.thibautperrin.tetrano.ui.components.ColorBlob
import fr.thibautperrin.tetrano.ui.theme.TetranoRingColors

/** Wires [SettingsViewModel] to the stateless [SettingsScreen]. */
@Composable
fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val uiModel by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val savedMessage = stringResource(R.string.Settings_Saved)
    LaunchedEffect(uiModel.justSaved) {
        if (uiModel.justSaved) {
            Toast.makeText(context, savedMessage, Toast.LENGTH_SHORT).show()
            viewModel.onAction(SettingsAction.SavedAcknowledged)
        }
    }
    SettingsScreen(uiModel = uiModel, onAction = viewModel::onAction)
}

/** Ports `SettingsActivity` (see docs/GLOSSARY.md, docs/PLAN.md P8). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(uiModel: SettingsUiModel, onAction: (SettingsAction) -> Unit) {
    val nameHint = stringResource(R.string.User)

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.action_settings)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            OutlinedTextField(
                value = uiModel.userName,
                onValueChange = { onAction(SettingsAction.UserNameChanged(it)) },
                label = { Text(stringResource(R.string.User_Name)) },
                placeholder = { Text(nameHint) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color(uiModel.userColor),
                    unfocusedTextColor = Color(uiModel.userColor),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            ColorPickerRow(
                label = stringResource(R.string.User_Color),
                selected = uiModel.userColor,
                onColorSelected = { onAction(SettingsAction.UserColorSelected(it)) },
            )
            ColorPickerRow(
                label = stringResource(R.string.Adversary_Color),
                selected = uiModel.adversaryColor,
                onColorSelected = { onAction(SettingsAction.AdversaryColorSelected(it)) },
            )
            Button(
                onClick = { onAction(SettingsAction.SaveClicked) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.Confirm))
            }
        }
    }
}

@Composable
private fun ColorPickerRow(label: String, selected: Int, onColorSelected: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (option in TetranoRingColors.selectable) {
                val isSelected = option.toArgb() == selected
                ColorBlob(
                    color = option,
                    modifier = Modifier
                        .then(
                            if (isSelected) {
                                Modifier.border(2.dp, MaterialTheme.colorScheme.primary)
                            } else {
                                Modifier
                            },
                        )
                        .clickable { onColorSelected(option.toArgb()) },
                )
            }
        }
    }
}
