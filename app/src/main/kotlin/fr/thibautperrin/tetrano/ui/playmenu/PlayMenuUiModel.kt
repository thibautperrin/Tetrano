package fr.thibautperrin.tetrano.ui.playmenu

sealed interface PlayMenuAction {
    data object TwoPlayersClicked : PlayMenuAction
    data object OnePlayerClicked : PlayMenuAction
    data object AnalyseGamesClicked : PlayMenuAction
    data object ChallengeClicked : PlayMenuAction
    data object PuzzlesClicked : PlayMenuAction
}
