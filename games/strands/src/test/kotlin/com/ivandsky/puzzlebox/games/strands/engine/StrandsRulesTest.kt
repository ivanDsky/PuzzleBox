package com.ivandsky.puzzlebox.games.strands.engine

import com.ivandsky.puzzlebox.game.Move
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.StrandsFixtures
import com.ivandsky.puzzlebox.games.strands.StrandsPuzzle
import com.ivandsky.puzzlebox.games.strands.StrandsState
import com.ivandsky.puzzlebox.games.strands.StrandsWord
import com.ivandsky.puzzlebox.games.strands.render.CellState
import com.ivandsky.puzzlebox.games.strands.render.StrandsRendererModel
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrandsRulesTest {

    /**
     * ```
     * B E A M    theme BEAM = row 0; also traceable as (0,0)(1,0)(1,1)(1,2)
     * E A M S    spangram EAMS = row 1
     * ```
     * BEAMS is a valid non-theme word.
     */
    private val beam = StrandsWord("BEAM", row(0))
    private val eams = StrandsWord("EAMS", row(1))
    private val board = StrandsPuzzle(
        rows = 2,
        cols = 4,
        letters = "BEAMEAMS",
        clue = "Rafters",
        spangram = eams,
        themeWords = listOf(beam),
        validWords = setOf("BEAM", "EAMS", "BEAMS"),
        constructors = "Test Setter",
    )
    private val fresh = StrandsState(board)
    private val atCap = fresh.copy(nonTheme = (1..StrandsRules.MAX_NON_THEME_WORDS).map { "WORD$it" }.toSet())

    private val beamElsewhere = trace(0 to 0, 1 to 0, 1 to 1, 1 to 2)
    private val beams = trace(0 to 0, 0 to 1, 0 to 2, 0 to 3, 1 to 3)
    private val beae = trace(0 to 0, 0 to 1, 1 to 1, 1 to 0)

    private val fixture = StrandsFixtures.puzzle(LocalDate.of(2026, 11, 5))

    private fun row(r: Int) = (0 until 4).map { Coord(r, it) }
    private fun trace(vararg cells: Pair<Int, Int>) = StrandsMove(cells.map { Coord(it.first, it.second) })
    private fun verdict(move: Move, state: StrandsState = fresh) = StrandsRules.validate(move, state)
    private fun StrandsRendererModel.cell(row: Int, col: Int) = cells[row * cols + col]

    /** [length] cells snaking left-right then right-left down a [cols]-wide board. */
    private fun snake(cols: Int, length: Int) = StrandsMove(
        (0 until length).map { i -> Coord(i / cols, if ((i / cols) % 2 == 0) i % cols else cols - 1 - i % cols) },
    )

    @Test
    fun `a foreign move is foreign before anything else`() {
        assertEquals(StrandsVerdict.Foreign, verdict(object : Move {}))
    }

    @Test
    fun `the path guard comes before every length verdict`() {
        assertEquals(StrandsVerdict.InvalidPath, verdict(trace(0 to 0, 0 to 2)))
        assertEquals(StrandsVerdict.InvalidPath, verdict(trace(0 to 0, 2 to 0)))
        assertEquals(StrandsVerdict.InvalidPath, verdict(trace(0 to 0, 0 to 1, 0 to 0)))
    }

    @Test
    fun `an empty trace is empty`() {
        assertEquals(StrandsVerdict.Empty, verdict(StrandsMove(emptyList())))
    }

    @Test
    fun `under four letters is too short, even when already found`() {
        val bea = trace(0 to 0, 0 to 1, 0 to 2)
        assertEquals(StrandsVerdict.TooShort, verdict(bea))
        assertEquals(StrandsVerdict.TooShort, verdict(bea, fresh.copy(nonTheme = setOf("BEA"))))
        assertEquals(StrandsVerdict.ThemeWord(beam), verdict(trace(0 to 0, 0 to 1, 0 to 2, 0 to 3)))
    }

    @Test
    fun `over twenty-four letters is too long, even when already found`() {
        val long = snake(fixture.cols, StrandsRules.MAX_LETTERS + 1)
        val word = long.path.map(fixture::letterAt).joinToString("")
        val state = StrandsState(fixture)
        assertEquals(StrandsVerdict.TooLong, StrandsRules.validate(long, state))
        assertEquals(StrandsVerdict.TooLong, StrandsRules.validate(long, state.copy(nonTheme = setOf(word))))
        assertEquals(
            StrandsVerdict.NotInWordList,
            StrandsRules.validate(snake(fixture.cols, StrandsRules.MAX_LETTERS), state),
        )
    }

    @Test
    fun `already found beats the theme match and the cap`() {
        val found = atCap.copy(found = listOf("BEAM", "EAMS"), nonTheme = atCap.nonTheme + "BEAMS")
        assertEquals(StrandsVerdict.AlreadyFound, verdict(beamElsewhere, found))
        assertEquals(StrandsVerdict.AlreadyFound, verdict(StrandsMove(eams.cells), found))
        assertEquals(StrandsVerdict.AlreadyFound, verdict(beams, found))
    }

    @Test
    fun `theme words and the spangram match before the word cap (NYT checks the cap first)`() {
        assertEquals(StrandsVerdict.ThemeWord(beam), verdict(beamElsewhere, atCap))
        assertEquals(StrandsVerdict.Spangram(eams), verdict(StrandsMove(eams.cells), atCap))
    }

    @Test
    fun `a hundred non-theme words is the word limit, before the word list`() {
        assertEquals(StrandsVerdict.WordLimitReached, verdict(beams, atCap))
        assertEquals(StrandsVerdict.WordLimitReached, verdict(beae, atCap))
        val belowCap = atCap.copy(nonTheme = atCap.nonTheme.drop(1).toSet())
        assertEquals(StrandsVerdict.NonTheme("BEAMS"), verdict(beams, belowCap))
    }

    @Test
    fun `a word outside the list is not in word list, a listed one is non-theme`() {
        assertEquals(StrandsVerdict.NotInWordList, verdict(beae))
        assertEquals(StrandsVerdict.NonTheme("BEAMS"), verdict(beams))
    }

    @Test
    fun `found cells can be traced again`() {
        val found = fresh.copy(found = listOf("BEAM", "EAMS"))
        assertEquals(StrandsVerdict.NonTheme("BEAMS"), verdict(beams, found))
    }

    @Test
    fun `a theme word spelled on other cells counts and paints its intended cells`() {
        val spangramElsewhere = trace(0 to 1, 0 to 2, 0 to 3, 1 to 3)
        assertEquals(StrandsVerdict.Spangram(eams), verdict(spangramElsewhere))

        val spelled = verdict(beamElsewhere) as StrandsVerdict
        assertEquals(StrandsVerdict.ThemeWord(beam), spelled)
        val model = StrandsRendererModel.of(fresh.accept(spelled))
        beam.cells.forEach { assertEquals(CellState.FoundTheme, model.cell(it.row, it.col).state) }
        (beamElsewhere.path - beam.cells.toSet()).forEach { assertEquals(CellState.Idle, model.cell(it.row, it.col).state) }
    }

    @Test
    fun `every fixture word traced on its intended cells counts by its kind`() {
        val state = StrandsState(fixture)
        fixture.themeWords.forEach {
            assertEquals(StrandsVerdict.ThemeWord(it), StrandsRules.validate(StrandsMove(it.cells), state))
        }
        assertEquals(
            StrandsVerdict.Spangram(fixture.spangram),
            StrandsRules.validate(StrandsMove(fixture.spangram.cells), state),
        )
    }

    @Test
    fun `tracing the whole fixture set completes it and every retrace is already found`() {
        val words = fixture.themeWords + fixture.spangram
        val done = words.fold(StrandsState(fixture)) { state, word ->
            assertFalse(StrandsRules.isComplete(state))
            state.accept(StrandsRules.validate(StrandsMove(word.cells), state) as StrandsVerdict)
        }
        assertTrue(StrandsRules.isComplete(done))
        words.forEach { assertEquals(StrandsVerdict.AlreadyFound, StrandsRules.validate(StrandsMove(it.cells), done)) }
    }

    @Test
    fun `completion needs every theme word and the spangram`() {
        assertFalse(StrandsRules.isComplete(fresh.copy(found = listOf("BEAM"))))
        assertFalse(StrandsRules.isComplete(fresh.copy(found = listOf("EAMS"))))
        assertTrue(StrandsRules.isComplete(fresh.copy(found = listOf("EAMS", "BEAM"))))
    }

    @Test
    fun `the hint is the hint economy's decision`() {
        assertEquals(StrandsHints.hintFor(fresh), StrandsRules.hint(fresh))
        assertEquals(StrandsHints.hintFor(atCap), StrandsRules.hint(atCap))
    }
}
