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
 * ```
 *
 * Each stack keeps its newest [MAX_HISTORY] steps; older ones drop on encode. Unknown
 * versions, tags, off-board cells or edges and trailing bytes are rejected, never guessed.
 */
object TangoDraftCodec {
    const val VERSION: Int = 1

    /** Steps each of the undo and redo stacks keeps; the oldest drop first. */
    const val MAX_HISTORY: Int = 10_000

    private const val TAG_REPLACE = 0x02
    private const val CELLS = TangoDraft.N * TangoDraft.N

    /** History entries: ReplaceDraft<TangoDraft> only, each with a tag byte. */
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
            check(step is ReplaceDraft<*>) { "no draft codec tag for ${step::class.simpleName}" }
            out.write(TAG_REPLACE)
            writeBoard(out, step.before as TangoDraft)
            writeBoard(out, step.after as TangoDraft)
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
        return TangoDraft(givens, markers)
    }

    private fun readSteps(cursor: Cursor): List<Command<TangoDraft>>? {
        val count = cursor.u16() ?: return null
        if (count > MAX_HISTORY) return null // before allocating anything proportional to it
        val steps = ArrayList<Command<TangoDraft>>(count)
        repeat(count) {
            if (cursor.u8() != TAG_REPLACE) return null
            val before = readBoard(cursor) ?: return null
            steps.add(ReplaceDraft(before, readBoard(cursor) ?: return null))
        }
        return steps
    }

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
