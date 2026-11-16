package com.ivandsky.puzzlebox.games.sudoku.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Completion
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.FinishReadiness
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.sudoku.KnownSolutions
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuGeometry
import com.ivandsky.puzzlebox.games.sudoku.SudokuVariants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Finish for me on Sudoku (spec E1): readiness, and completions of seeded drafts cut from
 * published grids at both sizes. Seeded and unbudgeted, so every run is the same.
 */
class SudokuCompleterTest {

    @Test
    fun `two givens clashing in a row, a column or a box block finishing and name the cells`() {
        for (size in SudokuDraft.SIZES) {
            for (other in listOf(size - 1, size * (size - 1), size + 1)) {
                val clashing = SudokuDraft(size, List(size * size) { if (it == 0 || it == other) 3 else 0 })

                assertEquals("size $size", FinishReadiness.Blocked(setOf(0, other)), SudokuCompleter.readiness(clashing))
            }
        }
    }

    @Test
    fun `one given short of the minimum needs one more`() {
        for (size in SudokuDraft.SIZES) {
            val short = diagonal(size, SudokuCompleter.minimumGivens(size) - 1)

            assertEquals(
                "size $size",
                FinishReadiness.NeedsMore(IncompleteReason(SudokuCompleter.GIVENS, 1)),
                SudokuCompleter.readiness(short),
            )
        }
    }

    @Test
    fun `a draft at the minimum is ready`() {
        for (size in SudokuDraft.SIZES) {
            assertEquals("size $size", FinishReadiness.Ready, SudokuCompleter.readiness(diagonal(size, SudokuCompleter.minimumGivens(size))))
        }
    }

    @Test
    fun `both Sudoku editors can finish a draft`() {
        for (definition in listOf(SudokuGames.MINI, SudokuGames.CLASSIC)) {
            val editor = SudokuEditor(definition)

            assertTrue("${definition.id}", EditorDraft.fresh(editor, definition.variant.id).canFinish)
        }
    }

    @Test
    fun `every seeded draft completes`() {
        assertEquals(emptyList<String>(), cases.filter { it.completion !is Completion.Done }.map { it.name })
    }

    @Test
    fun `a completion keeps every given`() {
        for ((name, draft, done) in completed()) {
            assertEquals(name, draft.givens, done.givens.mapIndexed { idx, digit -> if (draft.givens[idx] == 0) 0 else digit })
        }
    }

    @Test
    fun `a completion is unique and the hint ladder finishes it`() {
        for ((name, _, done) in completed()) {
            val verdict = analyze(done)
            assertTrue("$name: $verdict", verdict is Verdict.Unique && !verdict.needsGuessing)
        }
    }

    @Test
    fun `a completion of a draft at the minimum leaves most cells for the player`() {
        val atMinimum = completed().filter { (_, draft, _) -> draft.givens.count { it != 0 } == SudokuCompleter.minimumGivens(draft.size) }
        assertEquals(SudokuDraft.SIZES, atMinimum.map { (_, draft, _) -> draft.size }.toSet())
        for ((name, _, done) in atMinimum) {
            val givens = done.givens.count { it != 0 }
            assertTrue("$name: $givens givens", givens < done.givens.size / 2)
        }
    }

    @Test
    fun `the same seed completes to the same board`() {
        for (draft in atMinimum()) {
            val first = SudokuCompleter.complete(draft, Random(7), Budget.Unlimited)

            assertTrue("$first", first is Completion.Done)
            assertEquals(first, SudokuCompleter.complete(draft, Random(7), Budget.Unlimited))
        }
    }

    @Test
    fun `another seed completes to another board`() {
        for (draft in atMinimum()) {
            val first = SudokuCompleter.complete(draft, Random(7), Budget.Unlimited)
            val second = SudokuCompleter.complete(draft, Random(8), Budget.Unlimited)

            assertTrue("$first", first is Completion.Done)
            assertNotEquals(first, second)
        }
    }

