package com.ivandsky.puzzlebox.games.tango.codec

import com.ivandsky.puzzlebox.game.CommandHistory
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GameSessionCodec
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPlace
import com.ivandsky.puzzlebox.games.tango.TangoPuzzleRef
import com.ivandsky.puzzlebox.games.tango.TangoSession
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate

/**
 * Tango session codec v3 (A19): self-describing version byte, canonical encode, the
 * puzzle riding by value so an in-progress day survives offline replay (Zip precedent).
 *
 * ```
 * 0   u8    formatVersion = 3
 *     u16   puzzleLen (BE); puzzleLen x u8   (TangoCodec bytes)
 *     u8    flags                            (bit0 completedAt present, bit1 ref present)
 *     [bit1] u8 keyTag                       (v3+; 0 = Daily, 1 = Custom)
 *     [bit1, Daily]  i64 epochDay, u8 numberPresent, [numberPresent] i64 puzzleNumber
 *     [bit1, Custom] u8 idLen; idLen x u8 puzzleId (UTF-8)
 *     u8    placedCount; placedCount x (u8 idx, u8 value)
 *     u8    hintsUsed; u8 mistakeCount
 *     i64   elapsedMs
 *     [bit0] i64 completedAt epochMilli
 *     u16   doneCount;   doneCount   x (u8 idx, u8 value, u8 previous)   oldest first
 *     u16   undoneCount; undoneCount x (u8 idx, u8 value, u8 previous)   last = next redo
 *     ...   unknown tail carried verbatim (A19)
 * ```
 * i64 fields are little-endian. A history cell byte is 0 for "empty", else the [TangoCell]
 * wire byte. v2 is the same layout without the key tag (a ref is always a Daily); v1 is v2
 * without the two history lists. Both still decode, so saves and archived replays written
 * before custom boards and before undo keep working.
 */
object TangoSessionCodec : GameSessionCodec<TangoSession> {

    const val VERSION: Int = 3

    /** Decode bound on each history list; [serialize] keeps only the newest this many. */
    const val MAX_HISTORY: Int = 10_000

    override fun serialize(session: TangoSession): ByteArray {
        val puzzleBytes = TangoCodec.encode(session.puzzle) // canonical, self-describing (A19 nesting)
        require(puzzleBytes.size <= 0xFFFF) { "puzzle blob exceeds u16 length" }
        require(session.placements.size <= 0xFF) { "v2 layout carries u8 placement count" }
        require(session.hintsUsed in 0..255) { "v2 layout carries u8 hintsUsed" }
        require(session.mistakeCount in 0..255) { "v2 layout carries u8 mistakeCount" }

        val out = ByteArrayOutputStream(puzzleBytes.size + 32)
        out.write(VERSION)
        out.write((puzzleBytes.size shr 8) and 0xFF)
        out.write(puzzleBytes.size and 0xFF)
        out.write(puzzleBytes)

        var flags = 0
        if (session.completedAt != null) flags = flags or FLAG_COMPLETED
        if (session.ref != null) flags = flags or FLAG_REF
        out.write(flags)

        session.ref?.let { writeRef(out, it.key) }

        val placements = session.placements.entries.sortedBy { it.key }
        out.write(placements.size)
        placements.forEach { (idx, cell) ->
            out.write(idx)
            out.write(cell.toByte())
        }
        out.write(session.hintsUsed)
        out.write(session.mistakeCount)
        writeI64(out, session.elapsedMs)
        session.completedAt?.let { writeI64(out, it.toEpochMilli()) }
        writeHistory(out, session.history.done)
        writeHistory(out, session.history.undone)
        out.write(session.unknownTail)
        return out.toByteArray()
    }

    override fun deserialize(bytes: ByteArray): DecodeResult<TangoSession> {
        val cursor = Cursor(bytes)
        val version = cursor.u8() ?: return malformed("empty session blob")
        if (version !in 1..VERSION) {
            return DecodeResult.Err(DecodeError.UnsupportedVersion(found = version, maxKnown = VERSION))
        }

        val puzzleLen = cursor.u16() ?: return malformed("truncated puzzle length")
        val puzzleBytes = cursor.bytes(puzzleLen) ?: return malformed("truncated puzzle blob")
        val puzzle = when (val decoded = TangoCodec.decode(puzzleBytes)) {
            is DecodeResult.Ok -> decoded.value
            is DecodeResult.Err -> return DecodeResult.Err(decoded.error)
        }

        val flags = cursor.u8() ?: return malformed("truncated session flags")
        val hasCompletedAt = flags and FLAG_COMPLETED != 0
        val hasRef = flags and FLAG_REF != 0

        var ref: TangoPuzzleRef? = null
        if (hasRef) {
            val key = if (version >= 3) readKey(cursor) else readDaily(cursor)
            ref = TangoPuzzleRef(key ?: return malformed("bad ref key"))
        }

        val placedCount = cursor.u8() ?: return malformed("truncated placement count")
        val placements = LinkedHashMap<Int, TangoCell>(placedCount)
        repeat(placedCount) {
            val idx = cursor.u8() ?: return malformed("truncated placement index")
            if (idx >= puzzle.cellCount) return malformed("placement index $idx out of range")
            val value = cursor.u8()?.let(TangoCell::fromByte) ?: return malformed("truncated placement value")
            if (idx in placements) return malformed("duplicate placement at $idx")
            placements[idx] = value
        }

        val hintsUsed = cursor.u8() ?: return malformed("truncated hints")
        val mistakes = cursor.u8() ?: return malformed("truncated mistakes")
        val elapsedMs = cursor.i64() ?: return malformed("truncated elapsedMs")
        val completedAt = if (hasCompletedAt) {
            Instant.ofEpochMilli(cursor.i64() ?: return malformed("truncated completedAt"))
        } else {
            null
        }

        var done = emptyList<TangoPlace>()
        var undone = emptyList<TangoPlace>()
        if (version >= 2) {
            done = readHistory(cursor, puzzle.cellCount) ?: return malformed("bad done history")
            undone = readHistory(cursor, puzzle.cellCount) ?: return malformed("bad undone history")
        }

        val unknownTail = cursor.remaining()
        return DecodeResult.Ok(
            TangoSession(
                ref = ref,
                puzzle = puzzle,
                placements = placements,
                hintsUsed = hintsUsed,
                mistakeCount = mistakes,
                elapsedMs = elapsedMs,
                completedAt = completedAt,
                history = CommandHistory(done, undone),
                unknownTail = unknownTail,
            ),
        )
    }

