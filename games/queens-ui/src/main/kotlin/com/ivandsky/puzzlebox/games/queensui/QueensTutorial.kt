package com.ivandsky.puzzlebox.games.queensui

import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import com.ivandsky.puzzlebox.games.queens.queensPlugin
import com.ivandsky.puzzlebox.games.queens.queensVariantId
import com.ivandsky.puzzlebox.gameui.TutorialStep
import java.time.LocalDate

/**
 * Queens' three lessons (spec A12/A13) on 4×4 boards, the smallest Queens size. A Queens
 * board has no given queens, so every lesson starts empty and takes four queens. Each
 * board's colors force its answer fast, and each answer is shipped as the solution.
 * Cells are row-major: 0 1 2 3 / 4 5 6 7 / 8 9 10 11 / 12 13 14 15.
 *
 * 1. The tap cycle, guided: three colors are one square each, so the queens are plain
 *    to see, and the board shows a faint queen on each answer square. No rule is taught
 *    yet, so this lesson never shows a rule caption or rule stripes.
 * 2. Row, column and color: one color is a single square, and each queen after it is
 *    the last free square of its color, row or column. These rules alone decide it.
 * 3. No touching: without that rule the board has six answers. The top's lone square
 *    takes a queen, and from there each next row keeps one free square: the others
 *    touch a queen (corners included) or share its column.
 *
 * A child placing only answer squares never breaks a rule: × marks break nothing, and
 * answer queens never share a line or touch. Auto-cross never marks an answer square,
 * so every answer square takes exactly two taps, × then queen.
 */
internal object QueensTutorial {

    /** Tutorial refs are custom boards: a fixed day and no puzzle number (A11). */
    private val day: LocalDate = LocalDate.of(2026, 1, 1)

    val steps: List<TutorialStep> = listOf(
        lesson(
            "Tap once for ×. Tap twice for a queen.",
            rows = listOf("0010", "2000", "0003", "0000"),
            queens = listOf(2, 4, 11, 13),
            guided = true,
        ),
        lesson(
            "One queen in each row, column and color.",
            rows = listOf("0000", "0001", "2011", "2231"),
            queens = listOf(1, 7, 8, 14),
        ),
        lesson(
            "Queens never touch, not even corner to corner.",
            rows = listOf("1103", "1223", "1223", "1233"),
            queens = listOf(2, 4, 11, 13),
        ),
    )

    /**
     * One lesson from its [rows] of region ids (one digit per square) and its answer
     * [queens]. A [guided] lesson shows that answer on the board.
     */
    private fun lesson(
        instruction: String,
        rows: List<String>,
        queens: List<Int>,
        guided: Boolean = false,
    ): TutorialStep {
        val puzzle = QueensPuzzle(
            size = rows.size,
            regions = rows.joinToString("").map { it.digitToInt() },
            solution = queens.map(::Cell),
        )
        return TutorialStep(
            instruction = instruction,
            puzzle = Puzzle(
                gameId = QueensDefinition.id,
                variant = queensVariantId(puzzle.size),
                formatVersion = QueensCodec.VERSION,
                payload = QueensCodec.encode(puzzle),
            ),
            ref = queensPlugin.refOf(day, null),
            guided = guided,
        )
    }
}
