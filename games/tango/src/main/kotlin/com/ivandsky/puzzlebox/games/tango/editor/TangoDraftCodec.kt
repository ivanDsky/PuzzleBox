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
 * Versioned Tango draft bytes (ADR-0005), integers big-endian, cells row-major on the board
 * that holds them:
 *
 * ```
 * v1: [u8 1] board  [u16 n][n × step] done (oldest first)  [u16 m][m × step] undone   (every board 6×6)
 *     board = [u8 g][g × (u8 cell, u8 TangoCell byte)]   (ascending cells)
 *             [u8 k][k × (u8 cell, u8 side, u8 kind)]     (draft order)
 *     step  = [u8 TAG_REPLACE] board before, board after
 *           | [u8 TAG_SET_CELL][u8 cell][u8 value][u8 previous]                  (TangoCell byte, 0 = empty)
 *           | [u8 TAG_SET_MARKER][u8 cell][u8 side][u8 kind][u8 previous]        (kind byte, NO_MARKER = none)
 * v2: [u8 2] board2 [u16 n][n × step2] done  [u16 m][m × step2] undone
 *     board2 = [u8 size] board                                                   (size ∈ TangoDraft.SIZES)
 *     step2  = [u8 TAG_REPLACE] board2 before, board2 after | TAG_SET_CELL … | TAG_SET_MARKER …   (as v1)
 * ```
 *
 * Encode writes v1 whenever the current board and every board in both kept stacks are 6×6, so
 * stored 6×6 drafts, their `layoutKey` and older builds stay unchanged; any other size writes v2.
 * A resize is a replace whose boards differ in size, which is why every v2 board carries one.
 *
 * Markers decode in (cell, side) order with a repeated edge keeping its last kind, so every
 * decoded draft is one the commands revert exactly.
 *
 * The command tags joined v1 without a bump: a ReplaceDraft-only history writes exactly its
 * earlier bytes, so every stored draft and the frozen `tango-draft-v1.bin` decode unchanged.
 *
 * Each stack keeps its newest [MAX_HISTORY] steps; older ones drop on encode. Decode replays
 * board sizes through the stacks, so a step that could not apply to the board it meets is
 * malformed. Unknown versions, sizes, tags, off-board cells or edges and trailing bytes are
 * rejected, never guessed.
 */
object TangoDraftCodec {
    const val VERSION: Int = 2

    /** Steps each of the undo and redo stacks keeps; the oldest drop first. */
    const val MAX_HISTORY: Int = 10_000

    private const val TAG_REPLACE = 0x02
    private const val TAG_SET_CELL = 0x03
    private const val TAG_SET_MARKER = 0x04
    private const val NO_CELL = 0
    private const val NO_MARKER = 0xFF

    /** v1 bytes carry no size: every board in them is 6×6. */
    private const val V1 = 1
    private const val V1_N = 6

    /** History entries: TangoDraftCommand.SetCell/SetMarker and ReplaceDraft<TangoDraft>, each with a tag byte. */
    fun encode(state: DraftState<TangoDraft>): ByteArray {
        val done = state.history.done.takeLast(MAX_HISTORY)
        val undone = state.history.undone.takeLast(MAX_HISTORY)
        val sized = state.draft.n != V1_N || done.any(::holdsOtherSize) || undone.any(::holdsOtherSize)
        val out = ByteArrayOutputStream()
        out.write(if (sized) VERSION else V1)
        writeBoard(out, state.draft, sized)
        writeSteps(out, done, sized)
        writeSteps(out, undone, sized)
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): DecodeResult<DraftState<TangoDraft>> {
        val cursor = Cursor(bytes)
        val version = cursor.u8() ?: return malformed("empty draft bytes")
        if (version !in V1..VERSION) return DecodeResult.Err(DecodeError.UnsupportedVersion(version, VERSION))
        val sized = version == VERSION
        val draft = readBoard(cursor, sized) ?: return malformed("malformed board")
        val done = readSteps(cursor, sized) ?: return malformed("malformed undo history")
        val undone = readSteps(cursor, sized) ?: return malformed("malformed redo history")
        if (cursor.pos != bytes.size) return malformed("${bytes.size - cursor.pos} trailing bytes")
        if (!replays(done, draft.n, backward = true)) return malformed("undo history does not fit the board")
        if (!replays(undone, draft.n, backward = false)) return malformed("redo history does not fit the board")
        return DecodeResult.Ok(DraftState(draft, CommandHistory(done, undone)))
    }

