package com.ivandsky.puzzlebox.history

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.history.HistoryTestSupport.TANGO
import com.ivandsky.puzzlebox.history.HistoryTestSupport.ZIP
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Ticket 09 (spec A20): on a game tab every day of the game's archive range taps, played
 * or not, and shows the day header — "<Game> #N", the day's status and its one action,
 * which reports (game, day) to the shell. Days before the game's day1 and after today
 * stay disabled; the All tab still taps only days with rows. Drives [HistoryRoute] over a
 * real [HistoryViewModel] and [com.ivandsky.puzzlebox.domain.ObservePastDay] (fakes only,
 * never a fetch).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class HistoryDayHeaderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today: LocalDate = HistoryTestSupport.TODAY // Tuesday, November 3

    /** Zip's day1 in these tests: its archive opens today, so Monday (same week) falls before it. */
    private val dayOne: LocalDate = today
    private val beforeDayOne: LocalDate = today.minusDays(1)

    private var handedToShell: Pair<GameId, LocalDate>? = null

    private fun setContent(
        key: HistoryKey = HistoryKey(ZIP.value),
        zips: List<DailyEntry> = emptyList(),
        tangos: List<DailyEntry> = emptyList(),
        records: List<CompletionRecord> = emptyList(),
        savedSlots: Map<PlaySlot, Long> = emptyMap(),
        archiveStart: LocalDate = dayOne,
    ) {
        val daily = FakeDailyRepository().apply {
            setHistory(ZIP, zips)
            setHistory(TANGO, tangos)
            // Rows here are real captures; ObservePastDay reads Stored only off cachedPuzzle (A10).
            (zips + tangos).forEach { setCached(it.gameId, it.day, TestGames.puzzle(it.gameId)) }
            setArchiveRange(ZIP, archiveStart..today)
            setPuzzleNumbers(ZIP, ::puzzleNumber)
        }
        val saved = FakeSavedPlayRepository().apply {
            runBlocking { savedSlots.forEach { (slot, elapsed) -> save(slot, byteArrayOf(1), elapsed) } }
        }
        val viewModel = HistoryTestSupport.viewModel(daily, FakeCompletionRepository(records), key, saved = saved)
        composeRule.setContent {
            PuzzleBoxTheme {
                HistoryRoute(
                    key = key,
                    layout = HistoryLayout.SinglePane,
                    onBack = {},
                    onReplay = { game, day -> handedToShell = game to day },
                    viewModel = viewModel,
                )
            }
        }
    }

    private fun puzzleNumber(day: LocalDate): Int = NUMBER_TODAY - today.toEpochDay().minus(day.toEpochDay()).toInt()

    private fun hasTag(tag: String) =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun scrollTo(tag: String) {
        composeRule.waitUntil(5_000) {
            runCatching { composeRule.onNodeWithTag(LIST).performScrollToNode(hasTestTag(tag)) }.isSuccess
        }
    }

    private fun dayTag(day: LocalDate) = "history.calendar.day.$day"

    /** Taps [day] on the calendar and waits for its header. */
    private fun openHeader(day: LocalDate = today) {
        scrollTo(dayTag(day))
        composeRule.onNodeWithTag(dayTag(day)).assertIsEnabled().performClick()
        scrollTo(HEADER)
    }

    private fun assertHeader(headline: String, status: String, action: String) {
        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule.onNodeWithTag("$HEADER.status", useUnmergedTree = true).assertTextEquals(status)
            }.isSuccess
        }
        composeRule.onNodeWithTag("$HEADER.headline", useUnmergedTree = true).assertTextEquals(headline)
        composeRule.onNodeWithTag("$HEADER.action").assertTextEquals(action)
    }

    @Test
    fun `a game tab taps a day with no rows inside its range, never one before day1`() {
        setContent()

        scrollTo(dayTag(beforeDayOne))
        composeRule.onNodeWithTag(dayTag(beforeDayOne)).assertIsNotEnabled()
        composeRule.onNodeWithTag(dayTag(today.plusDays(1))).assertIsNotEnabled()
        openHeader(today)
        assertHeader("Zip #$NUMBER_TODAY", "Needs a connection the first time", "Play")
    }

    @Test
    fun `a day stored on the device reads Saved on this device and Play`() {
        setContent(zips = listOf(HistoryTestSupport.entry(dayOne, puzzleNumber = NUMBER_TODAY)))

        openHeader(dayOne)
        assertHeader("Zip #$NUMBER_TODAY", "Saved on this device", "Play")
    }

    @Test
    fun `a saved play reads In progress and Keep going`() {
        setContent(savedSlots = mapOf(PlaySlot(ZIP, today) to 42_000L))

        openHeader(today)
        assertHeader("Zip #$NUMBER_TODAY", "In progress", "Keep going")
    }

    @Test
    fun `a solved day reads its time and Play again`() {
        setContent(records = listOf(HistoryTestSupport.record(today, timeMs = 95_000)))

        openHeader(today)
        assertHeader("Zip #$NUMBER_TODAY", "Solved 1:35", "Play again")
    }

    @Test
    fun `the header's action opens that game and day`() {
        setContent()
        openHeader(today)

        composeRule.onNodeWithTag("$HEADER.action").performClick()

        composeRule.waitUntil(5_000) { handedToShell != null }
        assertEquals(ZIP to today, handedToShell)
    }

    @Test
    fun `the All tab still taps only days with rows and shows no header`() {
        // Zip's archive covers today, yet on All only Tango's played Monday taps.
        val played = beforeDayOne
        setContent(key = HistoryKey(), tangos = listOf(HistoryTestSupport.tangoEntry(played)))

        scrollTo("history.row.${TANGO.value}/$played")
        scrollTo(dayTag(today))
        composeRule.onNodeWithTag(dayTag(today)).assertIsNotEnabled()
        composeRule.onNodeWithTag(dayTag(played)).assertIsEnabled().performClick()
        scrollTo("history.day_filter")

        assertFalse("All shows no day header", hasTag(HEADER))
    }

    @Test
    fun `the calendar pages back months to day1, and a day there opens its header`() {
        // Spec SC9: an archive that opens in August, with no row anywhere.
        val augustDayOne = LocalDate.of(2026, 8, 20)
        val between = LocalDate.of(2026, 9, 17)
        setContent(archiveStart = augustDayOne)
        scrollTo("history.calendar.expand")
        composeRule.onNodeWithTag("history.calendar.expand").performClick()
        composeRule.waitUntil(5_000) { hasTag("history.calendar.grid") }

        repeat(3) { // November → August
            composeRule.onNodeWithTag("history.calendar.prev").assertIsEnabled().performClick()
            composeRule.waitForIdle()
        }
        composeRule.waitUntil(5_000) { hasTag(dayTag(augustDayOne)) }
        composeRule.onNodeWithTag(dayTag(augustDayOne)).assertIsEnabled()
        composeRule.onNodeWithTag(dayTag(augustDayOne.minusDays(1))).assertIsNotEnabled()
        composeRule.onNodeWithTag("history.calendar.prev").assertIsNotEnabled()

        composeRule.onNodeWithTag("history.calendar.next").assertIsEnabled().performClick()
        composeRule.waitUntil(5_000) { hasTag(dayTag(between)) }
        openHeader(between)
        assertHeader("Zip #${puzzleNumber(between)}", "Needs a connection the first time", "Play")
    }

    private companion object {
        const val LIST = "history.list"
        const val HEADER = "history.day_header"
        const val NUMBER_TODAY = 594
    }
}
