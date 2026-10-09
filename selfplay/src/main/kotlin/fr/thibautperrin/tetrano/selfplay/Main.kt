package fr.thibautperrin.tetrano.selfplay

import fr.thibautperrin.tetrano.engine.BoardState
import fr.thibautperrin.tetrano.engine.ai.Ai
import fr.thibautperrin.tetrano.engine.nn.GumbelMcts
import fr.thibautperrin.tetrano.engine.nn.QubicNet
import fr.thibautperrin.tetrano.engine.nn.Tactics
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/**
 * Usage: `selfplay <command> key=value...`
 *
 * - `selfplay model=<file|none> games=N sims=N threads=N out=<file> seed=N`
 * - `arena a=<file|none> b=<file|none> games=N sims=N threads=N`
 * - `vslegacy model=<file> level=100 games=N sims=N threads=N`
 * - `bench model=<file> sims=N`
 *
 * Output lines starting with `RESULT ` are machine-readable `key=value` pairs
 * read by tools/nn/loop.py.
 */
fun main(args: Array<String>) {
    val command = args.firstOrNull() ?: error("missing command")
    val options = args.drop(1).associate { arg ->
        val (k, v) = arg.split("=", limit = 2)
        k to v
    }
    when (command) {
        "selfplay" -> selfPlay(options)
        "arena" -> arena(options)
        "vslegacy" -> vsLegacy(options)
        "bench" -> bench(options)
        else -> error("unknown command $command")
    }
}

private fun Map<String, String>.int(key: String, default: Int) = this[key]?.toInt() ?: default
private fun Map<String, String>.long(key: String, default: Long) = this[key]?.toLong() ?: default

private fun loadNet(path: String?): QubicNet? =
    if (path == null || path == "none") null else File(path).inputStream().use { QubicNet.load(it) }

/** Bytes per recorded position — must match tools/nn/data.py. */
const val RECORD_SIZE = 8 + 8 + 4 + 64 * 2 + 4

private const val FLAG_POLICY = 1
private const val FLAG_EXACT = 2

private class Position(
    val mine: Long,
    val theirs: Long,
    val ply: Int,
    val policy: FloatArray,
    val value: Float,
    val flags: Int,
)

/** Plays one self-play game; returns its positions and the winner (0 first, 1 second, -1 draw). */
private fun playGame(mcts: GumbelMcts, sims: Int): Pair<List<Position>, Int> {
    val positions = ArrayList<Position>()
    var mine = 0L
    var theirs = 0L
    var ply = 0
    while (true) {
        val result = mcts.search(mine, theirs, sims)
        val mover = ply % 2
        if (result.status != GumbelMcts.Status.UNKNOWN) {
            val policyFlag = if (result.status == GumbelMcts.Status.WIN) FLAG_POLICY else 0
            positions += Position(mine, theirs, ply, result.policy, result.value, policyFlag or FLAG_EXACT)
            val winner = when (result.status) {
                GumbelMcts.Status.WIN -> mover
                GumbelMcts.Status.LOSS -> 1 - mover
                else -> -1
            }
            return positions to winner
        }
        val forced = result.simulations == 0
        positions += Position(mine, theirs, ply, result.policy, result.value, if (forced) 0 else FLAG_POLICY)
        val after = mine or (1L shl result.move)
        if (Tactics.wonWith(after, result.move)) return positions to mover
        if ((after or theirs) == -1L) return positions to -1
        mine = theirs
        theirs = after
        ply++
    }
}

private fun encode(positions: List<Position>, winner: Int, out: ByteBuffer) {
    for (p in positions) {
        val mover = p.ply % 2
        val outcome = when (winner) {
            -1 -> 1
            mover -> 0
            else -> 2
        }
        out.putLong(p.mine)
        out.putLong(p.theirs)
        out.put(outcome.toByte())
        out.put(p.flags.toByte())
        out.put(p.ply.toByte())
        out.put(0)
        for (c in 0 until 64) out.putShort(Math.round(p.policy[c].coerceIn(0f, 1f) * 65535f).toShort())
        out.putFloat(p.value)
    }
}

