package fr.thibautperrin.tetrano.engine.wingraph

import fr.thibautperrin.tetrano.engine.nn.QubicGeometry

/**
 * Canonical form of a position under the 192 board automorphisms: the image
 * (attacker, defender) that is smallest, comparing the attacker bitboards
 * first, then the defender ones, both as unsigned numbers. The empty board is
 * its own canonical form (symmetry 0), and so is any position whose canonical
 * form is itself.
 *
 * "Attacker" is the first player (the computer at the Unbeatable level), so
 * the attacker is to move iff both sides have as many rings.
 *
 * Not thread-safe: one instance per thread.
 */
class CanonicalForm {
    var attacker: Long = 0L
        private set
    var defender: Long = 0L
        private set

    /** The automorphism mapping the position onto its canonical form. */
    var symmetry: Int = 0
        private set

    fun of(attacker: Long, defender: Long): CanonicalForm {
        var bestA = attacker
        var bestD = defender
        var best = 0
        for (s in 1 until QubicGeometry.symmetryCount) {
            val a = QubicGeometry.transform(s, attacker)
            val c = java.lang.Long.compareUnsigned(a, bestA)
            if (c > 0) continue
            val d = QubicGeometry.transform(s, defender)
            if (c < 0 || java.lang.Long.compareUnsigned(d, bestD) < 0) {
                bestA = a
                bestD = d
                best = s
            }
        }
        this.attacker = bestA
        this.defender = bestD
        symmetry = best
        return this
    }

    /** The real cell of [canonicalCell], a cell of the canonical form last computed by [of]. */
    fun toReal(canonicalCell: Int): Int = INVERSE[symmetry][canonicalCell]

    companion object {
        /** `INVERSE[s][cell]` = the cell that automorphism s maps onto [cell]. */
        private val INVERSE: Array<IntArray> = Array(QubicGeometry.symmetryCount) { s ->
            val forward = QubicGeometry.symmetries[s]
            IntArray(QubicGeometry.CELLS).also { inverse ->
                for (cell in 0 until QubicGeometry.CELLS) inverse[forward[cell]] = cell
            }
        }
    }
}
