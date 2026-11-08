package com.ivandsky.puzzlebox.games.strands.editor

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.SharedOpen
import com.ivandsky.puzzlebox.game.editor.SharedOpenResult
import com.ivandsky.puzzlebox.game.editor.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Crafted share payloads a friend must never hang on: word lengths that cannot tile the board
 * pass decode and skip the tiling search, and the uncoverable walk that follows used to spin
 * with no budget. The walk now gives up as the budget spends, the analyzer answers Undetermined,
 * and the friend's open fails its check.
 */
class StrandsCraftedSharePayloadTest {

    /** 46 A's around a B (cell 0) and a C (cell 47) with one 42-letter word: lengths 42 ≠ 48. */
    private val crafted = StrandsDraft(
        letters = "B" + "A".repeat(46) + "C",
        clue = "crafted",
        paths = listOf(StrandsDraftPath(emptyList(), "A".repeat(40) + "BC")),
        spangram = 0,
    )

    /** A budget that counts its polls and runs out after [maxPolls] probes; no clock involved. */
    private class CountingBudget(private val maxPolls: Int) : Budget {
        var polls = 0
            private set

        override fun exhausted(): Boolean {
            polls++
            return polls > maxPolls
        }
    }

    private fun bytes(): ByteArray = StrandsShareCodec.encodeShare(crafted)

    @Test(timeout = 30_000)
    fun `a crafted payload the codec accepts analyses to Undetermined once the budget spends`() {
        val draft = (StrandsShareCodec.decodeShare(bytes()) as DecodeResult.Ok).value
        val budget = CountingBudget(maxPolls = 50_000)

        assertEquals(Verdict.Undetermined, StrandsDraftAnalyzer.analyze(draft, budget))
        assertTrue("the walk must consult the budget: ${budget.polls} polls", budget.polls > 0)
    }

    @Test(timeout = 30_000)
    fun `opening a crafted payload fails the friend's check within the budget`() {
        val budget = CountingBudget(maxPolls = 50_000)

        val result = SharedOpen.verify(StrandsEditor, StrandsEditor.shareFormatVersion, bytes(), budget)

        assertEquals(SharedOpenResult.FailedCheck, result)
    }

    @Test(timeout = 30_000)
    fun `an already-spent budget answers Undetermined without walking`() {
        assertEquals(Verdict.Undetermined, StrandsDraftAnalyzer.analyze(crafted, CountingBudget(maxPolls = 0)))
    }
}
