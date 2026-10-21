package fr.thibautperrin.tetrano.selfplay

import fr.thibautperrin.tetrano.engine.nn.GumbelMcts
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * `calibrate model=<file> positions=<file> [max=2048 fixed=512,1024 threads=N]`
 *
 * How well [GumbelMcts.searchAdaptive] matches a full search: for each position (a line
 * "mine theirs", side to move first), a reference search of `max` simulations, fixed searches of
 * `fixed` simulations and an adaptive search up to `max`, all without noise. Per variant: how often
 * it plays the reference's move (up to the position's symmetries), the mean regret (reference Q of
 * its move below the reference Q of the reference's move) and the mean simulations.
 */
internal fun calibrate(options: Map<String, String>) {
    val net = loadNet(options["model"]) ?: error("model= required")
    val max = options["max"]?.toInt() ?: 2048
    val fixed = (options["fixed"] ?: "512,1024").split(",").map { it.toInt() }
    val threads = options["threads"]?.toInt() ?: (Runtime.getRuntime().availableProcessors() - 2)
    val positions = File(options["positions"] ?: error("positions= required")).readLines().filter { it.isNotBlank() }.map { line ->
        val (m, t) = line.trim().split(" ").map { it.toLong() }
        m to t
    }
    val labels = fixed.map { "fixed $it" } + "adaptive"
    val agree = IntArray(labels.size)
    val regret = DoubleArray(labels.size)
    val sims = LongArray(labels.size)
    val stops = HashMap<Int, Int>()
    var refSims = 0L
    val next = AtomicInteger(0)
    val pool = Executors.newFixedThreadPool(threads)
    repeat(threads) { t ->
        pool.execute {
            val noNoise = GumbelMcts.Config(gumbelNoise = false)
            val reference = GumbelMcts(net, noNoise, Random(t.toLong()))
            val adaptive = GumbelMcts(net, noNoise, Random(t.toLong()))
            while (true) {
                val i = next.getAndIncrement()
                if (i >= positions.size) break
                val (mine, theirs) = positions[i]
                val ref = reference.search(mine, theirs, max)
                val results = fixed.map { reference.search(mine, theirs, it) } + adaptive.searchAdaptive(mine, theirs, max)
                synchronized(agree) {
                    refSims += ref.simulations
                    results.forEachIndexed { k, r ->
                        if (ref.moveClass[r.move] == ref.moveClass[ref.move]) agree[k]++
                        val q = ref.moveValues[r.move]
                        if (!q.isNaN()) regret[k] += (ref.moveValues[ref.move] - q).coerceAtLeast(0f)
                        sims[k] += r.simulations
                        if (k == fixed.size) stops.merge(r.simulations, 1, Int::plus)
                    }
                }
            }
        }
    }
    pool.shutdown()
    pool.awaitTermination(7, TimeUnit.DAYS)
    val n = positions.size.toDouble()
    println("positions: ${positions.size}, reference $max simulations: mean ${"%.0f".format(Locale.ROOT, refSims / n)}")
    labels.forEachIndexed { k, label ->
        println(
            "RESULT calibrate variant=\"$label\" agreement=${"%.3f".format(Locale.ROOT, agree[k] / n)} " +
                "regret=${"%.4f".format(Locale.ROOT, regret[k] / n)} sims=${"%.0f".format(Locale.ROOT, sims[k] / n)}" +
                (if (k == fixed.size) " stops=" + stops.toSortedMap().entries.joinToString(",") { "${it.key}:${it.value}" } else ""),
        )
    }
}

/**
 * `rounds model=<file> positions=<file> out=<file> [max=2048 threads=N]`: for each position, a
 * reference search of `max` simulations and an adaptive search that never stops early, written
 * as one JSON line: per round (64, 128, ... simulations) the class chosen and every class's
 * completed Q, visits and policy mass, plus the reference's chosen class and Q per class; stopping
 * rules are then compared offline (tools/nn/adaptive_rules.py).
 */
internal fun rounds(options: Map<String, String>) {
    val net = loadNet(options["model"]) ?: error("model= required")
    val max = options["max"]?.toInt() ?: 2048
    val threads = options["threads"]?.toInt() ?: (Runtime.getRuntime().availableProcessors() - 2)
    val positions = File(options["positions"] ?: error("positions= required")).readLines().filter { it.isNotBlank() }.map { line ->
        val (m, t) = line.trim().split(" ").map { it.toLong() }
        m to t
    }
    val lines = arrayOfNulls<String>(positions.size)
    val next = AtomicInteger(0)
    fun classes(r: GumbelMcts.Result): String {
        val byClass = (0 until 64).filter { r.moveClass[it] >= 0 }.groupBy { r.moveClass[it] }
        return byClass.entries.joinToString(",", "[", "]") { (c, cells) ->
            val q = r.moveValues[cells.first()]
            val mass = cells.sumOf { r.policy[it].toDouble() }
            "[$c,${if (q.isNaN()) "null" else "%.4f".format(Locale.ROOT, q)},${r.visits[cells.first()]},${"%.4f".format(Locale.ROOT, mass)}]"
        }
    }
    val pool = Executors.newFixedThreadPool(threads)
    repeat(threads) { t ->
        pool.execute {
            val config = GumbelMcts.Config(gumbelNoise = false)
            val reference = GumbelMcts(net, config, Random(t.toLong()))
            val adaptive = GumbelMcts(net, config, Random(t.toLong()))
            while (true) {
                val i = next.getAndIncrement()
                if (i >= positions.size) break
                val (mine, theirs) = positions[i]
                val ref = reference.search(mine, theirs, max)
                val rounds = ArrayList<String>()
                val last = adaptive.searchAdaptive(mine, theirs, max, stopRule = { r ->
                    rounds += "{\"sims\":${r.simulations},\"chosen\":${r.moveClass[r.move]},\"classes\":${classes(r)}}"
                    false
                })
                if (rounds.isEmpty() || last.simulations >= max) {
                    rounds += "{\"sims\":${last.simulations},\"chosen\":${if (last.move >= 0) last.moveClass[last.move] else -1},\"classes\":${classes(last)}}"
                }
                lines[i] = "{\"position\":$i,\"ref\":{\"sims\":${ref.simulations},\"chosen\":${if (ref.move >= 0) ref.moveClass[ref.move] else -1}," +
                    "\"classes\":${classes(ref)}},\"rounds\":[${rounds.joinToString(",")}]}"
            }
        }
    }
    pool.shutdown()
    pool.awaitTermination(7, TimeUnit.DAYS)
    File(options["out"] ?: error("out= required")).writeText(lines.joinToString("\n", postfix = "\n"))
    println("RESULT rounds positions=${positions.size}")
}
