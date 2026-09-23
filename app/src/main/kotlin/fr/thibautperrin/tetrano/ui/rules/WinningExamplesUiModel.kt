package fr.thibautperrin.tetrano.ui.rules

import fr.thibautperrin.tetrano.ui.components.BoardVisualState

/**
 * Which winning-line geometry each of the 6 static boards demonstrates —
 * ports `ExamplesActivity`'s 6 boards (see docs/GLOSSARY.md, docs/PLAN.md
 * P8's table, and §1's `ring = x + 4y + 16*size`). Inferred from each
 * example's 4 ring numbers rather than looked up in
 * [fr.thibautperrin.tetrano.engine.BoardTables], since docs/PLAN.md P8's
 * table already lists the winning rings directly:
 *
 * - 48/53/58/63: x==y, size 3 constant -> [DIAGONAL_CONSTANT_SIZE]
 * - 40..43: y==2, size 2 constant -> [ROW_CONSTANT_SIZE]
 * - 9/25/41/57: same (x, y) = (1, 2), size 0..3 -> [CONCENTRIC]
 * - 2/22/42/62: x==2, y and size both 0..3 -> [COLUMN_INCREASING_SIZE]
 * - 12/25/38/51: x 0..3, y 3..0, size 0..3 -> [DIAGONAL_INCREASING_SIZE]
 * - 1/5/9/13: x==1, size 0 constant -> [COLUMN_CONSTANT_SIZE]
 */
enum class WinningExampleKind {
    DIAGONAL_CONSTANT_SIZE,
    ROW_CONSTANT_SIZE,
    CONCENTRIC,
    COLUMN_INCREASING_SIZE,
    DIAGONAL_INCREASING_SIZE,
    COLUMN_CONSTANT_SIZE,
}

data class WinningExampleUiModel(val kind: WinningExampleKind, val board: BoardVisualState)

data class WinningExamplesUiModel(val examples: List<WinningExampleUiModel> = emptyList())
