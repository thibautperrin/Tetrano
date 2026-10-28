package fr.thibautperrin.tetrano.wingraph

import fr.thibautperrin.tetrano.engine.nn.QubicNet
import fr.thibautperrin.tetrano.engine.wingraph.WinRules
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties
import java.util.concurrent.atomic.AtomicLong

/** Settings of a run, kept in `config.properties` (command-line options override and are saved). */
class RunConfig(val props: Properties) {
    private fun int(key: String, default: Int) = props.getProperty(key)?.toInt() ?: default
    private fun long(key: String, default: Long) = props.getProperty(key)?.toLong() ?: default

    val threads: Int get() = int("threads", maxOf(1, Runtime.getRuntime().availableProcessors() - 4))

    /** df-pn evaluations per top-tree job. */
    val jobEvals: Long get() = long("job_evals", 500_000)

    /** Proof-number cost per ring below the root for unexpanded nodes (see [TopTree.depthPn]); 0 = off. */
    val depthPn: Long get() = long("depth_pn", 0)

    /** Largest df-pn search the extractor runs on one position (budgets grow ×4 up to it). */
    val maxChooseEvals: Long get() = long("max_choose_evals", 25_000_000)

    /** df-pn search budget of a reduction trial (per position). */
    val reduceEvals: Long get() = long("reduce_evals", 2_000_000)

    /** log2 of each worker's df-pn table (28 bytes per entry). */
    val ttLog2: Int get() = int("tt_log2", 22)

    val diskLimitBytes: Long get() = (props.getProperty("disk_limit_gb")?.toDouble() ?: 10.0).let { (it * 1e9).toLong() }
    val checkpointMinutes: Long get() = long("checkpoint_minutes", 10)

    /** See [Start]; fixed at the first launch. */
    val start: Start get() = Start.parse(props.getProperty("start"))
}

/** Counters of the current session. */
class Stats {
    val jobs = AtomicLong()
    val expansions = AtomicLong()
    val provedLeaves = AtomicLong()
    val disprovedLeaves = AtomicLong()
    val extractionFailures = AtomicLong()
    val walkedPositions = AtomicLong()
    val reduceTried = AtomicLong()
    val reduceImproved = AtomicLong()
}

/**
 * A run directory and everything kept in it:
 *
 * | File | Content |
 * |---|---|
 * | `config.properties` | settings ([RunConfig]) |
 * | `state.properties` | phase, cumulated time, graph sizes, reduction progress |
 * | `net.bin` | the network (copied at creation: the run is pinned to it) |
 * | `tree.bin` | the top tree ([TopTree]) |
 * | `book.bin` | every proven book move found so far ([PositionMap]: position -> cell) |
 * | `solved.bin` | top-tree positions proved (3) or disproved (4) |
 * | `covered.bin` | book positions whose whole subtree was walked (-> size) |
 * | `graph-v1.bin`, `graph-best.bin` | the first and the smallest verified graphs ([fr.thibautperrin.tetrano.engine.wingraph.WinBook]) |
 * | `log.txt`, `status.txt` | journal, state rewritten every 30 s |
 * | `STOP` | created by `wingraph stop`: pause asked |
 * | `run.lock` | held while running |
 *
 * Every file is written to a temporary file then renamed: a crash or a
 * closed window loses at most the time since the last checkpoint.
 */
class Run(val dir: File, overrides: Map<String, String>) {
    val config: RunConfig
    val state = Properties()
    val lock = Any()
    val stats = Stats()

    var tree: TopTree? = null
    val book: PositionMap
    val solved: PositionMap
    val covered: PositionMap
    val bookView = WinRules.Book { a, d -> book.get(a, d) }

    @Volatile
    var fatal: Throwable? = null

    @Volatile
    private var stopRequested = false
    private var stopReason = ""
    private var activePhase: (() -> Unit)? = null
    private val sessionStart = System.currentTimeMillis()
    private val logFile = File(dir, "log.txt")

    init {
        dir.mkdirs()
        val configFile = File(dir, "config.properties")
        val props = Properties()
        if (configFile.exists()) configFile.reader().use { props.load(it) }
        val fixedStart = if (File(dir, "tree.bin").exists()) props.getProperty("start") ?: "0" else null
        overrides.forEach { (k, v) -> props.setProperty(k, v) }
        require(fixedStart == null || (props.getProperty("start") ?: "0") == fixedStart) { "start= cannot change once the run has begun" }
        configFile.writer().use { props.store(it, "wingraph run settings") }
        config = RunConfig(props)
        val stateFile = File(dir, "state.properties")
        if (stateFile.exists()) stateFile.reader().use { state.load(it) }
        book = PositionMap.load(File(dir, "book.bin"))
        solved = PositionMap.load(File(dir, "solved.bin"))
        covered = PositionMap.load(File(dir, "covered.bin"))
        val treeFile = File(dir, "tree.bin")
        val start = config.start
        tree = if (treeFile.exists()) TopTree.load(treeFile) else TopTree(TNode(start.rootA, start.rootD, null, 0))
        require(tree!!.root.a == start.rootA && tree!!.root.d == start.rootD) { "tree.bin does not match start=$start" }
        wireTree(tree!!)
        tree!!.depthPn = config.depthPn
        tree!!.recomputeAll()
        // The first move: cell 0, a corner (all 16 corners and centres are equivalent).
        if (start.fromEmpty) book.putIfAbsent(0L, 0L, 0)
    }

