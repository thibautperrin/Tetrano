package fr.thibautperrin.tetrano.selfplay

import fr.thibautperrin.tetrano.engine.BoardState
import fr.thibautperrin.tetrano.engine.ai.Ai
import fr.thibautperrin.tetrano.engine.nn.ExactSolver
import fr.thibautperrin.tetrano.engine.nn.GumbelMcts
import fr.thibautperrin.tetrano.engine.nn.QubicGeometry
import fr.thibautperrin.tetrano.engine.nn.QubicNet
import fr.thibautperrin.tetrano.engine.nn.Tactics
import fr.thibautperrin.tetrano.engine.wingraph.CanonicalForm
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.exp
import kotlin.random.Random

// ---- relabel: exact values for the positions of a training file ----

/**
 * `relabel in=<v2 or v3 file> out=<v3 file> budget=3000 threads=N [deadline=<epoch ms>]`
 *
 * Writes [in]'s records as v3 records, each position not yet exact solved by [ExactSolver]
 * within `budget` evaluations: its exact value when proven (field 152, "exact" flag), unknown
 * otherwise. Resumable: blocks of records are appended to `<out>.part`, their count kept in
 * `<out>.done`; the same command continues after a stop, and the finished file is renamed to
 * [out]. Within a block each thread takes a contiguous slice (whole games) from its end, so the
 * proofs of a game's late positions help its earlier ones.
 */
