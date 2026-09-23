package fr.thibautperrin.tetrano.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.thibautperrin.tetrano.domain.service.StarService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Backs [HomeScreen] — replaces `MainActivity` (see docs/GLOSSARY.md, docs/PLAN.md P8). */
@HiltViewModel
class HomeViewModel @Inject constructor(
    starService: StarService,
) : ViewModel() {
    val uiState: StateFlow<HomeUiModel> = starService.numberStars
        .map { HomeUiModel(numberStars = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiModel())
}
