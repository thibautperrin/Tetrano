package fr.thibautperrin.tetrano.engine

/**
 * A set of bytes in `0 until capacity` supporting O(1) [add]/[remove]/
 * [contains], and O(size) traversal via [next] — a doubly linked list over
 * two flat arrays, used instead of a `HashSet` for the AI's hot path (see
 * docs/PLAN.md §6.2).
 *
 * Ports `thibautperrin.tetrano.Intelligence2.Ensemble` (see docs/GLOSSARY.md).
 *
 * **Traversal order is load-bearing**: the underlying structure is a stack
 * — [next] started from `-1` returns the *most recently added* element
 * first, then walks toward earlier insertions. The AI's fork search relies
 * on this exact order in several places (first match wins); see
 * docs/PLAN.md §6.1.1. Do not "fix" this into insertion order.
 */
class LineSet(capacity: Byte, filled: Boolean) {

    // "next towards more recent" / "next towards older" links of a doubly
    // linked list; -1 means "no link". Ports posSuiv / posPrec.
    private val towardRecent = ByteArray(capacity.toInt())
    private val towardOlder = ByteArray(capacity.toInt())

    // The most recently added element (the "top of the stack"), or -1 if
    // empty. Ports pointeur.
    private var head: Byte = -1
    private var count: Int = 0

    init {
        if (filled) {
            fill()
        } else {
            towardOlder.fill(-1)
            towardRecent.fill(-1)
        }
    }

    /** Copy constructor: an independent clone of [other]. Ports `Ensemble(Ensemble)`. */
    constructor(other: LineSet) : this(other.towardRecent.size.toByte(), false) {
        System.arraycopy(other.towardRecent, 0, towardRecent, 0, towardRecent.size)
        System.arraycopy(other.towardOlder, 0, towardOlder, 0, towardOlder.size)
        head = other.head
        count = other.count
    }

    fun contains(b: Byte): Boolean = towardRecent[b.toInt()] != NONE

    /** Adds [b] to the set (no-op if already present). */
    fun add(b: Byte) {
        if (contains(b)) return
        count++
        val bi = b.toInt()
        if (head == NONE) {
            head = b
            towardOlder[bi] = NONE
            towardRecent[bi] = b
        } else {
            towardRecent[head.toInt()] = b
            towardOlder[bi] = head
            head = b
            towardRecent[bi] = b
        }
    }

    /** Removes [b] from the set (no-op if absent). */
    fun remove(b: Byte) {
        if (!contains(b)) return
        count--
        val bi = b.toInt()
        val older = towardOlder[bi]
        val recent = towardRecent[bi]
        when {
            older == NONE && recent == b -> {
                // b was the only element.
                head = NONE
            }
            recent == b -> {
                // b is the head (most recent), and not the only element.
                head = older
                towardRecent[head.toInt()] = head
            }
            older == NONE -> {
                // b is the oldest element, but not the only one.
                towardOlder[recent.toInt()] = NONE
            }
            else -> {
                towardRecent[older.toInt()] = recent
                towardOlder[recent.toInt()] = older
            }
        }
        towardRecent[bi] = NONE
        towardOlder[bi] = NONE
    }

    /**
     * Traversal cursor: pass `-1` to get the most recently added element
     * (or `-1` if the set is empty), then pass each result back in to walk
     * towards earlier insertions until `-1` comes back.
     */
    fun next(b: Byte): Byte = if (b == NONE) head else towardOlder[b.toInt()]

    fun clear() {
        val capacity = towardOlder.size
        if (count > capacity / 4) {
            head = NONE
            towardOlder.fill(-1)
            towardRecent.fill(-1)
            count = 0
        } else {
            var b = next(NONE)
            while (b != NONE) {
                val current = b
                b = next(b)
                remove(current)
            }
        }
    }

    /** Resets the set to contain every value `0 until capacity`, in order. */
    fun fill() {
        val capacity = towardOlder.size
        for (i in 0 until capacity) {
            towardOlder[i] = (i - 1).toByte()
            towardRecent[i] = (i + 1).toByte()
        }
        towardRecent[capacity - 1] = (capacity - 1).toByte()
        head = (capacity - 1).toByte()
        count = capacity
    }

    fun length(): Int = count

    private companion object {
        const val NONE: Byte = -1
    }
}
