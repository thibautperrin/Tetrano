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
 * - `selfplay model=<file|none> games=N sims=N threads=N out=<file> seed=N random_plies=5 [disputed=0.2,0.8]
 *   [openings=schedule offset=N schedule_seed=N]` (with `openings=schedule`, [OpeningSchedule]'s
 *   openings, `disputed=` giving the first player's win range, and `random_plies` unused)
 * - `arena a=<file|none> b=<file|none> games=N sims=N threads=N`
 * - `vslegacy model=<file> level=100 games=N sims=N threads=N`
 * - `bench model=<file> sims=N`
 * - `match a=<file> b=<file> ...` (tournament games, see [tournamentMatch])
 * - `puzzles data=<dir> model=<file> out=<file> ...` (the app's puzzle set, see [generatePuzzles])
 * - `openings [schedule_seed=N] [cycle=N]` (summary of a cycle of [OpeningSchedule])
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
        "match" -> tournamentMatch(options)
        "puzzles" -> generatePuzzles(options)
        "openings" -> printOpeningSchedule(options)
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

/** A self-play game: recorded positions, the winner (0 first, 1 second, -1 draw) and its length in plies. */
private class PlayedGame(val positions: List<Position>, val winner: Int, val plies: Int)

/** A uniformly random free cell of a board that has one. */
internal fun randomFreeCell(occupied: Long, rng: Random): Int {
    val free = (occupied).inv()
    var index = rng.nextInt(java.lang.Long.bitCount(free))
    var bits = free
    while (index-- > 0) bits = bits and (bits - 1)
    return java.lang.Long.numberOfTrailingZeros(bits)
}

/**
 * Openings kept only when they are "disputed": the network's direct evaluation (one forward pass,
 * no search) gives the side to move a win probability in [minWin, maxWin].
 */
internal class DisputedFilter(val net: QubicNet, val minWin: Float, val maxWin: Float) {
    val attempts = AtomicLong(0)
    val accepted = AtomicLong(0)

    /** One per thread: the network's scratch buffers. */
    inner class Judge {
        private val workspace = net.newWorkspace()
        private val logits = FloatArray(64)
        private val wdl = FloatArray(3)

        fun accepts(mine: Long, theirs: Long): Boolean {
            attempts.incrementAndGet()
            net.evaluate(mine, theirs, workspace, logits, wdl)
            val ok = wdl[0] in minWin..maxWin
            if (ok) accepted.incrementAndGet()
            return ok
        }

        /** Like [accepts], on the first player's win probability ([firstToMove]: the side to move is it). */
        fun acceptsFirstPlayer(mine: Long, theirs: Long, firstToMove: Boolean): Boolean {
            attempts.incrementAndGet()
            net.evaluate(mine, theirs, workspace, logits, wdl)
            val ok = (if (firstToMove) wdl[0] else wdl[2]) in minWin..maxWin
            if (ok) accepted.incrementAndGet()
            return ok
        }
    }

    companion object {
        /** `disputed=0.2,0.8` -> a filter on [net] (none without the option). */
        fun parse(option: String?, net: QubicNet?): DisputedFilter? {
            if (option == null) return null
            val (lo, hi) = option.split(",").map { it.toFloat() }
            return DisputedFilter(net ?: error("disputed= needs a model"), lo, hi)
        }
    }
}

/**
 * Plays one self-play game. It starts with 0..[maxRandomPlies] uniformly random moves (not
 * recorded: they are not good moves to learn from), then the search plays both sides to the
 * real end of the game — also once a position is proven, so that every position knows how many
 * plies were left. With a [judge], always exactly [maxRandomPlies] random moves, drawn again
 * until the judge finds the position disputed.
 */
private fun playGame(
    mcts: GumbelMcts,
    sims: Int,
    maxRandomPlies: Int,
    rng: Random,
    judge: DisputedFilter.Judge? = null,
): PlayedGame = playGame(mcts, sims, randomStart(maxRandomPlies, rng, judge))

/** 0..[maxRandomPlies] random moves, or with a [judge] exactly [maxRandomPlies], drawn until disputed. */
private fun randomStart(maxRandomPlies: Int, rng: Random, judge: DisputedFilter.Judge?): Start {
    var mine: Long
    var theirs: Long
    var ply: Int
    do {
        mine = 0L
        theirs = 0L
        ply = 0
        var over = false
        repeat(if (judge != null) maxRandomPlies else rng.nextInt(maxRandomPlies + 1)) {
            if (over) return@repeat
            val cell = randomFreeCell(mine or theirs, rng)
            val after = mine or (1L shl cell)
            if (Tactics.wonWith(after, cell)) over = true
            mine = theirs
            theirs = after
            ply++
        }
    } while (over || (judge != null && !judge.accepts(mine, theirs)))
    return Start(mine, theirs, ply)
}

/** Plays a self-play game from [start] (whose moves are not recorded) to its real end. */
private fun playGame(mcts: GumbelMcts, sims: Int, start: Start): PlayedGame {
    var mine = start.mine
    var theirs = start.theirs
    var ply = start.ply
    val positions = ArrayList<Position>()
    while (true) {
        val result = mcts.search(mine, theirs, sims)
        val mover = ply % 2
        val flags = when (result.status) {
            GumbelMcts.Status.WIN -> FLAG_POLICY or FLAG_EXACT
            GumbelMcts.Status.UNKNOWN -> if (result.simulations == 0) 0 else FLAG_POLICY
            else -> FLAG_EXACT
        }
        positions += Position(mine, theirs, ply, result.policy, result.value, flags)
        if (result.move < 0) return PlayedGame(positions, -1, ply)
        val after = mine or (1L shl result.move)
        ply++
        if (Tactics.wonWith(after, result.move)) return PlayedGame(positions, mover, ply)
        if ((after or theirs) == -1L) return PlayedGame(positions, -1, ply)
        mine = theirs
        theirs = after
    }
}

/** Writes a game's positions; the free byte holds the plies left until the end (moves-left target). */
private fun encode(game: PlayedGame, out: ByteBuffer) {
    val winner = game.winner
    for (p in game.positions) {
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
        out.put((game.plies - p.ply).coerceIn(0, 255).toByte())
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
    val randomPlies = options.int("random_plies", 5)
    val filter = DisputedFilter.parse(options["disputed"], net)
    // openings=schedule: the openings of OpeningSchedule; this call plays games offset.. of the run.
    val schedule = if (options["openings"] == "schedule") OpeningSchedule(options.long("schedule_seed", 1L)) else null
    val offset = options.long("offset", 0L)
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
                val rng = Random(seed * 1000 + t)
                val mcts = GumbelMcts(net, GumbelMcts.Config(gumbelNoise = true), rng)
                val judge = filter?.Judge()
                val openings = schedule?.let { OpeningMaker(it, judge, rng) }
                while (true) {
                    val index = next.getAndIncrement()
                    if (index >= games) break
                    val game = if (openings != null) {
                        playGame(mcts, sims, openings.start(offset + index))
                    } else {
                        playGame(mcts, sims, randomPlies, rng, judge)
                    }
                    val buffer = ByteBuffer.allocate(game.positions.size * RECORD_SIZE).order(ByteOrder.LITTLE_ENDIAN)
                    encode(game, buffer)
                    synchronized(file) {
                        file.write(buffer.array())
                        wins[if (game.winner == -1) 2 else game.winner]++
                        plySum += game.plies
                    }
                    positionsCount.addAndGet(game.positions.size.toLong())
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
            "evals=${evaluations.get()} seconds=${"%.1f".format(java.util.Locale.ROOT, seconds)}" +
            (filter?.let { " openingDraws=${it.attempts.get()} openingsKept=${it.accepted.get()}" } ?: ""),
    )
}

/** A player choosing a move for the side to move (mine/theirs from its point of view). */
private fun interface Player {
    fun move(mine: Long, theirs: Long, firstMoves: List<Int>): Int
}

/**
 * Plays one game from the position after [moves] (empty board by default); [first] is the player
 * to move there. Returns 0 if [first] wins, 1 if [second] wins, -1 for a draw. The moves played
 * are appended to [moves].
 */
private fun playMatch(first: Player, second: Player, moves: MutableList<Int> = ArrayList()): Int {
    var mine = 0L
    var theirs = 0L
    for ((i, m) in moves.withIndex()) {
        if ((moves.size - i) % 2 == 1) theirs = theirs or (1L shl m) else mine = mine or (1L shl m)
    }
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

/**
 * A tournament player: [GumbelMcts] with [sims] simulations, Gumbel noise on its own first
 * [noisyMoves] moves only (like level 101 in the app), deterministic afterwards.
 */
private fun tournamentPlayer(net: QubicNet?, sims: Int, noisyMoves: Int, seed: Long): Player {
    val mcts = GumbelMcts(net, GumbelMcts.Config(gumbelNoise = false), Random(seed))
    var played = 0
    return Player { mine, theirs, _ -> mcts.search(mine, theirs, sims, noise = played++ < noisyMoves).move }
}

/**
 * The first [count] random openings of [plies] moves for [seed]: no line or threat for either
 * side, all distinct up to the 192 board symmetries, and disputed for [judge] if given.
 * Deterministic, so every pair of a tournament plays the same openings.
 */
private fun randomOpenings(seed: Long, count: Int, plies: Int, judge: DisputedFilter.Judge? = null): List<List<Int>> {
    val rng = Random(seed)
    val seen = HashSet<Pair<Long, Long>>()
    val openings = ArrayList<List<Int>>()
    while (openings.size < count) {
        var mine = 0L
        var theirs = 0L
        val moves = ArrayList<Int>()
        repeat(plies) {
            val cell = randomFreeCell(mine or theirs, rng)
            moves += cell
            val after = mine or (1L shl cell)
            mine = theirs
            theirs = after
        }
        if (Tactics.threats(mine, theirs) != 0L || Tactics.threats(theirs, mine) != 0L) continue
        if (Tactics.hasWon(mine) || Tactics.hasWon(theirs)) continue
        if (judge != null && !judge.accepts(mine, theirs)) continue
        var best: Pair<Long, Long>? = null
        for (s in 0 until fr.thibautperrin.tetrano.engine.nn.QubicGeometry.symmetryCount) {
            val key = fr.thibautperrin.tetrano.engine.nn.QubicGeometry.transform(s, mine) to
                fr.thibautperrin.tetrano.engine.nn.QubicGeometry.transform(s, theirs)
            if (best == null || java.lang.Long.compareUnsigned(key.first, best.first) < 0 ||
                (key.first == best.first && java.lang.Long.compareUnsigned(key.second, best.second) < 0)
            ) {
                best = key
            }
        }
        if (seen.add(best!!)) openings += moves
    }
    return openings
}

/**
 * `match a= b= games= sims= [sims_a= sims_b=] threads= openings=empty|random opening_plies=5 seed= offset= noisy_moves=4 games_out= [judge=<file> disputed=0.2,0.8]`
 *
 * empty: game g starts from the empty board, A moves first in even games.
 * random: game g starts from opening `offset + g / 2`, A to move in even games, B in odd ones
 * (each opening is played once each way); with `judge`, only openings that network finds
 * disputed (see [DisputedFilter]).
 */
private fun tournamentMatch(options: Map<String, String>) {
    val a = loadNet(options["a"])
    val b = loadNet(options["b"])
    val games = options.int("games", 40)
    val sims = options.int("sims", 128)
    val simsA = options.int("sims_a", sims)
    val simsB = options.int("sims_b", sims)
    val threads = options.int("threads", Runtime.getRuntime().availableProcessors() - 2)
    val noisyMoves = options.int("noisy_moves", 4)
    val seed = options.long("seed", 1)
    val random = options["openings"] == "random"
    val offset = options.int("offset", 0)
    val judge = DisputedFilter.parse(options["disputed"], loadNet(options["judge"]))?.Judge()
    val openings = if (random) randomOpenings(seed, offset + (games + 1) / 2, options.int("opening_plies", 5), judge) else emptyList()
    val lines = arrayOfNulls<String>(games)
    val next = AtomicInteger(0)
    val started = System.nanoTime()
    val pool = Executors.newFixedThreadPool(threads)
    repeat(threads) {
        pool.execute {
            while (true) {
                val g = next.getAndIncrement()
                if (g >= games) break
                val gameSeed = seed * 1_000_003L + (offset * 2L + g) * 7919L
                val pa = tournamentPlayer(a, simsA, noisyMoves, gameSeed)
                val pb = tournamentPlayer(b, simsB, noisyMoves, gameSeed + 1)
                val aFirst = g % 2 == 0
                val moves = if (random) ArrayList(openings[offset + g / 2]) else ArrayList()
                val result = if (aFirst) playMatch(pa, pb, moves) else playMatch(pb, pa, moves)
                lines[g] = "${if (aFirst) 1 else 0} $result ${moves.joinToString(" ")}"
            }
        }
    }
    pool.shutdown()
    pool.awaitTermination(7, TimeUnit.DAYS)
    options["games_out"]?.let { File(it).writeText(lines.joinToString("\n", postfix = "\n")) }
    var aScore = 0.0
    for (line in lines) {
        val (aFirst, result) = line!!.split(" ").let { (it[0] == "1") to it[1].toInt() }
        aScore += when {
            result == -1 -> 0.5
            (result == 0) == aFirst -> 1.0
            else -> 0.0
        }
    }
    println(
        "RESULT match games=$games aScore=${"%.1f".format(java.util.Locale.ROOT, aScore)} " +
            "seconds=${"%.1f".format(java.util.Locale.ROOT, (System.nanoTime() - started) / 1e9)}",
    )
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
        makeA = { seed -> mctsPlayer(net, sims, noise = options["noise"] != "false", seed = seed) },
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
