package com.ivandsky.puzzlebox.games.queens.codec

/** [QueensSessionCodec]'s reader: u8, u16 big-endian, i64 little-endian; null = truncated. */
internal class SessionCursor(private val bytes: ByteArray) {
    var pos = 0

    fun u8(): Int? = bytes.getOrNull(pos++)?.toInt()?.and(0xFF)

    fun u16(): Int? {
        val hi = u8() ?: return null
        val lo = u8() ?: return null
        return (hi shl 8) + lo
    }

    fun i64(): Long? {
        var value = 0L
        repeat(8) { shift ->
            val byte = u8() ?: return null
            value = value or (byte.toLong() shl (shift * 8))
        }
        return value
    }

    fun bytes(len: Int): ByteArray? {
        if (pos + len > bytes.size) return null
        return bytes.copyOfRange(pos, pos + len).also { pos += len }
    }
}
