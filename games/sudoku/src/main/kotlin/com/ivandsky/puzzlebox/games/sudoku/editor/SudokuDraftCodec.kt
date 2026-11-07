package com.ivandsky.puzzlebox.games.sudoku.editor

import com.ivandsky.puzzlebox.game.Command
import com.ivandsky.puzzlebox.game.CommandHistory
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.ReplaceDraft
import java.io.ByteArrayOutputStream

/**
 * Versioned Sudoku draft bytes (ADR-0005), integers big-endian:
 *
 * ```
 * v1: [u8 VERSION] board  [u16 n][n × step] done (oldest first)  [u16 m][m × step] undone
 *     board = [u8 size][size² × u8 digit]   (size 6 or 9, digit 0..size, 0 = empty)
 *     step  = [u8 TAG_REPLACE] board before, board after
 *           | [u8 TAG_SET_GIVEN][u8 cell][u8 digit][u8 previous]   (cell < size², digits 0..size)
 * ```
 *
 * Each stack keeps its newest [MAX_HISTORY] steps; older ones drop on encode. Decode walks the
 * stacks from the board, so a replace whose boards do not chain is malformed rather than a
 * wrong board on undo. Unknown versions, sizes, tags and trailing bytes are rejected.
 */
object SudokuDraftCodec {
    const val VERSION: Int = 1

    /** Steps each of the undo and redo stacks keeps; the oldest drop first. */
    const val MAX_HISTORY: Int = 10_000

    private const val TAG_REPLACE = 0x02
    private const val TAG_SET_GIVEN = 0x03

    fun encode(state: DraftState<SudokuDraft>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(VERSION)
        writeBoard(out, state.draft)
        writeSteps(out, state.history.done)
        writeSteps(out, state.history.undone)
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): DecodeResult<DraftState<SudokuDraft>> {
        val cursor = Cursor(bytes)
        val version = cursor.u8() ?: return malformed("empty draft bytes")
        if (version != VERSION) return DecodeResult.Err(DecodeError.UnsupportedVersion(version, VERSION))
        val draft = readBoard(cursor) ?: return malformed("malformed board")
        val done = readSteps(cursor) ?: return malformed("malformed undo history")
        val undone = readSteps(cursor) ?: return malformed("malformed redo history")
        if (cursor.pos != bytes.size) return malformed("${bytes.size - cursor.pos} trailing bytes")
        if (!chains(done.asReversed(), draft, undo = true)) return malformed("undo history does not fit the board")
        if (!chains(undone.asReversed(), draft, undo = false)) return malformed("redo history does not fit the board")
        return DecodeResult.Ok(DraftState(draft, CommandHistory(done, undone)))
    }

    private fun writeBoard(out: ByteArrayOutputStream, draft: SudokuDraft) {
        out.write(draft.size)
        draft.givens.forEach(out::write)
    }

    private fun writeSteps(out: ByteArrayOutputStream, steps: List<Command<SudokuDraft>>) {
        val kept = steps.takeLast(MAX_HISTORY)
        out.write(kept.size shr 8)
        out.write(kept.size and 0xFF)
        kept.forEach { step ->
            when (step) {
                is ReplaceDraft<*> -> {
                    out.write(TAG_REPLACE)
                    writeBoard(out, step.before as SudokuDraft)
                    writeBoard(out, step.after as SudokuDraft)
                }
                is SudokuDraftCommand.SetGiven -> {
                    out.write(TAG_SET_GIVEN)
                    out.write(step.cell)
                    out.write(step.digit)
                    out.write(step.previous)
                }
                else -> throw IllegalArgumentException("no draft codec tag for ${step::class.simpleName}")
            }
        }
    }

    /** Null when truncated, of an unsupported size, or holding a digit outside `0..size`. */
    private fun readBoard(cursor: Cursor): SudokuDraft? {
        val size = cursor.u8()?.takeIf { it in SudokuDraft.SIZES } ?: return null
        val givens = List(size * size) { cursor.u8()?.takeIf { d -> d <= size } ?: return null }
        return SudokuDraft(size, givens)
    }

    /** Null = truncated, unknown tag, bad board, or a count past [MAX_HISTORY]. */
    private fun readSteps(cursor: Cursor): List<Command<SudokuDraft>>? {
        val count = cursor.u16() ?: return null
        if (count > MAX_HISTORY) return null // before allocating anything proportional to it
        return List(count) {
            when (cursor.u8()) {
                TAG_REPLACE -> {
                    val before = readBoard(cursor) ?: return null
                    ReplaceDraft(before, readBoard(cursor) ?: return null)
                }
                TAG_SET_GIVEN -> readSetGiven(cursor) ?: return null
                else -> return null
            }
        }
    }

    /** Null when truncated; cell and digit ranges are checked as the steps chain. */
    private fun readSetGiven(cursor: Cursor): SudokuDraftCommand.SetGiven? {
        val cell = cursor.u8() ?: return null
        val digit = cursor.u8() ?: return null
        return SudokuDraftCommand.SetGiven(cell, digit, cursor.u8() ?: return null)
    }

    /** Each step, nearest the board first, must start from the board the previous one left. */
    private fun chains(steps: List<Command<SudokuDraft>>, board: SudokuDraft, undo: Boolean): Boolean {
        var current = board
        for (step in steps) {
            current = when (step) {
                is ReplaceDraft<*> -> {
                    val (from, to) = if (undo) step.after to step.before else step.before to step.after
                    if (from != current) return false
                    to as SudokuDraft
                }
                is SudokuDraftCommand.SetGiven -> {
                    if (!step.fits(current, undo)) return false
                    if (undo) step.revert(current) else step.apply(current)
                }
                else -> return false
            }
        }
        return true
    }

    /** In range, and standing on the digit it sets (undo) or replaces (redo) at that cell. */
    private fun SudokuDraftCommand.SetGiven.fits(board: SudokuDraft, undo: Boolean): Boolean =
        cell in board.givens.indices && digit <= board.size && previous <= board.size &&
            board.givens[cell] == if (undo) digit else previous

    private fun malformed(detail: String): DecodeResult.Err = DecodeResult.Err(DecodeError.Malformed(detail))

    private class Cursor(private val bytes: ByteArray) {
        var pos: Int = 0
            private set

        fun u8(): Int? = if (pos < bytes.size) bytes[pos++].toInt() and 0xFF else null

        fun u16(): Int? {
            val hi = u8() ?: return null
            return (hi shl 8) or (u8() ?: return null)
        }
    }
}