    @Test
    fun `a board the hint ladder already finishes comes back as it is`() {
        for (puzzle in listOf(KnownSolutions.miniSample, KnownSolutions.classicSample)) {
            val finished = SudokuDraft(puzzle.geometry.size, puzzle.givens)

            assertEquals(Completion.Done(finished), SudokuCompleter.complete(finished, Random(1), Budget.Unlimited))
        }
    }

    @Test
    fun `a unique board that needs guessing gets givens until the hint ladder finishes it`() {
        for (givens in listOf(MINI_GUESSING, CLASSIC_GUESSING).map(KnownSolutions::grid)) {
            val guessing = SudokuDraft(SudokuDraft.SIZES.first { it * it == givens.size }, givens)
            assertTrue((analyze(guessing) as Verdict.Unique).needsGuessing)

            val done = (SudokuCompleter.complete(guessing, Random(1), Budget.Unlimited) as Completion.Done).draft

            val verdict = analyze(done)
            assertTrue("$verdict", verdict is Verdict.Unique && !verdict.needsGuessing)
            assertEquals(givens, done.givens.mapIndexed { idx, digit -> if (givens[idx] == 0) 0 else digit })
        }
    }

    @Test
    fun `givens that clash give up`() {
        val clashing = SudokuDraft(6, List(36) { if (it == 0 || it == 5) 3 else 0 })

        assertEquals(Completion.GaveUp, SudokuCompleter.complete(clashing, Random(1), Budget.Unlimited))
    }

    @Test
    fun `givens no grid agrees with are ready to finish and give up`() {
        for (size in SudokuDraft.SIZES) {
            assertEquals("size $size", FinishReadiness.Ready, SudokuCompleter.readiness(noGrid(size)))
            assertEquals("size $size", Completion.GaveUp, SudokuCompleter.complete(noGrid(size), Random(1), Budget.Unlimited))
        }
    }

    @Test
    fun `a run that cannot finish stops once the budget is spent`() {
        for (size in SudokuDraft.SIZES) {
            var polls = 0
            val budget = Budget { ++polls >= SPENT_AT }

            assertEquals("size $size", Completion.GaveUp, SudokuCompleter.complete(noGrid(size), Random(1), budget))
            assertTrue("size $size: $polls polls", polls in SPENT_AT..SPENT_AT + 1)
        }
    }

    @Test
    fun `a spent budget gives up on a draft that would complete`() {
        val case = cases.first()

        assertTrue("${case.completion}", case.completion is Completion.Done)
        assertEquals(Completion.GaveUp, SudokuCompleter.complete(case.draft, Random(case.seed), Budget { true }))
    }

    @Test
    fun `a search for a second solution that stalls costs a given, not the budget`() {
        // The givens Random(114) adds to this draft leave the solver a search of millions of
        // nodes for the next pair of solutions: found by the measurement, where it took 2 s.
        val draft = SudokuDraft(9, List(81) { if (it == 76) 9 else 0 })
        var polls = 0

        val completion = SudokuCompleter.complete(draft, Random(114), Budget { ++polls > STALL_POLLS })

        assertTrue("$completion after $polls polls", completion is Completion.Done)
        val verdict = analyze((completion as Completion.Done).draft)
        assertTrue("$verdict", verdict is Verdict.Unique && !verdict.needsGuessing)
    }

    @Test
    fun `the budget stops the hint ladder between its placements`() {
        // Three open cells sharing no row, column or box, each forced: the fill and the count of
        // solutions each poll at 3 cells and a leaf, so poll 9 is the ladder's first.
        val solution = KnownSolutions.classicSample.solution
        val finished = SudokuDraft(9, solution.mapIndexed { idx, digit -> if (idx in setOf(0, 40, 80)) 0 else digit })
        var polls = 0
        val spentInTheLadder = Budget { ++polls >= 9 }

        assertEquals(Completion.GaveUp, SudokuCompleter.complete(finished, Random(1), spentInTheLadder))
        assertEquals(Completion.Done(finished), SudokuCompleter.complete(finished, Random(1), Budget.Unlimited))
    }

