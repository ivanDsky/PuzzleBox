package com.ivandsky.puzzlebox.games.sudoku.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.game.editor.PublishBlock
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.sudoku.KnownSolutions
import com.ivandsky.puzzlebox.games.sudoku.SudokuDefinition
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuTechnique
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuGraderTest {

    private class Fixture(val name: String, val definition: SudokuDefinition, givens: String, solution: String) {
        val puzzle = SudokuPuzzle(definition.variant, KnownSolutions.grid(givens), KnownSolutions.grid(solution))
    }

    private val mini = SudokuGames.MINI
    private val classic = SudokuGames.CLASSIC

    private val singles = listOf(
        Fixture("mini singles", mini, KnownSolutions.MINI_SAMPLE_GIVENS, KnownSolutions.MINI_SAMPLE_SOLUTION),
        Fixture("classic singles", classic, KnownSolutions.CLASSIC_SAMPLE_GIVENS, KnownSolutions.CLASSIC_SAMPLE_SOLUTION),
    )

    private val intersections = listOf(
        Fixture("mini intersections", mini, "000500000042000204005003506000030000", "214536653142361254425613546321132465"),
        Fixture(
            "classic intersections",
            classic,
            "200100309080032000400009200600010090000007000007390000304200005060500102000060403",
            "275186349189432756436759218623814597591627834847395621314278965968543172752961483",
        ),
    )

    private val pairs = listOf(
        Fixture("mini pairs", mini, "000250100040010024000000040000306010", "634251125346513624462135241563356412"),
        Fixture(
            "classic pairs",
            classic,
            "000005700000100030005006000027981000060020001900000520701200000000007040032058000",
            "243895716896172435175436289527981364368524971914763528781249653659317842432658197",
        ),
    )

    /** No 6×6 board that grades to triples turned up, so Expert has a 9×9 fixture only. */
    private val triples = listOf(
        Fixture(
            "classic triples",
            classic,
            "700042006840000000000006080209000000060001000401963200005700020604300000000628500",
            "713842956846597132592136784279485361368271495451963278985714623624359817137628549",
        ),
    )

    private val guessing = listOf(
        Fixture("mini guessing", mini, "000405001003000040030001020000600050", "362415541623156342234561425136613254"),
        Fixture(
            "classic guessing",
            classic,
            "103002080400801902000007000000600379000200000000003004540190000006380000900000005",
            "193462587467851932852937146215648379374219658689573214548196723726385491931724865",
        ),
    )

    private val labelled = mapOf(
        SudokuRung.SINGLES to singles,
        SudokuRung.INTERSECTIONS to intersections,
        SudokuRung.PAIRS to pairs,
        SudokuRung.TRIPLES to triples,
    )

    @Test
    fun `each fixture grades to its label with no guessing`() {
        for ((rung, fixtures) in labelled) {
            for (fixture in fixtures) {
                assertEquals(fixture.name, SudokuGrade(rung, needsGuessing = false), SudokuGrader.grade(fixture.puzzle))
            }
        }
    }

    @Test
    fun `every label has a 9x9 fixture and all but triples have a 6x6 one`() {
        for ((rung, fixtures) in labelled) {
            val sizes = fixtures.map { it.puzzle.geometry.size }.toSet()
            val expected = if (rung == SudokuRung.TRIPLES) setOf(9) else setOf(6, 9)
            assertEquals(rung.name, expected, sizes)
        }
    }

    @Test
    fun `a board the ladder cannot finish needs guessing at both sizes`() {
        for (fixture in guessing) {
            assertTrue(fixture.name, SudokuGrader.grade(fixture.puzzle).needsGuessing)
        }
    }

    @Test
    fun `a labelled board is Unique with that level and nothing blocks publishing`() {
        for ((rung, fixtures) in labelled) {
            for (fixture in fixtures) {
                val verdict = unique(fixture)
                assertEquals(fixture.name, Difficulty(rung), verdict.difficulty)
                assertFalse(fixture.name, verdict.needsGuessing)
                assertEquals(fixture.name, emptyList<PublishBlock>(), blocks(fixture, verdict))
            }
        }
    }

    @Test
    fun `a reveal board is Unique with no label and publishing is blocked as needs-guessing`() {
        for (fixture in guessing) {
            val verdict = unique(fixture)
            assertNull(fixture.name, verdict.difficulty)
            assertTrue(fixture.name, verdict.needsGuessing)
            assertEquals(fixture.name, listOf(PublishBlock(PublishBlock.NEEDS_GUESSING)), blocks(fixture, verdict))
        }
    }

    @Test
    fun `the gates block only a Unique verdict that needs guessing, with the ids they declare`() {
        val draft = SudokuDraft(6, List(36) { 0 })
        val solution = Solution(emptyList())
        val unblocked = listOf(Verdict.Checking, Verdict.Undetermined, Verdict.None(emptySet()), Verdict.Unique(solution))
        for (verdict in unblocked) assertEquals(emptyList<PublishBlock>(), SudokuPublishGates.blocks(draft, verdict))
        val blocked = SudokuPublishGates.blocks(draft, Verdict.Unique(solution, needsGuessing = true))
        assertEquals(SudokuPublishGates.blockIds, blocked.map { it.id }.toSet())
    }

    @Test
    fun `grading the same board twice gives the same grade`() {
        for (fixture in labelled.values.flatten() + guessing) {
            assertEquals(fixture.name, SudokuGrader.grade(fixture.puzzle), SudokuGrader.grade(fixture.puzzle))
            assertEquals(fixture.name, verdict(fixture), verdict(fixture))
        }
    }

    @Test
    fun `a full board has no label and does not need guessing`() {
        val full = KnownSolutions.miniSample.solution
        val solved = SudokuPuzzle(mini.variant, full, full)
        assertEquals(SudokuGrade(null, needsGuessing = false), SudokuGrader.grade(solved))
    }

    @Test
    fun `rungs are declared easiest first and every technique has one`() {
        assertEquals(SudokuRung.entries.sortedBy { it.level }, SudokuRung.entries)
        val rungs = SudokuTechnique.entries.map(SudokuRung::of)
        assertEquals(rungs.sorted(), rungs)
        assertEquals(SudokuRung.entries.toSet(), rungs.toSet())
    }

    private fun draftOf(fixture: Fixture): SudokuDraft = SudokuDraft(fixture.puzzle.geometry.size, fixture.puzzle.givens)

    private fun verdict(fixture: Fixture): Verdict =
        SudokuEditor(fixture.definition).analyze(draftOf(fixture), Budget.Unlimited)

    private fun unique(fixture: Fixture): Verdict.Unique {
        val verdict = verdict(fixture)
        assertTrue("${fixture.name}: $verdict", verdict is Verdict.Unique)
        return verdict as Verdict.Unique
    }

    private fun blocks(fixture: Fixture, verdict: Verdict): List<PublishBlock> =
        SudokuEditor(fixture.definition).publishBlocks(draftOf(fixture), verdict)
}
