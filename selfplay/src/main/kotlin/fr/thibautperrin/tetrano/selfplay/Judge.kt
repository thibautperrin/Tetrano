package fr.thibautperrin.tetrano.selfplay

import fr.thibautperrin.tetrano.engine.nn.GameReview
import fr.thibautperrin.tetrano.engine.nn.GumbelMcts
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.sqrt

/**
 * `judge bench=<dir> model=<file> [sims=0,64,512] [adaptive=0] [root_solve=0] [tree_solve=0] [threads=N]
 * [out=<file.md>] [label=...]`
 *
 * Accuracy of a network and its search on the truth benchmark ([truth]), as the analysis
 * screen uses them (no noise, no moves-left nudge). For each simulation count (0 = the network
 * alone): value error (most likely outcome differs from the exact one), mean probability given
 * to the exact outcome (network alone), share of positions proven by the search, correct moves
 * (the proposed move keeps the exact value, on positions with per-move labels), consistency of
 * consecutive values (v(t) + v(t+1), 0 if consistent), precision and recall of "this move
 * changed the exact value" as the review judges it, time per position. By family (A self-play,
 * B human-like, C openings) and ply range.
 */
internal fun judge(options: Map<String, String>) {
    val dir = File(options["bench"] ?: error("bench= required"))
    val net = loadNet(options["model"]) ?: error("model= required")
    val simsList = (options["sims"] ?: "0,64,512").split(",").map { it.trim().toInt() }
    val adaptive = options["adaptive"] == "1"
    val rootSolve = options["root_solve"]?.toLong() ?: 0L
    val treeSolve = options["tree_solve"]?.toInt() ?: 0
    val oldValue = options["old_value"] == "1"
    val threads = options["threads"]?.toInt() ?: (Runtime.getRuntime().availableProcessors() - 2)
    val limit = options["limit"]?.toInt() ?: Int.MAX_VALUE
    val positions = readBench(dir).take(limit)
    val labels = readLabels(dir)
    val moveLabels = readMoveLabels(dir)
    val label = options["label"] ?: File(options["model"]!!).name
    val report = StringBuilder()
    report.append("# Banc de vérité — $label\n\n")
    report.append("Réseau `${options["model"]}`, banc `${dir.path}` : ${positions.size} positions, " +
        "${labels.count { it.value.first != EXACT_UNKNOWN }} à valeur exacte, ${moveLabels.size} avec valeurs de tous les coups. " +
        "Recherche sans bruit ni plis restants${if (adaptive) ", adaptative" else ""}" +
        "${if (rootSolve > 0) ", solveur exact à la racine ($rootSolve évaluations)" else ""}" +
        "${if (treeSolve > 0) ", solveur dans l'arbre ($treeSolve évaluations par visite)" else ""}" +
        "${if (oldValue) ", valeur de racine ancienne (mélange des coups visités)" else ", valeur de racine = Q du coup choisi"}.\n\n")
    val summary = StringBuilder()
    for (sims in simsList) {
        val evals = evaluateAll(positions, net, sims, adaptive, rootSolve, treeSolve, oldValue, threads)
        report.append(section(sims, positions, labels, moveLabels, evals))
        val all = JudgeStats.of(positions, labels, moveLabels, evals) { true }
        summary.append(" sims$sims=valueErr:${fmt(all.valueError)},moveOk:${fmt(all.moveOk)},consistency:${fmt(all.consistency)}," +
            "proven:${fmt(all.proven)},recall:${fmt(all.recall)},precision:${fmt(all.precision)},ms:${fmt(all.ms)}")
    }
    val out = File(options["out"] ?: File(dir, "judge_$label.md").path)
    out.writeText(report.toString())
    println("RESULT judge label=$label$summary")
}

/** What the evaluation of one position says, for the side to move. */
internal class PositionEval(
    /** Most likely outcome: EXACT_WIN / EXACT_DRAW / EXACT_LOSS. */
    val predicted: Int,
    /** Probability given to each outcome (win, draw, loss). */
    val wdl: FloatArray,
    val value: Float,
    val status: GumbelMcts.Status,
    val move: Int,
    val ms: Double,
)

