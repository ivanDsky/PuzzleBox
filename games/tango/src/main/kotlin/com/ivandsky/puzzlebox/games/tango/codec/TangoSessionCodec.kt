package com.ivandsky.puzzlebox.games.tango.codec

import com.ivandsky.puzzlebox.game.CommandHistory
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GameSessionCodec
import com.ivandsky.puzzlebox.game.PuzzleKeyTag
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoClear
import com.ivandsky.puzzlebox.games.tango.TangoCommand
import com.ivandsky.puzzlebox.games.tango.TangoPlace
import com.ivandsky.puzzlebox.games.tango.TangoPuzzleRef
import com.ivandsky.puzzlebox.games.tango.TangoSession
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.io.ByteArrayOutputStream
import java.time.Instant

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
 *     [bit1, Custom] u8 idLen (1..255); idLen x u8 puzzleId (UTF-8)
 *     u8    placedCount; placedCount x (u8 idx, u8 value)
 *     u8    hintsUsed; u8 mistakeCount
 *     i64   elapsedMs
 *     [bit0] i64 completedAt epochMilli
 *     u16   doneCount;   doneCount   x command   oldest first
 *     u16   undoneCount; undoneCount x command   last = next redo
 *       command = Place (u8 idx, u8 value, u8 previous)
 *               | Clear (u8 0xFF, u8 n, n x (u8 idx, u8 value))   the wiped placements
 *     ...   unknown tail carried verbatim (A19)
 * ```
 * i64 fields are little-endian. A history cell byte is 0 for "empty", else the [TangoCell]
 * wire byte. 0xFF can never be a cell index (boards are at most 11x11, so indices stop at 120),
 * so it marks a Clear; saves without one are byte-identical to before it existed. v2 is the same layout without the key tag (a ref is always a Daily); v1 is v2
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
            ref = when (val key = readKey(cursor, version)) {
                is DecodeResult.Ok -> TangoPuzzleRef(key.value)
                is DecodeResult.Err -> return key
            }
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

        var done = emptyList<TangoCommand>()
        var undone = emptyList<TangoCommand>()
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

    private fun writeHistory(out: ByteArrayOutputStream, commands: List<TangoCommand>) {
        val kept = commands.takeLast(MAX_HISTORY) // oldest fall off: undo depth shrinks, state never changes
        out.write((kept.size shr 8) and 0xFF)
        out.write(kept.size and 0xFF)
        kept.forEach { cmd ->
            when (cmd) {
                is TangoPlace -> {
                    out.write(cmd.idx)
                    out.write(cmd.value?.toByte() ?: 0)
                    out.write(cmd.previous?.toByte() ?: 0)
                }

                is TangoClear -> {
                    val wiped = cmd.wiped.entries.sortedBy { it.key }
                    out.write(CLEAR_MARK)
                    out.write(wiped.size)
                    wiped.forEach { (idx, cell) ->
                        out.write(idx)
                        out.write(cell.toByte())
                    }
                }
            }
        }
    }

    /** Null on truncation, an out-of-range index, an unknown cell byte, a no-op command or an over-long list. */
    private fun readHistory(cursor: Cursor, cellCount: Int): List<TangoCommand>? {
        val count = cursor.u16() ?: return null
        if (count > MAX_HISTORY) return null
        val commands = ArrayList<TangoCommand>(count)
        repeat(count) {
            val idx = cursor.u8() ?: return null
            if (idx == CLEAR_MARK) {
                commands += readClear(cursor, cellCount) ?: return null
                return@repeat
            }
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

    /** A Clear's body after its mark: null on truncation, an index out of range or repeated, an empty or non-cell entry. */
    private fun readClear(cursor: Cursor, cellCount: Int): TangoClear? {
        val n = cursor.u8() ?: return null
        if (n == 0 || n > cellCount) return null // clearing nothing is never recorded: corrupt
        val wiped = LinkedHashMap<Int, TangoCell>(n)
        repeat(n) {
            val idx = cursor.u8() ?: return null
            if (idx >= cellCount || idx in wiped) return null
            wiped[idx] = cursor.u8()?.let(TangoCell::fromByte) ?: return null
        }
        return TangoClear(wiped)
    }

    /** 0 = empty cell; otherwise a known [TangoCell] wire byte. */
    private fun isCellOrEmpty(byte: Int): Boolean = byte == 0 || TangoCell.fromByte(byte) != null

    /** History entry first byte that marks a Clear; never a cell index. */
    private const val CLEAR_MARK = 0xFF

    private const val FLAG_COMPLETED = 1
    private const val FLAG_REF = 2

    private fun writeI64(out: ByteArrayOutputStream, value: Long) {
        repeat(8) { shift -> out.write(((value shr (shift * 8)) and 0xFF).toInt()) }
    }

    private fun writeRef(out: ByteArrayOutputStream, key: PuzzleKey) =
        PuzzleKeyTag.write(key, out::write) { daily ->
            writeI64(out, daily.date.toEpochDay())
            val number = daily.puzzleNumber
            if (number == null) {
                out.write(0)
            } else {
                out.write(1)
                writeI64(out, number.toLong())
            }
        }

    /** The Daily fields are the whole ref before v3. */
    private fun readKey(cursor: Cursor, version: Int): DecodeResult<PuzzleKey> =
        PuzzleKeyTag.read(version, taggedSince = V_KEY_TAG, u8 = cursor::u8) {
            val epochDay = cursor.i64() ?: return@read malformed("truncated ref date")
            val number = when (cursor.u8() ?: return@read malformed("truncated ref number flag")) {
                1 -> cursor.i64() ?: return@read malformed("truncated ref number")
                else -> null
            }
            PuzzleKeyTag.daily(epochDay, number)
        }

    /** The first format whose ref carries a [PuzzleKeyTag]. */
    private const val V_KEY_TAG = 3

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
