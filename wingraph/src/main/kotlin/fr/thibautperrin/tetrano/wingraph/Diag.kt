package fr.thibautperrin.tetrano.wingraph

import java.io.File

/** Prints the top tree around one root branch: `Diag <tree.bin> <reply> [depth]`. */
object DiagMain {
    private fun lbl(c: Int) = "${"ABCD"[c and 3]}${((c shr 2) and 3) + 1}/${(c shr 4) + 1}"

    private fun count(n: TNode): Long {
        var s = 1L
        n.children?.forEach { s += count(it) }
        return s
    }

    private fun num(x: Long) = if (x >= TNode.INF) "inf" else x.toString()

    private fun show(n: TNode, depth: Int, maxDepth: Int, width: Int) {
        val kids = n.children ?: return
        val order = if (n.isOr) kids.sortedBy { it.pn } else kids.sortedBy { it.dn }
        for (k in order.take(width)) {
            val st = listOf("leaf", "busy", "inner", "PROVED", "disproved")[k.state]
            println("  ".repeat(depth) + "${if (n.isOr) "A" else "D"} ${lbl(k.move)} pn=${num(k.pn)} dn=${num(k.dn)} $st " +
                "visits=${k.visits} sub=${count(k)} v=${"%.3f".format(k.value)} pending=${k.pendingCount}")
            if (depth < maxDepth && !k.solved) show(k, depth + 1, maxDepth, if (depth < 1) width else 4)
        }
        val solved = kids.count { it.state == TNode.PROVED }
        println("  ".repeat(depth) + "($solved/${kids.size} proved, pending ${n.pendingCount})")
    }

    @JvmStatic
    fun main(args: Array<String>) {
        java.util.Locale.setDefault(java.util.Locale.ROOT)
        val tree = TopTree.load(File(args[0]))
        val reply = args[1].toInt()
        val maxDepth = args.getOrNull(2)?.toInt() ?: 3
        val b = tree.root.children!!.first { it.move == reply }
        println("branch ${lbl(reply)} pn=${num(b.pn)} dn=${num(b.dn)} sub=${count(b)} pending=${b.pendingCount}")
        if (args.getOrNull(3) == "path") {
            var n: TNode = b
            var depth = 0
            val form = fr.thibautperrin.tetrano.engine.wingraph.CanonicalForm()
            while (true) {
                val kids = n.children ?: break
                val open = kids.filter { !it.solved }
                val proved = kids.count { it.state == TNode.PROVED }
                println("depth $depth ${if (n.isOr) "OR" else "AND"} rings=${java.lang.Long.bitCount(n.a or n.d)} pn=${num(n.pn)} dn=${num(n.dn)} " +
                    "kids=${kids.size} proved=$proved open=${open.size} pending=${n.pendingCount} sub=${count(n)} a=${java.lang.Long.toHexString(n.a)} d=${java.lang.Long.toHexString(n.d)}")
                val next = (if (n.isOr) open.minByOrNull { it.pn } else open.minByOrNull { it.dn }) ?: break
                n = next
                depth++
            }
            return
        }
        show(b, 1, maxDepth, 12)
    }
}
