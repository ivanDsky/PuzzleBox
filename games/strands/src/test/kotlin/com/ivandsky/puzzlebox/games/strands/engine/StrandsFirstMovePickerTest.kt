package com.ivandsky.puzzlebox.games.strands.engine

import com.ivandsky.puzzlebox.games.strands.ActiveHint
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.HintStage
import com.ivandsky.puzzlebox.games.strands.StrandsFixtures
import com.ivandsky.puzzlebox.games.strands.StrandsPuzzle
import com.ivandsky.puzzlebox.games.strands.StrandsState
import com.ivandsky.puzzlebox.games.strands.StrandsWord
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** The first-move hint target (nav-rethink B4), asked through [StrandsHints.hintFor] as the controller asks it. */
class StrandsFirstMovePickerTest {

    private val earned = (1..60).map { "W$it" }.toSet()

    private fun puzzle(rows: List<String>, spangram: StrandsWord, vararg theme: StrandsWord) = StrandsPuzzle(
        rows = rows.size,
        cols = rows.first().length,
        letters = rows.joinToString(""),
        clue = "clue",
        spangram = spangram,
        themeWords = theme.toList(),
        validWords = emptySet(),
        constructors = "",
    )

    private fun word(text: String, vararg cells: Pair<Int, Int>) = StrandsWord(text, cells.map { (r, c) -> Coord(r, c) })

    private fun row(text: String, r: Int) = StrandsWord(text, text.indices.map { Coord(r, it) })

    private fun target(puzzle: StrandsPuzzle, vararg found: String): String {
        val hint = StrandsHints.hintFor(StrandsState(puzzle, found.toList(), earned)) as StrandsHint.Reveal
        assertEquals(HintStage.Outlined, hint.hint.stage)
        return hint.hint.word
    }

    /**
     * BOOK is the shortest word and first in the feed, but its two Os can be traced in either
     * order (two routes); CHAIRS has one route.
     * ```
     * B O C H
     * O K S A
     * T A R I
     * B L E T
     * ```
     */
    private val twoRoutes = puzzle(
        rows = listOf("BOCH", "OKSA", "TARI", "BLET"),
        spangram = word("TABLET", 2 to 0, 2 to 1, 3 to 0, 3 to 1, 3 to 2, 3 to 3),
        word("BOOK", 0 to 0, 0 to 1, 1 to 0, 1 to 1),
        word("CHAIRS", 0 to 2, 0 to 3, 1 to 3, 2 to 3, 2 to 2, 1 to 2),
    )

    /**
     * Every letter differs, so every word has one route; the words are the rows. LAMB is first
     * in the feed, FROG and HENS sit on a board edge.
     * ```
     * F R O G
     * L A M B
     * D U C K   (spangram)
     * H E N S
     * ```
     */
    private val rowsBoard = puzzle(
        rows = listOf("FROG", "LAMB", "DUCK", "HENS"),
        spangram = row("DUCK", 2),
        row("LAMB", 1),
        row("FROG", 0),
        row("HENS", 3),
    )

    @Test
    fun `first move targets the word with the fewest routes`() {
        assertEquals("CHAIRS", target(twoRoutes))
    }

    @Test
    fun `equal routes - first move targets the word with the fewest free letters around it`() {
        // LAMB touches two free rows, FROG only one: the board edge hems FROG in.
        assertEquals("FROG", target(rowsBoard))
    }

    @Test
    fun `first move skips a word the found words and the edge already outline`() {
        // With LAMB found, FROG's four letters are a closed pocket: outlining it would say nothing new.
        assertEquals("HENS", target(rowsBoard, "LAMB"))
    }

    @Test
    fun `first move still targets an outlined word when no other theme word is left, the spangram last`() {
        assertEquals("FROG", target(rowsBoard, "LAMB", "HENS"))
        assertEquals("DUCK", target(rowsBoard, "LAMB", "HENS", "FROG"))
    }

    @Test
    fun `first move keeps the second press on the word already outlined`() {
        val outlined = StrandsState(twoRoutes, nonTheme = earned, hintsUsed = 1, activeHint = ActiveHint("BOOK", HintStage.Outlined))
        assertEquals(
            StrandsHint.Reveal(ActiveHint("BOOK", HintStage.OrderRevealed)),
            StrandsHints.hintFor(outlined),
        )
    }

    @Test
    fun `a real day - first move walks every theme word once, then the spangram`() {
        val puzzle = StrandsFixtures.puzzle(LocalDate.of(2024, 3, 4))
        val order = mutableListOf<String>()
        var state = StrandsState(puzzle, nonTheme = earned)
        repeat(puzzle.totalWords) {
            val word = (StrandsHints.hintFor(state) as StrandsHint.Reveal).hint.word
            order += word
            state = state.copy(found = state.found + word)
        }
        assertEquals(puzzle.themeWords.map { it.text }.toSet(), order.dropLast(1).toSet())
        assertEquals(puzzle.spangram.text, order.last())
    }
}