    private fun wireTree(t: TopTree) {
        t.solvedState = { a, d -> solved.get(a, d).let { if (it < 0) TNode.LEAF else it } }
        t.onSolved = { node, proved -> solved.put(node.a, node.d, if (proved) TNode.PROVED else TNode.DISPROVED) }
        t.onBookMove = { node, move ->
            // Only where the rules do not already decide (no win, no forced block, no VCF).
            if (WinRules.decide(node.a, node.d, null, treeVcf) == WinRules.NONE) book.putIfAbsent(node.a, node.d, move)
        }
    }

    private val treeVcf = WinRules.newVcf()

    var phase: String
        get() = state.getProperty("phase", "prove")
        set(value) {
            state.setProperty("phase", value)
        }

    fun stateLong(key: String): Long = state.getProperty(key)?.toLong() ?: 0L
    fun setState(key: String, value: Any) {
        state.setProperty(key, value.toString())
    }

    val stopping: Boolean get() = stopRequested

    fun requestStop(reason: String = "") {
        if (stopRequested) return
        stopRequested = true
        stopReason = reason
        activePhase?.invoke()
    }

    // ---- main loop ----

    /** Runs the phases from the current one until done or stopped; returns the process exit code. */
    fun execute(): Int {
        val lockHandle = acquireLock() ?: run {
            println("Un calcul tourne déjà dans $dir (run.lock tenu).")
            return 2
        }
        val netFile = File(dir, "net.bin")
        require(netFile.exists()) { "$netFile manquant (créé au premier lancement depuis model=…)" }
        val net = netFile.inputStream().use { QubicNet.load(it) }
        File(dir, "STOP").delete()
        log("démarrage : phase ${phase}, ${config.threads} threads, tas max ${gb(Runtime.getRuntime().maxMemory())} Go")
        val hook = Thread { requestStop("arrêt du processus"); waitForEnd() }
        Runtime.getRuntime().addShutdownHook(hook)
        val monitor = Thread({ monitorLoop() }, "monitor").also { it.isDaemon = true; it.start() }
        try {
            while (!stopRequested) {
                when (phase) {
                    "prove" -> {
                        val prover = Prover(this, net)
                        activePhase = { prover.requestStop() }
                        if (prover.prove()) {
                            log("PREUVE COMPLÈTE depuis start=${config.start} (arbre de pilotage : ${tree!!.nodeCount} nœuds)")
                            checkpoint()
                            phase = "assemble"
                        } else if (!stopRequested) {
                            fatal = IllegalStateException("root disproved: the corner would not win (bug?)")
                            requestStop("racine réfutée")
                        }
                    }
                    "assemble" -> {
                        val assembler = Assembler(this, net)
                        activePhase = { assembler.requestStop() }
                        val size = assembler.assemble(File(dir, "graph-v1.bin"))
                        if (size >= 0) {
                            File(dir, "graph-v1.bin").copyTo(File(dir, "graph-best.bin"), overwrite = true)
                            setState("graph_v1_size", size)
                            setState("graph_best_size", size)
                            log("GRAPHE v1 vérifié : $size nœuds")
                            phase = "reduce"
                            checkpoint()
                        } else if (!stopRequested) {
                            requestStop("assemblage incomplet (voir le journal) ; relancer réessaie")
                        }
                    }
                    "reduce" -> {
                        val reducer = Reducer(this, net)
                        activePhase = { reducer.requestStop() }
                        if (reducer.reduce()) {
                            phase = "done"
                            log("RÉDUCTION TERMINÉE : graphe de ${state.getProperty("graph_best_size")} nœuds (graph-best.bin)")
                            checkpoint()
                        }
                    }
                    else -> {
                        log("rien à faire : le calcul est terminé (graph-best.bin)")
                        break
                    }
                }
                activePhase = null
            }
            fatal?.let { log("ERREUR : ${it.javaClass.simpleName}: ${it.message}"); it.printStackTrace() }
            checkpoint()
            log(if (stopReason.isNotEmpty()) "pause ($stopReason) : relancer reprend ici" else "fin")
            writeStatus()
            return if (fatal != null) 1 else 0
        } finally {
            ended = true
            monitor.interrupt()
            runCatching { Runtime.getRuntime().removeShutdownHook(hook) }
            File(dir, "STOP").delete()
            lockHandle.release()
        }
    }

