package fr.thibautperrin.tetrano.domain.model

/**
 * Which kind of game [fr.thibautperrin.tetrano.domain.service.GameSessionService]
 * is running, and which per-mode counter a finished game counts towards
 * in [fr.thibautperrin.tetrano.domain.service.StarService]. Ports
 * `StaticFields.GAME_*` (see docs/GLOSSARY.md).
 *
 * [BLUETOOTH] is gone (see docs/PLAN.md §10) and [ONLINE] isn't
 * implemented yet — both values are kept only so a legacy stats counter
 * imported by [fr.thibautperrin.tetrano.data.legacy.LegacyDataImporter]
 * stays meaningful; nothing in this codebase can currently start a game
 * in either mode.
 */
enum class GameMode(val legacyCode: Int) {
    TWO_PLAYERS(1),
    VS_COMPUTER(2),
    BLUETOOTH(3),
    ONLINE(4),
}
