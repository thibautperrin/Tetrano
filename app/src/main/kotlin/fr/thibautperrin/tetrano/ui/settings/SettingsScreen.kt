package fr.thibautperrin.tetrano.ui.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.thibautperrin.tetrano.R
import fr.thibautperrin.tetrano.ui.components.LegacyTopBar
import fr.thibautperrin.tetrano.ui.components.RingSwatch
import fr.thibautperrin.tetrano.ui.theme.TetranoPalette
import fr.thibautperrin.tetrano.ui.theme.TetranoRingColors
import java.text.NumberFormat
import kotlin.math.roundToInt

/**
 * Wires [SettingsViewModel] to the stateless [SettingsScreen]. Back (the
 * arrow or the system gesture) saves; once saved, toasts `Settings_Saved`
 * and calls [onDone].
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
 * The settings, as three dark cards on the black page: the player's name; both ring colors, each a row of swatches next to
 * a ring drawn in the chosen color (the other player's color is shown as
 * unavailable, since both must differ); and the level-101 network's
 * thinking, the number of variations it tests per move.
 */
@Composable
fun SettingsScreen(uiModel: SettingsUiModel, onAction: (SettingsAction) -> Unit) {
    Scaffold(
        containerColor = PageColor,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            LegacyTopBar(
                title = { Text(stringResource(R.string.action_settings)) },
                navigationIcon = {
                    IconButton(onClick = { onAction(SettingsAction.SaveClicked) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PlayerCard(uiModel, onAction)
                ColorsCard(uiModel, onAction)
                NeuralCard(uiModel, onAction)
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = TetranoPalette.Panel),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = TitleColor)
            content()
        }
    }
}

@Composable
private fun PlayerCard(uiModel: SettingsUiModel, onAction: (SettingsAction) -> Unit) {
    val focusManager = LocalFocusManager.current
    SettingsCard(stringResource(R.string.settings_section_player)) {
        OutlinedTextField(
            value = uiModel.userNameDraft,
            onValueChange = { onAction(SettingsAction.UserNameChanged(it)) },
            label = { Text(stringResource(R.string.User_Name)) },
            placeholder = { Text(uiModel.currentUserName.ifBlank { stringResource(R.string.User) }) },
            singleLine = true,
            leadingIcon = { RingSwatch(Color(uiModel.userColor), size = 28.dp) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ColorsCard(uiModel: SettingsUiModel, onAction: (SettingsAction) -> Unit) {
    SettingsCard(stringResource(R.string.settings_section_colors)) {
        ColorChoice(
            label = stringResource(R.string.settings_your_rings),
            selected = uiModel.userColor,
            unavailable = uiModel.adversaryColor,
            onSelected = { onAction(SettingsAction.UserColorSelected(it)) },
        )
        ColorChoice(
            label = stringResource(R.string.settings_opponent_rings),
            selected = uiModel.adversaryColor,
            unavailable = uiModel.userColor,
            onSelected = { onAction(SettingsAction.AdversaryColorSelected(it)) },
        )
    }
}

/** A label with a ring in the [selected] color, above the 8 swatches of the legacy palette. */
@Composable
private fun ColorChoice(label: String, selected: Int, unavailable: Int, onSelected: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RingSwatch(Color(selected))
            Spacer(Modifier.size(10.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, color = BodyColor)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (color in TetranoRingColors.selectable) {
                val argb = color.toArgb()
                Swatch(
                    color = color,
                    isSelected = argb == selected,
                    isUnavailable = argb == unavailable,
                    onClick = { onSelected(argb) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Swatch(color: Color, isSelected: Boolean, isUnavailable: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val description = stringResource(if (isUnavailable) R.string.settings_color_unavailable else R.string.settings_color)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .widthIn(max = 48.dp)
            .aspectRatio(1f)
            .alpha(if (isUnavailable) 0.25f else 1f)
            .clip(CircleShape)
            .border(BorderStroke(if (isSelected) 3.dp else 1.dp, if (isSelected) TitleColor else TetranoPalette.Outline), CircleShape)
            .padding(if (isSelected) 4.dp else 1.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(enabled = !isUnavailable, role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = description
                this.selected = isSelected
            },
    ) {
        if (isSelected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun NeuralCard(uiModel: SettingsUiModel, onAction: (SettingsAction) -> Unit) {
    val choices = SettingsUiModel.SIMULATION_CHOICES
    val index = choices.indexOfFirst { it >= uiModel.neuralSimulations }.takeIf { it >= 0 } ?: choices.lastIndex
    val format = NumberFormat.getIntegerInstance()
    SettingsCard(stringResource(R.string.settings_section_neural)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.settings_neural_simulations),
                style = MaterialTheme.typography.bodyLarge,
                color = BodyColor,
                modifier = Modifier.weight(1f),
            )
            Text(
                format.format(choices[index]),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TitleColor,
            )
        }
        Slider(
            value = index.toFloat(),
            onValueChange = { onAction(SettingsAction.NeuralSimulationsSelected(choices[it.roundToInt()])) },
            valueRange = 0f..choices.lastIndex.toFloat(),
            steps = choices.size - 2,
            colors = SliderDefaults.colors(
                thumbColor = TitleColor,
                activeTrackColor = TitleColor,
                activeTickColor = TetranoPalette.OnAccent,
                inactiveTrackColor = TetranoPalette.PanelRaised,
                inactiveTickColor = TitleColor,
            ),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(format.format(choices.first()), fontSize = 12.sp, color = HintColor, modifier = Modifier.weight(1f))
            Text(format.format(choices.last()), fontSize = 12.sp, color = HintColor)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            stringResource(R.string.settings_neural_explanation, SettingsUiModel.DEFAULT_NEURAL_SIMULATIONS),
            style = MaterialTheme.typography.bodyMedium,
            color = HintColor,
        )
    }
}

private val PageColor = TetranoPalette.Page
private val TitleColor = TetranoPalette.Accent
private val BodyColor = TetranoPalette.Text
private val HintColor = TetranoPalette.TextSecondary
