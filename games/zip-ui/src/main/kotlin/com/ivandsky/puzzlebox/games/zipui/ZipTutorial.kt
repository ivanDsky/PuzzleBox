package com.ivandsky.puzzlebox.games.zipui

import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.gameui.TutorialStep
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.Wall
import com.ivandsky.puzzlebox.games.zip.WallDirection
import com.ivandsky.puzzlebox.games.zip.ZipBoard
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.ZipPuzzle
import com.ivandsky.puzzlebox.games.zip.ZipPuzzleRef
import com.ivandsky.puzzlebox.games.zip.codec.ZipCodec
import com.ivandsky.puzzlebox.games.zip.engine.ZipVariants
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.LocalDate

/**
 * Zip's three lessons (spec A12), one rule each, on the tutorial-only 3×3 and 4×4 boards
 * ([ZipVariants.tutorial], A11). Each puzzle carries its intended path as the solution.
 * Cells are row-major: on 3×3, 0 1 2 / 3 4 5 / 6 7 8.
 *
 * 1. Connect 1 → 2: walls make one snake, so the line from 1 to 2 fills the board by itself.
 * 2. Numbers in order: 1, 2, 3, 4 sit off reading order; the top row runs into 4 too soon.
 *    One path only.
 * 3. Fill every cell: 1 and 2 are neighbours, so the short line is right there and does
 *    not win. Six paths fill the board; any of them wins.
 */
object ZipTutorial {

    /** Every lesson ref's day; no puzzle number, so it is custom, never a daily. */
    private val lessonDay: LocalDate = LocalDate.of(2026, 1, 1)

    val steps: List<TutorialStep> = listOf(
        lesson(
            instruction = "Draw a line from 1 to 2.",
            n = 3,
            waypoints = listOf(0, 8),
            walls = setOf(0, 1, 4, 5).map { Wall(Cell(it), WallDirection.DOWN) }.toSet(),
            path = listOf(0, 1, 2, 5, 4, 3, 6, 7, 8),
        ),
        lesson(
            instruction = "Go to the numbers in order: 1, 2, 3, 4.",
            n = 3,
            waypoints = listOf(0, 8, 4, 2),
            path = listOf(0, 3, 6, 7, 8, 5, 4, 1, 2),
        ),
        lesson(
            instruction = "Fill every square on your way to 2.",
            n = 4,
            waypoints = listOf(0, 4),
            path = listOf(0, 1, 2, 3, 7, 11, 15, 14, 13, 12, 8, 9, 10, 6, 5, 4),
        ),
    )

    private fun lesson(
        instruction: String,
        n: Int,
        waypoints: List<Int>,
        path: List<Int>,
        walls: Set<Wall> = emptySet(),
    ): TutorialStep {
        val variant = checkNotNull(ZipVariants.byId(VariantId("zip/n$n"))) { "no zip/n$n regime" }
        val puzzle = ZipPuzzle(
            variant = variant.id,
            board = ZipBoard(n = n, walls = walls, waypoints = waypoints.map(::Cell)),
            solution = path.map(::Cell),
        )
        val envelope = Puzzle(
            gameId = ZipDefinition.id,
            variant = variant.id,
            formatVersion = ZipCodec.VERSION,
            payload = ZipCodec.encode(puzzle),
        )
        return TutorialStep(instruction, envelope, ZipPuzzleRef(PuzzleKey.Daily(lessonDay, puzzleNumber = null)))
    }
}
