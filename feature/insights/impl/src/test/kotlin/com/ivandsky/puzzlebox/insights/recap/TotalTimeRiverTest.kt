package com.ivandsky.puzzlebox.insights.recap

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.up
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.insights.domain.recap.DayTally
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.gameName
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Total time day river: the bars it draws, the hero it climbs to, and what a finger on it reads. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class TotalTimeRiverTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val october = RecapPeriod.Month(YearMonth.of(2026, 10))

    /** Play on 3 Oct (5m, 2 puzzles) and 11 Oct (16m, 5 puzzles); every other day of the month is a gap. */
    private val month = RecapCard.TotalTime(
        totalMs = 21 * MINUTE,
        solved = 7,
        days = listOf(
            DayTally(LocalDate.of(2026, 10, 3), solved = 2, timeMs = 5 * MINUTE, hintFree = 2),
            DayTally(LocalDate.of(2026, 10, 11), solved = 5, timeMs = 16 * MINUTE, hintFree = 4),
        ),
        period = october,
    )

    private fun page(card: RecapCard.TotalTime) = rule.setContent {
        PuzzleBoxTheme { RecapCardPage(card, "October 2026", gameName, onWatchReplay = {}) }
    }

    @Test
    fun `a month river has a bar for every day, the days nobody played a stub`() {
        val bars = month.riverBars()

        assertEquals(31, bars.size)
        assertEquals(LocalDate.of(2026, 10, 1), bars.first().start)
        assertEquals(LocalDate.of(2026, 10, 31), bars.last().start)
        assertEquals(0L, bars[1].timeMs)
        assertEquals(listOf(2, 10), bars.indices.filter { bars[it].timeMs > 0 })
    }

    @Test
    fun `a year river draws Monday weeks with the first clipped to 1 January and weeks of no play left out`() {
        val year = RecapCard.TotalTime(
            totalMs = 30 * MINUTE,
            solved = 3,
            days = listOf(
                DayTally(LocalDate.of(2026, 1, 1), solved = 1, timeMs = 10 * MINUTE, hintFree = 1),
                DayTally(LocalDate.of(2026, 1, 2), solved = 1, timeMs = 10 * MINUTE, hintFree = 1),
                DayTally(LocalDate.of(2026, 1, 20), solved = 1, timeMs = 10 * MINUTE, hintFree = 1),
            ),
            period = RecapPeriod.Year(2026),
        )

        val bars = year.riverBars()

        assertEquals(listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 19)), bars.map { it.start })
        assertEquals(20 * MINUTE, bars.first().timeMs)
    }

    @Test
    fun `the hero reads hours and minutes at every size, never seconds`() {
        assertEquals("0h 1m", formatHeroDuration(72_000))
        assertEquals("3h 47m", formatHeroDuration(13_620_000))
        assertEquals("99h 59m", formatHeroDuration(359_940_000))
        assertEquals("1h 0m", formatHeroDuration(3_599_999 + 1))
    }

    @Test
    fun `a screen reader hears the settled time in words`() {
        page(month.copy(totalMs = 13_620_000))

        rule.onNodeWithTag("recap.totaltime.time").assertContentDescriptionEquals("Time spent solving, 3 hours 47 minutes")
    }

    @Test
    fun `the readout starts on the busiest day, follows a drag and goes back when it lifts`() {
        page(month)
        rule.waitForIdle()

        rule.onNodeWithTag("recap.totaltime.readout").assertTextContains("BUSIEST DAY").assertTextContains("Sun 11 Oct").assertTextContains("16m · 5 puzzles")

        rule.onNodeWithTag("recap.totaltime.river").performTouchInput { down(Offset(width * 10.5f / 31, height / 2f)); moveTo(Offset(width * 2.5f / 31, height / 2f)) }
        rule.waitForIdle()
        rule.onNodeWithTag("recap.totaltime.readout").assertTextContains("DAY").assertTextContains("Sat 3 Oct").assertTextContains("5m · 2 puzzles")

        rule.onNodeWithTag("recap.totaltime.river").performTouchInput { up() }
        rule.waitForIdle()
        rule.onNodeWithTag("recap.totaltime.readout").assertTextContains("BUSIEST DAY").assertTextContains("Sun 11 Oct")
    }

    @Test
    fun `a tap on the river turns to the next card and picks no day`() {
        val state = RecapUiState.Ready(period = october, title = "October 2026", cards = listOf(RecapFixtures.solved, month, RecapFixtures.moments), initialPage = 1)
        rule.setContent { PuzzleBoxTheme { RecapScreen(state, "October 2026", gameName, onPage = {}, onWatchReplay = {}, onClose = {}) } }
        rule.waitForIdle()

        rule.onNodeWithTag("recap.totaltime.river").performTouchInput { click(Offset(width * 20.5f / 31, height / 2f)) }
        rule.waitForIdle()

        rule.onNodeWithTag("recap.moments").assertIsDisplayed()
    }

    @Test
    fun `dragging along the river reads days and does not turn the card`() {
        val state = RecapUiState.Ready(
            period = october,
            title = "October 2026",
            cards = listOf(RecapFixtures.solved, month, RecapFixtures.moments),
            initialPage = 1,
        )
        rule.setContent { PuzzleBoxTheme { RecapScreen(state, "October 2026", gameName, onPage = {}, onWatchReplay = {}, onClose = {}) } }

        rule.onNodeWithTag("recap.totaltime.river").performTouchInput { swipeLeft() }
        rule.waitForIdle()

        rule.onNodeWithTag("recap.totaltime").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun `on a tablet the list's small river reads the day the story's river is held on`() {
        val state = RecapUiState.Ready(period = october, title = "October 2026", cards = listOf(RecapFixtures.solved, month, RecapFixtures.moments), initialPage = 1)
        rule.setContent { PuzzleBoxTheme { RecapScreen(state, "October 2026", gameName, onPage = {}, onWatchReplay = {}, onClose = {}) } }
        rule.waitForIdle()
        assertTrue(rule.onNodeWithTag("recap.totaltime.miniReadout").isDisplayedNode())

        rule.onNodeWithTag("recap.totaltime.river").performTouchInput { down(Offset(width * 10.5f / 31, height / 2f)); moveTo(Offset(width * 2.5f / 31, height / 2f)) }
        rule.waitForIdle()

        rule.onNodeWithTag("recap.totaltime.miniReadout").assertTextContains("Sat 3 Oct")
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.isDisplayedNode() = runCatching { assertIsDisplayed() }.isSuccess

    private companion object {
        const val MINUTE = 60_000L
    }
}
