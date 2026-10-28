package fr.thibautperrin.tetrano.wingraph

import fr.thibautperrin.tetrano.engine.nn.QubicNet
import fr.thibautperrin.tetrano.engine.nn.Tactics
import fr.thibautperrin.tetrano.engine.wingraph.CanonicalForm
import fr.thibautperrin.tetrano.engine.wingraph.UnbeatablePlayer
import fr.thibautperrin.tetrano.engine.wingraph.WinBook
import java.io.File
import kotlin.system.exitProcess

/**
 * Usage: `wingraph <command> key=value...`
 *
 * - `run dir=<run dir> [model=<net file>] [threads=N] [job_evals=N] [disk_limit_gb=10] [start=0,...] ...`:
 *   starts or resumes the run (see [Run]); `model` and `start` ([Start]) count the first time only.
 * - `status dir=<run dir>`: prints `status.txt` and whether the run is going.
 * - `stop dir=<run dir>`: asks the running computation to save and pause.
 * - `verify graph=<file> [start=0,...]`: checks a graph file alone.
 * - `bench model=<net file> [plies=N] [count=N] [job_evals=N]`: speeds of the network and of df-pn.
 * - `play graph=<file> model=<net file> [games=N] [seed=N] [first=strong|weak]`: real games of the app's player.
 * - `export graph=<file> out=<file> [start=0,...] [rate=0.1]`: proven training positions ([ProvenExport]).
 */
fun main(args: Array<String>) {
    val command = args.firstOrNull() ?: error("missing command")
    val options = args.drop(1).associate { arg ->
        val (k, v) = arg.split("=", limit = 2)
        k to v
    }
    val code = when (command) {
        "run" -> runCommand(options)
        "status" -> statusCommand(options)
        "stop" -> stopCommand(options)
        "verify" -> verifyCommand(options)
        "bench" -> benchCommand(options)
        "play" -> playCommand(options)
        "export" -> exportCommand(options)
        else -> error("unknown command $command")
    }
    exitProcess(code)
}

private fun runDir(options: Map<String, String>) = File(options["dir"] ?: error("dir= missing")).absoluteFile

private fun runCommand(options: Map<String, String>): Int {
    val dir = runDir(options)
    dir.mkdirs()
    val net = File(dir, "net.bin")
    if (!net.exists()) {
        val model = File(options["model"] ?: error("first run: model=<network file> missing"))
        model.inputStream().use { QubicNet.load(it) } // must be a valid network
        model.copyTo(net)
    }
    val overrides = options.filterKeys { it != "dir" && it != "model" }
    return Run(dir, overrides).execute()
}

private fun statusCommand(options: Map<String, String>): Int {
    val dir = runDir(options)
    val running = Run.isRunning(dir)
    val stop = File(dir, "STOP").exists()
    println(
        when {
            running && stop -> "ÉTAT : pause demandée, sauvegarde en cours"
            running -> "ÉTAT : en cours"
            File(dir, "state.properties").exists() -> "ÉTAT : à l'arrêt (relancer pour reprendre)"
            else -> "ÉTAT : jamais lancé dans $dir"
        },
    )
    val status = File(dir, "status.txt")
    if (status.exists()) {
        println()
        print(status.readText())
    }
    val log = File(dir, "log.txt")
    if (log.exists()) {
        println()
        println("Dernières lignes du journal (log.txt) :")
        log.readLines().takeLast(8).forEach { println("  $it") }
    }
    return 0
}

private fun stopCommand(options: Map<String, String>): Int {
    val dir = runDir(options)
    if (!Run.isRunning(dir)) {
        println("Rien ne tourne dans $dir.")
        return 0
    }
    File(dir, "STOP").writeText(Run.now())
    print("Pause demandée : le calcul sauvegarde puis s'arrête")
    val deadline = System.currentTimeMillis() + 120_000
    while (Run.isRunning(dir) && System.currentTimeMillis() < deadline) {
        Thread.sleep(1000)
        print(".")
    }
    println()
    println(if (Run.isRunning(dir)) "Toujours en cours après 2 min : vérifier la fenêtre du calcul." else "Arrêté. Relancer reprend où il en était.")
    return 0
}

private fun verifyCommand(options: Map<String, String>): Int {
    val file = File(options["graph"] ?: error("graph= missing"))
    val start = System.currentTimeMillis()
    val v = Assembler.verify(file, Start.parse(options["start"]))
    println("${file.name} : ${v.nodes} nœuds, ${v.positions} positions, parties d'au plus ${v.maxRings} anneaux, " +
        "${System.currentTimeMillis() - start} ms")
    println(if (v.failure == null) "VÉRIFIÉ : l'ordinateur gagne contre toutes les défenses." else "ÉCHEC : ${v.failure}")
    return if (v.failure == null) 0 else 1
}

