package fr.thibautperrin.tetrano.engine.puzzle

import fr.thibautperrin.tetrano.engine.nn.QubicGeometry
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * A tactics puzzle: the solver ("mine", to move) wins within [movesToWin]
 * of its moves whatever the opponent does. [solution] holds every correct
 * move at each step and the opponent's stored reply to each (the most
 * stubborn defence), so a puzzle is played without any search.
 *
 * Cells are ring numbers 0..63 (bit i of a bitboard = ring i).
 */
class Puzzle(
    val id: Int,
    /** Elo-like difficulty: a player of the same rating solves it half of the time. */
    val rating: Int,
    val mine: Long,
    val theirs: Long,
    /** The opponent's last ring (the move that led to the puzzle), or -1. */
    val lastMove: Int,
    val movesToWin: Int,
    val solution: Step,
) {
    /** The solver to move: every correct move, with what follows. */
    class Step(val branches: List<Branch>) {
        fun branch(move: Int): Branch? = branches.firstOrNull { it.move == move }

        /** Distinct complete solutions (lines of play) below this step. */
        val lineCount: Int get() = branches.sumOf { it.next?.lineCount ?: 1 }
    }

    /** A correct [move]; [reply] is the opponent's answer and [next] the next step, both absent when [move] wins. */
    class Branch(val move: Int, val reply: Int, val next: Step?)

    /** The same puzzle under board automorphism [symmetry] (see [QubicGeometry.symmetries]). */
    fun transformed(symmetry: Int): Puzzle {
        val map = QubicGeometry.symmetries[symmetry]
        fun step(s: Step): Step = Step(s.branches.map { b -> Branch(map[b.move], if (b.reply >= 0) map[b.reply] else -1, b.next?.let(::step)) })
        return Puzzle(
            id, rating,
            QubicGeometry.transform(symmetry, mine), QubicGeometry.transform(symmetry, theirs),
            if (lastMove >= 0) map[lastMove] else -1, movesToWin, step(solution),
        )
    }

    companion object {
        private const val MAGIC = 0x315A5051 // "QPZ1" little endian

        /** Reads a puzzle file written by [write]. */
        fun readAll(input: InputStream): List<Puzzle> {
            val data = DataInputStream(input.buffered())
            check(Integer.reverseBytes(data.readInt()) == MAGIC) { "not a puzzle file" }
            val count = data.readInt()
            return List(count) {
                val id = data.readInt()
                val rating = data.readShort().toInt()
                val mine = data.readLong()
                val theirs = data.readLong()
                val lastMove = data.readByte().toInt()
                val movesToWin = data.readByte().toInt()
                Puzzle(id, rating, mine, theirs, lastMove, movesToWin, readStep(data))
            }
        }

        private fun readStep(data: DataInputStream): Step {
            val count = data.readUnsignedByte()
            return Step(
                List(count) {
                    val move = data.readByte().toInt()
                    val reply = data.readByte().toInt()
                    Branch(move, reply, if (reply >= 0) readStep(data) else null)
                },
            )
        }

        /** Binary format: magic, count, then per puzzle its header and its solution tree (big endian). */
        fun write(puzzles: List<Puzzle>, output: OutputStream) {
            val bytes = ByteArrayOutputStream()
            DataOutputStream(bytes).use { out ->
                out.writeInt(Integer.reverseBytes(MAGIC))
                out.writeInt(puzzles.size)
                for (p in puzzles) {
                    out.writeInt(p.id)
                    out.writeShort(p.rating)
                    out.writeLong(p.mine)
                    out.writeLong(p.theirs)
                    out.writeByte(p.lastMove)
                    out.writeByte(p.movesToWin)
                    writeStep(p.solution, out)
                }
            }
            output.write(bytes.toByteArray())
        }

        private fun writeStep(step: Step, out: DataOutputStream) {
            require(step.branches.size in 1..255)
            out.writeByte(step.branches.size)
            for (b in step.branches) {
                out.writeByte(b.move)
                out.writeByte(b.reply)
                if (b.reply >= 0) writeStep(checkNotNull(b.next), out)
            }
        }
    }
}

/**
 * Playing a [Puzzle]: the solver's moves are checked against the stored
 * solution, the opponent's replies come from it. Not thread-safe.
 */
class PuzzleAttempt(val puzzle: Puzzle) {

    sealed interface Outcome {
        /** Correct; the opponent answers [reply]; more moves are needed. */
        data class Continue(val reply: Int) : Outcome

        /** Correct, and the line is complete. */
        data object Solved : Outcome

        /** Not a winning move (within the remaining moves); [correctMoves] were. */
        data class Wrong(val correctMoves: List<Int>) : Outcome
    }

    private var step: Puzzle.Step? = puzzle.solution

    var mine: Long = puzzle.mine
        private set
    var theirs: Long = puzzle.theirs
        private set

    /** Moves played so far, both sides, in order. */
    val played = ArrayList<Int>()

    /** Correct moves at the current step (empty once over). */
    val correctMoves: List<Int> get() = step?.branches?.map { it.move }.orEmpty()

    val isOver: Boolean get() = step == null

    /** Plays [move] for the solver. */
    fun play(move: Int): Outcome {
        val current = checkNotNull(step) { "attempt over" }
        val branch = current.branch(move)
        if (branch == null) {
            step = null
            return Outcome.Wrong(current.branches.map { it.move })
        }
        mine = mine or (1L shl move)
        played += move
        if (branch.next == null) {
            step = null
            return Outcome.Solved
        }
        theirs = theirs or (1L shl branch.reply)
        played += branch.reply
        step = branch.next
        return Outcome.Continue(branch.reply)
    }
}
