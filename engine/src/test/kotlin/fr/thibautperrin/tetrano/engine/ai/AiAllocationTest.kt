package fr.thibautperrin.tetrano.engine.ai

import com.sun.management.ThreadMXBean
import fr.thibautperrin.tetrano.engine.BoardState
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.management.ManagementFactory

/**
 * Guards the anti-GC discipline described in docs/PLAN.md §6.2: a fork
 * search must not allocate per node. If this test ever fails after a
 * change to [Ai], that change introduced an allocation somewhere in the
 * search path — look for a stray `byte[]`/`IntArray`/boxed value where a
 * scratch buffer should have been reused instead.
 *
 * The budget below is deliberately generous relative to the measured
 * value (see the class doc history / commit message for the first
 * measurement): it exists to catch a *regression*, not to chase the
 * absolute minimum.
 */
class AiAllocationTest {

    @Test
    fun bigAdvancedForkAllocatesNearNothing() {
        val board = BoardState()
        // A dense, hard-to-resolve position (from AiTest.testBigAdvancedFork3):
        // order 4 finds nothing here, so this forces a full, unshortened
        // search of the memoized subtree — a worst case for this budget.
        val moves = intArrayOf(26, 21, 15, 60, 63, 41, 12, 37, 47, 31, 14, 13)
        var player = 1
        for (ring in moves) {
            board.play(ring, player)
            player = 3 - player
        }
        val ai = Ai(board)

        // Warm up the JIT before measuring.
        repeat(200) { ai.bigAdvancedFork(1, 4) }

        val bean = ManagementFactory.getThreadMXBean() as ThreadMXBean
        @Suppress("DEPRECATION")
        val threadId = Thread.currentThread().id
        val calls = 100
        val before = bean.getThreadAllocatedBytes(threadId)
        repeat(calls) { ai.bigAdvancedFork(1, 4) }
        val after = bean.getThreadAllocatedBytes(threadId)

        val allocatedPerCall = (after - before) / calls
        assertTrue(
            "bigAdvancedFork allocated $allocatedPerCall bytes/call, budget is $BUDGET_BYTES_PER_CALL",
            allocatedPerCall < BUDGET_BYTES_PER_CALL,
        )
    }

    private companion object {
        // Measured exactly 0 B/call (JBR 21, this machine): every scratch
        // structure the search touches (LineSet, IntScratch, LongByteMap,
        // the fork-chain buffer) is pre-allocated. Budget left generous to
        // absorb JIT/JVM noise across environments while still catching a
        // genuine regression (a stray allocation per node would show up as
        // thousands of bytes/call, not a handful).
        const val BUDGET_BYTES_PER_CALL: Long = 512
    }
}
