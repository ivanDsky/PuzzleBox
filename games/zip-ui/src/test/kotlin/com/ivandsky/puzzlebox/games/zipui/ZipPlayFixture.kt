package com.ivandsky.puzzlebox.games.zipui

import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.Wall
import com.ivandsky.puzzlebox.games.zip.WallDirection
import com.ivandsky.puzzlebox.games.zip.ZipBoard
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.ZipPuzzle
import com.ivandsky.puzzlebox.games.zip.ZipPuzzleRef
import com.ivandsky.puzzlebox.games.zip.ZipSession
import com.ivandsky.puzzlebox.games.zip.codec.ZipCodec
import com.ivandsky.puzzlebox.games.zip.engine.ZipVariants
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.LocalDate

/**
 * Deterministic 5x5 fixture (hand-verifiable, mirrors games/zip's ZipTestSupport board):
 * boustrophedon solution; DOWN walls on every row crossing the solution does NOT use, so
 * exactly one full path exists.
 */
object ZipPlayFixture {

    val solution: List<Cell> = listOf(
        0, 1, 2, 3, 4, 9, 8, 7, 6, 5, 10, 11, 12, 13, 14,
        19, 18, 17, 16, 15, 20, 21, 22, 23, 24,
    ).map(::Cell)

    private val wallCells: Set<Int> = (0..19).toSet() - setOf(4, 5, 14, 15)

    val waypoints: List<Cell> = listOf(Cell(0), Cell(12), Cell(24))

    fun board(): ZipBoard =
        ZipBoard(n = 5, walls = wallCells.map { Wall(Cell(it), WallDirection.DOWN) }.toSet(), waypoints = waypoints)

    fun puzzle(): ZipPuzzle =
        ZipPuzzle(variant = ZipVariants.byId(VariantId("zip/n5"))!!.id, board = board(), solution = solution)

    /** The board with wall (3,DOWN) removed: [0,1,2,3,8] becomes a provably dead trace
     *  (the 8-entry pocket strands the rest), while [0,1,2,3] still completes. */
    fun branchyPuzzle(): ZipPuzzle {
        val walls = board().walls - Wall(Cell(3), WallDirection.DOWN)
        return ZipPuzzle(variant = ZipVariants.byId(VariantId("zip/n5"))!!.id, board = ZipBoard(5, walls, waypoints), solution = solution)
    }

    /** Envelope as DailyRepository hands it to the UI (payload = codec bytes). */
    fun envelope(puzzle: ZipPuzzle = puzzle()): Puzzle = Puzzle(
        gameId = ZipDefinition.id,
        variant = puzzle.variant,
        formatVersion = ZipCodec.VERSION,
        payload = ZipCodec.encode(puzzle),
    )

    /** Valid, provably dead trace on [branchyPuzzle]. */
    fun deadTrace(): List<Cell> = listOf(0, 1, 2, 3, 8).map(::Cell)

    fun path(vararg idx: Int): List<Cell> = idx.map(::Cell)

    fun ref(day: LocalDate, puzzleNumber: Int? = 592) = ZipPuzzleRef(PuzzleKey.Daily(day, puzzleNumber))

    fun session(ref: ZipPuzzleRef? = null) = ZipSession(ref = ref, puzzle = puzzle())
}
