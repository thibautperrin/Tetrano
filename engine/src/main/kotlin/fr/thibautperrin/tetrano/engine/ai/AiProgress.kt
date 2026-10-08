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

    /** The level-101 neural search ([fr.thibautperrin.tetrano.engine.nn.NeuralPlayer]): the whole move in one phase. */
    NEURAL_SEARCH,
}

/**
 * Reports incremental progress of a long-running [Ai] search, and lets the
 * caller ask it to stop. Used to drive a progress indicator (e.g. an arc
 * on the UI) and to make an in-progress search cancellable at high AI
 * levels, where a single move can take a noticeable time to compute.
 * Ports the `Handler`-based progress messages of the legacy `IA` class —
 * see docs/PLAN.md §6.2/§6.3.
 *
 * [Ai] has no dependency on coroutines (or anything else outside the
 * plain JVM): [isCancelled] is checked at exactly the points [onProgress]
 * is reported, so a caller that does use coroutines (see
 * `AiService` in the `:app` module) can back it with
 * `!currentCoroutineContext().isActive` without `:engine` needing to know
 * that coroutines exist.
 */
fun interface AiProgress {
    fun onProgress(phase: AiSearchPhase, current: Int, total: Int)

    /** Checked right after each [onProgress] call; true aborts the search early. */
    fun isCancelled(): Boolean = false
}
