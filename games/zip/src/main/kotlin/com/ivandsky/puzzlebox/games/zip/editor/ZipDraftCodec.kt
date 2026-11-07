package com.ivandsky.puzzlebox.games.zip.editor

import com.ivandsky.puzzlebox.game.Command
import com.ivandsky.puzzlebox.game.CommandHistory
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.ReplaceDraft
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.Wall
import com.ivandsky.puzzlebox.games.zip.WallDirection
import com.ivandsky.puzzlebox.games.zip.codec.ZipSessionCursor
import com.ivandsky.puzzlebox.games.zip.engine.ZipVariants
import java.io.ByteArrayOutputStream

/**
 * Versioned Zip draft bytes (ADR-0005), integers big-endian, cells row-major (n ≤ 8, so one byte):
 *
 * ```
 * v1: [u8 VERSION] board  [u16 n][n × step] done (oldest first)  [u16 m][m × step] undone
 *     board = [u8 n][u8 w][w × (u8 cell, u8 dir 0=RIGHT|1=DOWN)]   (walls ascending by cell, dir)
 *             [u8 p][p × u8 cell]                                  (path, drawing order)
 *             [u8 k][k × u8 cell]                                  (waypoints, label order)
 *     step  = [u8 TAG_REPLACE] board before, board after
 * ```
 *
 * Each stack keeps its newest [MAX_HISTORY] steps; older ones drop on encode. Decode replays
 * board sizes through the stacks, so a step that could not apply to the board it meets is
 * malformed. Unknown versions, sizes, tags and trailing bytes are rejected, never guessed.
 */
object ZipDraftCodec {
    const val VERSION: Int = 1

    /** Steps each of the undo and redo stacks keeps; the oldest drop first. */
    const val MAX_HISTORY: Int = 10_000

    private const val TAG_REPLACE = 0x02

    /** History entries: ReplaceDraft<ZipDraft> only, each with a tag byte. */
    fun encode(state: DraftState<ZipDraft>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(VERSION)
        writeBoard(out, state.draft)
        writeSteps(out, state.history.done)
        writeSteps(out, state.history.undone)
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): DecodeResult<DraftState<ZipDraft>> {
        val cursor = ZipSessionCursor(bytes)
        val version = cursor.u8() ?: return malformed("empty draft bytes")
        if (version != VERSION) return DecodeResult.Err(DecodeError.UnsupportedVersion(version, VERSION))
        val draft = when (val board = readBoard(cursor)) {
            is DecodeResult.Ok -> board.value
            is DecodeResult.Err -> return board
        }
        val done = readSteps(cursor) ?: return malformed("malformed undo history")
        val undone = readSteps(cursor) ?: return malformed("malformed redo history")
        if (cursor.pos != bytes.size) return malformed("${bytes.size - cursor.pos} trailing bytes")
        if (!fitsBackward(done, draft.n)) return malformed("undo history does not fit the board")
        if (!fitsForward(undone, draft.n)) return malformed("redo history does not fit the board")
        return DecodeResult.Ok(DraftState(draft, CommandHistory(done, undone)))
    }

    private fun writeBoard(out: ByteArrayOutputStream, draft: ZipDraft) {
        out.write(draft.n)
        val walls = draft.walls.sortedWith(compareBy({ it.cell.idx }, { it.direction }))
        out.write(walls.size)
        walls.forEach {
            out.write(it.cell.idx)
            out.write(it.direction.ordinal)
        }
        out.write(draft.path.size)
        draft.path.forEach(out::write)
        out.write(draft.waypoints.size)
        draft.waypoints.forEach(out::write)
    }

    private fun writeSteps(out: ByteArrayOutputStream, steps: List<Command<ZipDraft>>) {
        val kept = steps.takeLast(MAX_HISTORY)
        out.write(kept.size shr 8)
        out.write(kept.size and 0xFF)
        kept.forEach { step ->
            check(step is ReplaceDraft<*>) { "no draft codec tag for ${step::class.simpleName}" }
            out.write(TAG_REPLACE)
            writeBoard(out, step.before as ZipDraft)
            writeBoard(out, step.after as ZipDraft)
        }
    }

    private fun readBoard(cursor: ZipSessionCursor): DecodeResult<ZipDraft> {
        val n = cursor.u8() ?: return malformed("truncated board size")
        val variant = "zip/n$n"
        if (ZipVariants.byId(VariantId(variant)) == null) return DecodeResult.Err(DecodeError.UnknownVariant(variant))
        val cells = n * n
        val walls = LinkedHashSet<Wall>()
        var last = -1
        repeat(cursor.u8() ?: return malformed("truncated walls")) {
            val cell = cursor.cell(cells) ?: return malformed("wall cell off the board")
            val direction = WallDirection.entries.getOrNull(cursor.u8() ?: return malformed("truncated wall"))
                ?: return malformed("unknown wall direction")
            val key = cell.idx * 2 + direction.ordinal
            if (key <= last) return malformed("walls out of order")
            last = key
            walls.add(Wall(cell, direction))
        }
        val path = cursor.cells(cells) ?: return malformed("malformed path")
        val waypoints = cursor.cells(cells) ?: return malformed("malformed waypoints")
        return DecodeResult.Ok(ZipDraft(n, walls, path.map(Cell::idx), waypoints.map(Cell::idx)))
    }

    /** Null = malformed (truncated, unknown tag, bad board, or past [MAX_HISTORY]). */
    private fun readSteps(cursor: ZipSessionCursor): List<Command<ZipDraft>>? {
        val count = cursor.u16() ?: return null
        if (count > MAX_HISTORY) return null // before allocating anything proportional to it
        val steps = ArrayList<Command<ZipDraft>>(count)
        repeat(count) {
            if (cursor.u8() != TAG_REPLACE) return null
            val before = readBoard(cursor).getOrNull() ?: return null
            steps.add(ReplaceDraft(before, readBoard(cursor).getOrNull() ?: return null))
        }
        return steps
    }

    /** Walk [done] newest-first from the current [n]: each step must end on the size it meets. */
    private fun fitsBackward(done: List<Command<ZipDraft>>, n: Int): Boolean {
        var current = n
        for (step in done.asReversed()) {
            step as ReplaceDraft<*>
            if ((step.after as ZipDraft).n != current) return false
            current = (step.before as ZipDraft).n
        }
        return true
    }

    /** Walk [undone] next-redo-first from the current [n]. */
    private fun fitsForward(undone: List<Command<ZipDraft>>, n: Int): Boolean {
        var current = n
        for (step in undone.asReversed()) {
            step as ReplaceDraft<*>
            if ((step.before as ZipDraft).n != current) return false
            current = (step.after as ZipDraft).n
        }
        return true
    }

    private fun malformed(detail: String): DecodeResult.Err = DecodeResult.Err(DecodeError.Malformed(detail))
}
