package com.ivandsky.puzzlebox.history

import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.compose.collectAsLazyPagingItems
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.games.zip.ZipPuzzleRef
import com.ivandsky.puzzlebox.games.zip.ZipSession
import com.ivandsky.puzzlebox.games.zip.codec.ZipSessionCodec
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric compose coverage for the history browser (A17): empty state reuse, lazy
 * day rows, the streak teaser, record-informed detail, and the replay hand-off —
 * Ready reaches the shell, Missing/Unreadable (A16) stay put with a message.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class HistoryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var nextLaunch: ReplayLaunch = ReplayLaunch.Missing
    private var handedToShell: ReplayLaunch.Ready? = null

    private fun setContent(
        repo: FakeZipDailyRepository,
        layout: HistoryLayout = HistoryLayout.SinglePane,
        records: Map<LocalDate, CompletionRecord> = emptyMap(),
    ) {
        handedToShell = null
        composeRule.setContent {
            PuzzleBoxTheme {
                val lazyDays = remember(repo) { historyDays(repo) }.collectAsLazyPagingItems()
                HistoryScreen(
                    layout = layout,
                    onBack = {},
                    days = lazyDays,
                    entries = repo.entries,
                    recordsByDay = records,
                    streak = streakEndingAtNewest(repo.entries.map { it.day }),
                    replayFor = { nextLaunch },
                    onReplay = { ready -> handedToShell = ready },
                )
            }
        }
    }

    private fun awaitTag(tag: String) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `empty cache shows the captured-nothing empty state`() {
        setContent(FakeZipDailyRepository())

        awaitTag("history.empty")
        composeRule.onNodeWithText("Nothing captured yet").assertExists()
        composeRule.onNodeWithText("Day streak").assertDoesNotExist()
    }

    @Test
    fun `captured days list newest first with the streak teaser`() {
        val days = listOf(
            LocalDate.of(2026, 11, 1),
            LocalDate.of(2026, 11, 2),
            LocalDate.of(2026, 11, 3),
        )
        setContent(FakeZipDailyRepository(history = days.map { HistoryTestSupport.entry(it) }))

        awaitTag("history.row.2026-11-03")
        composeRule.onNodeWithText("Tuesday, November 3").assertExists()
        // Every row carries the identity line; all three seeded rows are in the page.
        composeRule.onAllNodesWithText("Daily Zip · Puzzle #${HistoryTestSupport.PUZZLE_NUMBER}").assertCountEquals(3)
        // Streak teaser: 3 consecutive captured days; the count voice matches plurality.
        composeRule.onNodeWithTag("history.streak").assertExists()
        composeRule.onNodeWithText("Day streak").assertExists()
        composeRule.onNodeWithText("3 days saved on this device").assertExists()
    }

    @Test
    fun `won day detail shows its record and hands the replay blob to the shell`() {
        val day = LocalDate.of(2026, 11, 3)
        nextLaunch = ReplayLaunch.Ready(ZipSessionCodec.serialize(HistoryTestSupport.expectedSession(day)))
        setContent(
            repo = FakeZipDailyRepository(history = listOf(HistoryTestSupport.entry(day))),
            records = mapOf(day to HistoryTestSupport.record(day)),
        )

        composeRule.onNodeWithTag("history.row.$day").performClick()
        composeRule.onNodeWithText("Your record").assertExists()
        composeRule.onNodeWithText("1:35 · 2 hints").assertExists()
        composeRule.onNodeWithTag("history.replay").performClick()

        val handed = handedToShell
        assertNotNull("shell must receive the Ready launch", handed)
        val session = ZipSessionCodec.deserialize(handed!!.session)
        assertTrue(session is DecodeResult.Ok)
        // The handed blob keeps the replayed DAY's identity, never today's (A4).
        assertEquals(ZipPuzzleRef(day, HistoryTestSupport.PUZZLE_NUMBER), (session as DecodeResult.Ok).value.ref)
    }

    @Test
    fun `unfinished day detail offers replay with a fresh session`() {
        val day = LocalDate.of(2026, 11, 3)
        nextLaunch = ReplayLaunch.Ready(
            ZipSessionCodec.serialize(
                ZipSession(ref = ZipPuzzleRef(day, HistoryTestSupport.PUZZLE_NUMBER), puzzle = HistoryTestSupport.puzzle()),
            ),
        )
        setContent(FakeZipDailyRepository(history = listOf(HistoryTestSupport.entry(day))))

        composeRule.onNodeWithTag("history.row.$day").performClick()
        composeRule.onNodeWithText("Not finished yet").assertExists()
        composeRule.onNodeWithTag("history.replay").performClick()

        assertNotNull(handedToShell)
    }

    @Test
    fun `stale day blocks replay with a message`() {
        val day = LocalDate.of(2026, 11, 3)
        nextLaunch = ReplayLaunch.Missing
        setContent(FakeZipDailyRepository(history = listOf(HistoryTestSupport.entry(day))))

        composeRule.onNodeWithTag("history.row.$day").performClick()
        composeRule.onNodeWithTag("history.replay").performClick()

        assertNull(handedToShell)
        composeRule.onNodeWithText("That day is no longer saved on this device.").assertExists()
    }

    @Test
    fun `unreadable payload blocks replay with a message (A16)`() {
        val day = LocalDate.of(2026, 11, 3)
        nextLaunch = ReplayLaunch.Unreadable(DecodeError.Malformed("test"))
        setContent(FakeZipDailyRepository(history = listOf(HistoryTestSupport.entry(day))))

        composeRule.onNodeWithTag("history.row.$day").performClick()
        composeRule.onNodeWithTag("history.replay").performClick()

        assertNull(handedToShell)
        composeRule.onNodeWithText("This puzzle's data couldn't be read, so it can't be replayed.").assertExists()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w800dp-h1000dp") // two-pane is the expanded policy (07)
    fun `two-pane shows the detail hint until a day is selected`() {
        val day = LocalDate.of(2026, 11, 3)
        val repo = FakeZipDailyRepository(history = listOf(HistoryTestSupport.entry(day)))
        setContent(repo, layout = HistoryLayout.TwoPane)

        awaitTag("history.detail_hint")
        composeRule.onNodeWithText("Select a day").assertExists()
        awaitTag("history.row.$day")
        composeRule.onNodeWithTag("history.row.$day").performClick()
        awaitTag("history.detail")
    }
}
