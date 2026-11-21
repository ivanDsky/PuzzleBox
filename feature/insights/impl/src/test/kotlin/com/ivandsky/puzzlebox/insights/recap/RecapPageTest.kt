package com.ivandsky.puzzlebox.insights.recap

import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** The story's page map: Fastest gives each game its own page, every other card one, and the Share page comes last. */
class RecapPageTest {

    private val solved = RecapCard.Solved(total = 9, perGame = emptyList())

    @Test
    fun `a Fastest card with one game is one page`() {
        assertEquals(
            listOf(RecapPage(0), RecapPage(1, 0), RecapPage(2)),
            pagesOf(listOf(solved, fastestOf(1), RecapFixtures.totalTime), share = false),
        )
    }

    @Test
    fun `a Fastest card with two games is two pages, in game order`() {
        assertEquals(
            listOf(RecapPage(0), RecapPage(1, 0), RecapPage(1, 1), RecapPage(2)),
            pagesOf(listOf(solved, fastestOf(2), RecapFixtures.totalTime), share = false),
        )
    }

    @Test
    fun `a Fastest card with five games is five pages and the Share page follows the last card`() {
        val pages = pagesOf(listOf(solved, fastestOf(5), RecapFixtures.totalTime), share = true)

        assertEquals(1 + 5 + 1 + 1, pages.size)
        assertEquals((0 until 5).map { RecapPage(1, it) }, pages.subList(1, 6))
        assertEquals(RecapPage(3), pages.last())
    }

    @Test
    fun `the Share page sits past the last card`() {
        assertEquals(listOf(RecapPage(0), RecapPage(1)), pagesOf(listOf(solved), share = true))
        assertEquals(listOf(RecapPage(0)), pagesOf(listOf(solved), share = false))
    }

    @Test
    fun `a Fastest card without games has no page`() {
        assertEquals(listOf(RecapPage(0), RecapPage(2)), pagesOf(listOf(solved, fastestOf(0), RecapFixtures.totalTime), share = false))
    }

    @Test
    fun `no cards is no page, or the Share page alone`() {
        assertEquals(emptyList<RecapPage>(), pagesOf(emptyList(), share = false))
        assertEquals(listOf(RecapPage(0)), pagesOf(emptyList(), share = true))
    }
}

/** A Fastest card with [games] rows, one game each. */
internal fun fastestOf(games: Int) = RecapCard.Fastest(
    List(games) { RecapCard.Fastest.Row(GameId("game$it"), 1_000L * (it + 1), LocalDate.of(2026, 11, 1 + it), null, hasReplay = false, averageMs = null, solves = 1) },
)
