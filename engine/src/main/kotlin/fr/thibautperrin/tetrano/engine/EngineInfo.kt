package fr.thibautperrin.tetrano.engine

/**
 * Placeholder for the `:engine` module bootstrap (see docs/PLAN.md, phase P1).
 *
 * The real game engine (board state, line/fork detection, AI) is ported from
 * the legacy Java implementation in phase P2. This object only exists so the
 * module has something to compile against while the Gradle setup is being
 * validated.
 */
object EngineInfo {
    const val NAME: String = "Tetrano Engine"
}