    private fun writeHistory(out: ByteArrayOutputStream, commands: List<TangoPlace>) {
        val kept = commands.takeLast(MAX_HISTORY) // oldest fall off: undo depth shrinks, state never changes
        out.write((kept.size shr 8) and 0xFF)
        out.write(kept.size and 0xFF)
        kept.forEach { cmd ->
            out.write(cmd.idx)
            out.write(cmd.value?.toByte() ?: 0)
            out.write(cmd.previous?.toByte() ?: 0)
        }
    }

    /** Null on truncation, an out-of-range index, an unknown cell byte, a no-op command or an over-long list. */
    private fun readHistory(cursor: Cursor, cellCount: Int): List<TangoPlace>? {
        val count = cursor.u16() ?: return null
        if (count > MAX_HISTORY) return null
        val commands = ArrayList<TangoPlace>(count)
        repeat(count) {
            val idx = cursor.u8() ?: return null
            if (idx >= cellCount) return null
            val valueByte = cursor.u8() ?: return null
            val previousByte = cursor.u8() ?: return null
            if (!isCellOrEmpty(valueByte) || !isCellOrEmpty(previousByte)) return null
            val value = TangoCell.fromByte(valueByte)
            val previous = TangoCell.fromByte(previousByte)
            if (value == previous) return null // a no-op is never recorded: corrupt
            commands += TangoPlace(idx, value, previous)
        }
        return commands
    }

    /** 0 = empty cell; otherwise a known [TangoCell] wire byte. */
    private fun isCellOrEmpty(byte: Int): Boolean = byte == 0 || TangoCell.fromByte(byte) != null

    private const val FLAG_COMPLETED = 1
    private const val FLAG_REF = 2

    private fun writeI64(out: ByteArrayOutputStream, value: Long) {
        repeat(8) { shift -> out.write(((value shr (shift * 8)) and 0xFF).toInt()) }
    }

    private fun writeRef(out: ByteArrayOutputStream, key: PuzzleKey) {
        when (key) {
            is PuzzleKey.Daily -> {
                out.write(KEY_DAILY)
                writeI64(out, key.date.toEpochDay())
                val number = key.puzzleNumber
                if (number == null) {
                    out.write(0)
                } else {
                    out.write(1)
                    writeI64(out, number.toLong())
                }
            }
            is PuzzleKey.Custom -> {
                val id = key.puzzleId.toByteArray(Charsets.UTF_8)
                require(id.size <= 0xFF) { "custom puzzleId exceeds u8 length" }
                out.write(KEY_CUSTOM)
                out.write(id.size)
                out.write(id)
            }
        }
    }

    /** v3+ ref: a key tag, then that key's fields. Null on truncation or an unknown tag. */
    private fun readKey(cursor: Cursor): PuzzleKey? = when (cursor.u8()) {
        KEY_DAILY -> readDaily(cursor)
        KEY_CUSTOM -> cursor.u8()?.let(cursor::bytes)?.let { PuzzleKey.Custom(it.toString(Charsets.UTF_8)) }
        else -> null
    }

    /** The Daily fields — the whole ref before v3. Null on truncation. */
    private fun readDaily(cursor: Cursor): PuzzleKey.Daily? {
        val epochDay = cursor.i64() ?: return null
        val number = when (cursor.u8() ?: return null) {
            1 -> cursor.i64() ?: return null
            else -> null
        }
        return PuzzleKey.Daily(LocalDate.ofEpochDay(epochDay), number?.toInt())
    }

    private const val KEY_DAILY = 0
    private const val KEY_CUSTOM = 1

    private fun malformed(detail: String): DecodeResult.Err =
        DecodeResult.Err(DecodeError.Malformed(detail))

    private class Cursor(private val bytes: ByteArray) {
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

        fun remaining(): ByteArray = bytes.copyOfRange(pos, bytes.size)
    }
}
