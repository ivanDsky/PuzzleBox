package com.ivandsky.puzzlebox.games.sudoku.engine.trainer

import com.ivandsky.puzzlebox.game.trainer.BoardKind
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Mini Sudoku intro's recordings (spec §2.7): which variants a technique has and the real step each one shows. */
class SudokuIntroTest {

    private val order = SudokuTechniqueIds.all

    @Test
    fun `last cell has a line and a box variant, the rest one`() {
        val names = order.associate { it.value to SudokuIntro.variantCount(it) }
        assertEquals(
            mapOf(
                "last-cell" to 2,
                "hidden-single-box" to 1,
                "hidden-single-line" to 1,
                "naked-single" to 1,
                "pointing" to 1,
                "claiming" to 1,
                "naked-pair" to 1,
                "hidden-pair" to 1,
            ),
            names,
        )
    }

    /** The fixtures and the corpus: the boards the demos may come from (the corpus alone holds no claiming or hidden pair). */
    private val boards by lazy { SudokuTrainerFixtures.all + SudokuTrainerFixtures.corpus }

    /** The recording of every variant of every technique on [boards], with where it came from. */
    private val recordings by lazy {
        boards.flatMap { (name, puzzle) ->
            order.flatMap { technique ->
                (0 until SudokuIntro.variantCount(technique)).mapNotNull { variant ->
                    SudokuIntro.record(puzzle, technique, variant)?.let { Triple(name, technique, it) }
                }
            }
        }
    }

    @Test
    fun `every variant of every technique is recorded on some fixture or corpus board`() {
        val seen = recordings.map { (_, technique, recording) -> technique to recording.variant }.toSet()
        val missing = order.flatMap { technique -> (0 until SudokuIntro.variantCount(technique)).map { technique to it } } - seen
        assertTrue("variants with no recording: ${missing.map { it.first.value to it.second }}", missing.isEmpty())
    }

    @Test
    fun `a recording opens on the focus board and ends on that position with the step made`() {
        val puzzles = boards
        assertTrue(recordings.isNotEmpty())
        for ((name, technique, recording) in recordings) {
            val puzzle = puzzles.getValue(name)
            val label = "$name ${technique.value} variant ${recording.variant}"
            assertEquals(label, SudokuPractice.start(puzzle, technique, BoardKind.Focus), recording.before)
            val made = SudokuPractice.commands(recording.before, recording.step).fold(recording.before) { board, command -> command.apply(board) }
            assertEquals(label, made, recording.landings.last())
            assertEquals(label, technique, SudokuTechniqueIds.of(recording.step))
            assertEquals(label, recording.variant, SudokuIntro.variantOf(recording.step))
        }
    }

    @Test
    fun `a recording lands one square at a time, each landing changing only that square's cell`() {
        for ((name, technique, recording) in recordings) {
            val label = "$name ${technique.value} variant ${recording.variant}"
            assertEquals(label, recording.squares.size, recording.landings.size)
            val boards = listOf(recording.before) + recording.landings
            boards.zipWithNext().forEachIndexed { i, (was, now) ->
                assertNotEquals("$label landing $i", was, now)
                val cell = recording.squares[i]
                val step = recording.step
                if (step is SudokuStep.Elimination) {
                    // Only the struck cell's notes change: no other cell's content differs.
                    was.cells.indices.filter { it != cell }.forEach { assertEquals("$label landing $i cell $it", was.cells[it], now.cells[it]) }
                    assertTrue("$label landing $i", now.cells[cell] is SudokuCell.Notes)
                } else {
                    assertEquals("$label landing $i", (step as SudokuStep.Single).place.digit, now.digitAt(cell))
                }
            }
        }
    }

    @Test
    fun `an elimination's squares are the struck cells, each landing the digits struck there`() {
        val eliminations = recordings.filter { it.third.step is SudokuStep.Elimination }
        assertTrue(eliminations.isNotEmpty())
        for ((name, technique, recording) in eliminations) {
            val step = recording.step as SudokuStep.Elimination
            assertEquals("$name ${technique.value}", step.struck.keys.toList(), recording.squares)
        }
    }

    @Test
    fun `a puzzle shows one variant of a technique, the one its opening step belongs to`() {
        for ((name, puzzle) in SudokuTrainerFixtures.all) {
            for (technique in order) {
                val variants = (0 until SudokuIntro.variantCount(technique)).filter { SudokuIntro.record(puzzle, technique, it) != null }
                val hasBoard = SudokuPractice.start(puzzle, technique, BoardKind.Focus) != null
                assertEquals("$name ${technique.value}", if (hasBoard) 1 else 0, variants.size)
            }
        }
    }

    @Test
    fun `no recording for a variant the opening step is not, or one the technique does not have`() {
        val (name, technique, recording) = recordings.first { SudokuIntro.variantCount(it.second) == 2 }
        val puzzle = boards.getValue(name)
        assertNull(SudokuIntro.record(puzzle, technique, 1 - recording.variant))
        assertNull(SudokuIntro.record(puzzle, technique, 2))
        assertNotNull(SudokuIntro.record(puzzle, technique, recording.variant))
    }

    @Test
    fun `no recording where the technique is never the easiest rung`() {
        val dailies = SudokuTrainerFixtures.dailies.getValue("li:1")
        val absent = order.filter { SudokuPractice.start(dailies, it, BoardKind.Focus) == null }
        assertTrue(absent.isNotEmpty())
        for (technique in absent) for (variant in 0 until SudokuIntro.variantCount(technique)) {
            assertNull("${technique.value} $variant", SudokuIntro.record(dailies, technique, variant))
        }
    }
}
