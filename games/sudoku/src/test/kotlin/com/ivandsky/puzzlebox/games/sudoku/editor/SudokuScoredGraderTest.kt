package com.ivandsky.puzzlebox.games.sudoku.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.GradingMode
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.sudoku.KnownSolutions
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuVariants
import com.ivandsky.puzzlebox.games.sudoku.engine.solve.SudokuSolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sudoku's scored difficulty (92): the score of the whole solve, read through the grader the
 * Studio calls. Boards are literal, so every run is the same.
 */
class SudokuScoredGraderTest {

    /** Catches a score that does not grow with the work left: a fuller board must come out easier. */
    @Test
    fun `a board with givens taken away scores higher, at both sizes`() {
        for ((fuller, sparser) in listOf(MINI_FULLER to KnownSolutions.MINI_SAMPLE_GIVENS, CLASSIC_FULLER to KnownSolutions.CLASSIC_SAMPLE_GIVENS)) {
            assertTrue("$sparser: ${score(sparser)} over ${score(fuller)}", score(sparser) > score(fuller))
        }
    }

    /** Catches a score that forgets the technique: each twin is as hard to search, and only the first is ever stuck. */
    @Test
    fun `a board that needs a harder technique scores higher than its twin that singles finish`() {
        for ((rung, needs, twin) in TECHNIQUE_TWINS) {
            assertEquals(needs, rung, grade(needs)?.hardestRung)
            assertEquals(twin, SudokuRung.SINGLES, grade(twin)?.hardestRung)
            assertEquals(twin, needs.count { it != '0' }, twin.count { it != '0' })
            assertTrue("$needs: spotting ${tally(needs).spotting} under ${tally(twin).spotting}", tally(needs).spotting <= tally(twin).spotting)

            assertTrue("$needs: ${score(needs)} over ${score(twin)}", score(needs) > score(twin))
        }
    }

    /** Catches a score that only counts steps: the same steps cost more when few cells can be placed at a time. */
    @Test
    fun `of two boards with the same steps the one whose steps are harder to find scores higher`() {
        val steps = { givens: String -> tally(givens).let { it.score - it.spotting } }
        assertEquals(steps(EASY_TO_SPOT), steps(HARD_TO_SPOT))

        assertTrue("${score(HARD_TO_SPOT)} over ${score(EASY_TO_SPOT)}", score(HARD_TO_SPOT) >= score(EASY_TO_SPOT) + 40)
    }

    /** Catches a weight out of order: a step from further up the ladder must never weigh less. */
    @Test
    fun `a step weighs more the harder it is`() {
        val weights = SudokuSolveStep.entries.map { it.weight }

        assertEquals(weights.sorted().distinct(), weights)
        assertEquals(SudokuSolveStep.entries.map { it.rung }.sorted(), SudokuSolveStep.entries.map { it.rung })
    }

    /** Catches bands that leave the ladder's label as it was: every one of these is Easy by its rung. */
    @Test
    fun `a sparse daily the ladder calls Easy is not Easy by its score, and a typical daily still is`() {
        for (daily in listOf(TYPICAL_DAILY, SPARSE_DAILY, SPARSEST_DAILY)) assertEquals(daily, SudokuRung.SINGLES, grade(daily)?.hardestRung)

        assertEquals(Difficulty.Level.Easy, grade(TYPICAL_DAILY)?.level)
        assertEquals(Difficulty.Level.Medium, grade(SPARSE_DAILY)?.level)
        assertEquals(Difficulty.Level.Expert, grade(SPARSEST_DAILY)?.level)
    }

    /** Catches a label whose rung is not the solve's: the Studio shows it beside the level. */
    @Test
    fun `the label carries the hardest rung the solve needs`() {
        assertEquals(listOf(SudokuRung.SINGLES, SudokuRung.INTERSECTIONS, SudokuRung.PAIRS), MINI_LADDER.map { grade(it)?.hardestRung })
        assertEquals(SudokuRung.entries, CLASSIC_LADDER.map { grade(it)?.hardestRung })
    }

    /** Catches a label for a board that needs a guess: the walk stops with cells open. */
    @Test
    fun `a board the walk cannot finish gets no label`() {
        assertNull(grade(NEEDS_A_GUESS))
    }

    /** Catches a grader that ignores its budget and holds the Studio's check up. */
    @Test
    fun `a spent budget gives no label`() {
        assertNull(grade(KnownSolutions.CLASSIC_SAMPLE_GIVENS, Budget { true }))
    }

