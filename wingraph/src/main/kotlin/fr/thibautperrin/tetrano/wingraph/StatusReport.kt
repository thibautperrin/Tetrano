package fr.thibautperrin.tetrano.wingraph

import java.util.Locale

/** The text of `status.txt` (French, like the rest of the tooling's reports). */
internal object StatusReport {
    fun render(run: Run): String {
        val sb = StringBuilder()
        val rt = Runtime.getRuntime()
        val phase = when (run.phase) {
            "prove" -> "1/3 preuve (arbre de pilotage + df-pn)"
            "assemble" -> "2/3 assemblage et vérification du graphe"
            "reduce" -> "3/3 réduction du nombre de nœuds (passe ${run.stateLong("reduce_pass") + 1})"
            else -> "terminé"
        }
        sb.appendLine("Tetrano wingraph — ${run.dir.absolutePath}")
        sb.appendLine("Mis à jour : ${Run.now()}   (état : ${if (run.stopping) "arrêt en cours" else "en cours"})")
        sb.appendLine("Phase : $phase")
        sb.appendLine("Temps de calcul cumulé : ${duration(run.elapsedMs())}, session : ${duration((run.sessionMinutes * 60_000).toLong())}")
        sb.appendLine()
        val s = run.stats
        val perMin = if (run.sessionMinutes > 0.1) s.jobs.get() / run.sessionMinutes else 0.0
        val lines = synchronized(run.lock) { branchLines(run.tree!!) }
        sb.append(lines)
        sb.appendLine()
        sb.appendLine("Session : ${s.jobs.get()} travaux (${fmt(perMin)}/min), ${s.provedLeaves.get()} feuilles prouvées, " +
            "${s.disprovedLeaves.get()} réfutées, ${s.expansions.get()} développées, ${s.extractionFailures.get()} extractions à refaire")
        if (run.phase == "reduce") {
            sb.appendLine("Réduction (session) : ${s.reduceTried.get()} essais, ${s.reduceImproved.get()} nœuds améliorés ; " +
                "passe en cours : ${run.stateLong("reduce_done")} nœuds faits à la dernière sauvegarde")
        }
        sb.appendLine("Livre (coups prouvés, toutes branches) : ${run.book.size} positions ; sous-arbres vérifiés : ${run.covered.size} ; " +
            "positions résolues : ${run.solved.size}")
        val v1 = run.stateLong("graph_v1_size")
        if (v1 > 0) sb.appendLine("Graphe : v1 = $v1 nœuds, meilleur = ${run.stateLong("graph_best_size")} nœuds (graph-best.bin)")
        sb.appendLine("Mémoire : ${Run.gb(rt.totalMemory() - rt.freeMemory())} / ${Run.gb(rt.maxMemory())} Go ; " +
            "disque : ${Run.gb(Run.dirSize(run.dir))} / ${Run.gb(run.config.diskLimitBytes)} Go")
        sb.appendLine("Dernière sauvegarde : ${run.state.getProperty("saved_at", "aucune")} (toutes les ${run.config.checkpointMinutes} min)")
        return sb.toString()
    }

    private fun branchLines(tree: TopTree): String {
        val sb = StringBuilder()
        val root = tree.root
        sb.appendLine("Racine (coin joué, à l'adversaire) : ${stateName(root)}  pn ${num(root.pn)}  dn ${num(root.dn)} ; arbre de pilotage : ${tree.nodeCount} nœuds")
        val kids = root.children
        if (root.state == TNode.PROVED) {
            sb.appendLine("Les 12 réponses sont prouvées gagnantes pour l'ordinateur.")
        } else if (kids == null) {
            sb.appendLine("Branches : pas encore développées")
        } else {
            sb.appendLine("Branches (réponse de l'adversaire au coin 0) :")
            sb.appendLine("  case  état        pn          dn          gain estimé  nœuds sous la branche")
            for (k in kids.sortedBy { it.move }) {
                val below = countBelow(k)
                sb.appendLine(String.format(Locale.FRANCE, "  %4d  %-10s  %-10s  %-10s  %-11s  %s",
                    k.move, stateName(k), num(k.pn), num(k.dn),
                    if (k.value.isNaN()) "-" else String.format(Locale.FRANCE, "%.3f", k.value),
                    if (k.state == TNode.PROVED) "prouvée, ~${k.size} nœuds de graphe" else "$below dans l'arbre"))
            }
        }
        return sb.toString()
    }

    private fun countBelow(n: TNode): Int {
        var c = 0
        n.children?.forEach { c += 1 + countBelow(it) }
        return c
    }

    private fun stateName(n: TNode) = when (n.state) {
        TNode.PROVED -> "PROUVÉE"
        TNode.DISPROVED -> "RÉFUTÉE"
        TNode.BUSY -> "en calcul"
        TNode.INNER -> "en cours"
        else -> "à faire"
    }

    private fun num(v: Long) = if (v >= TNode.INF) "∞" else v.toString()

    private fun fmt(v: Double) = String.format(Locale.FRANCE, "%.1f", v)

    fun duration(ms: Long): String {
        val minutes = ms / 60_000
        return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${String.format("%02d", minutes % 60)}"
    }
}
