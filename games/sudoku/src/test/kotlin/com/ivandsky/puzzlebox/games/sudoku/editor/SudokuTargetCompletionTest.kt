package com.ivandsky.puzzlebox.games.sudoku.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Completion
import com.ivandsky.puzzlebox.game.editor.Difficulty.Level
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.GradingMode
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.sudoku.KnownSolutions
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuVariants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Finish for me aimed at a level on Sudoku (92). Seeded, with budgets that count polls, so every run is the same. */
class SudokuTargetCompletionTest {

    /** Catches a Finish that ignores the level asked for, or reaches it too seldom to be worth a button. */
    @Test
    fun `Finish gives a board of the level asked for, at every level and both sizes`() {
        for (size in SudokuDraft.SIZES) for (target in Level.entries) {
            val levels = (0 until SEEDS).map { seed -> verdict(finish(oneGiven(size), seed, target)).difficulty?.level }

            assertTrue("size $size $target: $levels", levels.count { it == target } >= SEEDS * 8 / 10)
        }
    }

    /** Catches a tuned board the Studio would refuse to publish, or one that lost a digit the author placed. */
    @Test
    fun `a board aimed at a level keeps every given and is one the Studio calls unique with nothing to guess`() {
        for (size in SudokuDraft.SIZES) for (target in Level.entries) for (seed in 0 until 3) {
            val geometry = requireNotNull(SudokuVariants.bySize(size)).geometry
            val draft = SudokuTestDrafts.cut(geometry, solution(size), kept = size, Random(seed))

            val done = finish(draft, seed, target)

            assertEquals("size $size $target", draft.givens, done.givens.mapIndexed { idx, digit -> if (draft.givens[idx] == 0) 0 else digit })
            assertTrue("size $size $target", !verdict(done).needsGuessing)
        }
    }

    /** Catches a Finish that gives up, or drops author givens, when the level cannot be had: the board is the closest one. */
    @Test
    fun `a level out of reach still gives a board, the closest there is`() {
        for (puzzle in listOf(KnownSolutions.miniSample, KnownSolutions.classicSample)) {
            val finished = SudokuDraft(puzzle.geometry.size, puzzle.givens)
            assertEquals(Level.Easy, verdict(finished).difficulty?.level)

            assertEquals(finished, finish(finished, seed = 1, Level.Expert))
        }
    }

    /** Catches a Finish that only ever takes givens away: an Easy board from a draft that is already a harder one. */
    @Test
    fun `a board harder than asked for gets givens until it is at the level`() {
        val hard = SudokuDraft(6, KnownSolutions.grid(SPARSEST_DAILY))
        assertEquals(Level.Expert, verdict(hard).difficulty?.level)

        for (target in listOf(Level.Easy, Level.Medium, Level.Hard)) {
            val done = finish(hard, seed = 3, target)

            assertEquals("$target", target, verdict(done).difficulty?.level)
            assertEquals("$target", hard.givens, done.givens.mapIndexed { idx, digit -> if (hard.givens[idx] == 0) 0 else digit })
        }
    }

    /** Catches a search that runs on past its budget, and one that throws away the board it had when time ran out. */
    @Test
    fun `a budget spent mid-search gives the closest board found, and a budget spent at once gives up`() {
        var polls = 0
        val short = Budget { ++polls > FIRST_BOARD_POLLS }

        val completion = SudokuCompleter.complete(oneGiven(9), Random(7), short, Level.Expert)

        assertTrue("$completion", completion is Completion.Done)
        assertTrue("$polls polls", polls in FIRST_BOARD_POLLS..FIRST_BOARD_POLLS + 4)
        assertEquals(Completion.GaveUp, SudokuCompleter.complete(oneGiven(9), Random(7), Budget { true }, Level.Expert))
    }

    /** Catches a search that reads a clock or a shared random: a seed must give one board. */
    @Test
    fun `the same seed and level give the same board`() {
        for (size in SudokuDraft.SIZES) {
            assertEquals("size $size", finish(oneGiven(size), seed = 5, Level.Hard), finish(oneGiven(size), seed = 5, Level.Hard))
        }
    }

    /** Pins the flag-off path: with no target the completer is the one from before levels, digit for digit. */
    @Test
    fun `with no target a seed still completes to the board it did before levels`() {
        val boards = SudokuDraft.SIZES.map { size -> print((SudokuCompleter.complete(oneGiven(size), Random(7), Budget.Unlimited) as Completion.Done).draft) }

        assertEquals(
            listOf(
                "13.4...64..1.51..4...5.3.43.6..1....",
                "19364.7.....3.8.9.5...7.....1.5.43..4.9.3....8..7...5..2.1..47.671......3.5.6..21",
            ),
            boards,
        )
    }

    /** One press of Finish on [draft] for [target], under a budget of [POLLS] polls. */
    private fun finish(draft: SudokuDraft, seed: Int, target: Level): SudokuDraft {
        var polls = 0
        val completion = SudokuCompleter.complete(draft, Random(seed), Budget { ++polls > POLLS }, target)
        return (completion as? Completion.Done)?.draft ?: error("size ${draft.size} $target seed $seed: $completion after $polls polls")
    }

    /** What the Studio's scored check says of [draft]; it fails unless the board is unique. */
    private fun verdict(draft: SudokuDraft): Verdict.Unique {
        val definition = if (draft.size == 6) SudokuGames.MINI else SudokuGames.CLASSIC
        return EditorDraft(SudokuEditor(definition), DraftState(draft)).analyze(Budget.Unlimited, GradingMode.Scored) as Verdict.Unique
    }

    private fun solution(size: Int): List<Int> = (if (size == 6) KnownSolutions.miniSample else KnownSolutions.classicSample).solution

    private fun print(draft: SudokuDraft): String = draft.givens.joinToString("") { if (it == 0) "." else it.toString() }

    private companion object {
        const val SEEDS = 10

        /** A tenth of what the Studio's 2 s buys on the Mac JVM (2 000 polls a millisecond), so a pass here leaves a phone room. */
        const val POLLS = 400_000

        /** Seed 7's first 9×9 board takes 3 298 polls and its Expert board 13 779: this ends between the two. */
        const val FIRST_BOARD_POLLS = 6_000

        /** LinkedIn Mini Sudoku #111, nine givens: Expert by its score. */
        const val SPARSEST_DAILY = "100000040000200401000020005000060300"

        /** A draft at the minimum Finish asks for: a 1 in the top-left cell. */
        fun oneGiven(size: Int): SudokuDraft = SudokuDraft(size, List(size * size) { if (it == 0) 1 else 0 })
    }
}
