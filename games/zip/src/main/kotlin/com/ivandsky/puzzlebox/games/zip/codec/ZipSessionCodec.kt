package com.ivandsky.puzzlebox.games.zip.codec

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GameSessionCodec
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipPuzzleRef
import com.ivandsky.puzzlebox.games.zip.ZipSession
import java.io.ByteArrayOutputStream
import java.time.Instant

/**
 * Zip session codec v1 — the §4 reader rules applied to the play session, field set
 * {ref, path, hintsUsed, elapsedMs, completedAt} plus the §10 mistake/backtrack counters
 * and the timerStarted bit (still v1 — pre-release). Layout (little-endian):
 *
 * ```
 * 0   u8    formatVersion = 1
 *     u16   puzzleLen; puzzleLen x u8   (ZipCodec bytes: the puzzle rides by VALUE so an
 *                                        in-progress day survives offline replay, F2 §2)
 *     u8    flags                       (bit0 completedAt present, bit1 ref present,
 *                                        bit2 timerStarted)
 *     [bit1] i32 epochDay, u8 numberPresent, i32 puzzleNumber
 *     u8    pathLen; pathLen x u8 cellIdx
 *     u8    hintsUsed; u8 mistakeCount; u8 backtrackCount
 *     i64   elapsedMs
 *     [bit0] i64 completedAt epochMilli
 *     ...   remaining bytes = unknown extension: preserved verbatim, re-emitted on encode
 * ```
 */
object ZipSessionCodec : GameSessionCodec<ZipSession> {

    const val VERSION: Int = 1

    override fun serialize(session: ZipSession): ByteArray {
        val puzzleBytes = ZipCodec.encode(session.puzzle) // canonical, self-describing (A19 nesting)
        require(puzzleBytes.size <= 0xFFFF) { "puzzle blob exceeds u16 length" }
        require(session.path.size in 0..255) { "v1 layout carries u8 path length" }

        val out = ByteArrayOutputStream(puzzleBytes.size + 32)
        out.write(VERSION)
        out.write((puzzleBytes.size shr 8) and 0xFF)
        out.write(puzzleBytes.size and 0xFF)
        out.write(puzzleBytes)

        var flags = 0
        if (session.completedAt != null) flags = flags or FLAG_COMPLETED
        if (session.ref != null) flags = flags or FLAG_REF
        if (session.timerStarted) flags = flags or FLAG_TIMER_STARTED
        out.write(flags)

        session.ref?.let { ref ->
            writeI64(out, ref.date.toEpochDay()) // fits i32 for any plausible calendar, kept i64-simple
            val number = ref.puzzleNumber
            if (number == null) {
                out.write(0)
            } else {
                out.write(1)
                writeI64(out, number.toLong())
            }
        }

        out.write(session.path.size)
        session.path.forEach { out.write(it.idx) }
        out.write(session.hintsUsed)
        out.write(session.mistakeCount)
        out.write(session.backtrackCount)
        writeI64(out, session.elapsedMs)
        session.completedAt?.let { writeI64(out, it.toEpochMilli()) }
        // NOTE: unknownTail extension bytes are a ZipPuzzle concern; the session adds none yet.
        return out.toByteArray()
    }

    override fun deserialize(bytes: ByteArray): DecodeResult<ZipSession> {
        val cursor = Cursor(bytes)
        val version = cursor.u8() ?: return malformed("empty session blob")
        if (version != VERSION) {
            return DecodeResult.Err(DecodeError.UnsupportedVersion(found = version, maxKnown = VERSION))
        }

        val puzzleLen = cursor.u16() ?: return malformed("truncated puzzle length")
        val puzzleBytes = cursor.bytes(puzzleLen) ?: return malformed("truncated puzzle blob")
        val puzzle = when (val decoded = ZipCodec.decode(puzzleBytes)) {
            is DecodeResult.Ok -> decoded.value
            is DecodeResult.Err -> return DecodeResult.Err(decoded.error)
        }

        val flags = cursor.u8() ?: return malformed("truncated session flags")
        val hasCompletedAt = flags and FLAG_COMPLETED != 0
        val hasRef = flags and FLAG_REF != 0
        val timerStarted = flags and FLAG_TIMER_STARTED != 0

        var ref: ZipPuzzleRef? = null
        if (hasRef) {
            val epochDay = cursor.i64() ?: return malformed("truncated ref date")
            val hasNumber = cursor.u8() ?: return malformed("truncated ref number flag")
            val number = if (hasNumber == 1) {
                cursor.i64() ?: return malformed("truncated ref number")
            } else {
                null
            }
            ref = ZipPuzzleRef(java.time.LocalDate.ofEpochDay(epochDay), number?.toInt())
        }

        val pathLen = cursor.u8() ?: return malformed("truncated path length")
        val path = ArrayList<Cell>(pathLen)
        repeat(pathLen) {
            val cellIdx = cursor.u8() ?: return malformed("truncated path cell")
            if (cellIdx >= puzzle.board.cellCount) return malformed("path cell $cellIdx out of range")
            path += Cell(cellIdx)
        }

        val hintsUsed = cursor.u8() ?: return malformed("truncated hints")
        val mistakes = cursor.u8() ?: return malformed("truncated mistakes")
        val backtracks = cursor.u8() ?: return malformed("truncated backtracks")
        val elapsedMs = cursor.i64() ?: return malformed("truncated elapsedMs")
        val completedAt = if (hasCompletedAt) {
            Instant.ofEpochMilli(cursor.i64() ?: return malformed("truncated completedAt"))
        } else {
            null
        }

        if (cursor.remaining().isNotEmpty()) return malformed("trailing session bytes are not an extension")
        // runningSince is a runtime anchor, never persisted: restore paused at elapsedMs.
        return DecodeResult.Ok(
            ZipSession(
                ref = ref,
                puzzle = puzzle,
                path = path,
                hintsUsed = hintsUsed,
                mistakeCount = mistakes,
                backtrackCount = backtracks,
                elapsedMs = elapsedMs,
                timerStarted = timerStarted,
                runningSince = null,
                completedAt = completedAt,
            ),
        )
    }

    private const val FLAG_COMPLETED = 1
    private const val FLAG_REF = 2
    private const val FLAG_TIMER_STARTED = 4

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
