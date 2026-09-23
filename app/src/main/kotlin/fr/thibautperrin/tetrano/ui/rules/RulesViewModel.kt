package fr.thibautperrin.tetrano.ui.rules

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject

/** Backs [RulesScreen] — replaces `NoticeFormelleActivity` (see docs/GLOSSARY.md, docs/PLAN.md P8). */
@HiltViewModel
class RulesViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(RulesUiModel)
    val uiState: StateFlow<RulesUiModel> = _uiState.asStateFlow()

    private val _navigateToWinningExamples = Channel<Unit>(Channel.BUFFERED)
    val navigateToWinningExamples: Flow<Unit> = _navigateToWinningExamples.receiveAsFlow()

    fun onAction(action: RulesAction) {
        when (action) {
            RulesAction.WinningExamplesClicked -> _navigateToWinningExamples.trySend(Unit)
        }
    }
}
