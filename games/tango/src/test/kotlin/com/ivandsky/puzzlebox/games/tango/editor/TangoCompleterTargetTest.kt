package com.ivandsky.puzzlebox.games.tango.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Completion
import com.ivandsky.puzzlebox.game.editor.Difficulty.Level
import com.ivandsky.puzzlebox.game.editor.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Finish for me aimed at a level (93): [TangoCompleter.complete] with a target. The levels are the
 * ones [TangoScoredGrader] gives, so a hit is a board the Studio labels with the level asked for.
 */
class TangoCompleterTargetTest {

    @Test
    fun `no target finishes the board the plain Finish always gave`() {
        val draft = draft(6, sample = 3, givens = 3, markers = 2)

        val board = done(TangoCompleter.complete(draft, Random(7), Budget.Unlimited, target = null))

        assertEquals(PLAIN_FINISH, TangoNotation.print(board))
    }

    @Test
    fun `every level is hit on at least 8 of 10 drafts at 6x6 and 8x8`() {
        for (n in listOf(6, 8)) {
            for (target in Level.entries) {
                val levels = List(10) { sample -> levelOf(finish(draft(n, sample, givens = 2, markers = sample % 3), sample, target)) }

                assertTrue("n$n $target: got $levels", levels.count { it == target } >= 8)
            }
        }
    }

    @Test
    fun `a finished board keeps the author's pieces, has one answer and needs no guessing`() {
        for (n in TangoDraft.SIZES) {
            for (target in Level.entries) {
                val draft = draft(n, sample = target.ordinal, givens = n / 2, markers = n / 2)

                val board = finish(draft, seed = n, target)

                assertEquals("n$n $target", draft.markers, board.markers)
                assertEquals("n$n $target", draft.givens, board.givens.filterKeys(draft.givens::containsKey))
                val verdict = TangoDraftAnalyzer.analyze(board, Budget.Unlimited)
                assertTrue("n$n $target: $verdict", verdict is Verdict.Unique && !verdict.needsGuessing)
            }
        }
    }

    @Test
    fun `a level the draft cannot reach gives the closest board, not a give-up`() {
        val markedThrough = draft(6, sample = 1, givens = 1, markers = TangoDraftCuts.edgeCount(6))

        val board = finish(markedThrough, seed = 1, Level.Expert)

        assertEquals(Level.Easy, levelOf(board))
        assertEquals(markedThrough, board)
    }

    @Test
    fun `a 4x4 board asked for Expert comes back as hard as it gets`() {
        val levels = List(10) { sample -> levelOf(finish(draft(4, sample, givens = 1, markers = 0), sample, Level.Expert)) }

        assertTrue("got $levels", levels.all { it != null && it >= Level.Medium })
    }

    @Test
    fun `the same seed and target finish to the same board`() {
        val draft = draft(6, sample = 5, givens = 2, markers = 2)

        assertEquals(finish(draft, seed = 4, Level.Hard), finish(draft, seed = 4, Level.Hard))
    }

    @Test
    fun `a budget spent before any board is found gives up`() {
        val draft = draft(6, sample = 2, givens = 2, markers = 0)

        assertEquals(Completion.GaveUp, TangoCompleter.complete(draft, Random(2), Budget { true }, Level.Hard))
    }

    @Test
    fun `a budget spent mid-search returns the best board found so far`() {
        val draft = draft(6, sample = 2, givens = 2, markers = 0)
        var total = 0
        TangoCompleter.complete(draft, Random(2), Budget { total++; false }, Level.Expert)

        var found = false
        for (spentAt in 1..total step (total / 200).coerceAtLeast(1)) {
            var polls = 0
            val completion = TangoCompleter.complete(draft, Random(2), Budget { ++polls >= spentAt }, Level.Expert)
            if (completion is Completion.Done) {
                found = true
                val verdict = TangoDraftAnalyzer.analyze(completion.draft, Budget.Unlimited)
                assertTrue("spent at poll $spentAt: $verdict", verdict is Verdict.Unique && !verdict.needsGuessing)
            } else {
                assertTrue("gave up at poll $spentAt after a board was found at an earlier one", !found)
            }
        }
        assertTrue("no cut-off of $total polls returned a board", found)
    }

    @Test
    fun `the editor's Finish aims for the target`() {
        val draft = draft(6, sample = 0, givens = 2, markers = 0)

        val board = done(TangoEditor.complete(draft, Random(0), Budget.Unlimited, Level.Easy))

        assertEquals(Level.Easy, levelOf(board))
    }

    private fun finish(draft: TangoDraft, seed: Int, target: Level): TangoDraft =
        done(TangoCompleter.complete(draft, Random(seed), Budget.Unlimited, target))

    private fun done(completion: Completion<TangoDraft>): TangoDraft = (completion as Completion.Done).draft

    private fun levelOf(board: TangoDraft): Level? {
        val verdict = TangoDraftAnalyzer.analyze(board, Budget.Unlimited) as Verdict.Unique
        return TangoScoredGrader.grade(board, verdict.solution, Budget.Unlimited)?.level
    }

    /** The [sample]-th draft of width [n]: [givens] cells and [markers] edges cut from a generated answer. */
    private fun draft(n: Int, sample: Int, givens: Int, markers: Int): TangoDraft =
        TangoDraftCuts.cut(n, TangoDraftCuts.answer(n, seed = n * 100L + sample), givens, markers, Random(n * 1000 + sample))

    private companion object {
        /** `TangoNotation` of what `complete` gave for the first test's draft and seed before 93. */
        val PLAIN_FINISH = """
            puzzlebox tango v1
            S..MM.
            .....M
            S..S.S
            .....M
            .M.S.M
            ...M..
            x 1,1 down
            x 5,4 right

        """.trimIndent()
    }
}
