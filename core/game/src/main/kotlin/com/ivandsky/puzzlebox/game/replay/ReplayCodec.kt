package com.ivandsky.puzzlebox.game.replay

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.model.GameId
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * The one byte format of a replay; the in-progress snapshot (outcome Open) and the sealed replay
 * share it. Stored form: `u8 formatVersion = 1`, then the raw deflate of this layout:
 *
 * ```
 * u8      gameIdLen, ASCII gameId
 * u8      outcome: 0 Open, 1 Solved, 2 GaveUp
 * varint  endMs
 * varint  moveCount
 * repeat  varint dtMs                 // delta from the previous stamp, ms; first delta is from 0
 *         u8     kind<<3 | argc       // argc 7 = a varint argc follows
 *         varint args[argc]           // zig-zag, so negative ints are legal
 * ```
 *
 * The sealed size cap is not checked here: [com.ivandsky.puzzlebox.model.SolveReplay.of] owns it.
 */
object ReplayCodec {

    const val MAX_MOVES: Int = 4_096
    const val MAX_ARGS: Int = 64
    const val MAX_KIND: Int = 31
    const val MAX_INFLATED_BYTES: Int = 32_768

    private const val VERSION = 1
    private const val MAX_ID_CHARS = 0xFF
    private const val MAX_ASCII = 0x7F
    private const val KIND_SHIFT = 3
    private const val ARGC_FOLLOWS = 7
    private const val MAX_ZIGZAG_INT = 0xFFFF_FFFFL

    /** Null when any cap is breached. Never truncates. Never throws. */
    fun encode(record: ReplayRecord): ByteArray? {
        val id = record.game.value
        if (id.isEmpty() || id.length > MAX_ID_CHARS || id.any { it.code > MAX_ASCII }) return null
        if (record.endMs < 0 || record.moves.size > MAX_MOVES) return null
        val layout = LayoutWriter()
        layout.u8(id.length)
        id.forEach { layout.u8(it.code) }
        layout.u8(byteOf(record.outcome))
        layout.varint(record.endMs)
        layout.varint(record.moves.size.toLong())
        var previousMs = 0L
        for (stamped in record.moves) {
            val move = stamped.move
            if (stamped.atMs < previousMs || move.kind !in 0..MAX_KIND || move.args.size > MAX_ARGS) return null
            layout.varint(stamped.atMs - previousMs)
            previousMs = stamped.atMs
            layout.move(move)
            if (layout.size > MAX_INFLATED_BYTES) return null
        }
        return deflate(layout)
    }

    /** Err for anything this build did not author or that breaches a cap. Never throws. */
    fun decode(bytes: ByteArray): DecodeResult<ReplayRecord> {
        if (bytes.isEmpty()) return malformed("empty")
        val version = bytes[0].toInt() and 0xFF
        if (version > VERSION) return DecodeResult.Err(DecodeError.UnsupportedVersion(found = version, maxKnown = VERSION))
        if (version != VERSION) return malformed("format version $version")
        // One byte of room past the cap tells a layout of exactly the cap from a longer one.
        val layout = ByteArray(MAX_INFLATED_BYTES + 1)
        val size = inflate(bytes, layout)
        if (size < 0) return malformed("deflate stream is corrupt, cut short or followed by more bytes")
        if (size > MAX_INFLATED_BYTES) return malformed("inflates past $MAX_INFLATED_BYTES bytes")
        return record(LayoutCursor(layout, size))
    }

    private fun LayoutWriter.move(move: ReplayMove) {
        val argc = move.args.size
        if (argc < ARGC_FOLLOWS) {
            u8((move.kind shl KIND_SHIFT) or argc)
        } else {
            u8((move.kind shl KIND_SHIFT) or ARGC_FOLLOWS)
            varint(argc.toLong())
        }
        for (arg in move.args) varint(((arg shl 1) xor (arg shr 31)).toLong() and MAX_ZIGZAG_INT)
    }

