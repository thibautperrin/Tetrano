package fr.thibautperrin.tetrano.ui.savedgames

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.thibautperrin.tetrano.R
import fr.thibautperrin.tetrano.ui.components.FitText
import fr.thibautperrin.tetrano.ui.theme.LegacyColors
import fr.thibautperrin.tetrano.ui.theme.LocalLegacyDimens

/** Wires [SavedGamesViewModel] to the stateless [SavedGamesScreen], and handles its navigation. */
@Composable
fun SavedGamesRoute(
    onWatch: (String) -> Unit,
    onExit: () -> Unit,
    viewModel: SavedGamesViewModel = hiltViewModel(),
) {
    val uiModel by viewModel.uiState.collectAsState()
    // The "no saved game" dialog's dismissal must close the screen only once.
    var exited by remember { mutableStateOf(false) }
    SavedGamesScreen(
        uiModel = uiModel,
        onAction = { action ->
            when (action) {
                is SavedGamesAction.WatchClicked -> onWatch(action.name)
                else -> viewModel.onAction(action)
            }
        },
        onNoSavedGameDismissed = {
            if (!exited) {
                exited = true
                onExit()
            }
        },
    )
}

/**
 * Ports `ListePartiesEnregistreesActivity` (see docs/GLOSSARY.md, docs/PLAN.md
 * P8) and its `activity_liste_parties_enregistrees` layout: a black page (a
 * plain `Activity`, no title bar) listing the saved games' names in
 * `COLOR_VERT_CLAIR`, the selected one inverted, above a row of 3 equal
 * default-style buttons — Watch, Rename, Delete — acting on the selection
 * (they do nothing when no row is selected).
 */
@Composable
fun SavedGamesScreen(
    uiModel: SavedGamesUiModel,
    onAction: (SavedGamesAction) -> Unit,
    onNoSavedGameDismissed: () -> Unit,
) {
    val dimens = LocalLegacyDimens.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(dimens.screenMargin),
    ) {
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(uiModel.names, key = { it }) { name ->
                SavedGameRow(
                    name = name,
                    selected = name == uiModel.selected,
                    onClick = { onAction(SavedGamesAction.GameClicked(name)) },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val selected = uiModel.selected
            DefaultButton(
                text = stringResource(R.string.Watch),
                onClick = { selected?.let { onAction(SavedGamesAction.WatchClicked(it)) } },
                modifier = Modifier.weight(1f),
            )
            DefaultButton(
                text = stringResource(R.string.Rename),
                onClick = { selected?.let { onAction(SavedGamesAction.RenameClicked(it)) } },
                modifier = Modifier.weight(1f),
            )
            DefaultButton(
                text = stringResource(R.string.Delete),
                onClick = { selected?.let { onAction(SavedGamesAction.DeleteClicked(it)) } },
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (uiModel.noSavedGame) {
        // displayDialogNoSavedGame(): message only, cancelable, finish() on dismiss.
        AlertDialog(
            onDismissRequest = onNoSavedGameDismissed,
            text = { Text(stringResource(R.string.Sentence_No_Saved_Game)) },
            confirmButton = {},
        )
    }

    uiModel.renameTarget?.let {
        RenameDialog(draft = uiModel.renameDraft, error = uiModel.renameError, onAction = onAction)
    }

    if (uiModel.deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { onAction(SavedGamesAction.DeleteDismissed) },
            text = { Text(stringResource(R.string.Sentence_Confirm_Delete_Game)) },
            confirmButton = {
                TextButton(onClick = { onAction(SavedGamesAction.DeleteConfirmed) }) { Text(stringResource(R.string.Confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { onAction(SavedGamesAction.DeleteDismissed) }) { Text(stringResource(R.string.Cancel)) }
            },
        )
    }
}

/** `MyArrayAdapter.getView`: green on black, or black on green when selected; long names wrap. */
@Composable
private fun SavedGameRow(name: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = name,
        color = if (selected) Color.Black else LegacyColors.SavedGameGreen,
        fontSize = LocalLegacyDimens.current.body,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) LegacyColors.SavedGameGreen else Color.Black)
            .clickable(role = Role.Button, onClick = onClick)
            // The legacy text size was accidentally scaled by the density
            // (px passed as sp), which made rows much taller: keep a
            // comfortable touch target instead.
            .heightIn(min = 40.dp)
            .padding(horizontal = 4.dp, vertical = 8.dp),
    )
}

/**
 * The platform's default (Material light) `Button` the legacy layout used
 * for Watch / Rename / Delete: grey, all caps, [LocalLegacyDimens]'
 * `bottomButtons` size at most — the label shrinks rather than being cut.
 */
@Composable
private fun DefaultButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(DefaultButtonGrey)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        FitText(
            text = text.uppercase(),
            maxFontSize = LocalLegacyDimens.current.bottomButtons,
            color = Color.Black,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            minFontSize = 6.sp,
        )
    }
}

/** `Widget.Material.Light.Button`'s background (`button_material_light`). */
private val DefaultButtonGrey = Color(0xFFD6D7D7)

/** The legacy rename dialog: title `Rename_A_Saved_Game`, message `Enter_A_Name`, pre-filled field. */
@Composable
private fun RenameDialog(draft: String, error: Boolean, onAction: (SavedGamesAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(SavedGamesAction.RenameDismissed) },
        title = { Text(stringResource(R.string.Rename_A_Saved_Game)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.Enter_A_Name))
                OutlinedTextField(
                    value = draft,
                    onValueChange = { onAction(SavedGamesAction.RenameDraftChanged(it)) },
                    isError = error,
                    supportingText = if (error) {
                        { Text(stringResource(R.string.name_already_used)) }
                    } else {
                        null
                    },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAction(SavedGamesAction.RenameConfirmed) },
                enabled = draft.isNotBlank(),
            ) { Text(stringResource(R.string.Confirm)) }
        },
        dismissButton = {
            TextButton(onClick = { onAction(SavedGamesAction.RenameDismissed) }) { Text(stringResource(R.string.Cancel)) }
        },
    )
}
