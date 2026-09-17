package fr.thibautperrin.tetrano.engine.ai

/**
 * Which half of a search [AiProgress] is reporting on. Ports the two
 * distinct `Handler` message types the legacy `IA` sent
 * (`LABEL_INFO_FORK`/`LABEL_INFO_BLOCK_FORK`): [Ai.bigAdvancedFork] (and,
 * transitively, [Ai.playLevel]/[Ai.playLevelExpert] while they're inside
 * it) reports [SEARCHING_FORK]; [Ai.blockAdvancedFork] reports
 * [BLOCKING_FORK]. A caller driving a single progress indicator across a
 * whole [Ai.playLevel] call needs this tag to know which half of the
 * indicator (e.g. which 180° arc) it's currently updating — it cannot
 * otherwise tell which internal search is currently running.
 */
enum class AiSearchPhase {
    SEARCHING_FORK,
    BLOCKING_FORK,
}

/**
 * Reports incremental progress of a long-running [Ai] search: [current] of
 * [total] candidates examined so far, for the given [phase]. Used to drive
 * a progress indicator (e.g. an arc on the UI) at high AI levels, where a
 * single move can take a noticeable time to compute. Ports the
 * `Handler`-based progress messages of the legacy `IA` class — see
 * docs/PLAN.md §6.3.
 */
fun interface AiProgress {
    fun onProgress(phase: AiSearchPhase, current: Int, total: Int)
}
