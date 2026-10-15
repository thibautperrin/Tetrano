package fr.thibautperrin.tetrano.engine.puzzle

import fr.thibautperrin.tetrano.engine.nn.QubicGeometry
import fr.thibautperrin.tetrano.engine.nn.Vcf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random

class PuzzleTest {

    private fun bits(vararg cells: Int) = cells.fold(0L) { acc, c -> acc or (1L shl c) }

    @Test
    fun immediateWinAndDoubleThreat() {
        val solver = WinSolver()
        // Row 0..3 holds 0, 1 and column 3, 7, 11, 15 holds 7, 11: playing 3 threatens 2 and 15.
        val p = bits(0, 1, 7, 11)
        val o = bits(60, 62, 48)
        assertFalse(solver.winIn(p, o, 1))
        assertTrue(solver.winIn(p, o, 2))
        assertEquals(2, solver.minimalWin(p, o, 5))
        assertTrue(solver.winningMoves(p, o, 2) and bits(3) != 0L)
        assertEquals(1, solver.minimalWin(bits(0, 1, 2), o, 5))
        assertEquals(bits(3), solver.winningMoves(bits(0, 1, 2), o, 1))
    }

    @Test
    fun opponentThreatMustBeBlocked() {
        val solver = WinSolver()
        // p could make the double threat at 3, but o threatens 63 first: p must block.
        val p = bits(0, 1, 7, 11)
        val o = bits(60, 61, 62)
        assertFalse(solver.winIn(p, o, 2))
        assertEquals(0L, solver.winningMoves(p, o, 2))
    }

    @Test
    fun everyVcfIsFoundAtItsLength() {
        val rng = Random(3)
        val vcf = Vcf(16)
        val solver = WinSolver()
        var checked = 0
        repeat(3000) {
            var p = 0L
            var o = 0L
            repeat(8 + rng.nextInt(10)) { k ->
                val free = (p or o).inv()
                var cell: Int
                do cell = rng.nextInt(64) while (free and (1L shl cell) == 0L)
                if (k % 2 == 0) p = p or (1L shl cell) else o = o or (1L shl cell)
            }
            if (fr.thibautperrin.tetrano.engine.nn.Tactics.threats(o, p) != 0L) return@repeat
            if (vcf.shortest(p, o, 100_000) >= 0 && vcf.shortestLength in 1..4) {
                solver.clear()
                assertTrue(solver.winIn(p, o, vcf.shortestLength))
                checked++
            }
        }
        assertTrue("only $checked positions checked", checked > 20)
    }

    private fun samplePuzzle(): Puzzle {
        // Play 3 (double threat 2 and 15); the opponent blocks 2; 15 wins.
        val solution = Puzzle.Step(
            listOf(Puzzle.Branch(3, 2, Puzzle.Step(listOf(Puzzle.Branch(15, -1, null))))),
        )
        return Puzzle(7, 1234, bits(0, 1, 7, 11), bits(60, 62, 48), 48, 2, solution)
    }

    @Test
    fun codecRoundTrip() {
        val out = ByteArrayOutputStream()
        Puzzle.write(listOf(samplePuzzle(), samplePuzzle().transformed(5)), out)
        val read = Puzzle.readAll(ByteArrayInputStream(out.toByteArray()))
        assertEquals(2, read.size)
        val p = read[0]
        assertEquals(7, p.id)
        assertEquals(1234, p.rating)
        assertEquals(bits(0, 1, 7, 11), p.mine)
        assertEquals(48, p.lastMove)
        assertEquals(1, p.solution.lineCount)
        assertEquals(15, p.solution.branches[0].next!!.branches[0].move)
        val t = read[1]
        val map = QubicGeometry.symmetries[5]
        assertEquals(QubicGeometry.transform(5, bits(0, 1, 7, 11)), t.mine)
        assertEquals(map[3], t.solution.branches[0].move)
        assertEquals(map[2], t.solution.branches[0].reply)
    }

    @Test
    fun attemptFollowsTheSolution() {
        val attempt = PuzzleAttempt(samplePuzzle())
        assertEquals(PuzzleAttempt.Outcome.Continue(2), attempt.play(3))
        assertEquals(listOf(15), attempt.correctMoves)
        assertEquals(PuzzleAttempt.Outcome.Solved, attempt.play(15))
        assertTrue(attempt.isOver)

        val wrong = PuzzleAttempt(samplePuzzle())
        assertEquals(PuzzleAttempt.Outcome.Wrong(listOf(3)), wrong.play(20))
        assertTrue(wrong.isOver)
    }
}
