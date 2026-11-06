package com.ivandsky.puzzlebox.play

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.play.di.playModule
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Play tab's games hub over stand-in games: one row per playable game in registry
 * order under `route.hub`, picking a row reports that game without fetching, and a game
 * reads today's status and what a tap does: a save "In progress · m:ss" / Continue, a solve
 * "Solved · m:ss ✓" / Replay, otherwise "Not started" / Play (A13, A14, SC11). A game with
 * an archive offers "Past puzzles": a date picker bounded to its range whose Open reports
 * the picked day, with no daily read on the way (A22).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HubRouteTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val zip = GameId("zip")
    private val alpha = TestGames.alpha
    private val fakes = FakeGames(listOf(zip, alpha))
    private val daily = FakeDailyRepository()
    private val today = LocalDate.parse("2026-11-04")
    private val saved = FakeSavedPlayRepository()
    private val completions = FakeCompletionRepository()

    private var koin: KoinApplication? = null

    @Before
    fun setUp() {
        stopKoin() // tests share the JVM-global Koin context
        koin = startKoin {
            modules(
                commonModule,
                module {
                    single<DailyRepository> { daily }
                    single<CompletionRepository> { completions }
                    single<GameRegistry> { fakes.games }
                    single<GamePlayRegistry> { fakes.registry }
                    single<SavedPlayRepository> { saved }
                    single<GameCalendar> { FakeGameCalendar(today) }
                    factory { LoadPastDay(get(), get()) }
                    factory { ObserveTodaySet(get(), get(), get(), get()) }
                },
                playModule,
            )
        }
    }

    @After
    fun tearDown() {
        koin?.close()
    }

    private fun setContent(
        onPickDay: (GameId, LocalDate) -> Unit = { _, _ -> },
        onPick: (GameId) -> Unit,
    ) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                HubRoute(onPick = onPick, onPickDay = onPickDay)
            }
        }
    }

    /** A picker day cell: its merged text is the full date ("Monday, November 2, 2026"). */
    private fun day(date: String) = composeRule.onNode(
        hasText(date, substring = true) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
    )

    @Test
    fun `the hub lists every playable game`() {
        setContent {}

        composeRule.onNodeWithTag("route.hub").assertExists()
        composeRule.onNodeWithTag("hub.row.zip").assertExists()
        composeRule.onNodeWithTag("hub.row.alpha").assertExists()
        composeRule.onNodeWithText("Zip").assertExists()
        composeRule.onNodeWithText("Alpha").assertExists()
    }

    @Test
    fun `picking zip reports zip and fetches nothing`() {
        val picked = ArrayList<GameId>()
        setContent { picked.add(it) }

        composeRule.onNodeWithTag("hub.row.zip").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(GameId("zip")), picked)
        assertEquals(0, daily.todayCalls(zip))
    }

    @Test
    fun `a game saved today reads in progress with its clock and continue`() {
        runBlocking { saved.save(PlaySlot(zip, today), byteArrayOf(1), elapsedMs = 42_000L) }
        setContent {}

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("hub.status.zip", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("hub.status.zip", useUnmergedTree = true).assertTextEquals("In progress · 0:42")
        composeRule.onNodeWithTag("hub.action.zip", useUnmergedTree = true).assertTextEquals("Continue")
        composeRule.onNodeWithTag("hub.status.alpha", useUnmergedTree = true).assertTextEquals("Not started")
        composeRule.onNodeWithTag("hub.action.alpha", useUnmergedTree = true).assertTextEquals("Play")
    }

    @Test
    fun `a game solved today reads solved with its time and replay`() {
        runBlocking {
            completions.onCompleted(
                CompletionRecord(TestGames.Ref(zip, today, 7), time = 36_000L, hints = 0, stats = RecordStats(1, "{}")),
            )
        }
        setContent {}

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Solved · 0:36 ✓", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("hub.status.zip", useUnmergedTree = true).assertTextEquals("Solved · 0:36 ✓")
        composeRule.onNodeWithTag("hub.action.zip", useUnmergedTree = true).assertTextEquals("Replay")
    }

    @Test
    fun `past puzzles shows only on a game with an archive`() {
        daily.setArchiveRange(alpha, LocalDate.parse("2026-10-01")..today)
        setContent {}

        composeRule.onNodeWithTag("hub.past.alpha").assertExists()
        composeRule.onNodeWithTag("hub.past.alpha").assert(hasContentDescription("Past Alpha puzzles"))
        composeRule.onNodeWithTag("hub.past.zip").assertDoesNotExist()
        composeRule.onAllNodesWithText("Past puzzles").assertCountEquals(1)
    }

    @Test
    fun `the picker allows only the archive range, opens the picked day and fetches nothing`() {
        daily.setArchiveRange(alpha, LocalDate.parse("2026-11-01")..today)
        val picked = ArrayList<Pair<GameId, LocalDate>>()
        val rowTaps = ArrayList<GameId>()
        setContent(onPickDay = { game, day -> picked.add(game to day) }) { rowTaps.add(it) }

        composeRule.onNodeWithTag("hub.past.alpha").performClick()
        composeRule.waitForIdle()

        day("November 4, 2026").assertIsEnabled().assertIsSelected()
        day("November 1, 2026").assertIsEnabled()
        day("November 5, 2026").assertIsNotEnabled()
        day("November 30, 2026").assertIsNotEnabled()
        assertEquals(0, daily.todayCalls(alpha))
        assertEquals(0, daily.dayCalls(alpha, LocalDate.parse("2026-11-02")))

        // A semantic tap (TalkBack's): under Robolectric an injected touch does not select a day cell.
        day("November 2, 2026").performSemanticsAction(SemanticsActions.OnClick)
        day("November 2, 2026").assertIsSelected()
        composeRule.onNodeWithText("Open").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(alpha to LocalDate.parse("2026-11-02")), picked)
        assertEquals(emptyList<GameId>(), rowTaps)
        assertEquals(0, daily.dayCalls(alpha, LocalDate.parse("2026-11-02")))
        composeRule.onNodeWithText("Open").assertDoesNotExist()
    }

    @Test
    fun `cancel closes the picker without opening a day`() {
        daily.setArchiveRange(alpha, LocalDate.parse("2026-11-01")..today)
        val picked = ArrayList<Pair<GameId, LocalDate>>()
        setContent(onPickDay = { game, day -> picked.add(game to day) }) {}

        composeRule.onNodeWithTag("hub.past.alpha").performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Open").assertDoesNotExist()
        assertEquals(emptyList<Pair<GameId, LocalDate>>(), picked)
    }
}
