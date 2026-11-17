package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.game.editor.FinishReadiness
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What Finish says when it did not do what was asked (90): a give-up names its cause (a board with
 * room left asks for more pieces; one that already has its answers asks for changes), and a board
 * finished at another level says which. The words are the product's, so they are pinned here.
 */
class FinishNoticeTest {

    private val solution = Solution(listOf(1))
    private val reason = IncompleteReason("fake.blank", 1)

    @Test
    fun `a give-up on a board that already has its answers asks for changes`() {
        assertEquals(FinishNotice.NoFix, gaveUpNotice(FinishReadiness.Ready, Verdict.Unique(solution)))
        assertEquals(FinishNotice.NoFix, gaveUpNotice(FinishReadiness.Ready, Verdict.Multiple(solution, Solution(listOf(2)), setOf(0))))
    }

    @Test
    fun `a give-up on a board with room left asks for more pieces`() {
        assertEquals(FinishNotice.NeedsMore, gaveUpNotice(FinishReadiness.Ready, Verdict.Incomplete(reason)))
        assertEquals(FinishNotice.NeedsMore, gaveUpNotice(FinishReadiness.Ready, Verdict.Undetermined))
    }

    @Test
    fun `a give-up on pieces the completer says are too few asks for more, whatever the verdict`() {
        assertEquals(FinishNotice.NeedsMore, gaveUpNotice(FinishReadiness.NeedsMore(reason), Verdict.Unique(solution)))
    }

    @Test
    fun `each notice says its own words`() {
        assertEquals("Not enough on the board to finish yet. Place a few more pieces and try again.", FinishNotice.NeedsMore.text)
        assertEquals("Couldn't fix this board in time. Change a few highlighted cells and try again.", FinishNotice.NoFix.text)
        assertEquals(
            "Finished at Medium \u2014 Hard wasn't found in time. Press Finish to try again.",
            FinishNotice.Missed(got = Difficulty.Level.Medium, wanted = Difficulty.Level.Hard).text,
        )
    }

    @Test
    fun `a finish that changed painted cells says how many and that they are highlighted`() {
        assertEquals("Finished. 2 painted cells changed color; they're highlighted.", FinishNotice.Recoloured(2).text)
        assertEquals("Finished. 1 painted cell changed color; it's highlighted.", FinishNotice.Recoloured(1).text)
    }
}
