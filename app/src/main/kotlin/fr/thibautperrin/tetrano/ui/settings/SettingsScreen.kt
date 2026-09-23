package fr.thibautperrin.tetrano.ui.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.times
import androidx.hilt.navigation.compose.hiltViewModel
import fr.thibautperrin.tetrano.R
import fr.thibautperrin.tetrano.ui.components.ColorBlobRow
import fr.thibautperrin.tetrano.ui.components.FitText
import fr.thibautperrin.tetrano.ui.components.LegacyButton
import fr.thibautperrin.tetrano.ui.components.LegacyTopBar
import fr.thibautperrin.tetrano.ui.theme.LegacyColors
import fr.thibautperrin.tetrano.ui.theme.LegacyDimens
import fr.thibautperrin.tetrano.ui.theme.LocalLegacyDimens

/**
 * Wires [SettingsViewModel] to the stateless [SettingsScreen]. Confirm and
 * Back both save; once saved, toasts `Settings_Saved` and calls [onDone]
 * (`boutonValider` → `finish()`, `onBackPressed`).
 */
@Composable
fun SettingsRoute(onDone: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val uiModel by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val savedMessage = stringResource(R.string.Settings_Saved)
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(viewModel) {
        viewModel.saved.collect {
            Toast.makeText(context, savedMessage, Toast.LENGTH_SHORT).show()
            currentOnDone()
        }
    }
    BackHandler { viewModel.onAction(SettingsAction.SaveClicked) }
    SettingsScreen(uiModel = uiModel, onAction = viewModel::onAction)
}

/**
 * Ports `SettingsActivity` (see docs/GLOSSARY.md, docs/PLAN.md P8) and its
 * `activity_settings` layout, in a scrolling column under the dark action
 * bar (`action_settings`):
 * - "User name" label + a 10-ems field — label, text and hint (the saved
 *   name) in the user's color;
 * - "User color" / "Adversary color" titles in their respective colors,
 *   each above a row of 8 [ColorBlobRow] blots;
 * - the yellow Confirm button with blue text.
 *
 * The blots take the full width like the legacy `button_tache_style`,
 * unless that would push the page past the window's height (landscape):
 * they then shrink, down to a touchable size, before the page scrolls.
 */
@Composable
fun SettingsScreen(uiModel: SettingsUiModel, onAction: (SettingsAction) -> Unit) {
    val dimens = LocalLegacyDimens.current
    val userColor = Color(uiModel.userColor)
    val adversaryColor = Color(uiModel.adversaryColor)

    Scaffold(
        containerColor = LegacyColors.Page,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { LegacyTopBar(title = stringResource(R.string.action_settings)) },
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
            val contentWidth = (maxWidth - 2 * dimens.screenMargin).coerceAtLeast(0.dp)
            val blobSize = blobSize(contentWidth, maxHeight, dimens)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(dimens.screenMargin),
            ) {
                UserNameRow(
                    draft = uiModel.userNameDraft,
                    hint = uiModel.currentUserName.ifBlank { stringResource(R.string.User) },
                    color = userColor,
                    fieldWidth = min(tenEms(dimens.body), contentWidth * 0.6f),
                    onValueChange = { onAction(SettingsAction.UserNameChanged(it)) },
                )
                ColorTitle(stringResource(R.string.User_Color), userColor)
                ColorBlobRow(
                    onColorSelected = { onAction(SettingsAction.UserColorSelected(it.toArgb())) },
                    modifier = Modifier.width(blobSize * BLOB_COUNT).align(Alignment.CenterHorizontally),
                )
                ColorTitle(stringResource(R.string.Adversary_Color), adversaryColor)
                ColorBlobRow(
                    onColorSelected = { onAction(SettingsAction.AdversaryColorSelected(it.toArgb())) },
                    modifier = Modifier.width(blobSize * BLOB_COUNT).align(Alignment.CenterHorizontally),
                )
                LegacyButton(
                    text = stringResource(R.string.Confirm),
                    onClick = { onAction(SettingsAction.SaveClicked) },
                    textColor = LegacyColors.TextBlue,
                    fontSize = dimens.title3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = dimens.tinyMargin),
                )
            }
        }
    }
}

/** `TextNomUtilisateur` + `editTextNomUtilisateur`. */
@Composable
private fun UserNameRow(
    draft: String,
    hint: String,
    color: Color,
    fieldWidth: Dp,
    onValueChange: (String) -> Unit,
) {
    val dimens = LocalLegacyDimens.current
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Row(verticalAlignment = Alignment.CenterVertically) {
        FitText(
            text = stringResource(R.string.User_Name),
            maxFontSize = dimens.body,
            color = color,
            maxLines = 2,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(modifier = Modifier.width(8.dp))
        BasicTextField(
            value = draft,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = color, fontSize = dimens.body),
            cursorBrush = SolidColor(color),
            interactionSource = interactionSource,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier.width(fieldWidth),
            decorationBox = { innerTextField ->
                Column {
                    Box(modifier = Modifier.padding(vertical = 8.dp)) {
                        // setHint(nomUtilisateur) + setHintTextColor(couleurUtilisateur).
                        if (draft.isEmpty()) FitText(text = hint, maxFontSize = dimens.body, color = color)
                        innerTextField()
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (focused) 2.dp else 1.dp)
                            .background(if (focused) color else UnfocusedUnderline),
                    )
                }
            },
        )
    }
}

/** `TextCouleurUtilisateur` / `TextCouleurAdversaire`: `Taille_titre_3`, `Petite_marge` above. */
@Composable
private fun ColorTitle(text: String, color: Color) {
    val dimens = LocalLegacyDimens.current
    FitText(
        text = text,
        maxFontSize = dimens.title3,
        color = color,
        maxLines = 2,
        modifier = Modifier.padding(top = dimens.smallMargin),
    )
}

/** `android:ems="10"`: ten times the text size. */
@Composable
private fun tenEms(textSize: TextUnit): Dp = with(LocalDensity.current) { textSize.toDp() * 10 }

/**
 * A blot's side: 1/8 of the width (legacy), or less when the page would
 * otherwise overflow [availableHeight] — but never under a touchable size.
 */
@Composable
private fun blobSize(contentWidth: Dp, availableHeight: Dp, dimens: LegacyDimens): Dp {
    val density = LocalDensity.current
    fun line(size: TextUnit): Dp = with(density) { size.toDp() } * LINE_HEIGHT_FACTOR
    val fixedHeight = 2 * dimens.screenMargin +
        max(line(dimens.body) + 18.dp, 40.dp) +
        2 * (dimens.smallMargin + line(dimens.title3)) +
        dimens.tinyMargin + max(line(dimens.title3) + 12.dp, 48.dp)
    val fullWidth = contentWidth / BLOB_COUNT
    val fitting = (availableHeight - fixedHeight) / 2
    return min(fullWidth, max(fitting, MIN_BLOB_SIZE)).coerceAtLeast(0.dp)
}

private const val BLOB_COUNT = 8
private const val LINE_HEIGHT_FACTOR = 1.3f
private val MIN_BLOB_SIZE = 40.dp
private val UnfocusedUnderline = Color(0x8A000000)
