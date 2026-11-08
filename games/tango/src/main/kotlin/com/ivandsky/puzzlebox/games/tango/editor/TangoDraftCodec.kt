package com.ivandsky.puzzlebox.games.tango.editor

import com.ivandsky.puzzlebox.game.Command
import com.ivandsky.puzzlebox.game.CommandHistory
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.ReplaceDraft
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarker
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import java.io.ByteArrayOutputStream

/**
 * Versioned Tango draft bytes (ADR-0005), integers big-endian, cells row-major on the
 * [TangoDraft.N] × [TangoDraft.N] board:
 *
 * ```
 * v1: [u8 VERSION] board  [u16 n][n × step] done (oldest first)  [u16 m][m × step] undone
 *     board = [u8 g][g × (u8 cell, u8 TangoCell byte)]   (ascending cells)
 *             [u8 k][k × (u8 cell, u8 side, u8 kind)]     (draft order)
 *     step  = [u8 TAG_REPLACE] board before, board after
 *           | [u8 TAG_SET_CELL][u8 cell][u8 value][u8 previous]                  (TangoCell byte, 0 = empty)
 *           | [u8 TAG_SET_MARKER][u8 cell][u8 side][u8 kind][u8 previous]        (kind byte, NO_MARKER = none)
 * ```
 *
 * Markers decode in (cell, side) order with a repeated edge keeping its last kind, so every
 * decoded draft is one the commands revert exactly.
 *
 * The command tags joined v1 without a bump: a ReplaceDraft-only history writes exactly its
 * earlier bytes, so every stored draft and the frozen `tango-draft-v1.bin` decode unchanged.
 *
 * Each stack keeps its newest [MAX_HISTORY] steps; older ones drop on encode. Unknown
 * versions, tags, off-board cells or edges and trailing bytes are rejected, never guessed.
 */
object TangoDraftCodec {
    const val VERSION: Int = 1

    /** Steps each of the undo and redo stacks keeps; the oldest drop first. */
    const val MAX_HISTORY: Int = 10_000

    private const val TAG_REPLACE = 0x02
    private const val TAG_SET_CELL = 0x03
    private const val TAG_SET_MARKER = 0x04
    private const val NO_CELL = 0
    private const val NO_MARKER = 0xFF
    private const val CELLS = TangoDraft.N * TangoDraft.N

    /** History entries: TangoDraftCommand.SetCell/SetMarker and ReplaceDraft<TangoDraft>, each with a tag byte. */
    fun encode(state: DraftState<TangoDraft>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(VERSION)
        writeBoard(out, state.draft)
        writeSteps(out, state.history.done)
        writeSteps(out, state.history.undone)
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): DecodeResult<DraftState<TangoDraft>> {
        val cursor = Cursor(bytes)
        val version = cursor.u8() ?: return malformed("empty draft bytes")
        if (version != VERSION) return DecodeResult.Err(DecodeError.UnsupportedVersion(version, VERSION))
        val draft = readBoard(cursor) ?: return malformed("malformed board")
        val done = readSteps(cursor) ?: return malformed("malformed undo history")
        val undone = readSteps(cursor) ?: return malformed("malformed redo history")
        if (cursor.pos != bytes.size) return malformed("${bytes.size - cursor.pos} trailing bytes")
        return DecodeResult.Ok(DraftState(draft, CommandHistory(done, undone)))
    }

    private fun writeBoard(out: ByteArrayOutputStream, draft: TangoDraft) {
        out.write(draft.givens.size)
        draft.givens.toSortedMap().forEach { (cell, value) ->
            out.write(cell)
            out.write(value.toByte())
        }
        out.write(draft.markers.size)
        draft.markers.forEach {
            out.write(it.cell)
            out.write(it.side.toByte())
            out.write(it.kind.toByte())
        }
    }

    private fun writeSteps(out: ByteArrayOutputStream, steps: List<Command<TangoDraft>>) {
        val kept = steps.takeLast(MAX_HISTORY)
        out.write(kept.size shr 8)
        out.write(kept.size and 0xFF)
        kept.forEach { step ->
            when (step) {
                is TangoDraftCommand.SetCell -> {
                    out.write(TAG_SET_CELL)
                    out.write(step.cell)
                    out.write(step.value?.toByte() ?: NO_CELL)
                    out.write(step.previous?.toByte() ?: NO_CELL)
                }

                is TangoDraftCommand.SetMarker -> {
                    out.write(TAG_SET_MARKER)
                    out.write(step.cell)
                    out.write(step.side.toByte())
                    out.write(step.kind?.toByte() ?: NO_MARKER)
                    out.write(step.previous?.toByte() ?: NO_MARKER)
                }

                is ReplaceDraft<*> -> {
                    out.write(TAG_REPLACE)
                    writeBoard(out, step.before as TangoDraft)
                    writeBoard(out, step.after as TangoDraft)
                }

                else -> error("no draft codec tag for ${step::class.simpleName}")
            }
        }
    }

