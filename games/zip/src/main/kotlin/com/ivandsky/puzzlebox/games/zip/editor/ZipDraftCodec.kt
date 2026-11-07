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
 *           | [u8 TAG_PATH_EDIT] cells before, cells after, cells waypointsBefore, cells waypointsAfter
 *           | [u8 TAG_TOGGLE_MARK] [u8 cell] cells waypointsBefore, cells waypointsAfter
 *           | [u8 TAG_TOGGLE_WALL] [u8 cell, u8 dir]
 *           | [u8 TAG_CLEAR_WALLS] [u8 w][w × (u8 cell, u8 dir)]
 *     cells = [u8 k][k × u8 cell]
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

    /** Largest board is 8 × 8; command steps are checked against their real size once the board is known. */
    private const val MAX_CELLS = 64

    private const val TAG_REPLACE = 0x02
    private const val TAG_PATH_EDIT = 0x03
    private const val TAG_TOGGLE_MARK = 0x04
    private const val TAG_TOGGLE_WALL = 0x05
    private const val TAG_CLEAR_WALLS = 0x06

    /** History entries: ReplaceDraft<ZipDraft> and [ZipDraftCommand], each with a tag byte. */
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
        writeWalls(out, draft.walls)
        writeCells(out, draft.path)
        writeCells(out, draft.waypoints)
    }

    private fun writeWalls(out: ByteArrayOutputStream, walls: Set<Wall>) {
        out.write(walls.size)
        walls.sortedWith(compareBy({ it.cell.idx }, { it.direction })).forEach { writeWall(out, it) }
    }

    private fun writeWall(out: ByteArrayOutputStream, wall: Wall) {
        out.write(wall.cell.idx)
        out.write(wall.direction.ordinal)
    }

    private fun writeCells(out: ByteArrayOutputStream, cells: List<Int>) {
        out.write(cells.size)
        cells.forEach(out::write)
    }

    private fun writeSteps(out: ByteArrayOutputStream, steps: List<Command<ZipDraft>>) {
        val kept = steps.takeLast(MAX_HISTORY)
        out.write(kept.size shr 8)
        out.write(kept.size and 0xFF)
        kept.forEach { step ->
            writeStep(out, step)
        }
    }

    private fun writeStep(out: ByteArrayOutputStream, step: Command<ZipDraft>) {
        when (step) {
            is ReplaceDraft<*> -> {
                out.write(TAG_REPLACE)
                writeBoard(out, step.before as ZipDraft)
                writeBoard(out, step.after as ZipDraft)
            }
            is ZipDraftCommand.PathEdit -> {
                out.write(TAG_PATH_EDIT)
                writeCells(out, step.before)
                writeCells(out, step.after)
                writeCells(out, step.waypointsBefore)
                writeCells(out, step.waypointsAfter)
            }
            is ZipDraftCommand.ToggleMark -> {
                out.write(TAG_TOGGLE_MARK)
                out.write(step.cell)
                writeCells(out, step.waypointsBefore)
                writeCells(out, step.waypointsAfter)
            }
            is ZipDraftCommand.ToggleWall -> {
                out.write(TAG_TOGGLE_WALL)
                writeWall(out, step.wall)
            }
            is ZipDraftCommand.ClearWalls -> {
                out.write(TAG_CLEAR_WALLS)
                writeWalls(out, step.before)
            }
            else -> error("no draft codec tag for ${step::class.simpleName}")
        }
    }

    private fun readBoard(cursor: ZipSessionCursor): DecodeResult<ZipDraft> {
        val n = cursor.u8() ?: return malformed("truncated board size")
        val variant = "zip/n$n"
        if (ZipVariants.byId(VariantId(variant)) == null) return DecodeResult.Err(DecodeError.UnknownVariant(variant))
        val cells = n * n
        val walls = readWalls(cursor, cells) ?: return malformed("malformed walls")
        val path = cursor.cells(cells) ?: return malformed("malformed path")
        val waypoints = cursor.cells(cells) ?: return malformed("malformed waypoints")
        return DecodeResult.Ok(ZipDraft(n, walls, path.map(Cell::idx), waypoints.map(Cell::idx)))
    }

    /** Walls ascending by cell then direction, as [writeWalls] lays them out; null = malformed. */
    private fun readWalls(cursor: ZipSessionCursor, cells: Int): Set<Wall>? {
        val walls = LinkedHashSet<Wall>()
        var last = -1
        repeat(cursor.u8() ?: return null) {
            val wall = readWall(cursor, cells) ?: return null
            val key = wall.cell.idx * 2 + wall.direction.ordinal
            if (key <= last) return null
            last = key
            walls.add(wall)
        }
        return walls
    }

    private fun readWall(cursor: ZipSessionCursor, cells: Int): Wall? {
        val cell = cursor.cell(cells) ?: return null
        val direction = WallDirection.entries.getOrNull(cursor.u8() ?: return null) ?: return null
        return Wall(cell, direction)
    }

    private fun readCells(cursor: ZipSessionCursor): List<Int>? = cursor.cells(MAX_CELLS)?.map(Cell::idx)

    /** Null = malformed (truncated, unknown tag, bad board, or past [MAX_HISTORY]). */
    private fun readSteps(cursor: ZipSessionCursor): List<Command<ZipDraft>>? {
        val count = cursor.u16() ?: return null
        if (count > MAX_HISTORY) return null // before allocating anything proportional to it
        val steps = ArrayList<Command<ZipDraft>>(count)
        repeat(count) { steps.add(readStep(cursor) ?: return null) }
        return steps
    }

    private fun readStep(cursor: ZipSessionCursor): Command<ZipDraft>? = when (cursor.u8()) {
        TAG_REPLACE -> {
            val before = readBoard(cursor).getOrNull()
            val after = if (before == null) null else readBoard(cursor).getOrNull()
            if (before == null || after == null) null else ReplaceDraft(before, after)
        }
        TAG_PATH_EDIT -> {
            val before = readCells(cursor)
            val after = if (before == null) null else readCells(cursor)
            val waypointsBefore = if (after == null) null else readCells(cursor)
            val waypointsAfter = if (waypointsBefore == null) null else readCells(cursor)
            if (waypointsAfter == null) null else ZipDraftCommand.PathEdit(before!!, after!!, waypointsBefore!!, waypointsAfter)
        }
        TAG_TOGGLE_MARK -> {
            val cell = cursor.cell(MAX_CELLS)?.idx
            val before = if (cell == null) null else readCells(cursor)
            val after = if (before == null) null else readCells(cursor)
            if (after == null) null else ZipDraftCommand.ToggleMark(cell!!, before!!, after)
        }
        TAG_TOGGLE_WALL -> readWall(cursor, MAX_CELLS)?.let(ZipDraftCommand::ToggleWall)
        TAG_CLEAR_WALLS -> readWalls(cursor, MAX_CELLS)?.let(ZipDraftCommand::ClearWalls)
        else -> null
    }

    /** Walk [done] newest-first from the current [n]: each step must end on the size it meets. */
    private fun fitsBackward(done: List<Command<ZipDraft>>, n: Int): Boolean {
        var current = n
        for (step in done.asReversed()) {
            if (step is ReplaceDraft<*>) {
                if ((step.after as ZipDraft).n != current) return false
                current = (step.before as ZipDraft).n
            } else if (!onBoard(step, current)) return false
        }
        return true
    }

    /** Walk [undone] next-redo-first from the current [n]. */
    private fun fitsForward(undone: List<Command<ZipDraft>>, n: Int): Boolean {
        var current = n
        for (step in undone.asReversed()) {
            if (step is ReplaceDraft<*>) {
                if ((step.before as ZipDraft).n != current) return false
                current = (step.after as ZipDraft).n
            } else if (!onBoard(step, current)) return false
        }
        return true
    }

    /** Every cell a command names lies on an [n] × [n] board, so applying or reverting it builds a valid draft. */
    private fun onBoard(step: Command<ZipDraft>, n: Int): Boolean {
        val named = when (step) {
            is ZipDraftCommand.PathEdit -> step.before + step.after + step.waypointsBefore + step.waypointsAfter
            is ZipDraftCommand.ToggleMark -> step.waypointsBefore + step.waypointsAfter + step.cell
            is ZipDraftCommand.ToggleWall -> listOf(step.wall.cell.idx)
            is ZipDraftCommand.ClearWalls -> step.before.map { it.cell.idx }
            else -> return false
        }
        return named.all { it in 0 until n * n }
    }

    private fun malformed(detail: String): DecodeResult.Err = DecodeResult.Err(DecodeError.Malformed(detail))
}
