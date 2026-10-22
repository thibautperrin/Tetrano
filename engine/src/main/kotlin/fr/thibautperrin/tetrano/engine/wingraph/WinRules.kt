package fr.thibautperrin.tetrano.engine.wingraph

import fr.thibautperrin.tetrano.engine.nn.QubicGeometry.lowestCell
import fr.thibautperrin.tetrano.engine.nn.Tactics
import fr.thibautperrin.tetrano.engine.nn.Vcf

/**
 * How the Unbeatable player chooses its move, on a canonical position
 * ([CanonicalForm]) with the attacker (first player) to move. The winning
 * graph generator (`:wingraph`), its verifier and the app all call this one
 * function, so a graph proven with it is played exactly as proven, on any
 * device: the rules depend on nothing but the position and the graph.
 *
 * In order:
 * 1. complete a line ([WIN]);
 * 2. block the defender's line of 3 ([BLOCK]);
 * 3. play the graph's move for this position ([BOOK]);
 * 4. play the first move of a VCF found by a cleared [Vcf] of
 *    [VCF_TABLE_LOG2] slots within [VCF_BUDGET] nodes ([VCF]).
 *
 * Rule 4 is deterministic because the table is cleared first and the budget
 * counts nodes, not time. Rules 1, 2 and 4 cost no graph node: the graph only
 * holds the positions where they do not apply.
 */
object WinRules {
    const val VCF_TABLE_LOG2: Int = 16
    const val VCF_BUDGET: Int = 20_000

    const val WIN: Int = 1
    const val BLOCK: Int = 2
    const val BOOK: Int = 3
    const val VCF: Int = 4

    /** No rule applies: the position is outside the graph. */
    const val NONE: Int = -1

    /** The graph: move of a canonical attacker-to-move position, or -1 if absent. */
    fun interface Book {
        fun move(attacker: Long, defender: Long): Int
    }

    fun newVcf(): Vcf = Vcf(VCF_TABLE_LOG2)

    /** The rule's move (a canonical cell); see [decide]. */
    fun move(decision: Int): Int = decision and 0xFF

    /** The rule that chose; see [decide]. */
    fun rule(decision: Int): Int = decision ushr 8

    /**
     * The attacker's move in canonical position ([attacker], [defender]), as
     * `rule shl 8 or cell` (read it with [rule] and [move]), or [NONE].
     * [vcf] must come from [newVcf]; it is cleared before use.
     */
    fun decide(attacker: Long, defender: Long, book: Book?, vcf: Vcf): Int {
        val win = Tactics.threats(attacker, defender)
        if (win != 0L) return WIN shl 8 or lowestCell(win)
        val block = Tactics.threats(defender, attacker)
        if (block != 0L) return BLOCK shl 8 or lowestCell(block)
        if (book != null) {
            val m = book.move(attacker, defender)
            if (m >= 0) return BOOK shl 8 or m
        }
        val m = vcfMove(attacker, defender, vcf)
        return if (m >= 0) VCF shl 8 or m else NONE
    }

    /** Rule 4 alone: the deterministic VCF move, or -1. */
    fun vcfMove(attacker: Long, defender: Long, vcf: Vcf): Int {
        vcf.clear()
        val m = vcf.solve(attacker, defender, VCF_BUDGET)
        return if (m >= 0) m else -1
    }
}
