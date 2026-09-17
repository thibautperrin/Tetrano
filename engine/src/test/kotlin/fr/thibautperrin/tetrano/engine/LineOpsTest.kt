package fr.thibautperrin.tetrano.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/** Ports the legacy `StatiqueAligTest` (see docs/GLOSSARY.md). */
class LineOpsTest {

    @Test
    fun testRingCountsAndLineKind() {
        var line: Byte = 0
        assertEquals(0, LineOps.ringCount1(line).toInt())
        assertEquals(0, LineOps.ringCount2(line).toInt())
        assertEquals(0, LineOps.lineKind(line).toInt())

        line = 1
        assertEquals(1, LineOps.ringCount1(line).toInt())
        assertEquals(0, LineOps.ringCount2(line).toInt())
        assertEquals(1, LineOps.lineKind(line).toInt())

        line = 2
        assertEquals(2, LineOps.ringCount1(line).toInt())
        assertEquals(0, LineOps.ringCount2(line).toInt())
        assertEquals(2, LineOps.lineKind(line).toInt())

        line = 3
        assertEquals(3, LineOps.ringCount1(line).toInt())
        assertEquals(0, LineOps.ringCount2(line).toInt())
        assertEquals(3, LineOps.lineKind(line).toInt())

        line = 4
        assertEquals(4, LineOps.ringCount1(line).toInt())
        assertEquals(0, LineOps.ringCount2(line).toInt())
        assertEquals(4, LineOps.lineKind(line).toInt())

        line = 5
        assertEquals(0, LineOps.ringCount1(line).toInt())
        assertEquals(1, LineOps.ringCount2(line).toInt())
        assertEquals(5, LineOps.lineKind(line).toInt())

        line = 6
        assertEquals(1, LineOps.ringCount1(line).toInt())
        assertEquals(1, LineOps.ringCount2(line).toInt())
        assertEquals(9, LineOps.lineKind(line).toInt())

        line = 7
        assertEquals(2, LineOps.ringCount1(line).toInt())
        assertEquals(1, LineOps.ringCount2(line).toInt())
        assertEquals(9, LineOps.lineKind(line).toInt())

        line = 8
        assertEquals(3, LineOps.ringCount1(line).toInt())
        assertEquals(1, LineOps.ringCount2(line).toInt())
        assertEquals(9, LineOps.lineKind(line).toInt())

        line = 10
        assertEquals(0, LineOps.ringCount1(line).toInt())
        assertEquals(2, LineOps.ringCount2(line).toInt())
        assertEquals(6, LineOps.lineKind(line).toInt())

        line = 11
        assertEquals(1, LineOps.ringCount1(line).toInt())
        assertEquals(2, LineOps.ringCount2(line).toInt())
        assertEquals(9, LineOps.lineKind(line).toInt())

        line = 12
        assertEquals(2, LineOps.ringCount1(line).toInt())
        assertEquals(2, LineOps.ringCount2(line).toInt())
        assertEquals(9, LineOps.lineKind(line).toInt())

        line = 15
        assertEquals(0, LineOps.ringCount1(line).toInt())
        assertEquals(3, LineOps.ringCount2(line).toInt())
        assertEquals(7, LineOps.lineKind(line).toInt())

        line = 16
        assertEquals(1, LineOps.ringCount1(line).toInt())
        assertEquals(3, LineOps.ringCount2(line).toInt())
        assertEquals(9, LineOps.lineKind(line).toInt())

        line = 20
        assertEquals(0, LineOps.ringCount1(line).toInt())
        assertEquals(4, LineOps.ringCount2(line).toInt())
        assertEquals(8, LineOps.lineKind(line).toInt())
    }