private fun evaluateAll(
    positions: List<BenchPosition>, net: fr.thibautperrin.tetrano.engine.nn.QubicNet, sims: Int, adaptive: Boolean,
    rootSolve: Long, treeSolve: Int, oldValue: Boolean, threads: Int,
): Array<PositionEval?> {
    val out = arrayOfNulls<PositionEval>(positions.size)
    val next = AtomicInteger(0)
    val failure = AtomicReference<Throwable>()
    val pool = Executors.newFixedThreadPool(threads)
    repeat(threads) {
        pool.execute(
            failFast(failure) {
                val mcts = GumbelMcts(
                    net,
                    GumbelMcts.Config(gumbelNoise = false, movesLeftWeight = 0f, rootSolveBudget = rootSolve, treeSolvePerVisit = treeSolve,
                        chosenMoveValue = !oldValue,
                    ),
                )
                val ws = net.newWorkspace()
                val logits = FloatArray(64)
                val wdl = FloatArray(3)
                while (true) {
                    val i = next.getAndIncrement()
                    if (i >= positions.size || failure.get() != null) break
                    val p = positions[i]
                    val t0 = System.nanoTime()
                    out[i] = if (sims == 0) {
                        net.evaluate(p.mine, p.theirs, ws, logits, wdl)
                        var best = -1
                        for (c in 0 until 64) if (((p.mine or p.theirs) ushr c) and 1L == 0L && (best < 0 || logits[c] > logits[best])) best = c
                        PositionEval(argmax(wdl), wdl.copyOf(), wdl[0] - wdl[2], GumbelMcts.Status.UNKNOWN, best, (System.nanoTime() - t0) / 1e6)
                    } else {
                        val r = if (adaptive) mcts.searchAdaptive(p.mine, p.theirs, sims) else mcts.search(p.mine, p.theirs, sims)
                        val w = searchWdl(r)
                        val predicted = when (r.status) {
                            GumbelMcts.Status.WIN -> EXACT_WIN
                            GumbelMcts.Status.LOSS -> EXACT_LOSS
                            GumbelMcts.Status.DRAW -> EXACT_DRAW
                            GumbelMcts.Status.UNKNOWN -> argmax(w)
                        }
                        PositionEval(predicted, w, r.value, r.status, r.move, (System.nanoTime() - t0) / 1e6)
                    }
                }
            },
        )
    }
    pool.shutdown()
    pool.awaitTermination(7, TimeUnit.DAYS)
    failure.get()?.let { throw IllegalStateException("judge failed", it) }
    return out
}

private fun argmax(w: FloatArray): Int = when {
    w[0] >= w[1] && w[0] >= w[2] -> EXACT_WIN
    w[2] >= w[1] -> EXACT_LOSS
    else -> EXACT_DRAW
}

/** Same as the analysis screen's: the network's draw share kept, win and loss apart by the search value. */
internal fun searchWdl(result: GumbelMcts.Result): FloatArray {
    if (result.status != GumbelMcts.Status.UNKNOWN) return result.netWdl
    val v = result.value
    var draw = result.netWdl[1].coerceIn(0f, 1f)
    var win = (1f - draw + v) / 2f
    var loss = (1f - draw - v) / 2f
    if (win < 0f) {
        win = 0f
        loss = -v
        draw = 1f + v
    } else if (loss < 0f) {
        loss = 0f
        win = v
        draw = 1f - v
    }
    return floatArrayOf(win, draw, loss)
}

private fun rank(code: Int): Int = when (code) {
    EXACT_WIN -> 2
    EXACT_DRAW -> 1
    else -> 0
}

private fun flip(code: Int): Int = when (code) {
    EXACT_WIN -> EXACT_LOSS
    EXACT_LOSS -> EXACT_WIN
    else -> code
}

