package com.ivandsky.puzzlebox.games.sudokuui.trainer

import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightRole
import com.ivandsky.puzzlebox.game.trainer.BoardKind
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuVariants
import com.ivandsky.puzzlebox.games.sudoku.SudokuSamples
import com.ivandsky.puzzlebox.games.sudoku.codec.SudokuCodec
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuIntro
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuPractice
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuTechniqueIds
import com.ivandsky.puzzlebox.gameui.trainer.IntroVariant
import org.junit.Assert.assertEquals
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Mini Sudoku intro's variants and demos (spec §2.7) as the trainer hands them out. */
class SudokuTrainerIntroTest {

    private val trainer = SudokuTrainerFixtures.trainer
    private val order = SudokuTechniqueIds.all

    private fun variants(id: String): List<IntroVariant> = trainer.variants(TechniqueId(id))

    private class Shown(val name: String, val technique: TechniqueId, val variant: Int, val demo: SudokuIntroDemo)

    /** The fixture boards, with the real daily li:6, whose first last cell is in a box (no fixture board has one). */
    private val puzzles: Map<String, SudokuPuzzle> by lazy {
        SudokuTrainerFixtures.all + ("li:6" to SudokuPuzzle(
            SudokuVariants.MINI,
            grid("1.......523.2.4..53.6..44.1..3......"),
            grid("132546645231214365356124421653563412"),
        ))
    }

    private fun grid(text: String): List<Int> = text.map { if (it == '.') 0 else it.digitToInt() }

    /** Every demo any of [puzzles] hands out, for every variant of every technique. */
    private val demos: List<Shown> by lazy {
        puzzles.flatMap { (name, puzzle) ->
            order.flatMap { technique ->
                trainer.variants(technique).indices.mapNotNull { variant ->
                    (trainer.demo(SudokuTrainerFixtures.canonical(puzzle), technique, variant) as SudokuIntroDemo?)
                        ?.let { Shown(name, technique, variant, it) }
                }
            }
        }
    }

    @Test
    fun `last cell has a line and a box variant, the others one titled by name`() {
        assertEquals(listOf("Last cell in a row or column", "Last cell in a box"), variants("last-cell").map { it.title })
        for (id in listOf("hidden-single-box", "hidden-single-line", "naked-single", "pointing", "claiming", "naked-pair", "hidden-pair")) {
            assertEquals(id, listOf(SudokuTrainerCatalog.name(TechniqueId(id))), variants(id).map { it.title })
        }
        for (technique in order) assertEquals(technique.value, SudokuIntro.variantCount(technique), trainer.variants(technique).size)
    }

    @Test
    fun `every variant is written out`() {
        for (technique in order) for (variant in trainer.variants(technique)) {
            listOf(variant.rule, variant.spotIt, variant.doIt).forEach { assertTrue("${technique.value}: $it", it.length > 10) }
            assertTrue(technique.value, variant.title.isNotBlank())
        }
    }

    @Test
    fun `variant and demo copy uses number, square and row or column, never digit, cell or line`() {
        val words = Regex("""\b(digits?|cells?|lines?|cols?|box \d)\b""", RegexOption.IGNORE_CASE)
        for (technique in order) for (variant in trainer.variants(technique)) {
            listOf(variant.rule, variant.spotIt, variant.doIt).forEach {
                assertEquals("${technique.value}: $it", null, words.find(it)?.value)
            }
        }
        for (shown in demos) shown.demo.captions.forEach { assertEquals(it, null, words.find(it)?.value) }
    }

    @Test
    fun `a demo's landing frames all tell the whole landing and the step track's two captions differ`() {
        for (shown in demos) {
            val label = "${shown.name} ${shown.technique.value} ${shown.variant}"
            val captions = shown.demo.captions
            assertEquals(label, 1, captions.drop(2).toSet().size)
            assertTrue(label, captions[1] != captions[2])
            assertTrue(label, captions[1].startsWith("Every open square shows, in small numbers"))
        }
    }

    @Test
    fun `the rule of the first chapters that mention notes says what notes are, and every note-off do-it says how`() {
        assertTrue(variants("hidden-single-box").single().rule.contains("shows its notes: small numbers"))
        for (id in listOf("pointing", "claiming", "naked-pair", "hidden-pair")) {
            val doIt = variants(id).single().doIt
            assertTrue("$id: $doIt", doIt.contains("turn Notes on, select the square, then tap the number"))
        }
    }

