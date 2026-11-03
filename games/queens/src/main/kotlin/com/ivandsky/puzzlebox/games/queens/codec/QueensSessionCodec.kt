package com.ivandsky.puzzlebox.games.queens.codec

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GameSessionCodec
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.MarkKind
import com.ivandsky.puzzlebox.games.queens.QueensMark
import com.ivandsky.puzzlebox.games.queens.QueensPuzzleRef
import com.ivandsky.puzzlebox.games.queens.QueensSession
import com.ivandsky.puzzlebox.games.queens.QueensSnapshot
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate

/**
 * Queens session codec (zip ZipSessionCodec shape, A19; pre-release): the puzzle rides
 * BY VALUE so an in-progress day survives offline replay. v2 (fix round 6) adds the
 * result facts + the Auto-cross setting; v1 blobs are rejected as UnsupportedVersion
 * and degrade to a fresh fetch (pre-release, no shipped sessions). Layout (i64
 * little-endian, u16 big-endian):
 *
 * ```
 * 0   u8    formatVersion = 2
 *     u16   puzzleLen; puzzleLen x u8   (QueensCodec bytes)
 *     u8    flags                       (bit0 = completedAt present, bit1 = ref present,
 *                                       bit2 = startedAt present, bit3 = autoCross OFF)
 *     [bit1] i64 epochDay, u8 numberPresent, i64 puzzleNumber
 *     u8    queensLen; queensLen x u8 cellIdx
 *     u16   marksLen; marksLen x { u8 cellIdx, u8 kind (0 MANUAL / 1 AUTO),
 *                                [kind=1] u8 sourceIdx }
 *     u16   undoLen; per snapshot { u8 queensLen; queensLen x u8 cellIdx,
 *                                   u16 marksLen; marks as above }
 *     [bit0] i64 completedAt epochMilli
 *     [bit2] i64 startedAt epochMilli
 *     u8    hintsUsed
 * ```
 *
 * Marks serialize with their PROVENANCE and undo snapshots in stack order (bottom first);
 * decode validates cell ranges and that AUTO marks point at a queen of their snapshot
 * (A10's "exists only while the source queen stands" is a decode invariant too).
 */
object QueensSessionCodec : GameSessionCodec<QueensSession> {

    const val VERSION: Int = 2

    private const val FLAG_COMPLETED = 1
    private const val FLAG_REF = 2
    private const val FLAG_STARTED = 4
    private const val FLAG_AUTO_CROSS_OFF = 8

    private const val KIND_MANUAL = 0
    private const val KIND_AUTO = 1

