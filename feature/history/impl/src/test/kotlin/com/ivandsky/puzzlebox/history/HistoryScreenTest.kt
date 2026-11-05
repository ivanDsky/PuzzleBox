package com.ivandsky.puzzlebox.history

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.history.HistoryTestSupport.QUEENS
import com.ivandsky.puzzlebox.history.HistoryTestSupport.TANGO
import com.ivandsky.puzzlebox.history.HistoryTestSupport.ZIP
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
 * Fix round 5, defect C: rows are per-game via the game-agnostic merged history — a
 * solved Queens day lists and details exactly like a captured day. tango-05: Zip and
 * Tango share the calendar without colliding, labels name the game, and a tango replay
 * hands a tango-named envelope.
 *
 * Drives [HistoryRoute] over a real [HistoryViewModel] (production use cases, fake
 * repositories, stand-in game plugins) — the route's exact projection.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class HistoryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var handedToShell: ByteArray? = null

    private fun setContent(
        zips: List<DailyEntry> = emptyList(),
        tangos: List<DailyEntry> = emptyList(),
        layout: HistoryLayout = HistoryLayout.SinglePane,
        records: List<CompletionRecord> = emptyList(),
        cached: Map<Pair<GameId, LocalDate>, Puzzle> = emptyMap(),
    ) {
        handedToShell = null
        val daily = FakeDailyRepository().apply {
            setHistory(ZIP, zips)
            setHistory(TANGO, tangos)
            cached.forEach { (key, puzzle) -> setCached(key.first, key.second, puzzle) }
        }
        val viewModel = HistoryTestSupport.viewModel(daily, FakeCompletionRepository(records))
        composeRule.setContent {
            PuzzleBoxTheme {
                HistoryRoute(
                    layout = layout,
                    onBack = {},
                    onReplay = { session -> handedToShell = session },
                    viewModel = viewModel,
                )
            }
        }
    }

    private fun awaitTag(tag: String) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitText(text: String) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `empty cache shows the captured-nothing empty state`() {
        setContent()

        awaitTag("history.empty")
        composeRule.onNodeWithTag("history.streak").assertDoesNotExist()
    }

    @Test
    fun `captured days list newest first with the streak teaser`() {
        val days = listOf(
            LocalDate.of(2026, 11, 1),
            LocalDate.of(2026, 11, 2),
            LocalDate.of(2026, 11, 3),
        )
        setContent(days.map { HistoryTestSupport.entry(it) })

        awaitTag("history.row.zip/2026-11-03")
        composeRule.onNodeWithText("Tuesday, November 3").assertExists()
        // Every row carries the identity line; all three seeded rows are in the page.
        composeRule.onAllNodesWithText("Daily Zip · Puzzle #${HistoryTestSupport.PUZZLE_NUMBER}").assertCountEquals(3)
        // Streak teaser: 3 consecutive captured days; the count voice matches plurality.
        composeRule.onNodeWithTag("history.streak").assertExists()
        composeRule.onNodeWithText("Day streak").assertExists()
        composeRule.onNodeWithText("3 days saved on this device").assertExists()
    }

    @Test
    fun `tango rows carry the tango label and never collide with a zip row on the same day`() {
        val day = LocalDate.of(2026, 11, 3)
        setContent(
            zips = listOf(HistoryTestSupport.entry(day)),
            tangos = listOf(HistoryTestSupport.tangoEntry(day)),
        )

        awaitTag("history.row.zip/$day")
        awaitTag("history.row.tango/$day")
        // Game-keyed identity: BOTH rows exist on the same calendar day.
        composeRule.onNodeWithTag("history.row.zip/$day").assertExists()
        composeRule.onNodeWithTag("history.row.tango/$day").assertExists()
        composeRule.onNodeWithText("Daily Tango · Puzzle #${HistoryTestSupport.TANGO_PUZZLE_NUMBER}").assertExists()
        composeRule.onAllNodesWithText("Tuesday, November 3").assertCountEquals(2)
    }

    @Test
    fun `won day detail shows its record and hands the replay blob to the shell`() {
        val day = LocalDate.of(2026, 11, 3)
        val cached = TestGames.puzzle(ZIP)
        setContent(
            zips = listOf(HistoryTestSupport.entry(day)),
            records = listOf(HistoryTestSupport.record(day)),
            cached = mapOf(ZIP to day to cached),
        )

        awaitTag("history.row.zip/$day")
        composeRule.onNodeWithTag("history.row.zip/$day").performClick()
        composeRule.onNodeWithText("Your record").assertExists()
        composeRule.onNodeWithText("1:35 · 2 hints").assertExists()
        composeRule.onNodeWithTag("history.replay").performClick()
        composeRule.waitForIdle()

        val handed = handedToShell
        assertNotNull("shell must receive the Ready launch", handed)
        // The handed blob keeps the replayed DAY's identity, never today's (A4).
        assertArrayEquals(TestGames.replayBlob(ZIP, cached, day, HistoryTestSupport.PUZZLE_NUMBER), handed)
    }

    @Test
    fun `tango detail hands the shell a tango-named envelope`() {
        val day = LocalDate.of(2026, 11, 3)
        val cached = TestGames.puzzle(TANGO)
        setContent(
            tangos = listOf(HistoryTestSupport.tangoEntry(day)),
            records = listOf(HistoryTestSupport.tangoRecord(day)),
            cached = mapOf(TANGO to day to cached),
        )

        awaitTag("history.row.tango/$day")
        composeRule.onNodeWithTag("history.row.tango/$day").performClick()
        // The detail speaks the tango voice; its record is the tango day's (1:01 · no hints).
        composeRule.onNodeWithText("Daily Tango · Puzzle #${HistoryTestSupport.TANGO_PUZZLE_NUMBER}").assertExists()
        composeRule.onNodeWithText("Your record").assertExists()
        composeRule.onNodeWithText("1:01 · 0 hints").assertExists()
        composeRule.onNodeWithTag("history.replay").performClick()
        composeRule.waitForIdle()

        val handed = handedToShell
        assertNotNull(handed)
        // The envelope NAMES the game — the play route dispatches tango (ADR-0005).
        assertEquals(TANGO, SessionBlob.decode(handed!!)?.game)
        assertArrayEquals(TestGames.replayBlob(TANGO, cached, day, HistoryTestSupport.TANGO_PUZZLE_NUMBER), handed)
    }

    @Test
    fun `unfinished day detail offers replay with a fresh session`() {
        val day = LocalDate.of(2026, 11, 3)
        setContent(
            zips = listOf(HistoryTestSupport.entry(day)),
            cached = mapOf(ZIP to day to TestGames.puzzle(ZIP)),
        )

        awaitTag("history.row.zip/$day")
        composeRule.onNodeWithTag("history.row.zip/$day").performClick()
        composeRule.onNodeWithText("Not finished yet").assertExists()
        composeRule.onNodeWithTag("history.replay").performClick()
        composeRule.waitForIdle()

        assertNotNull(handedToShell)
    }

    @Test
    fun `stale day blocks replay with a message`() {
        val day = LocalDate.of(2026, 11, 3)
        // Listed, but its payload is no longer cached.
        setContent(listOf(HistoryTestSupport.entry(day)))

        awaitTag("history.row.zip/$day")
        composeRule.onNodeWithTag("history.row.zip/$day").performClick()
        composeRule.onNodeWithTag("history.replay").performClick()

        awaitText("That day is no longer saved on this device.")
        assertNull(handedToShell)
    }

    @Test
    fun `unreadable payload blocks replay with a message (A16)`() {
        val day = LocalDate.of(2026, 11, 3)
        val corrupt = Puzzle(ZIP, TestGames.variant(ZIP), 1, byteArrayOf(0))
        setContent(
            zips = listOf(HistoryTestSupport.entry(day)),
            cached = mapOf(ZIP to day to corrupt),
        )

        awaitTag("history.row.zip/$day")
        composeRule.onNodeWithTag("history.row.zip/$day").performClick()
        composeRule.onNodeWithTag("history.replay").performClick()

        awaitText("This day didn't load right, so it can't be played again.")
        assertNull(handedToShell)
    }

    @Test
    fun `a queens completion lists and details without a capture - fix round 5 defect C`() {
        val day = LocalDate.of(2026, 11, 3)
        setContent(records = listOf(HistoryTestSupport.queensRecord(day)))

        // The record alone lists the day (no capture anywhere) with its game's name.
        awaitTag("history.row.${QUEENS.value}/$day")
        composeRule.onNodeWithText("Tuesday, November 3").assertExists()
        composeRule.onNodeWithText("Daily Queens · Puzzle #916").assertExists()

        composeRule.onNodeWithTag("history.row.queens/$day").performClick()
        composeRule.onNodeWithText("Daily Queens · Puzzle #916").assertExists()
        composeRule.onNodeWithText("Your record").assertExists()
        composeRule.onNodeWithText("0:41 · 1 hint").assertExists()
        // No captured entry for the day: no "Captured" line — the record is visible
        // without pretending the day was ever fetched.
        composeRule.onNodeWithText("Captured", substring = true).assertDoesNotExist()
        assertFalse(handedToShell != null)
    }

    @Test
    fun `zip completion still lists alongside captures - regression guard`() {
        val day = LocalDate.of(2026, 11, 3)
        setContent(
            zips = listOf(HistoryTestSupport.entry(day, fetchedAtMs = fetchedAtMs(day))),
            records = listOf(HistoryTestSupport.record(day)),
        )

        // The solved zip day merges into its captured row: ONE row, record badge on it.
        awaitTag("history.row.zip/$day")
        composeRule.onAllNodesWithTag("history.row.zip/$day").assertCountEquals(1)
        composeRule.onNodeWithTag("history.row.zip/$day").performClick()
        composeRule.onNodeWithText("Your record").assertExists()
        composeRule.onNodeWithText("Captured", substring = true).assertExists()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w800dp-h1000dp") // two-pane is the expanded policy (07)
    fun `two-pane shows the detail hint until a day is selected`() {
        val day = LocalDate.of(2026, 11, 3)
        setContent(listOf(HistoryTestSupport.entry(day)), layout = HistoryLayout.TwoPane)

        awaitTag("history.detail_hint")
        composeRule.onNodeWithText("Select a day").assertExists()
        awaitTag("history.row.zip/$day")
        composeRule.onNodeWithTag("history.row.zip/$day").performClick()
        awaitTag("history.detail.zip.$day")
    }
}
