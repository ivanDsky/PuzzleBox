package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.game.Command
import com.ivandsky.puzzlebox.game.CommandHistory
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.ReplaceDraft
import com.ivandsky.puzzlebox.games.queens.QueensVariants
import com.ivandsky.puzzlebox.games.queens.codec.SessionCursor
import com.ivandsky.puzzlebox.games.queens.queensVariantId
import java.io.ByteArrayOutputStream

/**
 * Versioned Queens draft bytes (ADR-0005), all integers big-endian, a region byte being
 * `0 until size` or [UNPAINTED_BYTE]:
 *
 * ```
 * v1: [u8 VERSION] board  [u16 n][n × step] done (oldest first)  [u16 m][m × step] undone
 *     board = [u8 size][size² × u8 region]
 *     step  = [u8 TAG_PAINT][u8 region][u8 k][k × u8 cell][u8 p][p × (u8 cell, u8 region)]
 *           | [u8 TAG_REPLACE] board before, board after
 * ```
 *
 * Each stack keeps its newest [MAX_HISTORY] steps; older ones drop on encode (undo depth
 * shrinks, the board never changes). Decode replays board sizes through the stacks, so a
 * step that could not apply to the board it meets is malformed rather than a crash on undo.
 * Unknown versions, sizes, tags and trailing bytes are rejected, never guessed.
 */
object QueensDraftCodec {
    const val VERSION: Int = 1

    /** Steps each of the undo and redo stacks keeps; the oldest drop first. */
    const val MAX_HISTORY: Int = 10_000

    private const val UNPAINTED_BYTE = 0xFF
    private const val TAG_PAINT = 0x01
    private const val TAG_REPLACE = 0x02

    /** History entries: QueensDraftCommand.Paint and ReplaceDraft<QueensDraft>, each with a tag byte. */
    fun encode(state: DraftState<QueensDraft>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(VERSION)
        writeBoard(out, state.draft)
        writeSteps(out, state.history.done)
        writeSteps(out, state.history.undone)
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): DecodeResult<DraftState<QueensDraft>> {
        val cursor = SessionCursor(bytes)
        val version = cursor.u8() ?: return malformed("empty draft bytes")
        if (version != VERSION) return DecodeResult.Err(DecodeError.UnsupportedVersion(version, VERSION))
        val draft = when (val board = readBoard(cursor)) {
            is DecodeResult.Ok -> board.value
            is DecodeResult.Err -> return board
        }
        val done = readSteps(cursor) ?: return malformed("malformed undo history")
        val undone = readSteps(cursor) ?: return malformed("malformed redo history")
        if (cursor.pos != bytes.size) return malformed("${bytes.size - cursor.pos} trailing bytes")
        if (!fitsBackward(done, draft.size)) return malformed("undo history does not fit the board")
        if (!fitsForward(undone, draft.size)) return malformed("redo history does not fit the board")
        return DecodeResult.Ok(DraftState(draft, CommandHistory(done, undone)))
    }

    private fun writeBoard(out: ByteArrayOutputStream, draft: QueensDraft) {
        out.write(draft.size)
        draft.regions.forEach { out.write(regionByte(it)) }
    }

    private fun writeSteps(out: ByteArrayOutputStream, steps: List<Command<QueensDraft>>) {
        val kept = steps.takeLast(MAX_HISTORY)
        out.write(kept.size shr 8)
        out.write(kept.size and 0xFF)
        kept.forEach { step ->
            when (step) {
                is QueensDraftCommand.Paint -> {
                    out.write(TAG_PAINT)
                    out.write(regionByte(step.region))
                    out.write(step.cells.size)
                    step.cells.forEach(out::write)
                    out.write(step.previous.size)
                    step.previous.forEach { (cell, region) ->
                        out.write(cell)
                        out.write(regionByte(region))
                    }
                }

                is ReplaceDraft<*> -> {
                    out.write(TAG_REPLACE)
                    writeBoard(out, step.before as QueensDraft)
                    writeBoard(out, step.after as QueensDraft)
                }

                else -> error("no draft codec tag for ${step::class.simpleName}")
            }
        }
    }

