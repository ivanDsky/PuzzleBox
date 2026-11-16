package com.ivandsky.puzzlebox.game.editor

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Finish to a target through the editor seam (90), the way a game tests its own completer without
 * the Studio: finish with a target, then read the level back with scored grading.
 */
class TargetCompletionTest {

    private val random = Random(1)

    private fun EditorDraft<List<Int>>.scoredLevel(): Difficulty.Level? =
        (analyze(Budget.Unlimited, GradingMode.Scored) as Verdict.Unique).difficulty?.level

    @Test
    fun `finish hands the target to the completer, which returns a board at that level`() {
        val pieces = FakeScoredGrader().draft(2, 0, 0)

        val levels = Difficulty.Level.entries.map { target -> pieces.finish(random, Budget.Unlimited, target = target)!!.scoredLevel() }

        assertEquals(Difficulty.Level.entries, levels)
    }

    @Test
    fun `a target out of reach still finishes, at the closest level`() {
        val pieces = FakeScoredGrader(tries = 3).draft(4, 0, 0)

        val finished = pieces.finish(random, Budget.Unlimited, target = Difficulty.Level.Expert)!!

        assertEquals("every board three tries reach is Medium, so the first one is kept", listOf(4, 1, 1), finished.state.draft)
        assertEquals(Difficulty.Level.Medium, finished.scoredLevel())
    }

    @Test
    fun `no target is today's behaviour, the first board found`() {
        val finished = FakeScoredGrader().draft(4, 0, 0).finish(random, Budget.Unlimited)!!

        assertEquals(listOf(4, 1, 1), finished.state.draft)
    }

    @Test
    fun `a spent budget with no board found gives up`() {
        assertEquals(null, FakeScoredGrader().draft(4, 0, 0).finish(random, Budget { true }, target = Difficulty.Level.Hard))
    }
}
