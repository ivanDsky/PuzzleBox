package com.ivandsky.puzzlebox.games.strands.engine.tiling

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraftAnalyzer
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraftPath
import com.ivandsky.puzzlebox.games.strands.editor.StrandsShareCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A crafted share code can make every placement of every word enormous: on a board of one repeated
 * letter an 8-letter word has 641,217 placements and a 12-letter one millions. The search must give
 * up on a count, not on the clock, before it builds them. The tests count budget polls (one per 1024
 * walk steps), so the bound is steps, never wall-clock time.
 */
class StrandsTilingBoundTest {

    /** Counts its polls and runs out after [maxPolls], so a search with no bound still ends. */
    private class CountingBudget(private val maxPolls: Int) : Budget {
        var polls = 0
            private set

        override fun exhausted(): Boolean {
            polls++
            return polls > maxPolls
        }
    }

    private fun search(words: List<String>, budget: Budget): StrandsTilingOutcome =
        StrandsTilingSearch.bounded(ROWS, COLS, ALL_A, words, first = null, limit = 2, budget)

    /** The draft a friend decodes from the largest share the codec takes: 48 letters, every word an A-run. */
    private fun shared(words: List<String>): StrandsDraft {
        val crafted = StrandsDraft(ALL_A, "crafted", words.map { StrandsDraftPath(emptyList(), it) }, spangram = 0)
        return (StrandsShareCodec.decodeShare(StrandsShareCodec.encodeShare(crafted)) as DecodeResult.Ok).value
    }

    @Test(timeout = 60_000)
    fun `a word with too many placements is exhausted after a bounded walk`() {
        val budget = CountingBudget(maxPolls = RUNAWAY_POLLS)

        val outcome = search(List(4) { "A".repeat(12) }, budget)

        assertEquals(StrandsTilingOutcome.Exhausted, outcome)
        assertTrue("walked past the placement cap: ${budget.polls} polls", budget.polls <= BOUNDED_POLLS)
    }

    @Test(timeout = 60_000)
    fun `many words each under the per-word cap are exhausted by the total cap`() {
        val words = List(8) { "A".repeat(5) } + List(2) { "A".repeat(4) }
        val budget = CountingBudget(maxPolls = RUNAWAY_POLLS)

        val outcome = search(words, budget)

        assertEquals(StrandsTilingOutcome.Exhausted, outcome)
        assertTrue("built every placement: ${budget.polls} polls", budget.polls <= BOUNDED_POLLS)
    }

    @Test(timeout = 60_000)
    fun `a crafted share the codec accepts analyses to Undetermined within the bound`() {
        val budget = CountingBudget(maxPolls = RUNAWAY_POLLS)

        val verdict = StrandsDraftAnalyzer.analyze(shared(List(4) { "A".repeat(12) }), budget)

        assertEquals(Verdict.Undetermined, verdict)
        assertTrue("walked past the placement cap: ${budget.polls} polls", budget.polls <= BOUNDED_POLLS)
    }

    private companion object {
        const val ROWS = 8
        const val COLS = 6
        val ALL_A = "A".repeat(ROWS * COLS)

        // A capped walk is about 64k steps, so about 70 polls; an uncapped one runs on to the budget.
        const val BOUNDED_POLLS = 400
        const val RUNAWAY_POLLS = 3_000
    }
}
