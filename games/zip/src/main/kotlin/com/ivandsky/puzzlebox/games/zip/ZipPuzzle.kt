package com.ivandsky.puzzlebox.games.zip

import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.game.VariantId
import java.time.LocalDate

/** Row-major board cell, `0 until n*n` (research 01: idx = row*n + col). */
@JvmInline
value class Cell(val idx: Int) {
    override fun toString(): String = "Cell($idx)"
}

/** Wall orientation, matching the wire's WallDirection_RIGHT / WallDirection_DOWN. */
enum class WallDirection { RIGHT, DOWN }

/** A wall on the interior edge between [cell] and its RIGHT/DOWN neighbor. */
data class Wall(val cell: Cell, val direction: WallDirection)

/**
 * The Zip board: n x n grid, interior walls, and waypoints — [waypoints][i] is the cell
 * labeled i+1 (research 01 orderedSequence). Walls only ever sit on interior edges.
 */
data class ZipBoard(
    val n: Int,
    val walls: Set<Wall>,
    val waypoints: List<Cell>,
) {
    val cellCount: Int = n * n

    fun contains(cell: Cell): Boolean = cell.idx in 0 until cellCount

    fun rowOf(cell: Cell): Int = cell.idx / n
    fun colOf(cell: Cell): Int = cell.idx % n

    /** Label (1-based) of the waypoint at [cell], or null. */
    fun waypointLabel(cell: Cell): Int? =
        waypoints.indexOf(cell).takeIf { it >= 0 }?.plus(1)

    fun isWaypoint(cell: Cell): Boolean = waypointLabel(cell) != null

    /** Is the interior edge between 4-adjacent [a] and [b] walled? (I2) */
    fun blocked(a: Cell, b: Cell): Boolean {
        val diff = b.idx - a.idx
        return when {
            diff == 1 && colOf(a) == colOf(b) - 1 -> Wall(a, WallDirection.RIGHT) in walls
            diff == -1 && colOf(b) == colOf(a) - 1 -> Wall(b, WallDirection.RIGHT) in walls
            diff == n -> Wall(a, WallDirection.DOWN) in walls
            diff == -n -> Wall(b, WallDirection.DOWN) in walls
            else -> false // not 4-adjacent: not a wall question
        }
    }

    /** The cell's in-bounds 4-neighbors, in stable (right, left, down, up) order. */
    fun neighbors(cell: Cell): List<Cell> = buildList(4) {
        val r = rowOf(cell)
        val c = colOf(cell)
        if (c < n - 1) add(Cell(cell.idx + 1))
        if (c > 0) add(Cell(cell.idx - 1))
        if (r < n - 1) add(Cell(cell.idx + n))
        if (r > 0) add(Cell(cell.idx - n))
    }
}

/**
 * A Zip puzzle: rules pin + board + the official solution when the daily ships one.
 * The solution is the A16 oracle INPUT (fetch validation compares the solver against it)
 * and, since A18 §6, the play-time hint source ([ZipHints] walks it); boards without one
 * fall back to the solver. [unknownTail] preserves bytes a newer reader added —
 * re-emitted verbatim by the codec so decode∘encode stays lossless across versions (A19).
 */
data class ZipPuzzle(
    val variant: VariantId,
    val board: ZipBoard,
    val solution: List<Cell>?,
    val unknownTail: ByteArray = ByteArray(0),
) {
    override fun equals(other: Any?): Boolean =
        other is ZipPuzzle &&
            variant == other.variant &&
            board == other.board &&
            solution == other.solution &&
            unknownTail.contentEquals(other.unknownTail)

    override fun hashCode(): Int {
        var h = variant.hashCode()
        h = 31 * h + board.hashCode()
        h = 31 * h + (solution?.hashCode() ?: 0)
        h = 31 * h + unknownTail.contentHashCode()
        return h
    }
}

/**
 * Puzzle identity (finding F2-3, ADR-0018): a daily's LinkedIn day plus puzzle number
 * (#592-style), or a friend's puzzle by share code. Lives on the session — identity is
 * fetch-time metadata, not board data; the codec bytes stay identity-free and core/data
 * owns persistence of identity (ADR-0004).
 */
data class ZipPuzzleRef(override val key: PuzzleKey) : PuzzleRef {
    override val gameId: GameId get() = ZipDefinition.id
    constructor(date: LocalDate, puzzleNumber: Int?) : this(PuzzleKey.Daily(date, puzzleNumber)) // deleted in P12
}
