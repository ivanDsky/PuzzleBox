package com.ivandsky.puzzlebox.games.strandsui

import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.StrandsNumber
import com.ivandsky.puzzlebox.games.strands.StrandsPuzzle
import com.ivandsky.puzzlebox.games.strands.StrandsWord
import com.ivandsky.puzzlebox.games.strands.codec.StrandsCodec
import com.ivandsky.puzzlebox.games.strands.strandsPlugin
import com.ivandsky.puzzlebox.gameui.TutorialStep
import java.time.LocalDate

/**
 * Strands' two lessons (spec A13) on tiny boards whose words read straight along a row,
 * so a first-time player sees each answer at a glance. In each, the theme words and the
 * spangram cover every letter exactly once, and the spangram runs edge to edge.
 *
 * 1. Tracing: two 4-letter rows, each a word.
 * 2. The spangram: three rows, the middle one spanning the board.
 */
internal object StrandsTutorial {

    private val day: LocalDate = LocalDate.of(2026, 1, 1)

    /**
     * A lesson needs a puzzle number, unlike Queens' custom boards: the Strands score model
     * gives an unnumbered board no completion record (A11), and the sandbox moves on only
     * when a record is handed to it. The sandbox drops that record, so the number never
     * reaches History.
     */
    private val number: Int = StrandsNumber.of(day)

    val steps: List<TutorialStep> = listOf(
        lesson(
            StrandsCopy.TUTORIAL_TRACE,
            clue = "Animal friends",
            rows = listOf("BIRD", "PETS"),
            spangramRow = 1,
        ),
        lesson(
            StrandsCopy.TUTORIAL_SPANGRAM,
            clue = "Bake sale",
            rows = listOf("PIES", "BAKE", "TART"),
            spangramRow = 1,
        ),
    )

    /** One lesson whose words are its [rows], left to right; [spangramRow] is the spangram. */
    private fun lesson(instruction: String, clue: String, rows: List<String>, spangramRow: Int): TutorialStep {
        val words = rows.mapIndexed { row, text -> StrandsWord(text, text.indices.map { Coord(row, it) }) }
        val puzzle = StrandsPuzzle(
            rows = rows.size,
            cols = rows.first().length,
            letters = rows.joinToString(""),
            clue = clue,
            spangram = words[spangramRow],
            themeWords = words - words[spangramRow],
            validWords = rows.toSet(),
            constructors = "PuzzleBox",
        )
        return TutorialStep(
            instruction = instruction,
            puzzle = StrandsCodec.envelope(puzzle),
            ref = strandsPlugin.refOf(day, number),
        )
    }
}
