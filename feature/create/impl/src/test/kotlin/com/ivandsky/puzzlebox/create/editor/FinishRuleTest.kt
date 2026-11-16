package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.game.editor.FinishReadiness
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * E1's enabling rule: Finish is off until the completer says Ready and the live verdict has settled
 * on something other than None (Checking and None keep it off, as Publish is); a repeat chain, a board
 * that is a completion the author has not edited, keeps it on.
 */
class FinishRuleTest {

    private val solution = Solution(listOf(1))
    private val ready = FinishReadiness.Ready
    private val needsMore = FinishReadiness.NeedsMore(IncompleteReason("fake.blank", 1))
    private val blocked = FinishReadiness.Blocked(setOf(0, 1))

    @Test
    fun `it is disabled for a game without a completer`() {
        assertFalse(finishEnabled(readiness = null, verdict = Verdict.Unique(solution), repeating = false))
    }

    @Test
    fun `it is disabled while the completer needs more pieces`() {
        assertFalse(finishEnabled(needsMore, Verdict.Incomplete(IncompleteReason("fake.blank", 1)), repeating = false))
        assertFalse(finishEnabled(needsMore, Verdict.Unique(solution), repeating = false))
    }

    @Test
    fun `it is disabled while the placed pieces contradict each other`() {
        assertFalse(finishEnabled(blocked, Verdict.None(setOf(0, 1)), repeating = false))
        assertFalse(finishEnabled(blocked, Verdict.Incomplete(IncompleteReason("fake.blank", 1)), repeating = false))
    }

    @Test
    fun `it is disabled while the verdict is Checking`() {
        assertFalse(finishEnabled(ready, Verdict.Checking, repeating = false))
    }

    @Test
    fun `it is disabled when the verdict is None`() {
        assertFalse(finishEnabled(ready, Verdict.None(setOf(0, 1)), repeating = false))
    }

    @Test
    fun `it is enabled once Ready and any other verdict has settled`() {
        assertTrue(finishEnabled(ready, Verdict.Incomplete(IncompleteReason("fake.blank", 1)), repeating = false))
        assertTrue(finishEnabled(ready, Verdict.Unique(solution), repeating = false))
        assertTrue(finishEnabled(ready, Verdict.Multiple(solution, Solution(listOf(2)), setOf(0)), repeating = false))
        assertTrue(finishEnabled(ready, Verdict.Undetermined, repeating = false))
    }

    @Test
    fun `an active repeat chain keeps it enabled through the recheck of the completion`() {
        assertTrue(finishEnabled(ready, Verdict.Checking, repeating = true))
        assertTrue(finishEnabled(null, Verdict.Checking, repeating = true))
    }
}