    /** Catches an editor that does not hand its scored grader to the Studio, or hands it to the legacy check. */
    @Test
    fun `the Studio's scored check labels a board by its score and the legacy check by its rung`() {
        for (definition in listOf(SudokuGames.MINI, SudokuGames.CLASSIC)) {
            val givens = if (definition == SudokuGames.MINI) MINI_LADDER[1] else CLASSIC_LADDER[1]
            val draft = EditorDraft(SudokuEditor(definition), DraftState(draftOf(givens)))

            val scored = (draft.analyze(Budget.Unlimited, GradingMode.Scored) as Verdict.Unique).difficulty
            val legacy = (draft.analyze(Budget.Unlimited) as Verdict.Unique).difficulty

            assertEquals("${definition.id}", grade(givens), scored)
            assertEquals("${definition.id}", score(givens), scored?.score)
            assertEquals("${definition.id}", Difficulty(SudokuRung.INTERSECTIONS), legacy)
        }
    }

    private fun score(givens: String): Int = checkNotNull(grade(givens)?.score) { "no score for $givens" }

    private fun tally(givens: String): SudokuSolveTally {
        val draft = draftOf(givens)
        val geometry = requireNotNull(SudokuVariants.bySize(draft.size)).geometry
        return checkNotNull(SudokuScoredSolve.of(geometry, draft.givens.toIntArray(), Budget.Unlimited))
    }

    private fun grade(givens: String, budget: Budget = Budget.Unlimited): Difficulty? {
        val draft = draftOf(givens)
        val geometry = requireNotNull(SudokuVariants.bySize(draft.size)).geometry
        val solution = checkNotNull(SudokuSolver.solve(geometry, draft.givens).solution)
        return SudokuScoredGrader.grade(draft, Solution(solution), budget)
    }

    private fun draftOf(givens: String): SudokuDraft {
        val grid = KnownSolutions.grid(givens.replace('0', '.'))
        return SudokuDraft(SudokuDraft.SIZES.first { it * it == grid.size }, grid)
    }

    private companion object {
        /** [KnownSolutions.MINI_SAMPLE_GIVENS] with its first row filled in from the solution. */
        const val MINI_FULLER = "543162..643..2.5.3..1....3...4.65.2."

        /** [KnownSolutions.CLASSIC_SAMPLE_GIVENS] with its first row filled in from the solution. */
        const val CLASSIC_FULLER = "5346789126..195....98....6.8...6...34..8.3..17...2...6.6....28....419..5....8..79"

        /**
         * A 9×9 board that needs the rung, and its twin: one given moved, so both hold as many,
         * and singles finish the twin. The twin costs more to search, so only the technique
         * can put the first above it.
         */
        val TECHNIQUE_TWINS = listOf(
            Triple(
                SudokuRung.PAIRS,
                "000430090030001000140000200503004000000000580089200400200006150000000602001050000",
                "000430090030000000140000200503004000000000580089200400200096150000000602001050000",
            ),
            Triple(
                SudokuRung.TRIPLES,
                "000400008072090030300050002400000100090000500008060000000540000600030700280007006",
                "000400008072090030300050002400000109090000500008060000000540000600030700280000006",
            ),
        )

        /** Two cuts of one 6×6 grid to ten givens; singles finish both, in steps of the same weight. */
        const val HARD_TO_SPOT = "040002001000000020000053600100100030"
        const val EASY_TO_SPOT = "040002001000300020000050603100100000"

        /** LinkedIn Mini Sudoku #148, twelve givens: the median score of the 454 dailies. */
        const val TYPICAL_DAILY = "123000000000002460014500000000000235"

        /** LinkedIn Mini Sudoku #145, eight givens: one daily in ten scores this high. */
        const val SPARSE_DAILY = "100000000054000060040000230000000006"

        /** LinkedIn Mini Sudoku #111, nine givens: the highest score of the 454 dailies. */
        const val SPARSEST_DAILY = "100000040000200401000020005000060300"

        /** Generated board rnd:1: one solution, and the ladder cannot reach it. */
        const val NEEDS_A_GUESS = "000240041060300000050000600500000036"

        /** SudokuGraderTest's 6×6 boards: singles finish the first, then intersections, then pairs. */
        val MINI_LADDER = listOf(
            KnownSolutions.MINI_SAMPLE_GIVENS,
            "000500000042000204005003506000030000",
            "000250100040010024000000040000306010",
        )

        /** SudokuGraderTest's 9×9 boards: singles, intersections, pairs, triples. */
        val CLASSIC_LADDER = listOf(
            KnownSolutions.CLASSIC_SAMPLE_GIVENS,
            "200100309080032000400009200600010090000007000007390000304200005060500102000060403",
            "000005700000100030005006000027981000060020001900000520701200000000007040032058000",
            "700042006840000000000006080209000000060001000401963200005700020604300000000628500",
        )
    }
}