    @Test
    fun testPlayUndoLine() {
        val board = BoardState()
        val line = 42

        assertEquals(0, LineOps.ringCount1(board.lines[line]).toInt())
        assertEquals(0, LineOps.ringCount2(board.lines[line]).toInt())

        LineOps.playLine(board, line, 1)
        assertEquals(1, LineOps.ringCount1(board.lines[line]).toInt())
        assertEquals(0, LineOps.ringCount2(board.lines[line]).toInt())

        LineOps.undoLine(board, line, 1)
        assertEquals(0, LineOps.ringCount1(board.lines[line]).toInt())
        assertEquals(0, LineOps.ringCount2(board.lines[line]).toInt())

        LineOps.playLine(board, line, 1)
        assertEquals(1, LineOps.ringCount1(board.lines[line]).toInt())
        assertEquals(0, LineOps.ringCount2(board.lines[line]).toInt())

        LineOps.playLine(board, line, 1)
        assertEquals(2, LineOps.ringCount1(board.lines[line]).toInt())
        assertEquals(0, LineOps.ringCount2(board.lines[line]).toInt())

        LineOps.undoLine(board, line, 1)
        assertEquals(1, LineOps.ringCount1(board.lines[line]).toInt())
        assertEquals(0, LineOps.ringCount2(board.lines[line]).toInt())

        LineOps.undoLine(board, line, 1)
        assertEquals(0, LineOps.ringCount1(board.lines[line]).toInt())
        assertEquals(0, LineOps.ringCount2(board.lines[line]).toInt())

        LineOps.playLine(board, line, 1)
        assertEquals(1, LineOps.ringCount1(board.lines[line]).toInt())
        assertEquals(0, LineOps.ringCount2(board.lines[line]).toInt())

        LineOps.playLine(board, line, 2)
        assertEquals(1, LineOps.ringCount1(board.lines[line]).toInt())
        assertEquals(1, LineOps.ringCount2(board.lines[line]).toInt())

        LineOps.playLine(board, line, 2)
        assertEquals(1, LineOps.ringCount1(board.lines[line]).toInt())
        assertEquals(2, LineOps.ringCount2(board.lines[line]).toInt())

        LineOps.undoLine(board, line, 1)
        assertEquals(0, LineOps.ringCount1(board.lines[line]).toInt())
        assertEquals(2, LineOps.ringCount2(board.lines[line]).toInt())

        LineOps.undoLine(board, line, 2)
        assertEquals(0, LineOps.ringCount1(board.lines[line]).toInt())
        assertEquals(1, LineOps.ringCount2(board.lines[line]).toInt())

        LineOps.undoLine(board, line, 2)
        assertEquals(0, LineOps.ringCount1(board.lines[line]).toInt())
        assertEquals(0, LineOps.ringCount2(board.lines[line]).toInt())
    }

    @Test
    fun testFindFreeRings() {
        val board = BoardState()
        board.play(0, 1)
        board.play(48, 1)
        assertEquals(16, LineOps.firstFreeRing(board, 1).toInt())
        assertEquals(32, LineOps.lastFreeRing(board, 1).toInt())
        board.play(16, 1)
        assertEquals(32, LineOps.lastRemainingRing(board, 1).toInt())
        board.restart()

        board.play(12, 1)
        board.play(28, 1)
        assertEquals(44, LineOps.firstFreeRing(board, 10).toInt())
        assertEquals(60, LineOps.lastFreeRing(board, 10).toInt())
        board.undo(12, 1)
        board.undo(28, 1)

        board.play(12, 1)
        board.play(44, 1)
        assertEquals(28, LineOps.firstFreeRing(board, 10).toInt())
        assertEquals(60, LineOps.lastFreeRing(board, 10).toInt())
        board.undo(12, 1)
        board.undo(44, 1)

        board.play(12, 1)
        board.play(60, 1)
        assertEquals(28, LineOps.firstFreeRing(board, 10).toInt())
        assertEquals(44, LineOps.lastFreeRing(board, 10).toInt())
        board.undo(12, 1)
        board.undo(60, 1)

        board.play(28, 1)
        board.play(60, 1)
        assertEquals(12, LineOps.firstFreeRing(board, 10).toInt())
        assertEquals(44, LineOps.lastFreeRing(board, 10).toInt())
        board.undo(28, 1)
        board.undo(60, 1)

        board.play(44, 1)
        board.play(60, 1)
        assertEquals(12, LineOps.firstFreeRing(board, 10).toInt())
        assertEquals(28, LineOps.lastFreeRing(board, 10).toInt())
        board.undo(44, 1)
        board.undo(60, 1)

        board.play(28, 1)
        board.play(44, 1)
        assertEquals(12, LineOps.firstFreeRing(board, 10).toInt())
        assertEquals(60, LineOps.lastFreeRing(board, 10).toInt())
        board.undo(28, 1)
        board.undo(44, 1)

        board.play(12, 1)
        board.play(28, 1)
        board.play(44, 1)
        assertEquals(60, LineOps.lastRemainingRing(board, 10).toInt())

        board.undo(44, 1)
        board.play(60, 1)
        assertEquals(44, LineOps.lastRemainingRing(board, 10).toInt())
        board.undo(28, 1)
        board.play(44, 1)
        assertEquals(28, LineOps.lastRemainingRing(board, 10).toInt())
        board.undo(12, 1)
        board.play(28, 1)
        assertEquals(12, LineOps.lastRemainingRing(board, 10).toInt())
        board.restart()
    }
}
