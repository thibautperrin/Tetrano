package fr.thibautperrin.tetrano.data.legacy

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * The legacy app's ad hoc binary format for `challengeVars.bin`. Ports
 * `thibautperrin.tetrano.packageChallenge.BinaryFileReadingWriting`
 * exactly (see docs/GLOSSARY.md and docs/PLAN.md P5) — this is read-only
 * data now, but both directions are ported (and tested against each
 * other) for the same fidelity reasons as the engine port.
 *
 * Integers are **sign-magnitude**, not two's complement: 4 bytes,
 * little-endian magnitude in the low 31 bits, with the top bit of the 4th
 * byte as the sign. Byte arrays are length-prefixed (a single byte, 0..255
 * — 0 and "no array" are indistinguishable, both read back as `null`).
 */
internal object LegacyBinaryIO {

    fun readInt(input: InputStream): Int {
        val byte1 = input.read()
        val byte2 = input.read()
        val byte3 = input.read()
        val byte4 = input.read()
        var number = byte1 + 256 * byte2 + 65536 * byte3 + 16777216 * (byte4 % 128)
        if (byte4 >= 128) number = -number
        return number
    }

    fun writeInt(output: OutputStream, number: Int) {
        val negative = number < 0
        val magnitude = if (negative) -number else number
        val byte1 = magnitude % 256
        val byte2 = (magnitude / 256) % 256
        val byte3 = (magnitude / 65536) % 256
        var byte4 = magnitude / 16777216
        if (negative) byte4 += 128
        output.write(byte1)
        output.write(byte2)
        output.write(byte3)
        output.write(byte4)
    }

    fun readBool(input: InputStream): Boolean = when (val value = input.read()) {
        1 -> true
        0 -> false
        else -> throw IOException("Problem in readBool: read value different from 0 and 1: $value")
    }

    fun writeBool(output: OutputStream, value: Boolean) {
        output.write(if (value) 1 else 0)
    }

    /** A `null` array and a 0-length array are indistinguishable on disk — both round-trip as `null`. */
    fun writeByteArray(output: OutputStream, bytes: ByteArray?) {
        if (bytes == null) {
            output.write(0)
            return
        }
        val length = bytes.size
        if (length > 255) {
            throw IOException("This method is not designed for arrays of more than 255 bytes.")
        }
        output.write(length)
        output.write(bytes)
    }

    fun readByteArray(input: InputStream): ByteArray? {
        val length = input.read()
        // length == 0 means "no array" per the format (see the class doc).
        // length == -1 means the stream ended early (a truncated/corrupt
        // file) — the original would build a negative-size array and crash
        // here; treating it as "no array" instead lets import validation
        // (see LegacyDataImporter) catch and report the real problem.
        if (length <= 0) return null
        val bytes = ByteArray(length)
        val read = input.read(bytes, 0, length)
        if (read == -1) {
            throw IOException("Byte array is not complete in the file.")
        }
        return bytes
    }

    fun readByte(input: InputStream): Byte = input.read().toByte()

    fun writeByte(output: OutputStream, value: Byte) {
        output.write(value.toInt())
    }
}
