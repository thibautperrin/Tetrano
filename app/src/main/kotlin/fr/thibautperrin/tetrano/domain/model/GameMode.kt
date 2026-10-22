package fr.thibautperrin.tetrano.domain.model

/**
 * Which kind of game [fr.thibautperrin.tetrano.domain.service.GameSessionService]
 * is running. Ports `StaticFields.GAME_*` (see docs/GLOSSARY.md), minus
 * Bluetooth and online play, which the rewrite dropped.
 */
enum class GameMode {
    TWO_PLAYERS,
    VS_COMPUTER,
}
