package fr.thibautperrin.tetrano.selfplay

import fr.thibautperrin.tetrano.engine.nn.GumbelMcts
import fr.thibautperrin.tetrano.engine.nn.QubicNet
import fr.thibautperrin.tetrano.engine.nn.Tactics
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * `battle a=<file> b=<file> sims_a=N sims_b=N out=<dir> [label=pc threads=N offset=0 schedule_seed=N
 * until=<epoch ms> max_pairs=N noisy_plies=4 seed=N]`
 *
 * Network A against network B, each searching like level 101 in the app (deterministic
 * [GumbelMcts], Gumbel noise only while fewer than `noisy_plies` rings are on the board). Pair p
 * (from `offset`) starts from [OpeningSchedule]'s opening p (disputed: the two networks' mean
 * first-player win probability in 20-80 %) and is played twice: A as the first player, then B.
 * No new pair starts after `until` or once `<out>/stop` exists; the pairs under way finish.
 *
 * Written to `<out>`, as each pair ends (a restart skips the pairs already there):
 * - `pairs.jsonl`: per pair the opening and, per leg, the result (0 first player wins, 1 second,
 *   -1 draw), the moves, each move's search time in ms and its simulations (0: proven position);
 * - `same.bin` / `diff.bin`: the positions of both legs (self-play records, see [encode]) of
 *   the pairs whose two legs had the same result, resp. not;
 * - `done.txt`, at the end: the `RESULT` line.
 */
internal fun battle(options: Map<String, String>) {
    val netA = loadNet(options["a"]) ?: error("a= required")
    val netB = loadNet(options["b"]) ?: error("b= required")
    val simsA = options["sims_a"]?.toInt() ?: 2048
    val simsB = options["sims_b"]?.toInt() ?: 512
    val threads = options["threads"]?.toInt() ?: (Runtime.getRuntime().availableProcessors() - 2)
    val offset = options["offset"]?.toLong() ?: 0L
    val maxPairs = options["max_pairs"]?.toLong() ?: Long.MAX_VALUE
    val until = options["until"]?.toLong() ?: Long.MAX_VALUE
    val noisyPlies = options["noisy_plies"]?.toInt() ?: 4
    val seed = options["seed"]?.toLong() ?: 1L
    val label = options["label"] ?: "battle"
    val schedule = OpeningSchedule(options["schedule_seed"]?.toLong() ?: 1L)
    val dir = File(options["out"] ?: error("out= required")).apply { mkdirs() }
    val pairsFile = File(dir, "pairs.jsonl")
    val stopFile = File(dir, "stop")
    File(dir, "done.txt").delete()

    val done = HashSet<Long>()
    if (pairsFile.exists()) {
        // A line cut by a crash is dropped (its pair is played again).
        val text = pairsFile.readText()
        val kept = text.substring(0, text.lastIndexOf('\n') + 1)
        if (kept.length != text.length) pairsFile.writeText(kept)
        Regex("\"pair\":(\\d+)").findAll(kept).forEach { done += it.groupValues[1].toLong() }
    }
    var nextPair = offset
    fun takePair(): Long? = synchronized(done) {
        while (nextPair in done) nextPair++
        if (nextPair - offset >= maxPairs) return null
        if (System.currentTimeMillis() >= until || stopFile.exists()) return null
        nextPair++
    }

    val stats = BattleStats()
    val started = System.nanoTime()
    println("battle $label: A ${options["a"]} ($simsA sims) vs B ${options["b"]} ($simsB sims), $threads threads, " +
        "pairs from $offset, ${done.size} already played")
    FileOutputStream(pairsFile, true).use { pairsOut ->
        FileOutputStream(File(dir, "same.bin"), true).use { sameOut ->
            FileOutputStream(File(dir, "diff.bin"), true).use { diffOut ->
                val pool = Executors.newFixedThreadPool(threads)
                repeat(threads) { t ->
                    pool.execute {
                        val rng = Random(seed * 1_000_003L + offset + t * 7919L)
                        val judgeA = Judge(netA)
                        val judgeB = Judge(netB)
                        val openings = OpeningMaker(schedule, { mine, theirs, firstToMove ->
                            (judgeA.firstPlayerWin(mine, theirs, firstToMove) + judgeB.firstPlayerWin(mine, theirs, firstToMove)) / 2f in 0.2f..0.8f
                        }, rng)
                        val mctsA = GumbelMcts(netA, GumbelMcts.Config(gumbelNoise = false), Random(rng.nextLong()))
                        val mctsB = GumbelMcts(netB, GumbelMcts.Config(gumbelNoise = false), Random(rng.nextLong()))
                        val a = Side(mctsA, simsA)
                        val b = Side(mctsB, simsB)
                        while (true) {
                            val p = takePair() ?: break
                            val slot = schedule.slot(p)
                            val start = openings.start(p)
                            val legs = listOf(playLeg(start, a, b, true, noisyPlies), playLeg(start, b, a, false, noisyPlies))
                            val same = legs[0].game.winner == legs[1].game.winner
                            val bytes = legs.sumOf { it.game.positions.size } * RECORD_SIZE
                            val buffer = ByteBuffer.allocate(bytes).order(ByteOrder.LITTLE_ENDIAN)
                            for (leg in legs) encode(leg.game, buffer)
                            val line = pairJson(p, slot, start, legs) + "\n"
                            synchronized(pairsOut) {
                                (if (same) sameOut else diffOut).write(buffer.array())
                                pairsOut.write(line.toByteArray())
                                stats.add(legs)
                                println("PAIR $p ${slot.kind} ${slot.plies}${if (slot.disputed) "d" else ""} " +
                                    "results ${legs[0].game.winner} ${legs[1].game.winner} | ${stats.summary()}")
                            }
                        }
                    }
                }
                pool.shutdown()
                pool.awaitTermination(30, TimeUnit.DAYS)
            }
        }
    }
    val result = "RESULT battle label=$label pairs=${stats.pairs} ${stats.summary()} " +
        "seconds=${"%.1f".format(Locale.ROOT, (System.nanoTime() - started) / 1e9)}"
    println(result)
    File(dir, "done.txt").writeText(result + "\n")
}

