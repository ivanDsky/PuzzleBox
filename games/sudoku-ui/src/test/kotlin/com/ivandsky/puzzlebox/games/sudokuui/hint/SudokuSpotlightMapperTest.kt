package com.ivandsky.puzzlebox.games.sudokuui.hint

import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightRole
import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuSamples
import com.ivandsky.puzzlebox.games.sudoku.SudokuUnit
import com.ivandsky.puzzlebox.games.sudoku.SudokuVariants
import com.ivandsky.puzzlebox.games.sudoku.UnitKind
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuHint
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuTechnique
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuSpotlightMapperTest {

    private val classic = SudokuSamples.classic
    private val classicBoard = SudokuBoard.of(classic)
    private val mini = SudokuPuzzle(
        SudokuVariants.MINI,
        List(36) { 0 },
        "612345345612456123123456561234234561".map { it.digitToInt() },
    )
    private val miniBoard = SudokuBoard.of(mini)

    private fun bit(digit: Int) = 1 shl (digit - 1)

    private fun place(cell: Int, digit: Int, technique: SudokuTechnique, unit: SudokuUnit?) =
        SudokuHint.Place(cell, digit, technique, unit, null, emptyList())

    private fun map(step: SudokuStep, puzzle: SudokuPuzzle = mini, board: SudokuBoard = miniBoard, playId: Int = 0) =
        checkNotNull(SudokuSpotlightMapper.map(step, puzzle, board, playId)) { "no spotlight for $step" }

    private fun rolesOf(step: SudokuStep) = map(step).roles

    private val box0 = SudokuUnit(UnitKind.BOX, 0)
    private val row0 = SudokuUnit(UnitKind.ROW, 0)
    private val col3 = SudokuUnit(UnitKind.COLUMN, 3)

    // Single

    @Test
    fun `a hidden single targets its cell, lights its unit and its blockers, nothing else`() {
        val step = SudokuStep.Single(place(7, 4, SudokuTechnique.HIDDEN_SINGLE_BOX, box0), null, setOf(20, 33))

        val roles = rolesOf(step)

        val expected = (listOf(0, 1, 2, 6, 8, 20, 33).associateWith { SpotlightRole.EVIDENCE }) + (7 to SpotlightRole.TARGET)
        assertEquals(expected, roles)
    }

    @Test
    fun `a last cell lights the whole unit it fills`() {
        val step = SudokuStep.Single(place(2, 6, SudokuTechnique.HIDDEN_SINGLE_LINE, row0), row0, emptySet())

        val roles = rolesOf(step)

        assertEquals(SpotlightRole.TARGET, roles[2])
        assertEquals(setOf(0, 1, 3, 4, 5), roles.filterValues { it == SpotlightRole.EVIDENCE }.keys)
    }

    @Test
    fun `a naked single lights only its blockers`() {
        val step = SudokuStep.Single(place(0, 4, SudokuTechnique.NAKED_SINGLE, null), null, setOf(1, 8, 30))

        val roles = rolesOf(step)

        assertEquals(mapOf(0 to SpotlightRole.TARGET, 1 to SpotlightRole.EVIDENCE, 8 to SpotlightRole.EVIDENCE, 30 to SpotlightRole.EVIDENCE), roles)
    }

    @Test
    fun `the target is never also evidence`() {
        val step = SudokuStep.Single(place(7, 4, SudokuTechnique.HIDDEN_SINGLE_BOX, box0), null, setOf(7, 20))

        val roles = rolesOf(step)

        assertEquals(SpotlightRole.TARGET, roles[7])
    }

    // Elimination

    @Test
    fun `an elimination lights its evidence, strikes its cells and has no target`() {
        val step = SudokuStep.Elimination(SudokuTechnique.POINTING, box0, bit(6), mapOf(3 to bit(6), 9 to bit(6)), setOf(0, 1))

        val roles = rolesOf(step)

        assertEquals(
            mapOf(0 to SpotlightRole.EVIDENCE, 1 to SpotlightRole.EVIDENCE, 3 to SpotlightRole.EXCLUDED, 9 to SpotlightRole.EXCLUDED),
            roles,
        )
        assertFalse(SpotlightRole.TARGET in roles.values)
    }

    @Test
    fun `a cell that is both evidence and struck is excluded`() {
        val step = SudokuStep.Elimination(SudokuTechnique.NAKED_PAIR, row0, bit(5) or bit(6), mapOf(1 to bit(5)), setOf(0, 1))

        assertEquals(SpotlightRole.EXCLUDED, rolesOf(step)[1])
        assertEquals(SpotlightRole.EVIDENCE, rolesOf(step)[0])
    }

    // Reveal, Mistake, Solved

    @Test
    fun `a reveal names only its cell as the target`() {
        assertEquals(mapOf(4 to SpotlightRole.TARGET), rolesOf(SudokuStep.Reveal(4, 5)))
    }

    @Test
    fun `a mistake names only the wrong cell as the culprit`() {
        assertEquals(mapOf(4 to SpotlightRole.CULPRIT), rolesOf(SudokuStep.Mistake(4, 5, null)))
    }

    @Test
    fun `a mistake that clashes shows its partner as evidence, never a second culprit`() {
        val roles = rolesOf(SudokuStep.Mistake(4, 5, 10))

        assertEquals(mapOf(4 to SpotlightRole.CULPRIT, 10 to SpotlightRole.EVIDENCE), roles)
    }

    @Test
    fun `a solved board has no spotlight and no key`() {
        assertNull(SudokuSpotlightMapper.map(SudokuStep.Solved, mini, miniBoard, 0))
        assertNull(SudokuSpotlightMapper.keyOf(SudokuStep.Solved))
    }

    // Size, playId, callout

    @Test
    fun `the grid is as big as the board and the playId passes through, mini and classic`() {
        val miniSpot = map(SudokuStep.Reveal(0, 6), mini, miniBoard, playId = 7)
        val classicSpot = map(SudokuStep.Reveal(0, 5), classic, classicBoard, playId = 3)

        assertEquals(listOf(6, 6, 7), listOf(miniSpot.rows, miniSpot.cols, miniSpot.playId))
        assertEquals(listOf(9, 9, 3), listOf(classicSpot.rows, classicSpot.cols, classicSpot.playId))
    }

    @Test
    fun `the callout text is the step copy and anchors on evidence, else the target, else the struck cells`() {
        val single = SudokuStep.Single(place(0, 4, SudokuTechnique.NAKED_SINGLE, null), null, setOf(1, 8))
        val reveal = SudokuStep.Reveal(4, 5)
        val blind = SudokuStep.Elimination(SudokuTechnique.NAKED_PAIR, row0, bit(1) or bit(2), mapOf(2 to bit(1)), emptySet())

        assertEquals(SudokuStepCopy.text(single, mini, miniBoard), map(single).callout?.text)
        assertEquals(setOf(1, 8), map(single).callout?.anchor)
        assertEquals(setOf(4), map(reveal).callout?.anchor)
        assertEquals(setOf(2), map(blind).callout?.anchor)
        assertEquals(setOf(10), map(SudokuStep.Mistake(4, 5, 10)).callout?.anchor)
        assertEquals(setOf(4), map(SudokuStep.Mistake(4, 5, null)).callout?.anchor)
    }

    // Glyph data

    @Test
    fun `glyph texts are the digit for a single and a reveal, the board digit for a mistake`() {
        val single = SudokuStep.Single(place(7, 4, SudokuTechnique.NAKED_SINGLE, null), null, emptySet())
        val board = miniBoard.with(4, SudokuCell.Entry(5))

        assertEquals(SudokuSpotlightGlyphs(mapOf(7 to "4")), map(single).glyphs)
        assertEquals(SudokuSpotlightGlyphs(mapOf(4 to "2")), map(SudokuStep.Reveal(4, 2)).glyphs)
        assertEquals(SudokuSpotlightGlyphs(mapOf(4 to "5")), map(SudokuStep.Mistake(4, 5, null), board = board).glyphs)
    }

    @Test
    fun `glyph texts of an elimination are the struck digits of each cell from its mask`() {
        val step = SudokuStep.Elimination(SudokuTechnique.HIDDEN_PAIR, row0, bit(2) or bit(3), mapOf(0 to (bit(1) or bit(4) or bit(6)), 1 to bit(2)), setOf(0, 1))

        assertEquals(SudokuSpotlightGlyphs(mapOf(0 to "1 4 6", 1 to "2")), map(step).glyphs)
    }

    // Keys

    @Test
    fun `equal steps give equal keys and any change of the step changes the key`() {
        val single = SudokuStep.Single(place(7, 4, SudokuTechnique.NAKED_SINGLE, null), null, setOf(1))
        val elim = SudokuStep.Elimination(SudokuTechnique.POINTING, box0, bit(6), mapOf(3 to bit(6)), setOf(0))
        val key = SudokuSpotlightMapper::keyOf

        assertEquals(key(single), key(single.copy()))
        assertEquals(key(elim), key(elim.copy(struck = mapOf(3 to bit(6)))))
        assertNotEquals(key(single), key(SudokuStep.Single(place(8, 4, SudokuTechnique.NAKED_SINGLE, null), null, setOf(1))))
        assertNotEquals(key(single), key(SudokuStep.Single(place(7, 5, SudokuTechnique.NAKED_SINGLE, null), null, setOf(1))))
        assertNotEquals(key(elim), key(elim.copy(technique = SudokuTechnique.CLAIMING)))
        assertNotEquals(key(elim), key(elim.copy(unit = SudokuUnit(UnitKind.BOX, 1))))
        assertNotEquals(key(elim), key(elim.copy(struck = mapOf(3 to bit(5)))))
        assertNotEquals(key(elim), key(elim.copy(struck = mapOf(4 to bit(6)))))
    }

    @Test
    fun `a mistake and a reveal on the same cell have different keys`() {
        assertNotEquals(SudokuSpotlightMapper.keyOf(SudokuStep.Mistake(4, 5, null)), SudokuSpotlightMapper.keyOf(SudokuStep.Reveal(4, 5)))
        assertNotEquals(SudokuSpotlightMapper.keyOf(SudokuStep.Mistake(4, 5, null)), SudokuSpotlightMapper.keyOf(SudokuStep.Mistake(4, 6, null)))
    }
}
