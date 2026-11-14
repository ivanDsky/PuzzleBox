package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.cloud.CloudSync
import com.ivandsky.puzzlebox.testing.cloud.FakeCloudSync
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.FinishLeg
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.today
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.di.playModule
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import java.time.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
 * The GameKey route over stand-in games: a key draws its slot's live board under the
 * `route.game` tag, Up and system back both reach the entry's back, and two entries
 * naming the same slot draw one shared controller.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameRouteTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val alpha = TestGames.alpha
    private val day = TestGames.definition(alpha).cadence.today()
    private val key = GameKey(alpha.value)
    private val fakes = FakeGames(listOf(alpha))
    private val daily = FakeDailyRepository().apply {
        setToday(alpha, DailyToday.Fresh(TestGames.puzzle(alpha)))
        setHistory(alpha, listOf(DailyEntry(alpha, day, 7, "alpha/v1", 0L)))
    }
    private val calendar = FakeGameCalendar(day)

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
                    single<SavedPlayRepository> { FakeSavedPlayRepository() }
                    single<ReceivedPuzzleRepository> { FakeReceivedPuzzleRepository() }
                    single<SharedPuzzleRepository> { FakeSharedPuzzleRepository() }
                    single<MatchRepository> { FakeMatchRepository() }
                    factory { FinishLeg(FakeMatchResultOutbox(), FakeIdentityService()) { Instant.EPOCH } }
                    single<GameCalendar> { calendar }
                    single<UserPreferencesRepository> { FakeUserPreferencesRepository() }
                    factory { LoadPastDay(get(), get()) }
                },
                module { single<CloudSync> { FakeCloudSync() } },
                playModule,
                hubOffFriendDay,
            )
        }
    }

    @After
    fun tearDown() {
        koin?.close()
    }

    private fun store() = koin!!.koin.get<PlaySessionStore>()

    @Test
    fun `a game key renders its board tagged route game`() {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                GameRoute(key = key, layout = PlayLayout.SinglePane, onBack = {}, onSolved = { _, _, _ -> })
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("route.game").assertExists()
        composeRule.onNodeWithText("alpha board 0").assertExists()
        assertNotNull(store().peek(PlaySlot.Daily(alpha, day)))
    }

    @Test
    fun `up and system back both invoke the entry's back`() {
        val stack = mutableStateListOf<NavKey>(RootKey, key)
        var backs = 0
        val back = {
            backs++
            stack.removeAt(stack.lastIndex)
        }
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                NavDisplay(
                    backStack = stack,
                    onBack = { back() },
                    entryProvider = entryProvider {
                        entry<RootKey> { Text("root") }
                        entry<GameKey> { k ->
                            GameRoute(key = k, layout = PlayLayout.SinglePane, onBack = { back() }, onSolved = { _, _, _ -> })
                        }
                    },
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("game.up").performClick()
        composeRule.waitForIdle()
        assertEquals(1, backs)
        composeRule.onNodeWithText("root").assertExists()

        composeRule.runOnIdle { stack.add(key) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("route.game").assertExists()
        (composeRule as AndroidComposeTestRule<*, *>).activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()
        assertEquals(2, backs)
        composeRule.onNodeWithText("root").assertExists()
    }

    @Test
    fun `two entries with the same key share one controller`() {
        val owners = List(2) { EntryOwner() }
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                owners.forEach { owner ->
                    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                        GameRoute(key = key, layout = PlayLayout.SinglePane, onBack = {}, onSolved = { _, _, _ -> })
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("alpha board 0").assertCountEquals(2)

        composeRule.runOnIdle { fakes[alpha].latest.move() }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("alpha board 1").assertCountEquals(2)
        assertEquals("one slot, one fetch", 1, daily.todayCalls(alpha))
    }

    @Test
    fun `a today board kept across midnight is labelled yesterday's, a dated one never`() {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Column {
                    GameRoute(key = key, layout = PlayLayout.SinglePane, onBack = {}, onSolved = { _, _, _ -> })
                    GameRoute(key = GameKey(alpha.value, "$day"), layout = PlayLayout.SinglePane, onBack = {}, onSolved = { _, _, _ -> })
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("game.past_day").assertDoesNotExist()

        composeRule.runOnIdle { calendar.setAll(day.plusDays(1)) }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag("game.past_day").assertCountEquals(1)
        composeRule.onNodeWithText("Yesterday's puzzle").assertExists()
    }

    private data object RootKey : NavKey

    /** A separate entry's ViewModel store, as Nav3's per-entry decorator gives each entry. */
    private class EntryOwner : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }
}
