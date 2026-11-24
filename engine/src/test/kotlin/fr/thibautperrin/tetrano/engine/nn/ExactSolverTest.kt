package fr.thibautperrin.tetrano.engine.nn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ExactSolverTest {

    /** A random position after [plies] moves with no line completed and no immediate win for the side to move. */
    private fun randomPosition(rng: Random, plies: Int): Pair<Long, Long>? {
        var mine = 0L
        var theirs = 0L
        repeat(plies) {
            val free = (mine or theirs).inv()
            var bits = free
            var k = rng.nextInt(java.lang.Long.bitCount(free))
            while (k-- > 0) bits = bits and (bits - 1)
            val cell = java.lang.Long.numberOfTrailingZeros(bits)
            val after = mine or (1L shl cell)
            if (Tactics.wonWith(after, cell)) return null
            mine = theirs
            theirs = after
        }
        if (Tactics.threats(mine, theirs) != 0L) return null
        return mine to theirs
    }

    private fun opponentView(o: ExactSolver.Outcome) = when (o) {
        ExactSolver.Outcome.WIN -> ExactSolver.Outcome.LOSS
        ExactSolver.Outcome.LOSS -> ExactSolver.Outcome.WIN
        else -> o
    }

    @Test
    fun `values agree with the minimax of the children`() {
        val rng = Random(7)
        val solver = ExactSolver(log2Entries = 18)
        val check = ExactSolver(log2Entries = 18)
        var checked = 0
        var outcomes = HashSet<ExactSolver.Outcome>()
        while (checked < 60) {
            val (mine, theirs) = randomPosition(rng, 26 + rng.nextInt(30)) ?: continue
            val a = solver.solve(mine, theirs, 200_000)
            if (a.outcome == ExactSolver.Outcome.UNKNOWN) continue
            outcomes += a.outcome
            // Children, seen from the side to move here.
            val children = ArrayList<ExactSolver.Outcome>()
            var free = (mine or theirs).inv()
            while (free != 0L) {
                val c = java.lang.Long.numberOfTrailingZeros(free)
                free = free and (free - 1)
                val after = mine or (1L shl c)
                children += when {
                    Tactics.wonWith(after, c) -> ExactSolver.Outcome.WIN
                    (after or theirs) == -1L -> ExactSolver.Outcome.DRAW
                    else -> opponentView(check.solve(theirs, after, 200_000, findMove = false).outcome)
                }
            }
            val known = children.filter { it != ExactSolver.Outcome.UNKNOWN }
            when (a.outcome) {
                ExactSolver.Outcome.WIN -> {
                    assertTrue(ExactSolver.Outcome.WIN in children || ExactSolver.Outcome.UNKNOWN in children)
                    val after = mine or (1L shl a.move)
                    assertTrue("winning move", Tactics.wonWith(after, a.move) ||
                        check.solve(theirs, after, 400_000, findMove = false).outcome == ExactSolver.Outcome.LOSS)
                }
                ExactSolver.Outcome.LOSS -> assertTrue(known.all { it == ExactSolver.Outcome.LOSS })
                ExactSolver.Outcome.DRAW -> {
                    assertTrue(ExactSolver.Outcome.WIN !in known)
                    assertNotEquals(-1, a.move)
                    val after = mine or (1L shl a.move)
                    val reply = if ((after or theirs) == -1L) ExactSolver.Outcome.DRAW else check.solve(theirs, after, 400_000, findMove = false).outcome
                    assertTrue("holding move", reply == ExactSolver.Outcome.DRAW || reply == ExactSolver.Outcome.UNKNOWN)
                }
                else -> {}
            }
            checked++
        }
        assertTrue("wins and losses seen: $outcomes", ExactSolver.Outcome.WIN in outcomes && ExactSolver.Outcome.LOSS in outcomes)
    }

    /** A full board, 32 rings each, with no line of a single colour (hill climbing on swaps). */
    private fun drawnBoard(rng: Random): Long {
        var first = 0L
        val cells = (0 until 64).shuffled(rng)
        for (c in cells.take(32)) first = first or (1L shl c)
        fun bad(f: Long) = QubicGeometry.lineMasks.count { (it and f) == it || (it and f) == 0L }
        var score = bad(first)
        while (score > 0) {
            val a = rng.nextInt(64)
            val b = rng.nextInt(64)
            if (((first ushr a) and 1L) == ((first ushr b) and 1L)) continue
            val f = first xor (1L shl a) xor (1L shl b)
            val s = bad(f)
            if (s <= score) {
                first = f
                score = s
            }
        }
        return first
    }

    @Test
    fun `draws are proven with a move holding them`() {
        val rng = Random(5)
        val solver = ExactSolver(log2Entries = 16)
        var draws = 0
        var tries = 0
        val seen = ArrayList<ExactSolver.Outcome>()
        while (draws < 5 && tries++ < 200) {
            val full = drawnBoard(rng)
            var first = full
            var second = full.inv()
            // Take back 6..12 rings of each (the first player then to move).
            val k = 3 + rng.nextInt(6)
            fun removeRandom(bits: Long): Long {
                var b = bits
                var i = rng.nextInt(java.lang.Long.bitCount(b))
                while (i-- > 0) b = b and (b - 1)
                return bits and java.lang.Long.lowestOneBit(b).inv()
            }
            repeat(k) { first = removeRandom(first) }
            repeat(k) { second = removeRandom(second) }
            if (Tactics.threats(first, second) != 0L) continue
            val a = solver.solve(first, second, 200_000)
            seen += a.outcome
            if (a.outcome != ExactSolver.Outcome.DRAW) continue
            assertNotEquals(-1, a.move)
            val after = first or (1L shl a.move)
            val reply = ExactSolver(log2Entries = 16).solve(second, after, 200_000, findMove = false).outcome
            assertTrue("holding move", reply == ExactSolver.Outcome.DRAW)
            draws++
        }
        assertEquals("outcomes: ${seen.groupingBy { it }.eachCount()}", 5, draws)
    }

    @Test
    fun `solving from either side gives mirrored answers`() {
        val rng = Random(11)
        val s1 = ExactSolver(log2Entries = 16)
        var n = 0
        while (n < 30) {
            val (mine, theirs) = randomPosition(rng, 30 + rng.nextInt(10)) ?: continue
            if (Tactics.threats(theirs, mine) != 0L) continue
            val a = s1.solve(mine, theirs, 100_000, findMove = false)
            val b = ExactSolver(log2Entries = 16).solve(mine, theirs, 100_000, opponentFirst = true, findMove = false)
            if (a.outcome != ExactSolver.Outcome.UNKNOWN && b.outcome != ExactSolver.Outcome.UNKNOWN) {
                assertEquals(a.outcome, b.outcome)
                n++
            }
        }
    }
}
