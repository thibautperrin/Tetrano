package fr.thibautperrin.tetrano.engine.ai

/**
 * Reports incremental progress of a long-running [Ai] search: [current] of
 * [total] candidates examined so far. Used to drive a progress indicator
 * (e.g. an arc on the UI) during [Ai.blockAdvancedFork]/[Ai.bigAdvancedFork]
 * at high AI levels, where a single move can take a noticeable time to
 * compute. Ports the `Handler`-based progress messages
 * (`LABEL_INFO_FORK`/`LABEL_INFO_BLOCK_FORK`) of the legacy `IA` class —
 * see docs/PLAN.md §6.3: the `:engine` module has no Android dependency,
 * so the distinction between the two progress "phases" (searching vs.
 * blocking a fork) is left to the caller, which knows which method it
 * invoked.
 */
fun interface AiProgress {
    fun onProgress(current: Int, total: Int)
}
