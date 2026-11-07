package com.ivandsky.puzzlebox.games.zip.codec

import com.ivandsky.puzzlebox.games.zip.Cell

/**
 * Forward reader over a [ZipSessionCodec] blob: every read returns null once the bytes run
 * out, so the codec turns truncation into `Malformed` instead of an exception. i64 fields
 * are little-endian, u16 big-endian (the session layout).
 */
internal class ZipSessionCursor(private val bytes: ByteArray) {
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

    fun cell(cellCount: Int): Cell? {
        val idx = u8() ?: return null
        return if (idx < cellCount) Cell(idx) else null
    }

    /** u8 count then that many in-range cells. */
    fun cells(cellCount: Int): List<Cell>? {
        val len = u8() ?: return null
        val cells = ArrayList<Cell>(len)
        repeat(len) { cells += cell(cellCount) ?: return null }
        return cells
    }

    fun remaining(): ByteArray = bytes.copyOfRange(pos, bytes.size)
}