/** Speeds on positions after the corner and [plies] more random plies (no immediate tactic). */
private fun benchCommand(options: Map<String, String>): Int {
    val net = File(options["model"] ?: error("model= missing")).inputStream().use { QubicNet.load(it) }
    val plies = options["plies"]?.toInt() ?: 9
    val count = options["count"]?.toInt() ?: 10
    val jobEvals = options["job_evals"]?.toLong() ?: 100_000
    val eval = NetEval(net)
    val t0 = System.nanoTime()
    repeat(2000) { eval.win(1L shl (it % 64), 0L) }
    println("réseau : ${"%.3f".format((System.nanoTime() - t0) / 2000 / 1e6)} ms par évaluation")
    val rng = kotlin.random.Random(1)
    val form = CanonicalForm()
    val dfpn = DfPn(22)
    var proved = 0
    var disproved = 0
    var totalMs = 0L
    repeat(count) {
        var a = 1L
        var d = 0L
        var placed = 0
        while (placed < plies) {
            val free = (a or d).inv()
            var bits = free
            var index = rng.nextInt(java.lang.Long.bitCount(free))
            while (index-- > 0) bits = bits and (bits - 1)
            val cell = java.lang.Long.numberOfTrailingZeros(bits)
            if (Board.attackerToMove(a, d)) a = a or (1L shl cell) else d = d or (1L shl cell)
            placed++
        }
        form.of(a, d)
        val start = System.currentTimeMillis()
        val before = dfpn.evals
        val r = dfpn.solve(form.attacker, form.defender, jobEvals)
        val ms = System.currentTimeMillis() - start
        totalMs += ms
        if (r == DfPn.PROVED) proved++
        if (r == DfPn.DISPROVED) disproved++
        println("position $it : ${listOf("?", "prouvée", "réfutée")[r]}, ${dfpn.evals - before} évaluations, $ms ms")
    }
    println("df-pn : $proved prouvées, $disproved réfutées sur $count ; ${totalMs / count} ms par recherche")
    return 0
}

/**
 * Real games of the app's player ([UnbeatablePlayer], real coordinates) from the empty board:
 * `games` against a random defender that blocks most threats, then as many against the network
 * (sampling its policy, so every game differs). Any game not won is reported.
 */
private fun playCommand(options: Map<String, String>): Int {
    val graph = File(options["graph"] ?: error("graph= missing")).inputStream().use { WinBook.load(it) }
    val net = File(options["model"] ?: error("model= missing")).inputStream().use { QubicNet.load(it) }
    val games = options["games"]?.toInt() ?: 1000
    val rng = kotlin.random.Random(options["seed"]?.toLong() ?: 1L)
    val first = if (options["first"] == "weak") UnbeatablePlayer.WEAK_RINGS else UnbeatablePlayer.STRONG_RINGS
    val player = UnbeatablePlayer(graph, first, rng)
    val eval = NetEval(net)
    val policy = FloatArray(64)
    var failures = 0
    for (opponent in listOf("aléatoire", "réseau")) {
        var won = 0
        var plies = 0L
        repeat(games) { g ->
            var a = 0L
            var d = 0L
            val moves = StringBuilder()
            while (true) {
                val m = player.chooseRing(a, d)
                if (m < 0 || (a or d) and (1L shl m) != 0L) {
                    println("ÉCHEC ($opponent, partie $g) : coup ${m} hors graphe ou illégal après $moves")
                    failures++
                    return@repeat
                }
                a = a or (1L shl m)
                moves.append(m).append(' ')
                if (Tactics.wonWith(a, m)) {
                    won++
                    plies += java.lang.Long.bitCount(a or d)
                    return@repeat
                }
                val free = (a or d).inv()
                if (free == 0L) {
                    println("ÉCHEC ($opponent, partie $g) : nulle après $moves")
                    failures++
                    return@repeat
                }
                val r = if (opponent == "aléatoire") {
                    val threats = Tactics.threats(a, d)
                    val choices = if (threats != 0L && rng.nextInt(10) != 0) threats else free
                    var bits = choices
                    var index = rng.nextInt(java.lang.Long.bitCount(choices))
                    while (index-- > 0) bits = bits and (bits - 1)
                    java.lang.Long.numberOfTrailingZeros(bits)
                } else {
                    eval.policy(a, d, free, policy)
                    var x = rng.nextFloat()
                    var pick = java.lang.Long.numberOfTrailingZeros(free)
                    for (c in 0 until 64) {
                        if (policy[c] <= 0f) continue
                        pick = c
                        x -= policy[c]
                        if (x <= 0f) break
                    }
                    pick
                }
                d = d or (1L shl r)
                moves.append(r).append(' ')
                if (Tactics.wonWith(d, r)) {
                    println("ÉCHEC ($opponent, partie $g) : l'adversaire gagne après $moves")
                    failures++
                    return@repeat
                }
            }
        }
        println("contre adversaire $opponent : $won / $games gagnées, ${"%.1f".format(plies.toDouble() / maxOf(won, 1))} anneaux en moyenne")
    }
    println(if (failures == 0) "AUCUN ÉCHEC" else "$failures ÉCHECS")
    return if (failures == 0) 0 else 1
}
