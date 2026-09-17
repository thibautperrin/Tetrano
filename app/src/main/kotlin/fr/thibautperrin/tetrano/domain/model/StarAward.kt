package fr.thibautperrin.tetrano.domain.model

/**
 * A fact about a star just earned, for the UI to turn into a localized
 * message (the legacy `Star_Won_*` string resources) and show — reporting
 * facts rather than pre-formatted text keeps [fr.thibautperrin.tetrano.domain.service.StarService]
 * free of any Android resource dependency. See docs/GLOSSARY.md
 * (`GameLog`) and docs/PLAN.md P6.
 */
sealed interface StarAward {
    /** [count] games finished in [mode] is a milestone (`GameLog.tab`). */
    data class GamesPlayed(val mode: GameMode, val count: Int) : StarAward

    /** [count] games *won* against the computer is a milestone. */
    data class GamesWonAgainstComputer(val count: Int) : StarAward

    /**
     * Two players, vs computer and Challenge have each been tried at
     * least once. Adjusted from the legacy "all 4 modes" (which required
     * Bluetooth and online too) per docs/PLAN.md P6/§10.
     */
    data object AllModesTried : StarAward
}