    private fun holdsOtherSize(step: Command<TangoDraft>): Boolean =
        step is ReplaceDraft<*> && ((step.before as TangoDraft).n != V1_N || (step.after as TangoDraft).n != V1_N)

    /** [sized] (v2) prefixes the board with its size; a v1 board is 6×6 by definition. */
    private fun writeBoard(out: ByteArrayOutputStream, draft: TangoDraft, sized: Boolean) {
        if (sized) out.write(draft.n)
        // 10 × 10 is 100 cells and 180 edges, so every count and cell index fits its u8.
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

    private fun writeSteps(out: ByteArrayOutputStream, kept: List<Command<TangoDraft>>, sized: Boolean) {
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
                    writeBoard(out, step.before as TangoDraft, sized)
                    writeBoard(out, step.after as TangoDraft, sized)
                }

                else -> error("no draft codec tag for ${step::class.simpleName}")
            }
        }
    }

    /** Null when truncated, unordered, duplicated, off the board or (v2) of a size outside [TangoDraft.SIZES]. */
    private fun readBoard(cursor: Cursor, sized: Boolean): TangoDraft? {
        val n = if (sized) cursor.u8()?.takeIf { it in TangoDraft.SIZES } ?: return null else V1_N
        val givens = LinkedHashMap<Int, TangoCell>()
        repeat(cursor.u8() ?: return null) {
            val cell = cursor.u8() ?: return null
            if (cell >= n * n || givens.keys.any { it >= cell }) return null
            givens[cell] = TangoCell.fromByte(cursor.u8() ?: return null) ?: return null
        }
        val markers = ArrayList<TangoMarker>()
        repeat(cursor.u8() ?: return null) {
            val cell = cursor.u8() ?: return null
            val side = TangoEdgeSide.fromByte(cursor.u8() ?: return null) ?: return null
            val kind = TangoMarkerKind.fromByte(cursor.u8() ?: return null) ?: return null
            if (!onBoard(cell, side, n)) return null
            markers.add(TangoMarker(cell, side, kind))
        }
        return TangoDraft(n, givens, markers.canonicalMarkers())
    }

    /** Command cells and edges are checked later by [replays], against the board each step meets. */
    private fun readSteps(cursor: Cursor, sized: Boolean): List<Command<TangoDraft>>? {
        val count = cursor.u16() ?: return null
        if (count > MAX_HISTORY) return null // before allocating anything proportional to it
        val steps = ArrayList<Command<TangoDraft>>(count)
        repeat(count) {
            val step: Command<TangoDraft> = when (cursor.u8() ?: return null) {
                TAG_REPLACE -> {
                    val before = readBoard(cursor, sized) ?: return null
                    ReplaceDraft(before, readBoard(cursor, sized) ?: return null)
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
        val cell = cursor.u8() ?: return null
        val value = readCellByte(cursor) ?: return null
        val previous = readCellByte(cursor) ?: return null
        return TangoDraftCommand.SetCell(cell, value.value, previous.value)
    }

    private fun readSetMarker(cursor: Cursor): TangoDraftCommand.SetMarker? {
        val cell = cursor.u8() ?: return null
        val side = TangoEdgeSide.fromByte(cursor.u8() ?: return null) ?: return null
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

    /**
     * Walks [steps] in replay order from the current board's [n]: done newest-first ([backward]),
     * undone next-redo-first. A replace must start on the size it meets and moves to its other
     * end; a cell or marker step must lie on the board it meets.
     */
    private fun replays(steps: List<Command<TangoDraft>>, n: Int, backward: Boolean): Boolean {
        var current = n
        for (step in steps.asReversed()) {
            when (step) {
                is ReplaceDraft<*> -> {
                    val meets = (if (backward) step.after else step.before) as TangoDraft
                    val lands = (if (backward) step.before else step.after) as TangoDraft
                    if (meets.n != current) return false
                    current = lands.n
                }

                is TangoDraftCommand.SetCell -> if (step.cell >= current * current) return false
                is TangoDraftCommand.SetMarker -> if (!onBoard(step.cell, step.side, current)) return false
                else -> return false
            }
        }
        return true
    }

    /** The marker's neighbor exists on an [n] × [n] board: RIGHT needs a column to the right, DOWN a row below. */
    private fun onBoard(cell: Int, side: TangoEdgeSide, n: Int): Boolean = cell < n * n && when (side) {
        TangoEdgeSide.RIGHT -> cell % n < n - 1
        TangoEdgeSide.DOWN -> cell / n < n - 1
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