/** A network's player in the battle (one per thread). */
private class Side(val mcts: GumbelMcts, val sims: Int)

/** One forward pass: the first player's win probability. */
private class Judge(private val net: QubicNet) {
    private val workspace = net.newWorkspace()
    private val logits = FloatArray(64)
    private val wdl = FloatArray(3)

    fun firstPlayerWin(mine: Long, theirs: Long, firstToMove: Boolean): Float {
        net.evaluate(mine, theirs, workspace, logits, wdl)
        return if (firstToMove) wdl[0] else wdl[2]
    }
}

/** A leg: the game (winner 0 first player, 1 second, -1 draw), its moves and their search times. */
private class Leg(val game: PlayedGame, val moves: IntArray, val nanos: LongArray, val sims: IntArray, val firstIsA: Boolean)

/** Plays [start] to the end, [first] playing the first player's moves (those of even plies). */
private fun playLeg(start: Start, first: Side, second: Side, firstIsA: Boolean, noisyPlies: Int): Leg {
    var mine = start.mine
    var theirs = start.theirs
    var ply = start.ply
    val positions = ArrayList<Position>()
    val moves = ArrayList<Int>()
    val nanos = ArrayList<Long>()
    val sims = ArrayList<Int>()
    while (true) {
        val side = if (ply % 2 == 0) first else second
        val t = System.nanoTime()
        val result = side.mcts.search(mine, theirs, side.sims, noise = ply < noisyPlies)
        nanos += System.nanoTime() - t
        sims += result.simulations
        val flags = when (result.status) {
            GumbelMcts.Status.WIN -> FLAG_POLICY or FLAG_EXACT
            GumbelMcts.Status.UNKNOWN -> if (result.simulations == 0) 0 else FLAG_POLICY
            else -> FLAG_EXACT
        }
        positions += Position(mine, theirs, ply, result.policy, result.value, flags)
        val leg = { winner: Int -> Leg(PlayedGame(positions, winner, ply), moves.toIntArray(), nanos.toLongArray(), sims.toIntArray(), firstIsA) }
        if (result.move < 0) return leg(-1)
        moves += result.move
        val after = mine or (1L shl result.move)
        val mover = ply % 2
        ply++
        if (Tactics.wonWith(after, result.move)) return leg(mover)
        if ((after or theirs) == -1L) return leg(-1)
        mine = theirs
        theirs = after
    }
}

/** The opening and both legs of pair [p], as one JSON line. */
private fun pairJson(p: Long, slot: OpeningSchedule.Slot, start: Start, legs: List<Leg>): String {
    val firstToMove = start.ply % 2 == 0
    val first = if (firstToMove) start.mine else start.theirs
    val second = if (firstToMove) start.theirs else start.mine
    val sb = StringBuilder()
    sb.append("{\"pair\":").append(p).append(",\"kind\":\"").append(slot.kind).append("\",\"plies\":").append(slot.plies)
        .append(",\"class\":").append(slot.classIndex).append(",\"disputed\":").append(slot.disputed)
        .append(",\"first\":").append(first).append(",\"second\":").append(second).append(",\"legs\":[")
    legs.forEachIndexed { i, leg ->
        if (i > 0) sb.append(',')
        sb.append("{\"aFirst\":").append(leg.firstIsA).append(",\"result\":").append(leg.game.winner)
            .append(",\"moves\":[").append(leg.moves.joinToString(",")).append("],\"ms\":[")
            .append(leg.nanos.joinToString(",") { "%.2f".format(Locale.ROOT, it / 1e6) })
            .append("],\"sims\":[").append(leg.sims.joinToString(",")).append("]}")
    }
    return sb.append("]}").toString()
}

/** Running totals of a battle (under the output lock). */
private class BattleStats {
    var pairs = 0
    private var samePairs = 0
    private var aPoints = 0.0
    private var aWins = 0
    private var draws = 0
    private var bWins = 0
    private val moves = LongArray(2)
    private val nanos = LongArray(2)

    fun add(legs: List<Leg>) {
        pairs++
        if (legs[0].game.winner == legs[1].game.winner) samePairs++
        for (leg in legs) {
            val aSide = if (leg.firstIsA) 0 else 1
            when (leg.game.winner) {
                -1 -> { draws++; aPoints += 0.5 }
                aSide -> { aWins++; aPoints += 1.0 }
                else -> bWins++
            }
            // Move i is the first player's when i has the parity of the start.
            val startPly = leg.game.positions.first().ply
            for (i in leg.nanos.indices) {
                val firstMoves = (startPly + i) % 2 == 0
                val side = if (firstMoves == leg.firstIsA) 0 else 1
                moves[side]++
                nanos[side] += leg.nanos[i]
            }
        }
    }

    fun summary(): String {
        val games = 2 * pairs
        return "aScore=${"%.1f".format(Locale.ROOT, aPoints)}/$games aWins=$aWins draws=$draws bWins=$bWins samePairs=$samePairs " +
            "msA=${"%.1f".format(Locale.ROOT, nanos[0] / 1e6 / moves[0].coerceAtLeast(1))} " +
            "msB=${"%.1f".format(Locale.ROOT, nanos[1] / 1e6 / moves[1].coerceAtLeast(1))}"
    }
}