    private fun deflate(layout: LayoutWriter): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        try {
            deflater.setInput(layout.bytes, 0, layout.size)
            deflater.finish()
            var stored = ByteArray(layout.size / 2 + 64)
            stored[0] = VERSION.toByte()
            var size = 1
            while (!deflater.finished()) {
                if (size == stored.size) stored = stored.copyOf(size * 2)
                size += deflater.deflate(stored, size, stored.size - size)
            }
            return stored.copyOf(size)
        } finally {
            deflater.end()
        }
    }

    /** The inflated size, never past the size of [into]; -1 for a stream that is corrupt, cut short or followed by more bytes. */
    private fun inflate(stored: ByteArray, into: ByteArray): Int {
        val inflater = Inflater(true)
        try {
            inflater.setInput(stored, 1, stored.size - 1)
            var size = 0
            while (size < into.size) {
                val inflated = inflater.inflate(into, size, into.size - size)
                if (inflated == 0) break
                size += inflated
            }
            if (size == into.size) return size
            return if (inflater.finished() && inflater.remaining == 0) size else -1
        } catch (e: DataFormatException) {
            return -1
        } finally {
            inflater.end()
        }
    }

    private fun record(layout: LayoutCursor): DecodeResult<ReplayRecord> {
        val idLength = layout.u8()
        if (idLength <= 0) return malformed("game id length")
        val id = layout.ascii(idLength) ?: return malformed("game id")
        val outcome = outcomeOf(layout.u8()) ?: return malformed("outcome")
        val endMs = layout.varint()
        if (endMs < 0) return malformed("end time")
        val moveCount = layout.varint()
        if (moveCount < 0 || moveCount > MAX_MOVES) return malformed("move count")
        val moves = ArrayList<StampedMove>(moveCount.toInt())
        var atMs = 0L
        for (index in 0 until moveCount.toInt()) {
            val deltaMs = layout.varint()
            if (deltaMs < 0 || deltaMs > Long.MAX_VALUE - atMs) return malformed("delta of move $index")
            atMs += deltaMs
            moves += StampedMove(atMs, layout.move() ?: return malformed("move $index"))
        }
        if (!layout.isExhausted) return malformed("trailing bytes")
        return DecodeResult.Ok(ReplayRecord(GameId(id), outcome, endMs, moves))
    }

    private fun LayoutCursor.move(): ReplayMove? {
        val head = u8()
        if (head < 0) return null
        var argc = head and ARGC_FOLLOWS
        if (argc == ARGC_FOLLOWS) {
            val followed = varint()
            if (followed < ARGC_FOLLOWS || followed > MAX_ARGS) return null
            argc = followed.toInt()
        }
        val args = IntArray(argc)
        for (index in 0 until argc) {
            val zigzag = varint()
            if (zigzag < 0 || zigzag > MAX_ZIGZAG_INT) return null
            args[index] = ((zigzag ushr 1) xor -(zigzag and 1)).toInt()
        }
        return ReplayMove(head ushr KIND_SHIFT, args)
    }

    // Not the ordinal: reordering the enum must not change bytes already stored.
    private fun byteOf(outcome: ReplayOutcome): Int = when (outcome) {
        ReplayOutcome.Open -> 0
        ReplayOutcome.Solved -> 1
        ReplayOutcome.GaveUp -> 2
    }

    private fun outcomeOf(byte: Int): ReplayOutcome? = when (byte) {
        0 -> ReplayOutcome.Open
        1 -> ReplayOutcome.Solved
        2 -> ReplayOutcome.GaveUp
        else -> null
    }

    private fun malformed(detail: String) = DecodeResult.Err(DecodeError.Malformed("replay: $detail"))
}

/** The layout bytes under construction; [bytes] is valid up to [size]. */
private class LayoutWriter {

    var bytes = ByteArray(256)
        private set
    var size = 0
        private set

    fun u8(value: Int) {
        if (size == bytes.size) bytes = bytes.copyOf(size * 2)
        bytes[size++] = value.toByte()
    }

    /** Unsigned LEB128 of a non-negative value. */
    fun varint(value: Long) {
        var rest = value
        while (rest >= 0x80) {
            u8((rest and 0x7F).toInt() or 0x80)
            rest = rest ushr 7
        }
        u8(rest.toInt())
    }
}

/**
 * Reads the first [size] bytes of [bytes]. A failed read is a negative number rather than a
 * null, so the 4 096-move loop boxes nothing.
 */
private class LayoutCursor(private val bytes: ByteArray, private val size: Int) {

    private var position = 0

    val isExhausted: Boolean get() = position == size

    /** -1 at the end. */
    fun u8(): Int = if (position < size) bytes[position++].toInt() and 0xFF else -1

    /** -1 when cut short or wider than 63 bits, so a value read is never negative. */
    fun varint(): Long {
        var value = 0L
        var shift = 0
        while (shift < Long.SIZE_BITS - 1) {
            val byte = u8()
            if (byte < 0) return -1
            value = value or ((byte and 0x7F).toLong() shl shift)
            if (byte and 0x80 == 0) return value
            shift += 7
        }
        return -1
    }

    /** Null unless [length] bytes are left and every one is ASCII. */
    fun ascii(length: Int): String? {
        if (length > size - position) return null
        // A lenient decode would swap a non-ASCII byte for U+FFFD and so read back a different id.
        for (index in position until position + length) if (bytes[index] < 0) return null
        return String(bytes, position, length, Charsets.US_ASCII).also { position += length }
    }
}