private fun selfPlay(options: Map<String, String>) {
    val net = loadNet(options["model"])
    val games = options.int("games", 100)
    val sims = options.int("sims", 48)
    val threads = options.int("threads", Runtime.getRuntime().availableProcessors() - 2)
    val seed = options.long("seed", System.nanoTime())
    val outFile = File(options["out"] ?: error("out= required"))
    val tmp = File(outFile.path + ".tmp")
    val started = System.nanoTime()
    val next = AtomicInteger(0)
    val positionsCount = AtomicLong(0)
    val wins = IntArray(3) // first, second, draw
    var plySum = 0L
    val evaluations = AtomicLong(0)
    FileOutputStream(tmp).use { file ->
        val pool = Executors.newFixedThreadPool(threads)
        repeat(threads) { t ->
            pool.execute {
                val mcts = GumbelMcts(net, GumbelMcts.Config(gumbelNoise = true), Random(seed * 1000 + t))
                while (next.getAndIncrement() < games) {
                    val (positions, winner) = playGame(mcts, sims)
                    val buffer = ByteBuffer.allocate(positions.size * RECORD_SIZE).order(ByteOrder.LITTLE_ENDIAN)
                    encode(positions, winner, buffer)
                    synchronized(file) {
                        file.write(buffer.array())
                        wins[if (winner == -1) 2 else winner]++
                        plySum += positions.last().ply + 1
                    }
                    positionsCount.addAndGet(positions.size.toLong())
                }
                evaluations.addAndGet(mcts.evaluations)
            }
        }
        pool.shutdown()
        pool.awaitTermination(7, TimeUnit.DAYS)
    }
    check(outFile.delete() || !outFile.exists())
    check(tmp.renameTo(outFile)) { "cannot rename $tmp" }
    val seconds = (System.nanoTime() - started) / 1e9
    println(
        "RESULT games=$games positions=${positionsCount.get()} firstWins=${wins[0]} secondWins=${wins[1]} " +
            "draws=${wins[2]} avgPlies=${"%.1f".format(java.util.Locale.ROOT, plySum.toDouble() / games)} " +
            "evals=${evaluations.get()} seconds=${"%.1f".format(java.util.Locale.ROOT, seconds)}",
    )
}

/** A player choosing a move for the side to move (mine/theirs from its point of view). */
private fun interface Player {
    fun move(mine: Long, theirs: Long, firstMoves: List<Int>): Int
}

/**
 * Plays one game; returns 0 if the first player wins, 1 for the second, -1 for a draw.
 * The moves are appended to [moves].
 */
private fun playMatch(first: Player, second: Player, moves: MutableList<Int> = ArrayList()): Int {
    var mine = 0L
    var theirs = 0L
    var ply = 0
    while (true) {
        val player = if (ply % 2 == 0) first else second
        val move = player.move(mine, theirs, moves)
        require(move in 0..63 && ((mine or theirs) ushr move) and 1L == 0L) { "illegal move $move" }
        moves += move
        val after = mine or (1L shl move)
        if (Tactics.wonWith(after, move)) return ply % 2
        if ((after or theirs) == -1L) return -1
        mine = theirs
        theirs = after
        ply++
    }
}

private fun mctsPlayer(net: QubicNet?, sims: Int, noise: Boolean, seed: Long): Player {
    val mcts = GumbelMcts(net, GumbelMcts.Config(gumbelNoise = noise), Random(seed))
    return Player { mine, theirs, _ -> mcts.search(mine, theirs, sims).move }
}

private fun legacyPlayer(level: Int, seed: Long): Player = Player { _, _, moves ->
    val board = BoardState()
    var player = 1
    for (m in moves) {
        board.play(m, player)
        player = 3 - player
    }
    Ai(board, Random(seed + moves.size * 7919L)).playLevel(player, level)
}

