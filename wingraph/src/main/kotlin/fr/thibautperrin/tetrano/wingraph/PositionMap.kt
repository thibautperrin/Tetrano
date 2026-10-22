package fr.thibautperrin.tetrano.wingraph

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * Map from a position (attacker, defender bitboards) to a non-negative Int,
 * open addressing with linear probing; grows at 60 % load. About 20 bytes
 * per slot, no object per entry. Thread-safe (every method is synchronized).
 */
class PositionMap(log2Capacity: Int = 12) {
    private var keyA = LongArray(1 shl log2Capacity)
    private var keyD = LongArray(1 shl log2Capacity)

    /** -1 = empty slot. */
    private var values = IntArray(1 shl log2Capacity).also { it.fill(-1) }
    private var mask = (1 shl log2Capacity) - 1

    @get:Synchronized
    var size: Int = 0
        private set

    private fun slot(a: Long, d: Long): Int {
        var h = a * -0x61c8864680b583ebL xor java.lang.Long.rotateLeft(d * -0x3d4d51c2d82b14b1L, 29)
        h = h xor (h ushr 31)
        return h.toInt() and mask
    }

    /** The value of (a, d), or -1. */
    @Synchronized
    fun get(a: Long, d: Long): Int {
        var i = slot(a, d)
        while (true) {
            val v = values[i]
            if (v < 0) return -1
            if (keyA[i] == a && keyD[i] == d) return v
            i = (i + 1) and mask
        }
    }

    /** Sets (a, d) to [value] (>= 0). */
    @Synchronized
    fun put(a: Long, d: Long, value: Int) {
        insert(a, d, value, overwrite = true)
    }

    /** Sets (a, d) to [value] unless present; returns the value now stored. */
    @Synchronized
    fun putIfAbsent(a: Long, d: Long, value: Int): Int = insert(a, d, value, overwrite = false)

    private fun insert(a: Long, d: Long, value: Int, overwrite: Boolean): Int {
        require(value >= 0)
        var i = slot(a, d)
        while (true) {
            val v = values[i]
            if (v < 0) break
            if (keyA[i] == a && keyD[i] == d) {
                if (overwrite) values[i] = value
                return values[i]
            }
            i = (i + 1) and mask
        }
        keyA[i] = a
        keyD[i] = d
        values[i] = value
        size++
        if (size * 5L > (mask + 1L) * 3L) grow()
        return value
    }

    private fun grow() {
        val oldA = keyA
        val oldD = keyD
        val oldV = values
        val capacity = (mask + 1) * 2
        keyA = LongArray(capacity)
        keyD = LongArray(capacity)
        values = IntArray(capacity).also { it.fill(-1) }
        mask = capacity - 1
        for (j in oldV.indices) {
            if (oldV[j] < 0) continue
            var i = slot(oldA[j], oldD[j])
            while (values[i] >= 0) i = (i + 1) and mask
            keyA[i] = oldA[j]
            keyD[i] = oldD[j]
            values[i] = oldV[j]
        }
    }

    @Synchronized
    fun clear() {
        if (size == 0) return
        values.fill(-1)
        size = 0
    }

    @Synchronized
    fun forEach(action: (a: Long, d: Long, value: Int) -> Unit) {
        for (i in values.indices) if (values[i] >= 0) action(keyA[i], keyD[i], values[i])
    }

    /** Bytes [save] writes. */
    @Synchronized
    fun fileSize(): Long = 8L + size * 20L

    @Synchronized
    fun save(file: File) {
        writeAtomically(file) { out ->
            out.writeInt(MAGIC)
            out.writeInt(size)
            for (i in values.indices) {
                if (values[i] < 0) continue
                out.writeLong(keyA[i])
                out.writeLong(keyD[i])
                out.writeInt(values[i])
            }
        }
    }

    companion object {
        private const val MAGIC = 0x504D4150 // "PMAP"

        fun load(file: File): PositionMap {
            if (!file.exists()) return PositionMap()
            DataInputStream(file.inputStream().buffered(1 shl 16)).use { input ->
                require(input.readInt() == MAGIC) { "$file: not a position map" }
                val n = input.readInt()
                var log2 = 12
                while ((1L shl log2) * 3 < n * 5L + 5) log2++
                val map = PositionMap(log2)
                repeat(n) { map.put(input.readLong(), input.readLong(), input.readInt()) }
                return map
            }
        }
    }
}

/** Writes [file] through a temporary file renamed at the end: a crash leaves the old version. */
fun writeAtomically(file: File, body: (DataOutputStream) -> Unit) {
    val tmp = File(file.parentFile, file.name + ".tmp")
    DataOutputStream(tmp.outputStream().buffered(1 shl 16)).use { body(it) }
    java.nio.file.Files.move(
        tmp.toPath(), file.toPath(),
        java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
    )
}
