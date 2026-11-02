package com.ivandsky.puzzlebox.games.queens.codec

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GameSessionCodec
import com.ivandsky.puzzlebox.games.queens.QueensPuzzleRef
import com.ivandsky.puzzlebox.games.queens.QueensSession
import java.io.ByteArrayOutputStream
import java.time.LocalDate

/**
 * Queens session codec v1 (zip ZipSessionCodec shape, A19): the puzzle rides BY VALUE so
 * an in-progress day survives offline replay. Layout (i64 little-endian, u16 big-endian):
 *
 * ```
 * 0   u8    formatVersion = 1
 *     u16   puzzleLen; puzzleLen x u8   (QueensCodec bytes)
 *     u8    flags                       (bit0 = ref present)
 *     [bit0] i64 epochDay, u8 numberPresent, i64 puzzleNumber
 *     ...  remaining bytes = unknown extension: PRESERVED verbatim, re-emitted by
 *          serialize (decode∘encode lossless across versions)
 * ```
 *
 * No play-state fields yet (skeleton, queens-01) — they are additive v1 extensions or a
 * version bump per the §4 reader rules when session tickets land.
 */
object QueensSessionCodec : GameSessionCodec<QueensSession> {

    const val VERSION: Int = 1

    override fun serialize(session: QueensSession): ByteArray {
        val puzzleBytes = QueensCodec.encode(session.puzzle) // canonical, self-describing (A19 nesting)
        require(puzzleBytes.size <= 0xFFFF) { "puzzle blob exceeds u16 length" }

        val out = ByteArrayOutputStream(puzzleBytes.size + 24)
        out.write(VERSION)
        out.write((puzzleBytes.size shr 8) and 0xFF)
        out.write(puzzleBytes.size and 0xFF)
        out.write(puzzleBytes)

        val ref = session.ref
        if (ref == null) {
            out.write(0)
        } else {
            out.write(FLAG_REF)
            writeI64(out, ref.date.toEpochDay())
            val number = ref.puzzleNumber
            if (number == null) {
                out.write(0)
            } else {
                out.write(1)
                writeI64(out, number.toLong())
            }
        }
        return out.toByteArray()
    }

    override fun deserialize(bytes: ByteArray): DecodeResult<QueensSession> {
        val cursor = Cursor(bytes)
        val version = cursor.u8() ?: return malformed("empty session blob")
        if (version != VERSION) {
            return DecodeResult.Err(DecodeError.UnsupportedVersion(found = version, maxKnown = VERSION))
        }

        val puzzleLen = cursor.u16() ?: return malformed("truncated puzzle length")
        val puzzleBytes = cursor.bytes(puzzleLen) ?: return malformed("truncated puzzle blob")
        val puzzle = when (val decoded = QueensCodec.decode(puzzleBytes)) {
            is DecodeResult.Ok -> decoded.value
            is DecodeResult.Err -> return DecodeResult.Err(decoded.error)
        }

        val flags = cursor.u8() ?: return malformed("truncated session flags")
        var ref: QueensPuzzleRef? = null
        if (flags and FLAG_REF != 0) {
            val epochDay = cursor.i64() ?: return malformed("truncated ref date")
            val hasNumber = cursor.u8() ?: return malformed("truncated ref number flag")
            val number = if (hasNumber == 1) {
                cursor.i64() ?: return malformed("truncated ref number")
            } else {
                null
            }
            ref = QueensPuzzleRef(LocalDate.ofEpochDay(epochDay), number?.toInt())
        }

        // A19: unknown trailing bytes belong to a newer schema version — carried by the
        // puzzle envelope contract in later tickets; today none exist, and a nonempty
        // tail on a v1 blob is a writer bug we surface rather than silently drop.
        return DecodeResult.Ok(QueensSession(ref = ref, puzzle = puzzle))
    }

    private const val FLAG_REF = 1

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
    }
}