    /** Null when truncated, unordered, duplicated or off the board. */
    private fun readBoard(cursor: Cursor): TangoDraft? {
        val givens = LinkedHashMap<Int, TangoCell>()
        repeat(cursor.u8() ?: return null) {
            val cell = cursor.u8() ?: return null
            if (cell >= CELLS || givens.keys.any { it >= cell }) return null
            givens[cell] = TangoCell.fromByte(cursor.u8() ?: return null) ?: return null
        }
        val markers = ArrayList<TangoMarker>()
        repeat(cursor.u8() ?: return null) {
            val cell = cursor.u8() ?: return null
            val side = TangoEdgeSide.fromByte(cursor.u8() ?: return null) ?: return null
            val kind = TangoMarkerKind.fromByte(cursor.u8() ?: return null) ?: return null
            if (!onBoard(cell, side)) return null
            markers.add(TangoMarker(cell, side, kind))
        }
        return TangoDraft(givens, markers.canonicalMarkers())
    }

    private fun readSteps(cursor: Cursor): List<Command<TangoDraft>>? {
        val count = cursor.u16() ?: return null
        if (count > MAX_HISTORY) return null // before allocating anything proportional to it
        val steps = ArrayList<Command<TangoDraft>>(count)
        repeat(count) {
            val step: Command<TangoDraft> = when (cursor.u8() ?: return null) {
                TAG_REPLACE -> {
                    val before = readBoard(cursor) ?: return null
                    ReplaceDraft(before, readBoard(cursor) ?: return null)
                }

                TAG_SET_CELL -> readSetCell(cursor) ?: return null
                TAG_SET_MARKER -> readSetMarker(cursor) ?: return null
                else -> return null
            }
            steps.add(step)
        }
        return steps
    }

    private fun readSetCell(cursor: Cursor): TangoDraftCommand.SetCell? {
        val cell = cursor.u8()?.takeIf { it < CELLS } ?: return null
        val value = readCellByte(cursor) ?: return null
        val previous = readCellByte(cursor) ?: return null
        return TangoDraftCommand.SetCell(cell, value.value, previous.value)
    }

    private fun readSetMarker(cursor: Cursor): TangoDraftCommand.SetMarker? {
        val cell = cursor.u8() ?: return null
        val side = TangoEdgeSide.fromByte(cursor.u8() ?: return null) ?: return null
        if (!onBoard(cell, side)) return null
        val kind = readKindByte(cursor) ?: return null
        val previous = readKindByte(cursor) ?: return null
        return TangoDraftCommand.SetMarker(cell, side, kind.value, previous.value)
    }

    /** Boxed so a legitimate "empty" is not confused with a malformed byte. */
    private fun readCellByte(cursor: Cursor): Optional<TangoCell>? {
        val raw = cursor.u8() ?: return null
        if (raw == NO_CELL) return Optional(null)
        return Optional(TangoCell.fromByte(raw) ?: return null)
    }

    private fun readKindByte(cursor: Cursor): Optional<TangoMarkerKind>? {
        val raw = cursor.u8() ?: return null
        if (raw == NO_MARKER) return Optional(null)
        return Optional(TangoMarkerKind.fromByte(raw) ?: return null)
    }

    private class Optional<T : Any>(val value: T?)

    /** The marker's neighbor exists: RIGHT needs a column to the right, DOWN a row below. */
    private fun onBoard(cell: Int, side: TangoEdgeSide): Boolean = cell < CELLS && when (side) {
        TangoEdgeSide.RIGHT -> cell % TangoDraft.N < TangoDraft.N - 1
        TangoEdgeSide.DOWN -> cell / TangoDraft.N < TangoDraft.N - 1
    }

    private fun malformed(detail: String): DecodeResult.Err = DecodeResult.Err(DecodeError.Malformed(detail))

    private class Cursor(private val bytes: ByteArray) {
        var pos = 0
            private set

        fun u8(): Int? = if (pos < bytes.size) bytes[pos++].toInt() and 0xFF else null

        fun u16(): Int? {
            val hi = u8() ?: return null
            val lo = u8() ?: return null
            return (hi shl 8) or lo
        }
    }
}
