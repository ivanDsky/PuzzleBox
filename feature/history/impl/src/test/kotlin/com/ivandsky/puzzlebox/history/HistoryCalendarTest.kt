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
import com.ivandsky.puzzlebox.history.HistoryTestSupport.ZIP
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric coverage for History's month calendar (spec A18): a day with plays filters
 * the list to that day and the filter pill brings every day back; the arrows walk back to
 * the oldest month with history and never past the current one; later days and days with
 * no plays never select. Drives [HistoryRoute] over a real [HistoryViewModel]; tags only.
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
                    onReplay = { _, _ -> },
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

    private fun dayTag(day: LocalDate) = "history.calendar.day.$day"

    private fun rowTag(day: LocalDate) = "history.row.${ZIP.value}/$day"

    @Test
    fun `a day with plays filters the list, and the filter pill brings every day back`() {
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
    fun `back shows the earlier month, and forward stops at the current month`() {
        val october = LocalDate.of(2026, 10, 20)
        setContent(listOf(HistoryTestSupport.entry(today), HistoryTestSupport.entry(october)))
        scrollTo(dayTag(today))

        composeRule.onNodeWithTag("history.calendar.next").assertIsNotEnabled()
        composeRule.onNodeWithTag("history.calendar.prev").assertIsEnabled().performClick()

        scrollTo(dayTag(october))
        composeRule.onNodeWithTag(dayTag(october)).assertIsEnabled()
        // October holds the oldest row: no further back, and forward is open again.
        composeRule.onNodeWithTag("history.calendar.prev").assertIsNotEnabled()
        composeRule.onNodeWithTag("history.calendar.next").assertIsEnabled().performClick()
        scrollTo(dayTag(today))
    }

    @Test
    fun `later days and days with no plays never select`() {
        val played = today.minusDays(1)
        setContent(listOf(HistoryTestSupport.entry(played)))
        val later = today.plusDays(2)
        val empty = today.minusDays(2)
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
