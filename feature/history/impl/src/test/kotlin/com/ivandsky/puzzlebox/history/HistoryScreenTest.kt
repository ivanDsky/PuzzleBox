package com.ivandsky.puzzlebox.history

import androidx.activity.compose.BackHandler
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.history.HistoryTestSupport.QUEENS
import com.ivandsky.puzzlebox.history.HistoryTestSupport.TANGO
import com.ivandsky.puzzlebox.history.HistoryTestSupport.ZIP
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import java.time.LocalDate
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric compose coverage for the history browser (A17, A18, A28): the empty state,
 * the All timeline whatever the key's game, the stats and calendar cards, day groups,
 * the Best pill, and the detail's hand-off — "See result" once solved, "Keep going"
 * while unfinished; both report the (game, day) to the shell and never build a session
 * blob (ticket 11), so cache state never blocks the tap.
 * Fix round 5, defect C: rows are per-game via the game-agnostic merged history — a
 * solved Queens day lists and details exactly like a captured day. tango-05: Zip and
 * Tango share the calendar without colliding, and a tango replay reports the tango game.
 *
 * Drives [HistoryRoute] over a real [HistoryViewModel] (production use cases, fake
 * repositories, stand-in game plugins) — the route's exact projection. Assertions sit on
 * tags and callbacks, never copy. Rows sit below the stats and calendar cards, so they are
 * reached by scrolling the list.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class HistoryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var handedToShell: Pair<GameId, LocalDate>? = null
    private var shellBacks = 0

    private val day: LocalDate = HistoryTestSupport.TODAY

    /** "Opened" is a wall-clock date in the device zone; tests that read it pin the zone. */
    private val hostZone: TimeZone = TimeZone.getDefault()

    @After
    fun restoreZone() {
        TimeZone.setDefault(hostZone)
    }

    private fun setContent(
        zips: List<DailyEntry> = emptyList(),
        tangos: List<DailyEntry> = emptyList(),
        layout: HistoryLayout = HistoryLayout.SinglePane,
        records: List<CompletionRecord> = emptyList(),
        cached: Map<Pair<GameId, LocalDate>, Puzzle> = emptyMap(),
        key: HistoryKey = HistoryKey(),
        hintsRanked: Set<GameId> = emptySet(),
    ) {
        handedToShell = null
        shellBacks = 0
        val daily = FakeDailyRepository().apply {
            setHistory(ZIP, zips)
            setHistory(TANGO, tangos)
            cached.forEach { (slot, puzzle) -> setCached(slot.first, slot.second, puzzle) }
        }
        val viewModel = HistoryTestSupport.viewModel(daily, FakeCompletionRepository(records), hintsRanked)
        composeRule.setContent {
            // Stand-in for the shell's NavDisplay pop: registered first, so History's own
            // handler (registered later) wins while it is enabled.
            BackHandler { shellBacks++ }
            PuzzleBoxTheme {
                HistoryRoute(
                    key = key,
                    layout = layout,
                    onBack = {},
                    onReplay = { game, replayed -> handedToShell = game to replayed },
                    viewModel = viewModel,
                )
            }
        }
    }

    /** Waits for [tag] anywhere (rows merge their children, so the unmerged tree). */
    private fun awaitTag(tag: String) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertNoTag(tag: String) {
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).assertDoesNotExist()
    }

    /** Scrolls the list until [game]'s [day] row composes, once its page has loaded. */
    private fun awaitRow(game: GameId, day: LocalDate = this.day) {
        val tag = rowTag(game, day)
        composeRule.waitUntil(5_000) {
            runCatching { composeRule.onNodeWithTag(LIST).performScrollToNode(hasTestTag(tag)) }.isSuccess
        }
    }

    private fun openRow(game: GameId, day: LocalDate = this.day) {
        awaitRow(game, day)
        composeRule.onNodeWithTag(rowTag(game, day)).performClick()
        awaitTag("history.detail.${game.value}.$day")
    }

    private fun rowTag(game: GameId, day: LocalDate) = "history.row.${game.value}/$day"

    @Test
    fun `nothing captured shows the empty state and no stats`() {
        setContent()

        awaitTag("history.empty")
        assertNoTag("history.stats")
        assertNoTag("history.calendar")
    }

    @Test
    fun `captured days list under the stats and calendar, one group per day`() {
        val days = listOf(day.minusDays(2), day.minusDays(1), day)
        setContent(days.map { HistoryTestSupport.entry(it) })

        awaitTag("history.stats")
        awaitTag("history.calendar")
        days.forEach { listed ->
            awaitRow(ZIP, listed)
            awaitTag("history.day.$listed")
            // Opened but never solved: the row says it is still going.
            awaitTag("history.unfinished.zip/$listed")
        }
    }

    @Test
    fun `tango and zip rows share a day without colliding, under one day label`() {
        setContent(
            zips = listOf(HistoryTestSupport.entry(day)),
            tangos = listOf(HistoryTestSupport.tangoEntry(day)),
        )

        // Game-keyed identity: BOTH rows exist on the same calendar day.
        awaitRow(ZIP)
        awaitRow(TANGO)
        composeRule.onAllNodesWithTag("history.day.$day").assertCountEquals(1)
    }

    @Test
    fun `solved day detail shows its score and best and plays again as that game and day`() {
        setContent(
            zips = listOf(HistoryTestSupport.entry(day)),
            records = listOf(HistoryTestSupport.record(day)),
            cached = mapOf(ZIP to day to TestGames.puzzle(ZIP)),
        )

        openRow(ZIP)
        awaitTag("history.score")
        awaitTag("history.detail.best")
        assertNoTag("history.keep_going")
        composeRule.onNodeWithTag("history.see_result").assertTextEquals("See result")
        composeRule.onNodeWithTag("history.see_result").performClick()
        composeRule.waitForIdle()

        // The replayed DAY's identity, never today's (A4, N18).
        assertEquals(ZIP to day, handedToShell)
    }

    @Test
    fun `single-pane back closes the day detail first and then leaves history`() {
        setContent(zips = listOf(HistoryTestSupport.entry(day)))
        val dispatcher = (composeRule as AndroidComposeTestRule<*, *>).activity.onBackPressedDispatcher

        openRow(ZIP)
        awaitTag("history.keep_going")

        composeRule.runOnUiThread { dispatcher.onBackPressed() }
        composeRule.waitForIdle()
        awaitRow(ZIP)
        assertNoTag("history.keep_going")
        assertEquals(0, shellBacks)

        composeRule.runOnUiThread { dispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertEquals(1, shellBacks)
    }

    @Test
    fun `tango detail reports the tango game and day`() {
        setContent(
            tangos = listOf(HistoryTestSupport.tangoEntry(day)),
            records = listOf(HistoryTestSupport.tangoRecord(day)),
            cached = mapOf(TANGO to day to TestGames.puzzle(TANGO)),
        )

        openRow(TANGO)
        composeRule.onNodeWithTag("history.see_result").performClick()
        composeRule.waitForIdle()

        assertEquals(TANGO to day, handedToShell)
    }

    @Test
    fun `unfinished day detail shows Keep going, never See result`() {
        setContent(
            zips = listOf(HistoryTestSupport.entry(day)),
            cached = mapOf(ZIP to day to TestGames.puzzle(ZIP)),
        )

        openRow(ZIP)
        awaitTag("history.score")
        assertNoTag("history.see_result")
        assertNoTag("history.detail.best") // no solve yet, so no best to show
        composeRule.onNodeWithTag("history.keep_going").performClick()
        composeRule.waitForIdle()

        assertEquals(ZIP to day, handedToShell)
    }

    @Test
    fun `uncached day still reports its replay - the play store resolves the slot`() {
        // Listed, but its payload is no longer cached: History no longer reads the cache.
        setContent(listOf(HistoryTestSupport.entry(day)))

        openRow(ZIP)
        composeRule.onNodeWithTag("history.keep_going").performClick()
        composeRule.waitForIdle()

        assertEquals(ZIP to day, handedToShell)
    }

    @Test
    fun `a queens completion lists and details without a capture - fix round 5 defect C`() {
        setContent(records = listOf(HistoryTestSupport.queensRecord(day)))

        // The record alone lists the day (no capture anywhere).
        openRow(QUEENS)
        awaitTag("history.score")
        awaitTag("history.see_result")
        assertNull(handedToShell)
    }

    @Test
    fun `zip completion still lists alongside captures - regression guard`() {
        setContent(
            zips = listOf(HistoryTestSupport.entry(day, fetchedAtMs = fetchedAtMs(day))),
            records = listOf(HistoryTestSupport.record(day)),
        )

        // The solved zip day merges into its captured row: ONE row, solved.
        awaitRow(ZIP)
        composeRule.onAllNodesWithTag(rowTag(ZIP, day)).assertCountEquals(1)
        assertNoTag("history.unfinished.zip/$day")
        openRow(ZIP)
        awaitTag("history.see_result")
    }

    @Test
    fun `the Best pill marks only the fastest numbered solve`() {
        val slower = day
        val fastest = day.minusDays(1)
        val practice = day.minusDays(2)
        setContent(
            zips = listOf(
                HistoryTestSupport.entry(slower),
                HistoryTestSupport.entry(fastest),
                HistoryTestSupport.entry(practice, puzzleNumber = null),
            ),
            records = listOf(
                HistoryTestSupport.record(slower, timeMs = 95_000),
                HistoryTestSupport.record(fastest, timeMs = 80_000),
                // Fastest of all, but practice: never a best.
                HistoryTestSupport.record(practice, timeMs = 30_000)
                    .copy(puzzleRef = TestGames.Ref(ZIP, PuzzleKey.Daily(practice, null))),
            ),
        )

        awaitRow(ZIP, fastest)
        awaitTag("history.best.zip/$fastest")
        awaitRow(ZIP, slower)
        assertNoTag("history.best.zip/$slower")
        awaitRow(ZIP, practice)
        assertNoTag("history.best.zip/$practice")
        composeRule.onAllNodesWithTag("history.best.zip/$fastest", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun `a hints-ranked game's Best pill marks its fewest-hints day`() {
        val quicker = day
        val fewestHints = day.minusDays(1)
        setContent(
            zips = listOf(HistoryTestSupport.entry(quicker), HistoryTestSupport.entry(fewestHints)),
            records = listOf(
                HistoryTestSupport.record(quicker, timeMs = 60_000, hints = 2),
                HistoryTestSupport.record(fewestHints, timeMs = 90_000, hints = 0),
            ),
            hintsRanked = setOf(ZIP),
        )

        awaitRow(ZIP, fewestHints)
        awaitTag("history.best.zip/$fewestHints")
        awaitRow(ZIP, quicker)
        assertNoTag("history.best.zip/$quicker")
    }

    @Test
    fun `a game's key opens the All timeline with every game and no tabs`() {
        setContent(
            zips = listOf(HistoryTestSupport.entry(day)),
            tangos = listOf(HistoryTestSupport.tangoEntry(day)),
            key = HistoryKey(TANGO.value),
        )

        awaitRow(ZIP)
        awaitRow(TANGO)
        awaitTag("history.stats")
        assertNoTag("history.tab.all")
        assertNoTag("history.tab.${TANGO.value}")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w800dp-h1000dp") // two-pane is the expanded policy (07)
    fun `two-pane shows the detail hint until a day is selected`() {
        setContent(listOf(HistoryTestSupport.entry(day)), layout = HistoryLayout.TwoPane)

        awaitTag("history.detail_hint")
        openRow(ZIP)
    }

    @Test
    fun `an archived row carries the archive tag and its detail the streak note`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val past = day.minusDays(30)
        setContent(
            zips = listOf(HistoryTestSupport.entry(past, fetchedAtMs = fetchedAtMs(day)).copy(archived = true)),
            records = listOf(HistoryTestSupport.record(past)),
        )

        awaitRow(ZIP, past)
        awaitTag("history.archived.zip/$past")
        openRow(ZIP, past)
        awaitTag("history.detail.archived")
        composeRule.onNodeWithTag("history.detail.archived", useUnmergedTree = true)
            .assertTextEquals("Opened Nov 3 · doesn't count toward your streak")
    }

    @Test
    fun `a same-day row shows neither the archive tag nor the note`() {
        setContent(
            zips = listOf(HistoryTestSupport.entry(day, fetchedAtMs = fetchedAtMs(day))),
            records = listOf(HistoryTestSupport.record(day)),
        )

        awaitRow(ZIP)
        assertNoTag("history.archived.zip/$day")
        openRow(ZIP)
        assertNoTag("history.detail.archived")
    }

    private companion object {
        const val LIST = "history.list"
    }
}
