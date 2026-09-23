package fr.thibautperrin.tetrano.ui.rules

/**
 * Ports `NoticeFormelleActivity` (see docs/GLOSSARY.md, docs/PLAN.md P8) —
 * its rules text (`Long_Sentence_Rules`) is entirely static, so this
 * model carries no state beyond the split's own convention (a
 * [RulesViewModel] exposing it as a `StateFlow`, matching the other P8
 * screens).
 */
data object RulesUiModel

sealed interface RulesAction {
    data object WinningExamplesClicked : RulesAction
}