    @Volatile
    private var ended = false

    private fun waitForEnd() {
        val deadline = System.currentTimeMillis() + 20_000
        while (!ended && System.currentTimeMillis() < deadline) Thread.sleep(100)
    }

    private fun acquireLock(): FileLock? {
        val channel = RandomAccessFile(File(dir, "run.lock"), "rw").channel
        return channel.tryLock()
    }

    private fun monitorLoop() {
        var lastStatus = 0L
        var lastCheckpoint = System.currentTimeMillis()
        try {
            while (!ended) {
                Thread.sleep(1000)
                val now = System.currentTimeMillis()
                if (File(dir, "STOP").exists()) requestStop("pause demandée (STOP)")
                if (now - lastStatus >= 30_000) {
                    lastStatus = now
                    writeStatus()
                    checkMemory()
                }
                if (!stopRequested && now - lastCheckpoint >= config.checkpointMinutes * 60_000) {
                    lastCheckpoint = now
                    checkpoint()
                }
            }
        } catch (_: InterruptedException) {
        }
    }

    private fun checkMemory() {
        val rt = Runtime.getRuntime()
        val used = rt.totalMemory() - rt.freeMemory()
        if (used > rt.maxMemory() * 0.92) {
            System.gc()
            val after = rt.totalMemory() - rt.freeMemory()
            if (after > rt.maxMemory() * 0.9) {
                log("mémoire presque pleine (${gb(after)} / ${gb(rt.maxMemory())} Go) : pause ; relancer avec plus de mémoire (WINGRAPH_XMX)")
                requestStop("mémoire")
            }
        }
    }

    // ---- checkpoints, disk ----

    private val checkpointLock = Any()

    /** Saves everything (keeps the previous files if the disk limit would be crossed). */
    fun checkpoint() {
        synchronized(checkpointLock) {
            val used = dirSize(dir)
            val treeBytes = synchronized(lock) { tree!!.nodeCount * 64L }
            val biggest = maxOf(book.fileSize(), solved.fileSize(), covered.fileSize(), treeBytes)
            if (used + biggest > config.diskLimitBytes) {
                log("LIMITE DISQUE : ${gb(used)} Go utilisés + ${gb(biggest)} Go à écrire > ${gb(config.diskLimitBytes)} Go ; pause sans sauvegarder")
                requestStop("limite disque")
                return
            }
            val start = System.currentTimeMillis()
            synchronized(lock) { tree!!.save(File(dir, "tree.bin")) }
            book.save(File(dir, "book.bin"))
            solved.save(File(dir, "solved.bin"))
            covered.save(File(dir, "covered.bin"))
            setState("elapsed_ms", elapsedMs())
            setState("saved_at", now())
            saveState()
            val total = dirSize(dir)
            log("sauvegarde (${System.currentTimeMillis() - start} ms), dossier ${gb(total)} Go")
            if (total > config.diskLimitBytes * 0.95) {
                log("LIMITE DISQUE presque atteinte (${gb(total)} / ${gb(config.diskLimitBytes)} Go) : pause")
                requestStop("limite disque")
            }
        }
    }

    private val elapsedAtStart = stateLong("elapsed_ms")

    fun elapsedMs(): Long = elapsedAtStart + (System.currentTimeMillis() - sessionStart)

    fun saveState() {
        val file = File(dir, "state.properties")
        val tmp = File(dir, "state.properties.tmp")
        tmp.writer().use { state.store(it, "wingraph state") }
        java.nio.file.Files.move(
            tmp.toPath(), file.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
        )
    }

    // ---- log, status ----

    @Synchronized
    fun log(message: String) {
        val line = "${now()} $message"
        println(line)
        logFile.appendText(line + System.lineSeparator())
    }

    fun writeStatus() {
        val text = StatusReport.render(this)
        val file = File(dir, "status.txt")
        val tmp = File(dir, "status.txt.tmp")
        tmp.writeText(text)
        runCatching {
            java.nio.file.Files.move(
                tmp.toPath(), file.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
            )
        }
    }

    val sessionMinutes: Double get() = (System.currentTimeMillis() - sessionStart) / 60_000.0

    companion object {
        fun now(): String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

        fun gb(bytes: Long): String = String.format(java.util.Locale.FRANCE, "%.2f", bytes / 1e9)

        fun dirSize(dir: File): Long = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

        fun isRunning(dir: File): Boolean {
            val f = File(dir, "run.lock")
            if (!f.exists()) return false
            RandomAccessFile(f, "rw").channel.use { ch ->
                val l = runCatching { ch.tryLock() }.getOrNull() ?: return true
                l.release()
                return false
            }
        }
    }
}