/** Runs [games] games alternating colours; returns the score of player A (win 1, draw 0.5). */
private fun match(
    games: Int,
    threads: Int,
    makeA: (Long) -> Player,
    makeB: (Long) -> Player,
    label: String,
    gamesOut: File? = null,
) {
    // One line per game: "<A played first: 1|0> <result: 0 first wins, 1 second wins, -1 draw> <moves...>".
    val lines = arrayOfNulls<String>(games)
    val next = AtomicInteger(0)
    val stats = IntArray(6) // A first: win/draw/loss, A second: win/draw/loss
    val pool = Executors.newFixedThreadPool(threads)
    val started = System.nanoTime()
    repeat(threads) { t ->
        pool.execute {
            while (true) {
                val g = next.getAndIncrement()
                if (g >= games) break
                val a = makeA(g * 31L + t)
                val b = makeB(g * 17L + t + 1_000_000)
                val aFirst = g % 2 == 0
                val moves = ArrayList<Int>()
                val result = if (aFirst) playMatch(a, b, moves) else playMatch(b, a, moves)
                lines[g] = "${if (aFirst) 1 else 0} $result ${moves.joinToString(" ")}"
                val base = if (aFirst) 0 else 3
                val aSide = if (aFirst) 0 else 1
                val slot = when (result) {
                    -1 -> 1
                    aSide -> 0
                    else -> 2
                }
                synchronized(stats) { stats[base + slot]++ }
            }
        }
    }
    pool.shutdown()
    pool.awaitTermination(7, TimeUnit.DAYS)
    gamesOut?.writeText(lines.joinToString("\n", postfix = "\n"))
    val score = stats[0] + stats[3] + 0.5 * (stats[1] + stats[4])
    println(
        "RESULT $label games=$games score=${"%.3f".format(java.util.Locale.ROOT, score / games)} " +
            "aFirstWin=${stats[0]} aFirstDraw=${stats[1]} aFirstLoss=${stats[2]} " +
            "aSecondWin=${stats[3]} aSecondDraw=${stats[4]} aSecondLoss=${stats[5]} " +
            "seconds=${"%.1f".format(java.util.Locale.ROOT, (System.nanoTime() - started) / 1e9)}",
    )
}

private fun arena(options: Map<String, String>) {
    val a = loadNet(options["a"])
    val b = loadNet(options["b"])
    val sims = options.int("sims", 64)
    match(
        games = options.int("games", 100),
        threads = options.int("threads", Runtime.getRuntime().availableProcessors() - 2),
        makeA = { seed -> mctsPlayer(a, sims, noise = true, seed = seed) },
        makeB = { seed -> mctsPlayer(b, sims, noise = true, seed = seed) },
        label = "arena",
        gamesOut = options["games_out"]?.let(::File),
    )
}

private fun vsLegacy(options: Map<String, String>) {
    val net = loadNet(options["model"])
    val sims = options.int("sims", 64)
    val level = options.int("level", 100)
    match(
        games = options.int("games", 20),
        threads = options.int("threads", Runtime.getRuntime().availableProcessors() - 2),
        makeA = { seed -> mctsPlayer(net, sims, noise = options["noise"] == "true", seed = seed) },
        makeB = { seed -> legacyPlayer(level, seed) },
        label = "vslegacy",
        gamesOut = options["games_out"]?.let(::File),
    )
}

private fun bench(options: Map<String, String>) {
    val net = loadNet(options["model"]) ?: error("model= required")
    val ws = net.newWorkspace()
    val policy = FloatArray(64)
    val wdl = FloatArray(3)
    val rng = Random(1)
    val positions = List(200) {
        var mine = 0L
        var theirs = 0L
        repeat(rng.nextInt(30)) {
            var c: Int
            do c = rng.nextInt(64) while (((mine or theirs) ushr c) and 1L != 0L)
            val after = mine or (1L shl c)
            mine = theirs
            theirs = after
        }
        mine to theirs
    }
    repeat(3) { round ->
        val t = System.nanoTime()
        var n = 0
        while (System.nanoTime() - t < 2_000_000_000L) {
            val (m, th) = positions[n % positions.size]
            net.evaluate(m, th, ws, policy, wdl)
            n++
        }
        println("round $round: ${"%.3f".format(java.util.Locale.ROOT, 2000.0 / n)} ms/eval")
    }
    val sims = options.int("sims", 200)
    val mcts = GumbelMcts(net, GumbelMcts.Config(gumbelNoise = false))
    val t = System.nanoTime()
    var total = 0
    for ((m, th) in positions.take(20)) total += mcts.search(m, th, sims).simulations
    println("search: ${"%.3f".format(java.util.Locale.ROOT, (System.nanoTime() - t) / 1e6 / total)} ms/simulation over $total simulations")
}
