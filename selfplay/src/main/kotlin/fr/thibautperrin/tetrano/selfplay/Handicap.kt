package fr.thibautperrin.tetrano.selfplay

import fr.thibautperrin.tetrano.engine.nn.GumbelMcts
import fr.thibautperrin.tetrano.engine.nn.Tactics
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * Strong self-play from a fixed first move, for the players' guide: how often each side wins or
 * draws when the first player is forced to open on [first] (a weak place by default).
 *
 * Usage: `handicap model=<file> first=<cell> games=N sims=N threads=N out=<file> [root_sims=N]`
 *
 * Both sides are the same network with a Gumbel MCTS of `sims` simulations per move and root
 * noise on (so that games differ, each move drawn from the search's improved policy). Writes one
 * line per game (`winner moves...`, winner 0 = first player, 1 = second, -1 = draw) and prints a
 * summary; with `root_sims`, also a deterministic deep search of the position after the first move
 * (the second player's replies with their values).
 */
object HandicapMain {
    @JvmStatic
    fun main(args: Array<String>) {
        Locale.setDefault(Locale.ROOT)
        val o = args.associate { val (k, v) = it.split("=", limit = 2); k to v }
        val net = loadNet(o["model"]) ?: error("model= required")
        val first = o["first"]?.toInt() ?: 1
        val games = o["games"]?.toInt() ?: 100
        val sims = o["sims"]?.toInt() ?: 400
        val threads = o["threads"]?.toInt() ?: 12
        val out = File(o["out"] ?: "handicap.txt")
        val seed = o["seed"]?.toLong() ?: 1L

        o["root_sims"]?.toInt()?.let { rootSims ->
            val mcts = GumbelMcts(net, GumbelMcts.Config(gumbelNoise = false), Random(seed))
            val r = mcts.search(0L, 1L shl first, rootSims)
            println("ROOT first=$first sims=${r.simulations} value(second)=${"%.3f".format(r.value)} " +
                "netWdl(second)=${r.netWdl.joinToString(",") { "%.3f".format(it) }} move=${r.move}")
            val seen = HashSet<Int>()
            (0 until 64).filter { r.moveClass[it] >= 0 }.sortedByDescending { r.moveValues[it] }.forEach { c ->
                if (seen.add(r.moveClass[c])) {
                    println("REPLY cell=$c value=${"%.3f".format(r.moveValues[c])} visits=${r.visits[c]} prior=${"%.3f".format(r.prior[c])}")
                }
            }
        }
        if (games <= 0) return

        val next = AtomicInteger(0)
        val results = IntArray(3) // first, second, draw
        val lines = arrayOfNulls<String>(games)
        val pool = Executors.newFixedThreadPool(threads)
        repeat(threads) { w ->
            pool.execute {
                val mcts = GumbelMcts(net, GumbelMcts.Config(gumbelNoise = true), Random(seed * 7919 + w))
                while (true) {
                    val g = next.getAndIncrement()
                    if (g >= games) break
                    val moves = arrayListOf(first)
                    var mine = 0L
                    var theirs = 1L shl first
                    var ply = 1
                    var winner: Int
                    while (true) {
                        val m = mcts.search(mine, theirs, sims).move
                        if (m < 0) { winner = -1; break }
                        moves += m
                        val after = mine or (1L shl m)
                        if (Tactics.wonWith(after, m)) { winner = ply % 2; break }
                        if ((after or theirs) == -1L) { winner = -1; break }
                        mine = theirs
                        theirs = after
                        ply++
                    }
                    synchronized(results) {
                        results[if (winner < 0) 2 else winner]++
                        lines[g] = "$winner ${moves.joinToString(" ")}"
                        val done = results.sum()
                        if (done % 10 == 0) println("progress $done/$games first=${results[0]} second=${results[1]} draw=${results[2]}")
                    }
                }
            }
        }
        pool.shutdown()
        pool.awaitTermination(7, TimeUnit.DAYS)
        out.writeText(lines.filterNotNull().joinToString("\n") + "\n")
        println("RESULT first=$first games=$games sims=$sims firstWins=${results[0]} secondWins=${results[1]} draws=${results[2]}")
    }
}
