package com.ivandsky.puzzlebox.history

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.history.HistoryTestSupport.ZIP
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric coverage for History's week/month calendar (spec A18): it opens on this
 * week's seven days; its arrows page weeks back to the oldest row's week and never past
 * this one; the title opens the month, whose arrows page months, and folds it back to a
 * week. A day with plays filters the list to that day and the filter pill brings every
 * day back; later days and days with no plays never select. Drives [HistoryRoute] over a
 * real [HistoryViewModel]; tags and bounds only.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class HistoryCalendarTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today: LocalDate = HistoryTestSupport.TODAY // Tuesday, November 3

    private fun setContent(zips: List<DailyEntry>) {
        val daily = FakeDailyRepository().apply { setHistory(ZIP, zips) }
        val viewModel = HistoryTestSupport.viewModel(daily, FakeCompletionRepository())
        composeRule.setContent {
            PuzzleBoxTheme {
                HistoryRoute(
                    key = HistoryKey(),
                    layout = HistoryLayout.SinglePane,
                    onBack = {},
                    onPlayAgain = { _, _ -> },
                    viewModel = viewModel,
                )
            }
        }
    }

    /** Scrolls the list until [tag] composes, once the state has loaded. */
    private fun scrollTo(tag: String) {
        composeRule.waitUntil(5_000) {
            runCatching { composeRule.onNodeWithTag(LIST).performScrollToNode(hasTestTag(tag)) }.isSuccess
        }
    }

    private fun hasTag(tag: String) =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun reachable(tag: String) =
        runCatching { composeRule.onNodeWithTag(LIST).performScrollToNode(hasTestTag(tag)) }.isSuccess

    private fun rowTag(day: LocalDate) = "history.row.${ZIP.value}/$day"

    private fun dayTag(day: LocalDate) = "history.calendar.day.$day"

    private fun waitForTag(tag: String) = composeRule.waitUntil(5_000) { hasTag(tag) }

    private fun waitWithout(tag: String) = composeRule.waitUntil(5_000) { !hasTag(tag) }

    /** Monday, November 2 — the start of [today]'s week. */
    private val monday: LocalDate = today.minusDays(1)

    @Test
    fun `it opens on this week's seven days`() {
        setContent(listOf(HistoryTestSupport.entry(today)))
        scrollTo(rowTag(today))

        scrollTo("history.calendar.week")
        (0L until 7L).forEach { assertTrue("day $it of this week", hasTag(dayTag(monday.plusDays(it)))) }
        assertFalse("no day before this week", hasTag(dayTag(monday.minusDays(1))))
        assertFalse("no day after this week", hasTag(dayTag(monday.plusDays(7))))
        assertFalse("the month stays folded", hasTag("history.calendar.grid"))
    }

    @Test
    fun `the arrows page weeks between the oldest row's week and this one`() {
        val lastWeek = today.minusWeeks(1)
        setContent(listOf(HistoryTestSupport.entry(today), HistoryTestSupport.entry(lastWeek)))
        scrollTo(rowTag(lastWeek))
        scrollTo(dayTag(today))

        composeRule.onNodeWithTag("history.calendar.next").assertIsNotEnabled()
        composeRule.onNodeWithTag("history.calendar.prev").assertIsEnabled().performClick()

        waitForTag(dayTag(lastWeek))
        waitWithout(dayTag(today))
        // Last week holds the oldest row: no further back, and forward is open again.
        composeRule.onNodeWithTag("history.calendar.prev").assertIsNotEnabled()
        composeRule.onNodeWithTag("history.calendar.next").assertIsEnabled().performClick()
        waitForTag(dayTag(today))
    }

    @Test
    fun `the title opens the month, whose arrows page months`() {
        val october = LocalDate.of(2026, 10, 20)
        setContent(listOf(HistoryTestSupport.entry(today), HistoryTestSupport.entry(october)))
        scrollTo(rowTag(today))
        scrollTo("history.calendar.expand")

        composeRule.onNodeWithTag("history.calendar.expand").performClick()
        waitForTag("history.calendar.grid")
        assertTrue("the whole month shows", hasTag(dayTag(LocalDate.of(2026, 11, 30))))

        composeRule.onNodeWithTag("history.calendar.next").assertIsNotEnabled()
        composeRule.onNodeWithTag("history.calendar.prev").assertIsEnabled().performClick()
        waitForTag(dayTag(october))
        waitForTag(dayTag(LocalDate.of(2026, 10, 1)))
        // October holds the oldest row: no further back, and forward is open again.
        composeRule.onNodeWithTag("history.calendar.prev").assertIsNotEnabled()
        composeRule.onNodeWithTag("history.calendar.next").assertIsEnabled().performClick()
        waitForTag(dayTag(LocalDate.of(2026, 11, 30)))
    }

    @Test
    fun `folding the month shows one week again`() {
        val october = LocalDate.of(2026, 10, 20)
        setContent(listOf(HistoryTestSupport.entry(today), HistoryTestSupport.entry(october)))
        scrollTo(rowTag(today))
        scrollTo("history.calendar.expand")
        composeRule.onNodeWithTag("history.calendar.expand").performClick()
        waitForTag("history.calendar.grid")
        composeRule.onNodeWithTag("history.calendar.prev").performClick()
        waitForTag(dayTag(LocalDate.of(2026, 10, 1)))

        composeRule.onNodeWithTag("history.calendar.expand").performClick()

        // October folds to the week of its first play.
        waitForTag("history.calendar.week")
        waitWithout("history.calendar.grid")
        assertTrue(hasTag(dayTag(october)))
        assertFalse(hasTag(dayTag(LocalDate.of(2026, 10, 1))))
    }

    @Test
    fun `month weeks sit one spacing step apart, so neighbouring day rings never touch`() {
        setContent(listOf(HistoryTestSupport.entry(today)))
        scrollTo(rowTag(today))
        scrollTo("history.calendar.expand")
        composeRule.onNodeWithTag("history.calendar.expand").performClick()
        waitForTag("history.calendar.grid")

        val week = composeRule.onNodeWithTag(dayTag(monday)).fetchSemanticsNode().boundsInRoot
        val nextWeek = composeRule.onNodeWithTag(dayTag(monday.plusWeeks(1))).fetchSemanticsNode().boundsInRoot
        assertEquals(with(composeRule.density) { Spacing.sm.toPx() }, nextWeek.top - week.bottom, 0.5f)
    }

    @Test
    fun `a day in the week filters the list, and the filter pill brings every day back`() {
        val picked = today.minusDays(1)
        setContent(listOf(HistoryTestSupport.entry(today), HistoryTestSupport.entry(picked)))
        scrollTo(rowTag(today))

        scrollTo(dayTag(picked))
        composeRule.onNodeWithTag(dayTag(picked)).performClick()

        scrollTo("history.day_filter")
        scrollTo(rowTag(picked))
        assertFalse("another day's row is filtered out", reachable(rowTag(today)))

        scrollTo("history.day_filter")
        composeRule.onNodeWithTag("history.day_filter").performClick()
        composeRule.waitUntil(5_000) { !hasTag("history.day_filter") }
        scrollTo(rowTag(today))
    }

    @Test
    fun `the picked day again clears the filter`() {
        val picked = today.minusDays(1)
        setContent(listOf(HistoryTestSupport.entry(today), HistoryTestSupport.entry(picked)))
        scrollTo(rowTag(picked)) // the rows have loaded, so the day can be picked

        scrollTo(dayTag(picked))
        composeRule.onNodeWithTag(dayTag(picked)).performClick()
        composeRule.waitUntil(5_000) { hasTag("history.day_filter") }
        scrollTo(dayTag(picked))
        composeRule.onNodeWithTag(dayTag(picked)).performClick()

        composeRule.waitUntil(5_000) { !hasTag("history.day_filter") }
    }

    @Test
    fun `later days and days with no plays never select`() {
        val played = monday
        setContent(listOf(HistoryTestSupport.entry(played)))
        val later = today.plusDays(2)
        val empty = today
        scrollTo(rowTag(played))

        scrollTo(dayTag(played))
        composeRule.onNodeWithTag(dayTag(played)).assertIsEnabled()
        scrollTo(dayTag(later))
        composeRule.onNodeWithTag(dayTag(later)).assertIsNotEnabled().performClick()
        composeRule.onNodeWithTag(dayTag(empty)).assertIsNotEnabled().performClick()
        composeRule.waitForIdle()

        assertFalse("nothing was picked", hasTag("history.day_filter"))
    }

    private companion object {
        const val LIST = "history.list"
    }
}
