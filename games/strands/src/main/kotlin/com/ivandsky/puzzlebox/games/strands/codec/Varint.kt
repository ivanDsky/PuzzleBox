package com.ivandsky.puzzlebox.games.strands.codec

import java.io.ByteArrayOutputStream

/**
 * Byte writer for both Strands codecs: unsigned LEB128 varints, zigzag varints for values
 * that may be negative, and varint-length-prefixed UTF-8 strings.
 */
internal class ByteWriter {

    private val out = ByteArrayOutputStream()

    fun u8(value: Int) = out.write(value)

    fun varint(value: Long) {
        require(value >= 0) { "varint takes non-negative values, got $value" }
        var v = value
        while (v >= 0x80) {
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        out.write(v.toInt())
    }

    fun varint(value: Int) = varint(value.toLong())

    fun zigzag(value: Long) = varint((value shl 1) xor (value shr 63))

    fun string(value: String) = bytes(value.toByteArray(Charsets.UTF_8))

    fun bytes(value: ByteArray) {
        varint(value.size)
        out.write(value)
    }

    fun toByteArray(): ByteArray = out.toByteArray()
}

/**
 * Read cursor mirroring [ByteWriter]. Every read returns null on truncation or an
 * out-of-range value, so decoders turn any null into [com.ivandsky.puzzlebox.game.DecodeError.Malformed].
 */
internal class ByteCursor(private val bytes: ByteArray) {

    private var pos = 0

    val isExhausted: Boolean get() = pos == bytes.size

    fun u8(): Int? = bytes.getOrNull(pos)?.toInt()?.and(0xFF)?.also { pos++ }

    fun varint(): Long? {
        var result = 0L
        var shift = 0
        while (shift < 63) {
            val byte = u8() ?: return null
            result = result or ((byte and 0x7F).toLong() shl shift)
            if (byte and 0x80 == 0) return result
            shift += 7
        }
        return null
    }

    /** A varint that fits an Int; null past [Int.MAX_VALUE]. */
    fun int(): Int? = varint()?.takeIf { it <= Int.MAX_VALUE }?.toInt()

    /**
     * A collection size. Each element takes at least one byte, so a count larger than what is
     * left is malformed — checked before any caller allocates for it.
     */
    fun count(): Int? = int()?.takeIf { it <= bytes.size - pos }

    fun zigzag(): Long? = varint()?.let { (it ushr 1) xor -(it and 1) }

    fun bytes(): ByteArray? {
        val len = count() ?: return null
        return bytes.copyOfRange(pos, pos + len).also { pos += len }
    }

    fun string(): String? = bytes()?.toString(Charsets.UTF_8)
}
