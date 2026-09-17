package fr.thibautperrin.tetrano.engine.util

/**
 * A pool of pre-allocated [IntArray] scratch buffers, one per recursion
 * depth, so a recursive search can build a candidate list at each depth
 * without allocating (or re-copying down to an exact size) on every call.
 * See docs/PLAN.md §6.2.
 *
 * Buffers at different depths are independent: a deeper recursive call
 * filling its own buffer never disturbs a shallower call's still-in-use
 * one.
 */
class IntScratch(maxDepth: Int, capacityPerLevel: Int) {

    private val buffers: Array<IntArray> = Array(maxDepth) { IntArray(capacityPerLevel) }

    /** The scratch buffer for recursion [depth] (0-based). Never allocates. */
    fun bufferAt(depth: Int): IntArray = buffers[depth]
}