    override fun serialize(session: QueensSession): ByteArray {
        val puzzleBytes = QueensCodec.encode(session.puzzle) // canonical, self-describing (A19 nesting)
        require(puzzleBytes.size <= 0xFFFF) { "puzzle blob exceeds u16 length" }
        require(session.queens.size <= 0xFF) { "v1 layout carries u8 queen count" }
        require(session.undo.size <= 0xFFFF) { "v1 layout carries u16 undo depth" }

        val out = ByteArrayOutputStream(puzzleBytes.size + 32)
        out.write(VERSION)
        out.write((puzzleBytes.size shr 8) and 0xFF)
        out.write(puzzleBytes.size and 0xFF)
        out.write(puzzleBytes)

        var flags = 0
        if (session.completedAt != null) flags = flags or FLAG_COMPLETED
        if (session.ref != null) flags = flags or FLAG_REF
        if (session.startedAt != null) flags = flags or FLAG_STARTED
        if (!session.autoCross) flags = flags or FLAG_AUTO_CROSS_OFF
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

        out.write(session.queens.size)
        session.queens.forEach { out.write(it.idx) }
        writeMarks(out, session.marks)

        writeU16(out, session.undo.size)
        session.undo.forEach { snapshot ->
            require(snapshot.queens.size <= 0xFF) { "v1 layout carries u8 snapshot queen count" }
            out.write(snapshot.queens.size)
            snapshot.queens.forEach { out.write(it.idx) }
            writeMarks(out, snapshot.marks)
        }

        session.completedAt?.let { writeI64(out, it.toEpochMilli()) }
        session.startedAt?.let { writeI64(out, it.toEpochMilli()) }
        out.write(session.hintsUsed)
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
        val cellRange = 0 until puzzle.cellCount

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

        val queensLen = cursor.u8() ?: return malformed("truncated queen count")
        val queens = mutableListOf<Cell>()
        repeat(queensLen) {
            val idx = cursor.u8() ?: return malformed("truncated queen cell")
            if (idx !in cellRange) return malformed("queen cell $idx outside board")
            queens.add(Cell(idx))
        }

        val marks = when (val decoded = readMarks(cursor, cellRange, queens)) {
            is DecodeResult.Ok -> decoded.value
            is DecodeResult.Err -> return DecodeResult.Err(decoded.error)
        }

        val undoLen = cursor.u16() ?: return malformed("truncated undo depth")
        val undo = mutableListOf<QueensSnapshot>()
        repeat(undoLen) {
            val snapshotQueensLen = cursor.u8() ?: return malformed("truncated snapshot queen count")
            val snapshotQueens = mutableListOf<Cell>()
            repeat(snapshotQueensLen) {
                val idx = cursor.u8() ?: return malformed("truncated snapshot queen cell")
                if (idx !in cellRange) return malformed("snapshot queen cell $idx outside board")
                snapshotQueens.add(Cell(idx))
            }
            val snapshotMarks = when (val decoded = readMarks(cursor, cellRange, snapshotQueens)) {
                is DecodeResult.Ok -> decoded.value
                is DecodeResult.Err -> return DecodeResult.Err(decoded.error)
            }
            undo.add(QueensSnapshot(snapshotQueens, snapshotMarks))
        }

        val completedAt = if (flags and FLAG_COMPLETED != 0) {
            Instant.ofEpochMilli(cursor.i64() ?: return malformed("truncated completedAt"))
        } else {
            null
        }

        val startedAt = if (flags and FLAG_STARTED != 0) {
            Instant.ofEpochMilli(cursor.i64() ?: return malformed("truncated startedAt"))
        } else {
            null
        }
        val hintsUsed = cursor.u8() ?: return malformed("truncated hintsUsed")

        return DecodeResult.Ok(
            QueensSession(
                ref = ref,
                puzzle = puzzle,
                queens = queens,
                marks = marks,
                undo = undo,
                completedAt = completedAt,
                startedAt = startedAt,
                hintsUsed = hintsUsed,
                autoCross = flags and FLAG_AUTO_CROSS_OFF == 0,
            ),
        )
    }

    /** Deterministic order: ascending cell index. AUTO carries its source queen (A10). */
    private fun writeMarks(out: ByteArrayOutputStream, marks: Map<Cell, QueensMark>) {
        require(marks.size <= 0xFFFF) { "v1 layout carries u16 mark count" }
        writeU16(out, marks.size)
        marks.toSortedMap(compareBy { it.idx }).forEach { (cell, mark) ->
            out.write(cell.idx)
            when (mark.kind) {
                MarkKind.MANUAL -> out.write(KIND_MANUAL)
                MarkKind.AUTO -> {
                    out.write(KIND_AUTO)
                    out.write(requireNotNull(mark.source).idx)
                }
            }
        }
    }

    /** [sourceQueens] is the queen set the marks belong to — AUTO sources must stand (A10). */
    private fun readMarks(
        cursor: Cursor,
        cellRange: IntRange,
        sourceQueens: List<Cell>,
    ): DecodeResult<Map<Cell, QueensMark>> {
        val marksLen = cursor.u16() ?: return malformed("truncated mark count")
        val marks = LinkedHashMap<Cell, QueensMark>(marksLen)
        repeat(marksLen) {
            val idx = cursor.u8() ?: return malformed("truncated mark cell")
            if (idx !in cellRange) return malformed("mark cell $idx outside board")
            if (Cell(idx) in marks) return malformed("duplicate mark cell $idx")
            val kind = cursor.u8() ?: return malformed("truncated mark kind")
            val mark = when (kind) {
                KIND_MANUAL -> QueensMark(MarkKind.MANUAL)
                KIND_AUTO -> {
                    val source = cursor.u8() ?: return malformed("truncated auto source")
                    if (source !in cellRange) return malformed("auto source $source outside board")
                    if (Cell(source) !in sourceQueens) return malformed("AUTO mark sourced by non-standing queen $source")
                    QueensMark(MarkKind.AUTO, Cell(source))
                }

                else -> return malformed("unknown mark kind $kind")
            }
            marks[Cell(idx)] = mark
        }
        return DecodeResult.Ok(marks)
    }

    private fun writeU16(out: ByteArrayOutputStream, value: Int) {
        out.write((value shr 8) and 0xFF)
        out.write(value and 0xFF)
    }

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
