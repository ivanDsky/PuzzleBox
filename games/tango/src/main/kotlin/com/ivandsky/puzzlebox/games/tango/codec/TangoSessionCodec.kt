package com.ivandsky.puzzlebox.games.tango.codec

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GameSessionCodec
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPuzzleRef
import com.ivandsky.puzzlebox.games.tango.TangoSession
import java.io.ByteArrayOutputStream
import java.time.Instant

/**
 * Tango session codec v1 (A19): self-describing version byte, canonical encode, the
 * puzzle riding by value so an in-progress day survives offline replay (Zip precedent).
 *
 * ```
 * 0   u8    formatVersion = 1
 *     u16   puzzleLen (BE); puzzleLen x u8   (TangoCodec bytes)
 *     u8    flags                            (bit0 completedAt present, bit1 ref present)
 *     [bit1] i64 epochDay, u8 numberPresent, i64 puzzleNumber
 *     u8    placedCount; placedCount x (u8 idx, u8 value)
 *     u8    hintsUsed; u8 mistakeCount
 *     i64   elapsedMs
 *     [bit0] i64 completedAt epochMilli
 *     ...   unknown tail carried verbatim (A19)
 * ```
 */
object TangoSessionCodec : GameSessionCodec<TangoSession> {

    const val VERSION: Int = 1

    override fun serialize(session: TangoSession): ByteArray {
        val puzzleBytes = TangoCodec.encode(session.puzzle) // canonical, self-describing (A19 nesting)
        require(puzzleBytes.size <= 0xFFFF) { "puzzle blob exceeds u16 length" }
        require(session.placements.size <= 0xFF) { "v1 layout carries u8 placement count" }
        require(session.hintsUsed in 0..255) { "v1 layout carries u8 hintsUsed" }
        require(session.mistakeCount in 0..255) { "v1 layout carries u8 mistakeCount" }

        val out = ByteArrayOutputStream(puzzleBytes.size + 32)
        out.write(VERSION)
        out.write((puzzleBytes.size shr 8) and 0xFF)
        out.write(puzzleBytes.size and 0xFF)
        out.write(puzzleBytes)

        var flags = 0
        if (session.completedAt != null) flags = flags or FLAG_COMPLETED
        if (session.ref != null) flags = flags or FLAG_REF
        out.write(flags)

        session.ref?.let { ref ->
            writeI64(out, ref.date.toEpochDay())
            val number = ref.puzzleNumber
            if (number == null) {
                out.write(0)
            } else {
                out.write(1)
                writeI64(out, number.toLong())
            }
        }

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
        out.write(session.unknownTail)
        return out.toByteArray()
    }

    override fun deserialize(bytes: ByteArray): DecodeResult<TangoSession> {
        val cursor = Cursor(bytes)
        val version = cursor.u8() ?: return malformed("empty session blob")
        if (version != VERSION) {
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
            val epochDay = cursor.i64() ?: return malformed("truncated ref date")
            val hasNumber = cursor.u8() ?: return malformed("truncated ref number flag")
            val number = if (hasNumber == 1) {
                cursor.i64() ?: return malformed("truncated ref number")
            } else {
                null
            }
            ref = TangoPuzzleRef(java.time.LocalDate.ofEpochDay(epochDay), number?.toInt())
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
                unknownTail = unknownTail,
            ),
        )
    }

    private const val FLAG_COMPLETED = 1
    private const val FLAG_REF = 2

    private fun writeI64(out: ByteArrayOutputStream, value: Long) {
        repeat(8) { shift -> out.write(((value shr (shift * 8)) and 0xFF).toInt()) }
    }

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