    @Test
    fun `claiming speaks of a column and pointing's spot it asks for a note left outside the box`() {
        val claiming = variants("claiming").single()
        assertTrue(claiming.rule, claiming.rule.contains("one box of a column"))
        assertTrue(claiming.spotIt, claiming.spotIt.startsWith("A column"))
        assertTrue(variants("pointing").single().spotIt.contains("still shows the note outside the box"))
    }

    @Test
    fun `the pair intros tell naked from hidden`() {
        assertTrue(variants("naked-pair").single().rule.contains("Naked means the pair's notes are plain to see."))
        assertTrue(variants("hidden-pair").single().rule.contains("Hidden means other notes hide the pair."))
    }

    @Test
    fun `a demo is handed out on a fixture for every variant of every technique`() {
        val seen = demos.map { it.technique to it.variant }.toSet()
        val missing = order.flatMap { technique -> trainer.variants(technique).indices.map { technique to it } } - seen
        assertTrue("no demo for ${missing.map { it.first.value to it.second }}", missing.isEmpty())
    }

    @Test
    fun `a demo has one caption per frame and opens on the focus board`() {
        assertTrue(demos.isNotEmpty())
        for (shown in demos) {
            val label = "${shown.name} ${shown.technique.value} ${shown.variant}"
            val puzzle = puzzles.getValue(shown.name)
            val demo = shown.demo
            assertEquals(label, shown.variant, demo.variant)
            assertEquals(label, SudokuPractice.start(puzzle, shown.technique, BoardKind.Focus), demo.frame(0).board)
            assertEquals(label, demo.frame(1).board, demo.frame(0).board)
            val last = demo.captions.lastIndex
            assertTrue(label, demo.frame(last).board != demo.frame(0).board)
            assertEquals(label, demo.frame(last), demo.frame(last + 7))
            assertEquals(label, demo.frame(0), demo.frame(-3))
            assertTrue(label, demo.captions.size >= 3)
            demo.captions.forEach { assertTrue("$label: $it", it.isNotBlank()) }
        }
    }

    @Test
    fun `a demo lights only its evidence, nothing on the first frame, more as each square lands`() {
        for (shown in demos) {
            val label = "${shown.name} ${shown.technique.value} ${shown.variant}"
            val demo = shown.demo
            val last = demo.captions.lastIndex
            assertNull(label, demo.frame(0).spotlight)
            var lit = 0
            for (i in 1..last) {
                val spotlight = checkNotNull(demo.frame(i).spotlight) { "$label frame $i" }
                assertNull(label, spotlight.callout)
                assertTrue(label, spotlight.roles.values.all { it == SpotlightRole.EVIDENCE })
                assertTrue("$label frame $i lights no more than the one before", spotlight.roles.size >= lit)
                lit = spotlight.roles.size
            }
            assertTrue(label, lit > 0)
        }
    }

    @Test
    fun `a demo's squares land one frame each, an elimination's struck cells and a single's placement`() {
        for (shown in demos) {
            val label = "${shown.name} ${shown.technique.value} ${shown.variant}"
            val demo = shown.demo
            val squares = demo.captions.size - 2
            val changed = (1..squares).count { demo.frame(it + 1).board != demo.frame(it).board }
            assertEquals(label, squares, changed)
        }
    }

    @Test
    fun `no demo for another game's puzzle, a technique the board never reaches, or a variant the opening step is not`() {
        val daily = SudokuTrainerFixtures.dailies.getValue("li:1")
        val shown = demos.first { SudokuIntro.variantCount(it.technique) == 2 }
        val puzzle = SudokuTrainerFixtures.canonical(puzzles.getValue(shown.name))
        assertNotNull(trainer.demo(puzzle, shown.technique, shown.variant))
        assertNull(trainer.demo(puzzle, shown.technique, 1 - shown.variant))
        assertNull(trainer.demo(puzzle, shown.technique, 2))
        val absent = order.first { SudokuPractice.start(daily, it, BoardKind.Focus) == null }
        assertNull(trainer.demo(SudokuTrainerFixtures.canonical(daily), absent, 0))
        assertNull(trainer.demo(Puzzle(GameId("queens"), puzzle.variant, puzzle.formatVersion, puzzle.payload), shown.technique, shown.variant))
    }
}
