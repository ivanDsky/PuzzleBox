package com.ivandsky.puzzlebox.games.strands.engine.tiling

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.games.strands.StrandsFixtures
import com.ivandsky.puzzlebox.games.strands.StrandsPuzzle
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrandsTilingSearchTest {

    @Test
    fun `a shipped daily has exactly one tiling, its own paths`() {
        val daily = StrandsFixtures.puzzle(LocalDate.of(2026, 11, 6))
        val words = daily.allWords().map { it.text }

        val outcome = StrandsTilingSearch.bounded(daily.rows, daily.cols, daily.letters, words, first = null, limit = 2, Budget.Unlimited)

        assertEquals(StrandsTilingOutcome.Done(listOf(daily.shippedTiling())), outcome)
    }

    @Test
    fun `words that swap their S halves tile the board two ways`() {
        val outcome = swapSearch(first = null, limit = 2)

        assertEquals(setOf(SWAP_A, SWAP_B), (outcome as StrandsTilingOutcome.Done).tilings.toSet())
    }

    @Test
    fun `the search stops at the limit`() {
        assertEquals(1, (swapSearch(first = null, limit = 1) as StrandsTilingOutcome.Done).tilings.size)
    }

    @Test
    fun `the author tiling is witness A whichever it is`() {
        assertEquals(listOf(SWAP_A, SWAP_B), (swapSearch(first = SWAP_A, limit = 2) as StrandsTilingOutcome.Done).tilings)
        assertEquals(listOf(SWAP_B, SWAP_A), (swapSearch(first = SWAP_B, limit = 2) as StrandsTilingOutcome.Done).tilings)
    }

    @Test
    fun `a letter no word uses is uncoverable`() {
        // The swap board with a Z column appended: A T T E Z / R S S M Z.
        assertEquals(setOf(4, 9), StrandsTilingSearch.uncoverable(2, 5, "ATTEZRSSMZ", SWAP_WORDS))
    }

    @Test
    fun `a word whose letters are missing leaves its cells uncoverable and no tiling`() {
        // M → Z: STEM fits nowhere, and only STEM reaches the T and E on the right.
        val letters = SWAP_LETTERS.replaceRange(7, 8, "Z")

        assertEquals(setOf(2, 3, 7), StrandsTilingSearch.uncoverable(2, 4, letters, SWAP_WORDS))
        assertEquals(StrandsTilingOutcome.Done(emptyList()), StrandsTilingSearch.bounded(2, 4, letters, SWAP_WORDS, null, 2, Budget.Unlimited))
    }

    @Test
    fun `a tileable board has no uncoverable cell`() {
        assertEquals(emptySet<Int>(), StrandsTilingSearch.uncoverable(2, 4, SWAP_LETTERS, SWAP_WORDS))
    }

    @Test
    fun `a spent budget is exhausted`() {
        assertEquals(StrandsTilingOutcome.Exhausted, StrandsTilingSearch.bounded(2, 4, SWAP_LETTERS, SWAP_WORDS, null, 2) { true })
    }

    @Test
    fun `the repeated-letter worst case returns within the bound`() {
        val started = System.nanoTime()

        val outcome = StrandsTilingSearch.bounded(
            8, 6, WORST_LETTERS, WORST_WORDS, first = null, limit = 2, Budget.within(WORST_BUDGET_MS, System::nanoTime),
        )

        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertTrue("took $elapsedMs ms", elapsedMs < BOUND_MS)
        assertEquals(StrandsTilingOutcome.Exhausted, outcome)
    }

    private fun swapSearch(first: List<Int>?, limit: Int): StrandsTilingOutcome =
        StrandsTilingSearch.bounded(2, 4, SWAP_LETTERS, SWAP_WORDS, first, limit, Budget.Unlimited)

    private fun StrandsPuzzle.allWords() = themeWords + spangram

    private fun StrandsPuzzle.shippedTiling(): List<Int> {
        val tiling = MutableList(rows * cols) { -1 }
        allWords().forEachIndexed { index, word -> word.cells.forEach { tiling[it.row * cols + it.col] = index } }
        return tiling
    }

    private companion object {
        // A T T E    STAR and STEM each reach either S: both S cells touch both T cells.
        // R S S M
        const val SWAP_LETTERS = "ATTERSSM"
        val SWAP_WORDS = listOf("STAR", "STEM")
        val SWAP_A = listOf(0, 0, 1, 1, 0, 0, 1, 1)
        val SWAP_B = listOf(0, 0, 1, 1, 0, 1, 0, 1)

        // Eight six-letter words over two letters, one flipped off the board's row snake: thousands of
        // placements per word and no quick tiling, so the search runs until the budget stops it.
        const val BOUND_MS = 1_500L
        const val WORST_BUDGET_MS = 400L
        const val WORST_LETTERS = "NANAANAANNNAAANAANNNNANANANANAAAANNAANANAANAANNN"
        val WORST_WORDS = listOf("NANAAN", "ANNNAA", "AANAAN", "ANANNN", "NAAANA", "ANNAAA", "ANANAA", "NNNAAN")
    }
}
