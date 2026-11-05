package com.ivandsky.puzzlebox.play

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.GetReplaySession
import com.ivandsky.puzzlebox.domain.ObservePlayProgress
import com.ivandsky.puzzlebox.game.GameRegistry
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
 * with a save for today reads "In progress" (A13, SC11).
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

    private var koin: KoinApplication? = null

    @Before
    fun setUp() {
        stopKoin() // tests share the JVM-global Koin context
        koin = startKoin {
            modules(
                commonModule,
                module {
                    single<DailyRepository> { daily }
                    single<CompletionRepository> { FakeCompletionRepository() }
                    single<GameRegistry> { fakes.games }
                    single<GamePlayRegistry> { fakes.registry }
                    single<SavedPlayRepository> { saved }
                    single<GameCalendar> { FakeGameCalendar(today) }
                    factory { GetReplaySession(get(), get()) }
                    factory { ObservePlayProgress(get(), get(), get(), get()) }
                },
                playModule,
            )
        }
    }

    @After
    fun tearDown() {
        koin?.close()
    }

    private fun setContent(onPick: (GameId) -> Unit) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                HubRoute(onPick = onPick)
            }
        }
    }

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
    fun `a game saved today reads in progress and the others do not`() {
        runBlocking { saved.save(PlaySlot(zip, today), byteArrayOf(1)) }
        setContent {}

        composeRule.waitUntil { composeRule.onAllNodesWithText("In progress").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("hub.row.zip").assert(hasText("In progress"))
        composeRule.onNodeWithTag("hub.row.alpha").assert(hasText("In progress").not())
        assertEquals(1, composeRule.onAllNodesWithText("In progress").fetchSemanticsNodes().size)
    }
}