    private fun readBoard(cursor: SessionCursor): DecodeResult<QueensDraft> {
        val size = cursor.u8() ?: return malformed("truncated board size")
        val variant = queensVariantId(size)
        if (QueensVariants.byId(variant) == null) return DecodeResult.Err(DecodeError.UnknownVariant(variant.value))
        val cells = size * size
        val regions = ArrayList<Int>(cells)
        for (idx in 0 until cells) {
            val raw = cursor.u8() ?: return malformed("truncated board: $idx of $cells cells")
            regions.add(region(raw, size) ?: return malformed("cell $idx region $raw outside 0 until $size"))
        }
        return DecodeResult.Ok(QueensDraft(size, regions))
    }

    /** Null = malformed (truncated, unknown tag, bad board, or past [MAX_HISTORY]). */
    private fun readSteps(cursor: SessionCursor): List<Command<QueensDraft>>? {
        val count = cursor.u16() ?: return null
        if (count > MAX_HISTORY) return null // before allocating anything proportional to it
        val steps = ArrayList<Command<QueensDraft>>(count)
        repeat(count) {
            val step: Command<QueensDraft> = when (cursor.u8() ?: return null) {
                TAG_PAINT -> readPaint(cursor) ?: return null
                TAG_REPLACE -> {
                    val before = readBoard(cursor).getOrNull() ?: return null
                    ReplaceDraft(before, readBoard(cursor).getOrNull() ?: return null)
                }

                else -> return null
            }
            steps.add(step)
        }
        return steps
    }

    /** Region ids are range-checked later, against the board size the step meets. */
    private fun readPaint(cursor: SessionCursor): QueensDraftCommand.Paint? {
        val region = rawRegion(cursor.u8() ?: return null)
        val cells = LinkedHashSet<Int>()
        repeat(cursor.u8() ?: return null) { if (!cells.add(cursor.u8() ?: return null)) return null }
        val previous = LinkedHashMap<Int, Int>()
        repeat(cursor.u8() ?: return null) {
            val cell = cursor.u8() ?: return null
            if (previous.put(cell, rawRegion(cursor.u8() ?: return null)) != null) return null
        }
        return QueensDraftCommand.Paint(cells, region, previous)
    }

    /** Walk [done] newest-first from the current [size]: each step must apply to the size it meets. */
    private fun fitsBackward(done: List<Command<QueensDraft>>, size: Int): Boolean {
        var current = size
        for (step in done.asReversed()) {
            current = when (step) {
                is ReplaceDraft<*> -> (step.before as QueensDraft).size.takeIf { (step.after as QueensDraft).size == current }
                else -> current.takeIf { (step as QueensDraftCommand.Paint).fits(current) }
            } ?: return false
        }
        return true
    }

    /** Walk [undone] next-redo-first from the current [size]. */
    private fun fitsForward(undone: List<Command<QueensDraft>>, size: Int): Boolean {
        var current = size
        for (step in undone.asReversed()) {
            current = when (step) {
                is ReplaceDraft<*> -> (step.after as QueensDraft).size.takeIf { (step.before as QueensDraft).size == current }
                else -> current.takeIf { (step as QueensDraftCommand.Paint).fits(current) }
            } ?: return false
        }
        return true
    }

    private fun QueensDraftCommand.Paint.fits(size: Int): Boolean {
        val cellCount = size * size
        fun ok(region: Int) = region == QueensDraft.UNPAINTED || region in 0 until size
        return ok(region) && cells.all { it < cellCount } && previous.all { (cell, prev) -> cell < cellCount && ok(prev) }
    }

    private fun regionByte(region: Int): Int = if (region == QueensDraft.UNPAINTED) UNPAINTED_BYTE else region

    private fun rawRegion(raw: Int): Int = if (raw == UNPAINTED_BYTE) QueensDraft.UNPAINTED else raw

    private fun region(raw: Int, size: Int): Int? = rawRegion(raw).takeIf { it == QueensDraft.UNPAINTED || it < size }

    private fun malformed(detail: String): DecodeResult.Err = DecodeResult.Err(DecodeError.Malformed(detail))
}
