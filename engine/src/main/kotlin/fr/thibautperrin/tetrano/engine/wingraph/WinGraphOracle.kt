package fr.thibautperrin.tetrano.engine.wingraph

import fr.thibautperrin.tetrano.engine.nn.Tactics

/**
 * Exact values for the analysis from the proven winning graphs: as long as the first player of a
 * game has followed the strategy of one of the graphs ([UnbeatablePlayer]'s rules: its first ring
 * on one of `firstRings`, then at each turn a move equivalent to [WinRules.decide]'s), every
 * position of the game is on that graph's verified walk: won for the first player whatever the
 * second does. Costs nothing but a few VCF calls; covers the opening, where nothing else is exact.
 * Not thread-safe.
 */
class WinGraphOracle(private val graphs: List<Pair<WinBook, Long>>) {
    private val form = CanonicalForm()
    private val other = CanonicalForm()
    private val vcf = WinRules.newVcf()

    private var cachedMoves: ByteArray? = null
    private var cachedUntil = 0
    private var cachedGraph = -1

    /**
     * For the position after the first [count] of [moves]: null if not proven by a graph, else
     * (true, the graph's move) when the first player is to move (won for the side to move) or
     * (false, -1) when the second is (lost for the side to move).
     */
    fun lookup(moves: ByteArray, count: Int): Pair<Boolean, Int>? {
        if (count == 0) return null // the empty board: the first move is the player's choice
        val (graph, until) = followed(moves)
        if (graph < 0 || count > until) return null
        if (count % 2 == 1) return false to -1
        val (first, second) = boards(moves, count)
        val book = graphs[graph].first
        form.of(first, second)
        val decision = WinRules.decide(form.attacker, form.defender, book, vcf)
        if (decision == WinRules.NONE) return null
        return true to form.toReal(WinRules.move(decision))
    }

    /** (graph index, number of moves while the first player follows it) for [moves], -1 if none. */
    private fun followed(moves: ByteArray): Pair<Int, Int> {
        if (cachedMoves?.contentEquals(moves) == true) return cachedGraph to cachedUntil
        var bestGraph = -1
        var bestUntil = 0
        if (moves.isNotEmpty()) {
            for ((g, graph) in graphs.withIndex()) {
                val (book, firstRings) = graph
                if ((firstRings ushr moves[0].toInt()) and 1L == 0L) continue
                var until = moves.size
                var i = 2
                while (i < moves.size) {
                    val (first, second) = boards(moves, i)
                    form.of(first, second)
                    val decision = WinRules.decide(form.attacker, form.defender, book, vcf)
                    if (decision == WinRules.NONE) {
                        until = i
                        break
                    }
                    val chosen = form.toReal(WinRules.move(decision))
                    val played = moves[i].toInt()
                    if (played != chosen) {
                        // Equivalent by a symmetry of the position?
                        other.of(first or (1L shl played), second)
                        form.of(first or (1L shl chosen), second)
                        if (other.attacker != form.attacker || other.defender != form.defender) {
                            until = i
                            break
                        }
                    }
                    if (Tactics.wonWith(first or (1L shl played), played)) break
                    i += 2
                }
                if (until > bestUntil) {
                    bestUntil = until
                    bestGraph = g
                }
            }
        }
        cachedMoves = moves.copyOf()
        cachedGraph = bestGraph
        cachedUntil = bestUntil
        return bestGraph to bestUntil
    }

    private fun boards(moves: ByteArray, count: Int): Pair<Long, Long> {
        var first = 0L
        var second = 0L
        for (i in 0 until count) {
            if (i % 2 == 0) first = first or (1L shl moves[i].toInt()) else second = second or (1L shl moves[i].toInt())
        }
        return first to second
    }
}