private class JudgeStats(
    val n: Int, val exact: Int, val valueError: Double, val pExact: Double, val proven: Double,
    val moveN: Int, val moveOk: Double, val consistency: Double, val consistencySd: Double,
    val mistakes: Int, val recall: Double, val precision: Double, val ms: Double,
) {
    companion object {
        fun of(
            positions: List<BenchPosition>, labels: Map<Int, Pair<Int, Int>>, moveLabels: Map<Int, Map<Int, Int>>,
            evals: Array<PositionEval?>, keep: (BenchPosition) -> Boolean,
        ): JudgeStats {
            var n = 0
            var exact = 0
            var wrong = 0
            var pSum = 0.0
            var proven = 0
            var moveN = 0
            var moveOk = 0
            var msSum = 0.0
            val cons = ArrayList<Double>()
            var trueMistakes = 0
            var flagged = 0
            var hit = 0
            for ((i, p) in positions.withIndex()) {
                if (!keep(p)) continue
                val e = evals[i] ?: continue
                n++
                msSum += e.ms
                if (e.status != GumbelMcts.Status.UNKNOWN) proven++
                val truth = labels[p.id]?.first ?: EXACT_UNKNOWN
                if (truth != EXACT_UNKNOWN) {
                    exact++
                    if (e.predicted != truth) wrong++
                    pSum += e.wdl[truth - 1]
                }
                moveLabels[p.id]?.let { ml ->
                    val code = ml[e.move]
                    if (code != null && truth != EXACT_UNKNOWN && code != EXACT_UNKNOWN) {
                        moveN++
                        if (code == truth) moveOk++
                    }
                }
                // The next position of the same game.
                val q = positions.getOrNull(i + 1)
                val f = evals.getOrNull(i + 1)
                if (q != null && f != null && p.game >= 0 && q.game == p.game && q.ply == p.ply + 1) {
                    if (e.status == GumbelMcts.Status.UNKNOWN && f.status == GumbelMcts.Status.UNKNOWN) cons += (e.value + f.value).toDouble()
                    val tNext = labels[q.id]?.first ?: EXACT_UNKNOWN
                    if (truth != EXACT_UNKNOWN && tNext != EXACT_UNKNOWN) {
                        val isMistake = rank(truth) > rank(flip(tNext))
                        val loss = (e.value + 1f) / 2f - (1f - f.value) / 2f
                        val flaggedHere = (e.status == GumbelMcts.Status.WIN && f.status != GumbelMcts.Status.LOSS) ||
                            (e.status != GumbelMcts.Status.LOSS && f.status == GumbelMcts.Status.WIN) ||
                            loss >= GameReview.MISTAKE
                        if (isMistake) trueMistakes++
                        if (flaggedHere) flagged++
                        if (isMistake && flaggedHere) hit++
                    }
                }
            }
            val mean = if (cons.isEmpty()) Double.NaN else cons.average()
            val sd = if (cons.size < 2) Double.NaN else sqrt(cons.sumOf { (it - mean) * (it - mean) } / (cons.size - 1))
            return JudgeStats(
                n, exact, if (exact > 0) wrong.toDouble() / exact else Double.NaN, if (exact > 0) pSum / exact else Double.NaN,
                if (n > 0) proven.toDouble() / n else Double.NaN, moveN, if (moveN > 0) moveOk.toDouble() / moveN else Double.NaN,
                mean, sd, trueMistakes,
                if (trueMistakes > 0) hit.toDouble() / trueMistakes else Double.NaN,
                if (flagged > 0) hit.toDouble() / flagged else Double.NaN,
                if (n > 0) msSum / n else Double.NaN,
            )
        }
    }
}

private fun fmt(x: Double): String = if (x.isNaN()) "-" else String.format(Locale.ROOT, "%.4f", x)
private fun pct(x: Double): String = if (x.isNaN()) "–" else String.format(Locale.ROOT, "%.1f %%", 100 * x)

private val PLY_RANGES = listOf(0 to 7, 8 to 11, 12 to 15, 16 to 19, 20 to 27, 28 to 64)

private fun section(
    sims: Int, positions: List<BenchPosition>, labels: Map<Int, Pair<Int, Int>>, moveLabels: Map<Int, Map<Int, Int>>,
    evals: Array<PositionEval?>,
): String {
    val sb = StringBuilder()
    sb.append(if (sims == 0) "## Réseau seul\n\n" else "## Recherche, $sims simulations\n\n")
    sb.append("| Groupe | Positions | Exactes | Erreur de valeur | P(issue exacte) | Prouvées | Coups jugés | Coup juste | v(t)+v(t+1) | écart-type | Fautes réelles | Rappel | Précision | ms |\n")
    sb.append("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n")
    fun row(name: String, keep: (BenchPosition) -> Boolean) {
        val s = JudgeStats.of(positions, labels, moveLabels, evals, keep)
        if (s.n == 0) return
        sb.append("| $name | ${s.n} | ${s.exact} | ${pct(s.valueError)} | ${if (sims == 0) fmt(s.pExact) else "–"} | ${pct(s.proven)} | ${s.moveN} | ${pct(s.moveOk)} | " +
            "${if (s.consistency.isNaN()) "–" else String.format(Locale.ROOT, "%+.3f", s.consistency)} | ${fmt(s.consistencySd)} | ${s.mistakes} | ${pct(s.recall)} | ${pct(s.precision)} | " +
            "${String.format(Locale.ROOT, "%.1f", s.ms)} |\n")
    }
    row("**Tout**") { true }
    for (f in listOf("A", "B", "C")) row("famille $f") { it.family == f }
    for ((lo, hi) in PLY_RANGES) row("plis $lo–$hi") { it.ply in lo..hi }
    sb.append("\n")
    return sb.toString()
}
