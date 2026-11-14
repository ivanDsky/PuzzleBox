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
 * v1: [u8 VERSION] board  [u16 n][n × step] done (oldest first)  [u16 m][m × step] undone  [crowns]
 *     board  = [u8 size][size² × u8 region]
 *     crowns = [u8 k][k × u8 cell]   (ascending; top level: only when k > 0, end of bytes ⇒ no crowns)
 *     step   = [u8 TAG_PAINT][u8 region][u8 k][k × u8 cell][u8 p][p × (u8 cell, u8 region)]
 *            | [u8 TAG_REPLACE] board before, board after                       (both crown-free)
 *            | [u8 TAG_CROWN][u8 cell][u8 placed 0|1][u8 seed | UNPAINTED_BYTE]  (since 35)
 *            | [u8 TAG_REPLACE_CROWNED] board crowns before, board crowns after (since 35; either crowned)
 * ```
 *
 * Crowns joined v1 without a bump (ledger ruling): a crown-free draft writes exactly its
 * pre-35 bytes, so every stored draft and the frozen `queens-draft-v1.bin` decode unchanged.
 *
 * Each stack keeps its newest [MAX_HISTORY] steps; older ones drop on encode (undo depth
 * shrinks, the board never changes). Decode re-applies board sizes through the stacks, so a
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
    private const val TAG_CROWN = 0x03
    private const val TAG_REPLACE_CROWNED = 0x04

    /** History entries: QueensDraftCommand.Paint/Crown and ReplaceDraft<QueensDraft>, each with a tag byte. */
    fun encode(state: DraftState<QueensDraft>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(VERSION)
        writeBoard(out, state.draft)
        writeSteps(out, state.history.done)
        writeSteps(out, state.history.undone)
        if (state.draft.crowns.isNotEmpty()) writeCrowns(out, state.draft.crowns)
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
        // Present only when k > 0, so an empty section is malformed rather than a second spelling.
        val crowns = if (cursor.pos == bytes.size) emptySet() else readCrowns(cursor, draft.regions.size)?.ifEmpty { null }
        if (crowns == null) return malformed("malformed crowns section")
        if (cursor.pos != bytes.size) return malformed("${bytes.size - cursor.pos} trailing bytes")
        if (!fitsBackward(done, draft.size)) return malformed("undo history does not fit the board")
        if (!fitsForward(undone, draft.size)) return malformed("redo history does not fit the board")
        return DecodeResult.Ok(DraftState(draft.copy(crowns = crowns), CommandHistory(done, undone)))
    }

    private fun writeBoard(out: ByteArrayOutputStream, draft: QueensDraft) {
        out.write(draft.size)
        draft.regions.forEach { out.write(regionByte(it)) }
    }

    private fun writeCrowns(out: ByteArrayOutputStream, crowns: Set<Int>) {
        out.write(crowns.size)
        crowns.sorted().forEach(out::write)
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

                is QueensDraftCommand.Crown -> {
                    out.write(TAG_CROWN)
                    out.write(step.cell)
                    out.write(if (step.placed) 1 else 0)
                    out.write(step.seed ?: UNPAINTED_BYTE)
                }

                is ReplaceDraft<*> -> writeReplace(out, step.before as QueensDraft, step.after as QueensDraft)

                else -> error("no draft codec tag for ${step::class.simpleName}")
            }
        }
    }

    private fun writeReplace(out: ByteArrayOutputStream, before: QueensDraft, after: QueensDraft) {
        val crowned = before.crowns.isNotEmpty() || after.crowns.isNotEmpty()
        out.write(if (crowned) TAG_REPLACE_CROWNED else TAG_REPLACE)
        for (board in listOf(before, after)) {
            writeBoard(out, board)
            if (crowned) writeCrowns(out, board.crowns)
        }
    }

    /** Crown-free; the crowns section, when present, follows separately. */
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

                TAG_CROWN -> readCrown(cursor) ?: return null
                TAG_REPLACE_CROWNED -> readCrownedReplace(cursor) ?: return null

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

    /** Seed ids are range-checked later, like paint regions. A removal carries no seed. */
    private fun readCrown(cursor: SessionCursor): QueensDraftCommand.Crown? {
        val cell = cursor.u8() ?: return null
        val placed = when (cursor.u8() ?: return null) {
            0 -> false
            1 -> true
            else -> return null
        }
        val seed = (cursor.u8() ?: return null).takeIf { it != UNPAINTED_BYTE }
        if (!placed && seed != null) return null
        return QueensDraftCommand.Crown(cell, placed, seed)
    }

    /** Null when malformed, or when neither board has crowns (that step is always TAG_REPLACE). */
    private fun readCrownedReplace(cursor: SessionCursor): ReplaceDraft<QueensDraft>? {
        val boards = List(2) {
            val board = readBoard(cursor).getOrNull() ?: return null
            board.copy(crowns = readCrowns(cursor, board.regions.size) ?: return null)
        }
        if (boards.all { it.crowns.isEmpty() }) return null
        return ReplaceDraft(boards[0], boards[1])
    }

    /** `[u8 k][k × u8 cell]`, strictly ascending and on the board; null when malformed or truncated. */
    private fun readCrowns(cursor: SessionCursor, cellCount: Int): Set<Int>? {
        val crowns = LinkedHashSet<Int>()
        repeat(cursor.u8() ?: return null) {
            val cell = cursor.u8() ?: return null
            if (cell >= cellCount || crowns.any { it >= cell }) return null
            crowns.add(cell)
        }
        return crowns
    }

    /** Walk [done] newest-first from the current [size]: each step must apply to the size it meets. */
    private fun fitsBackward(done: List<Command<QueensDraft>>, size: Int): Boolean {
        var current = size
        for (step in done.asReversed()) {
            current = when (step) {
                is ReplaceDraft<*> -> (step.before as QueensDraft).size.takeIf { (step.after as QueensDraft).size == current }
                else -> current.takeIf { (step as QueensDraftCommand).fits(current) }
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
                else -> current.takeIf { (step as QueensDraftCommand).fits(current) }
            } ?: return false
        }
        return true
    }

    private fun QueensDraftCommand.fits(size: Int): Boolean {
        val cellCount = size * size
        fun ok(region: Int) = region == QueensDraft.UNPAINTED || region in 0 until size
        return when (this) {
            is QueensDraftCommand.Paint ->
                ok(region) && cells.all { it < cellCount } && previous.all { (cell, prev) -> cell < cellCount && ok(prev) }

            is QueensDraftCommand.Crown -> cell < cellCount && (seed == null || seed in 0 until size)
        }
    }

    private fun regionByte(region: Int): Int = if (region == QueensDraft.UNPAINTED) UNPAINTED_BYTE else region

    private fun rawRegion(raw: Int): Int = if (raw == UNPAINTED_BYTE) QueensDraft.UNPAINTED else raw

    private fun region(raw: Int, size: Int): Int? = rawRegion(raw).takeIf { it == QueensDraft.UNPAINTED || it < size }

    private fun malformed(detail: String): DecodeResult.Err = DecodeResult.Err(DecodeError.Malformed(detail))
}