    private fun completed(): List<Triple<String, SudokuDraft, SudokuDraft>> {
        val done = cases.mapNotNull { case -> (case.completion as? Completion.Done)?.let { Triple(case.name, case.draft, it.draft) } }
        assertTrue("no seeded draft completed", done.isNotEmpty())
        return done
    }

    private fun atMinimum(): List<SudokuDraft> =
        SudokuDraft.SIZES.map { size -> cases.first { it.draft.size == size }.draft }

    private fun analyze(draft: SudokuDraft): Verdict = SudokuDraftAnalyzer.analyze(draft, geometry(draft.size), Budget.Unlimited)

    private class Case(val name: String, val seed: Int, val draft: SudokuDraft, val completion: Completion<SudokuDraft>)

    private companion object {
        /** Fewer search nodes than the fill needs to see that [noGrid] has no grid, at either size. */
        const val SPENT_AT = 20

        /** Polls a whole completion stays well under once a stalled search is cut short. */
        const val STALL_POLLS = 1_000_000

        /** SudokuGraderTest's boards with one solution that the hint ladder cannot finish. */
        const val MINI_GUESSING = "000405001003000040030001020000600050"
        const val CLASSIC_GUESSING = "103002080400801902000007000000600379000200000000003004540190000006380000900000005"

        /** Published full grids: two per size. */
        val GRIDS = listOf(
            KnownSolutions.MINI_SAMPLE_SOLUTION,
            KnownSolutions.PEANUT_450_SOLUTION,
            KnownSolutions.CLASSIC_SAMPLE_SOLUTION,
            "713842956846597132592136784279485361368271495451963278985714623624359817137628549",
        ).map(KnownSolutions::grid)

        /**
         * Each grid cut three ways, the first of each size at the minimum: down to the minimum
         * of givens, to a quarter of the cells and to half of them. Every cut has a solution,
         * and all but the lightest pin more of the board than the completer would add itself.
         */
        val cases: List<Case> by lazy {
            GRIDS.sortedBy { it.size }.flatMapIndexed { index, grid ->
                val size = SudokuDraft.SIZES.first { it * it == grid.size }
                listOf(SudokuCompleter.minimumGivens(size), grid.size / 4, grid.size / 2).map { kept ->
                    val seed = index * 100 + kept
                    val draft = SudokuTestDrafts.cut(geometry(size), grid, kept, Random(seed))
                    Case("size $size grid $index kept $kept", seed, draft, SudokuCompleter.complete(draft, Random(seed), Budget.Unlimited))
                }
            }
        }

        fun geometry(size: Int): SudokuGeometry = requireNotNull(SudokuVariants.bySize(size)).geometry

        /** [count] givens down the diagonal, each digit its row number: no two clash. */
        fun diagonal(size: Int, count: Int): SudokuDraft =
            SudokuDraft(size, List(size * size) { if (it / size == it % size && it / size < count) it / size + 1 else 0 })

        /**
         * No clash, yet no grid: 1 sits in the row or column of every open cell of the top-left
         * box and a 5 holds the last one, so the box has no cell for its 1. Each of the box's
         * open cells still has candidates, so only a search finds this out.
         */
        fun noGrid(size: Int): SudokuDraft {
            val box = geometry(size).boxCells().first()
            val boxRows = box.map { it / size }.distinct()
            val boxCols = box.map { it % size }.distinct()
            val givens = MutableList(size * size) { 0 }
            // A 1 in each of the box's rows but the last, right of the box, one box apart.
            boxRows.dropLast(1).forEachIndexed { step, row -> givens[row * size + boxCols.size * (step + 1)] = 1 }
            // A 1 in each of the box's columns but the last, below the box, one box apart.
            boxCols.dropLast(1).forEachIndexed { step, col -> givens[boxRows.size * (step + 1) * size + col] = 1 }
            givens[boxRows.last() * size + boxCols.last()] = 5
            return SudokuDraft(size, givens)
        }
    }
}