internal fun relabel(options: Map<String, String>) {
    val input = RecordFile(File(options["in"] ?: error("in= required")))
    val out = File(options["out"] ?: error("out= required"))
    val budget = options["budget"]?.toLong() ?: 3_000L
    val threads = options["threads"]?.toInt() ?: (Runtime.getRuntime().availableProcessors() - 2)
    val deadline = options["deadline"]?.toLong() ?: Long.MAX_VALUE
    val block = (options["block"]?.toInt() ?: 28_000).toLong()
    val part = File(out.path + ".part")
    val doneFile = File(out.path + ".done")
    var done = if (part.exists() && doneFile.exists()) doneFile.readText().trim().toLong() else 0L
    if (done == 0L) part.writeBytes(v3Header()) else RandomAccessFile(part, "rw").use { it.setLength(V3_HEADER + done * RECORD3_SIZE) }
    val solvers = ThreadLocal.withInitial { ExactSolver(log2Entries = 19) }
    val pool = Executors.newFixedThreadPool(threads)
    val started = System.currentTimeMillis()
    val stats = LongArray(5) // already exact, win, draw, loss, unknown
    var stopped = false
    try {
        while (done < input.count) {
            if (System.currentTimeMillis() > deadline) {
                stopped = true
                break
            }
            val end = minOf(input.count, done + block)
            val records = input.read(done, end)
            val slice = (records.size + threads - 1) / threads
            val futures = (0 until threads).map { t ->
                pool.submit {
                    val solver = solvers.get()
                    val from = t * slice
                    val to = minOf(records.size, from + slice)
                    for (i in to - 1 downTo from) {
                        val r = records[i]
                        if (r.exact != EXACT_UNKNOWN) continue
                        val a = solver.solve(r.mine, r.theirs, budget, opponentFirst = r.value < -0.2f, findMove = false)
                        r.exact = Record3.exactCode(a.outcome)
                        r.evals = a.evals
                    }
                }
            }
            futures.forEach { it.get() }
            val buf = ByteBuffer.allocate(records.size * RECORD3_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            for (r in records) {
                if (r.flags and FLAG_EXACT != 0) stats[0]++ else stats[r.exact.coerceAtMost(3).let { if (it == 0) 4 else it }]++
                if (r.exact != EXACT_UNKNOWN) r.flags = r.flags or FLAG_EXACT
                r.write(buf)
            }
            FileOutputStream(part, true).use { it.write(buf.array()) }
            done = end
            doneFile.writeText(done.toString())
        }
    } finally {
        pool.shutdownNow()
    }
    if (!stopped) {
        if (out.exists()) out.delete()
        check(part.renameTo(out)) { "cannot rename $part" }
        doneFile.delete()
    }
    println(
        "RESULT relabel in=${input.file.name} records=${input.count} done=$done stopped=$stopped alreadyExact=${stats[0]} " +
            "win=${stats[1]} draw=${stats[2]} loss=${stats[3]} unknown=${stats[4]} seconds=${(System.currentTimeMillis() - started) / 1000}",
    )
}

// ---- truth: the benchmark of positions with exact values ----

/** A position of the truth benchmark: first and second player's rings, [game] -1 for single positions. */
internal class BenchPosition(val id: Int, val family: String, val game: Int, val ply: Int, val first: Long, val second: Long) {
    val firstToMove: Boolean get() = ply % 2 == 0
    val mine: Long get() = if (firstToMove) first else second
    val theirs: Long get() = if (firstToMove) second else first
}

/**
 * `truth model=<net> out=<dir> threads=N [games_a=150 games_b=150 openings_c=1200 budget=1000000
 * move_positions=400 move_budget=100000 seed=20261124 deadline=<epoch ms>]`
 *
 * 1. `positions.csv`: every position of `games_a` self-play games of the network (scheduled
 *    openings, adaptive search up to 512), of `games_b` "human-like" games (legacy AI against
 *    itself, legacy AI against the network, network games with injected weak moves), and
 *    family C: every position after 2 and 3 moves up to symmetry plus `openings_c` random
 *    positions after 4 to 8 moves.
 * 2. `labels.csv`: the exact value of each position for the side to move ([ExactSolver],
 *    `budget` evaluations), a move keeping it.
 * 3. `moves.csv`: for `move_positions` solved positions of families A and B, the exact value of
 *    every legal move (the mover's value after it).
 *
 * Resumable at every step (lines are appended, done ones skipped).
 */
internal fun truth(options: Map<String, String>) {
    val dir = File(options["out"] ?: error("out= required")).also { it.mkdirs() }
    val net = loadNet(options["model"]) ?: error("model= required")
    val threads = options["threads"]?.toInt() ?: (Runtime.getRuntime().availableProcessors() - 2)
    val seed = options["seed"]?.toLong() ?: 20261124L
    val deadline = options["deadline"]?.toLong() ?: Long.MAX_VALUE
    val positionsFile = File(dir, "positions.csv")
    if (!positionsFile.exists()) {
        val positions = generateBench(
            net, threads, seed,
            gamesA = options["games_a"]?.toInt() ?: 150,
            gamesB = options["games_b"]?.toInt() ?: 150,
            openingsC = options["openings_c"]?.toInt() ?: 1200,
        )
        val tmp = File(dir, "positions.csv.tmp")
        tmp.writeText(
            "id,family,game,ply,first,second\n" + positions.joinToString("") {
                "${it.id},${it.family},${it.game},${it.ply},${java.lang.Long.toHexString(it.first)},${java.lang.Long.toHexString(it.second)}\n"
            },
        )
        check(tmp.renameTo(positionsFile))
        println("truth: ${positions.size} positions written")
    }
    val positions = readBench(dir)
    labelBench(dir, positions, net, threads, options["budget"]?.toLong() ?: 1_000_000L, deadline)
    labelMoves(
        dir, positions, threads, options["move_positions"]?.toInt() ?: 400,
        options["move_budget"]?.toLong() ?: 100_000L, seed, deadline,
    )
    println("RESULT truth positions=${positions.size}")
}

internal fun readBench(dir: File): List<BenchPosition> =
    File(dir, "positions.csv").readLines().drop(1).filter { it.isNotBlank() }.map { line ->
        val f = line.split(",")
        BenchPosition(f[0].toInt(), f[1], f[2].toInt(), f[3].toInt(), java.lang.Long.parseUnsignedLong(f[4], 16), java.lang.Long.parseUnsignedLong(f[5], 16))
    }

/** Exact labels: id -> (outcome code for the side to move, move keeping it). */
internal fun readLabels(dir: File): Map<Int, Pair<Int, Int>> {
    val f = File(dir, "labels.csv")
    if (!f.exists()) return emptyMap()
    return f.readLines().drop(1).filter { it.endsWith(";") }.associate { line ->
        val p = line.removeSuffix(";").split(",")
        p[0].toInt() to (p[1].toInt() to p[2].toInt())
    }
}

/** Per-move labels: id -> (cell -> outcome code of the mover after that move). */
internal fun readMoveLabels(dir: File): Map<Int, Map<Int, Int>> {
    val f = File(dir, "moves.csv")
    if (!f.exists()) return emptyMap()
    val out = HashMap<Int, HashMap<Int, Int>>()
    for (line in f.readLines().drop(1).filter { it.endsWith(";") }) {
        val p = line.removeSuffix(";").split(",")
        val id = p[0].toInt()
        val map = out.getOrPut(id) { HashMap() }
        for (pair in p.drop(1)) {
            val (cell, code) = pair.split(":").map { it.toInt() }
            map[cell] = code
        }
    }
    return out
}

private class GameRecord(val family: String, val boards: List<Pair<Long, Long>>)

private fun generateBench(net: QubicNet, threads: Int, seed: Long, gamesA: Int, gamesB: Int, openingsC: Int): List<BenchPosition> {
    val games = arrayOfNulls<GameRecord>(gamesA + gamesB)
    val next = AtomicInteger(0)
    val failure = AtomicReference<Throwable>()
    val pool = Executors.newFixedThreadPool(threads)
    val schedule = OpeningSchedule(seed)
    val filter = DisputedFilter(net, 0.2f, 0.8f)
    repeat(threads) { t ->
        pool.execute(
            failFast(failure) {
                val rng = Random(seed * 7919 + t)
                val mcts = GumbelMcts(net, GumbelMcts.Config(gumbelNoise = true), rng)
                val judge = filter.Judge()
                val openings = OpeningMaker(schedule, judge::acceptsFirstPlayer, rng)
                while (true) {
                    val g = next.getAndIncrement()
                    if (g >= games.size || failure.get() != null) break
                    games[g] = if (g < gamesA) {
                        playBenchGame("A", openings.start(1_000_000L + g), rng) { m, th, _ -> mcts.searchAdaptive(m, th, 512).move }
                    } else {
                        humanLikeGame((g - gamesA) % 3, mcts, rng)
                    }
                }
            },
        )
    }
    pool.shutdown()
    pool.awaitTermination(7, TimeUnit.DAYS)
    failure.get()?.let { throw IllegalStateException("bench generation failed", it) }
    val out = ArrayList<BenchPosition>()
    games.forEachIndexed { g, game ->
        for (board in game!!.boards) {
            out += BenchPosition(out.size, game.family, g, QubicGeometry.popCount(board.first or board.second), board.first, board.second)
        }
    }
    // Family C: openings.
    val form = CanonicalForm()
    for ((first, second) in OpeningSchedule.TWO_MOVES + OpeningSchedule.THREE_MOVES) {
        out += BenchPosition(out.size, "C", -1, QubicGeometry.popCount(first or second), first, second)
    }
    val rng = Random(seed + 3)
    val seen = HashSet<Pair<Long, Long>>()
    while (seen.size < openingsC) {
        val plies = 4 + rng.nextInt(5)
        var first = 0L
        var second = 0L
        repeat(plies) { k ->
            val cell = randomFreeCell(first or second, rng)
            if (k % 2 == 0) first = first or (1L shl cell) else second = second or (1L shl cell)
        }
        if (Tactics.threats(first, second) != 0L || Tactics.threats(second, first) != 0L) continue
        form.of(first, second)
        if (seen.add(form.attacker to form.defender)) out += BenchPosition(out.size, "C", -1, plies, first, second)
    }
    return out
}

/** Plays from [start] to the end with [chooser] (mine, theirs, ply -> cell); records every position from the start. */
private fun playBenchGame(family: String, start: Start, rng: Random, chooser: (Long, Long, Int) -> Int): GameRecord {
    var mine = start.mine
    var theirs = start.theirs
    var ply = start.ply
    val boards = ArrayList<Pair<Long, Long>>()
    while (true) {
        boards += if (ply % 2 == 0) mine to theirs else theirs to mine
        val move = chooser(mine, theirs, ply)
        check(move in 0..63 && ((mine or theirs) ushr move) and 1L == 0L) { "illegal move $move" }
        val after = mine or (1L shl move)
        if (Tactics.wonWith(after, move) || (after or theirs) == -1L) return GameRecord(family, boards)
        mine = theirs
        theirs = after
        ply++
    }
}

/** Kind 0: legacy AI against itself; 1: legacy AI against the network; 2: network with injected weak moves. */
private fun humanLikeGame(kind: Int, mcts: GumbelMcts, rng: Random): GameRecord = when (kind) {
    0 -> {
        val levels = intArrayOf(10 + rng.nextInt(76), 10 + rng.nextInt(76))
        playBenchGame("B", Start(0L, 0L, 0), rng) { m, th, ply -> legacyMove(m, th, ply, levels[ply % 2], rng) }
    }
    1 -> {
        val level = 40 + rng.nextInt(46)
        val netSide = rng.nextInt(2)
        playBenchGame("B", Start(0L, 0L, 0), rng) { m, th, ply ->
            if (ply % 2 == netSide) mcts.search(m, th, 256, noise = ply < 4).move else legacyMove(m, th, ply, level, rng)
        }
    }
    else -> playBenchGame("B", Start(0L, 0L, 0), rng) { m, th, ply ->
        val r = mcts.search(m, th, 256, noise = ply < 4)
        val u = rng.nextDouble()
        when {
            r.status != GumbelMcts.Status.UNKNOWN || u >= 0.17 -> r.move
            u < 0.05 -> randomFreeCell(m or th, rng)
            else -> sampleFromPrior(r.prior, 2.0, rng)
        }
    }
}

/** The legacy AI's move at [level] for the side to move ([ply] parity gives its colour). */
internal fun legacyMove(mine: Long, theirs: Long, ply: Int, level: Int, rng: Random): Int {
    val board = BoardState()
    val me = if (ply % 2 == 0) 1 else 2
    var bits = mine
    while (bits != 0L) {
        board.play(java.lang.Long.numberOfTrailingZeros(bits), me)
        bits = bits and (bits - 1)
    }
    bits = theirs
    while (bits != 0L) {
        board.play(java.lang.Long.numberOfTrailingZeros(bits), 3 - me)
        bits = bits and (bits - 1)
    }
    return Ai(board, Random(rng.nextLong())).playLevel(me, level)
}

/** A cell drawn from [prior] (0 for illegal cells) sharpened or flattened by [temperature]. */
internal fun sampleFromPrior(prior: FloatArray, temperature: Double, rng: Random): Int {
    val w = DoubleArray(64) { if (prior[it] > 0f) exp(kotlin.math.ln(prior[it].toDouble()) / temperature) else 0.0 }
    var u = rng.nextDouble() * w.sum()
    for (c in 0 until 64) {
        u -= w[c]
        if (u <= 0 && w[c] > 0) return c
    }
    return (0 until 64).last { w[it] > 0 }
}

private fun labelBench(dir: File, positions: List<BenchPosition>, net: QubicNet, threads: Int, budget: Long, deadline: Long) {
    val file = File(dir, "labels.csv")
    if (!file.exists()) file.writeText("id,outcome,move,evals,ms;\n")
    val done = readLabels(dir).keys
    // Work units: whole games (solved from their last position) and single positions.
    val units = positions.filter { it.id !in done }.groupBy { if (it.game >= 0) "g${it.game}" else "p${it.id}" }.values
        .map { unit -> unit.sortedByDescending { it.ply } }
    val next = AtomicInteger(0)
    val failure = AtomicReference<Throwable>()
    val pool = Executors.newFixedThreadPool(threads)
    val solved = AtomicLong(0)
    val started = System.currentTimeMillis()
    FileOutputStream(file, true).use { out ->
        repeat(threads) {
            pool.execute(
                failFast(failure) {
                    val solver = ExactSolver(log2Entries = 21)
                    val ws = net.newWorkspace()
                    val logits = FloatArray(64)
                    val wdl = FloatArray(3)
                    while (System.currentTimeMillis() < deadline) {
                        val u = next.getAndIncrement()
                        if (u >= units.size || failure.get() != null) break
                        for (p in units.elementAt(u)) {
                            if (System.currentTimeMillis() > deadline) break
                            net.evaluate(p.mine, p.theirs, ws, logits, wdl)
                            val t0 = System.nanoTime()
                            val a = solver.solve(p.mine, p.theirs, budget, opponentFirst = wdl[2] > wdl[0])
                            val ms = (System.nanoTime() - t0) / 1_000_000
                            if (a.outcome != ExactSolver.Outcome.UNKNOWN) solved.incrementAndGet()
                            val line = "${p.id},${Record3.exactCode(a.outcome)},${a.move},${a.evals},$ms;\n"
                            synchronized(out) {
                                out.write(line.toByteArray())
                                out.flush()
                            }
                        }
                    }
                },
            )
        }
        pool.shutdown()
        pool.awaitTermination(7, TimeUnit.DAYS)
    }
    failure.get()?.let { throw IllegalStateException("labelling failed", it) }
    println("truth labels: ${units.sumOf { it.size }} positions to do, ${solved.get()} newly solved, ${(System.currentTimeMillis() - started) / 1000} s")
}

private fun labelMoves(dir: File, positions: List<BenchPosition>, threads: Int, count: Int, budget: Long, seed: Long, deadline: Long) {
    val labels = readLabels(dir)
    val file = File(dir, "moves.csv")
    if (!file.exists()) file.writeText("id,cell:code...;\n")
    val done = readMoveLabels(dir).keys
    val candidates = positions.filter {
        it.family != "C" && it.ply >= 8 && (labels[it.id]?.first ?: 0) != EXACT_UNKNOWN &&
            Tactics.threats(it.mine, it.theirs) == 0L
    }.shuffled(Random(seed + 11)).take(count).filter { it.id !in done }
    val next = AtomicInteger(0)
    val failure = AtomicReference<Throwable>()
    val pool = Executors.newFixedThreadPool(threads)
    FileOutputStream(file, true).use { out ->
        repeat(threads) {
            pool.execute(
                failFast(failure) {
                    val solver = ExactSolver(log2Entries = 21)
                    val form = CanonicalForm()
                    while (System.currentTimeMillis() < deadline) {
                        val k = next.getAndIncrement()
                        if (k >= candidates.size || failure.get() != null) break
                        val p = candidates[k]
                        val byChild = HashMap<Pair<Long, Long>, Int>()
                        val parts = ArrayList<String>()
                        var free = (p.mine or p.theirs).inv()
                        while (free != 0L) {
                            val c = java.lang.Long.numberOfTrailingZeros(free)
                            free = free and (free - 1)
                            val after = p.mine or (1L shl c)
                            val code = if (Tactics.wonWith(after, c)) {
                                EXACT_WIN
                            } else if (after or p.theirs == -1L) {
                                EXACT_DRAW
                            } else {
                                form.of(after, p.theirs)
                                byChild.getOrPut(form.attacker to form.defender) {
                                    // The opponent's outcome, seen from the mover.
                                    when (solver.solve(p.theirs, after, budget, findMove = false).outcome) {
                                        ExactSolver.Outcome.WIN -> EXACT_LOSS
                                        ExactSolver.Outcome.LOSS -> EXACT_WIN
                                        ExactSolver.Outcome.DRAW -> EXACT_DRAW
                                        ExactSolver.Outcome.UNKNOWN -> EXACT_UNKNOWN
                                    }
                                }
                            }
                            parts += "$c:$code"
                        }
                        val line = "${p.id}," + parts.joinToString(",") + ";\n"
                        synchronized(out) {
                            out.write(line.toByteArray())
                            out.flush()
                        }
                    }
                },
            )
        }
        pool.shutdown()
        pool.awaitTermination(7, TimeUnit.DAYS)
    }
    failure.get()?.let { throw IllegalStateException("move labelling failed", it) }
    println("truth moves: ${candidates.size} positions to do")
}
