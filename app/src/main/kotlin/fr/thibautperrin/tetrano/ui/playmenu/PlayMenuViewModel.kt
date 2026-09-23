package fr.thibautperrin.tetrano.ui.playmenu

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** Backs [PlayMenuScreen] — replaces `JouerActivity` (see docs/GLOSSARY.md, docs/PLAN.md P8). */
@HiltViewModel
class PlayMenuViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(PlayMenuUiModel())
    val uiState: StateFlow<PlayMenuUiModel> = _uiState.asStateFlow()
}
