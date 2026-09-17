package fr.thibautperrin.tetrano.engine.util

/**
 * A `Long -> Byte` hash map over pre-allocated primitive arrays (open
 * addressing, linear probing), standing in for `LongSparseArray<Byte>` on
 * the AI's hot search path so caching a result never autoboxes a value —
 * and so a valid cached value of `-1` is never confused with "absent" (see
 * docs/PLAN.md §6.2).
 *
 * [clear] is O(1): entries are invalidated by bumping a generation counter
 * rather than by erasing the backing arrays.
 *
 * @param capacity slot count; must be a power of two, and should
 *   comfortably exceed the number of distinct keys ever inserted between
 *   two [clear] calls (entries are never evicted — a full table would spin
 *   forever probing for a free slot).
 */
class LongByteMap(capacity: Int) {

    init {
        require(capacity > 0 && (capacity and (capacity - 1)) == 0) {
            "capacity must be a power of two, was $capacity"
        }
    }

    private val mask = capacity - 1
    private val keys = LongArray(capacity)
    private val values = ByteArray(capacity)
    private val stamps = LongArray(capacity)
    private var generation = 1L

    /** Forgets every entry. O(1): does not touch the backing arrays. */
    fun clear() {
        generation++
    }

    /** The value stored for [key], or [NOT_FOUND] if none. */
    fun get(key: Long): Int {
        var index = probeStart(key)
        while (stamps[index] == generation) {
            if (keys[index] == key) return values[index].toInt()
            index = (index + 1) and mask
        }
        return NOT_FOUND
    }

    /** Stores [value] for [key], overwriting any previous value. */
    fun put(key: Long, value: Byte) {
        var index = probeStart(key)
        while (stamps[index] == generation) {
            if (keys[index] == key) {
                values[index] = value
                return
            }
            index = (index + 1) and mask
        }
        keys[index] = key
        values[index] = value
        stamps[index] = generation
    }

    private fun probeStart(key: Long): Int {
        val mixed = (key xor (key ushr 33)) * GOLDEN_RATIO_64
        return (mixed ushr 32).toInt() and mask
    }

    companion object {
        /** Sentinel returned by [get] when [key] has no cached value. Outside `Byte`'s range. */
        const val NOT_FOUND: Int = Int.MIN_VALUE
        private const val GOLDEN_RATIO_64: Long = -0x61c8864680b583ebL // 0x9E3779B97F4A7C15
    }
}
