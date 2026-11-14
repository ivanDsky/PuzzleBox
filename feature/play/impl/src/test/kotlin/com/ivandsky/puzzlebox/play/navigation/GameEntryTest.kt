package com.ivandsky.puzzlebox.play.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation3.runtime.NavKey
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
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.today
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.FakeController
import com.ivandsky.puzzlebox.play.FakeGames
import com.ivandsky.puzzlebox.play.OpenDecision
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import com.ivandsky.puzzlebox.play.di.playModule
import com.ivandsky.puzzlebox.play.hubOffFriendDay
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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
 * [GameEntry]'s three branches over the real [PlaySessionStore] and stand-in games, hosted
 * on a real [Navigator] whose top key is drawn the way the app's entry provider would: a
 * [GameKey] through [GameEntry], a [ResultKey] as a result marker. A finished day opens its
 * result without the board ever composing, and without waiting on a records read; a cold
 * store shows the loading frame and then the result (the accepted double transition); an
 * unsolved day opens its board.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameEntryTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val alpha = TestGames.alpha
    private val day = TestGames.definition(alpha).cadence.today()
    private val key = GameKey(alpha.value, day.toString())
    private val fakes = FakeGames(listOf(alpha))
    private val daily = FakeDailyRepository().apply {
        setToday(alpha, DailyToday.Fresh(TestGames.puzzle(alpha)))
        setHistory(alpha, listOf(DailyEntry(alpha, day, 7, "alpha/v1", 0L)))
    }
    private val calendar = FakeGameCalendar(day)
    private val navigator = Navigator(NavigationState(RootKey, listOf(listOf(RootKey, key))))

    /** The records flow, held back until a test emits: a store whose first read has not landed. */
    private val records = MutableSharedFlow<List<CompletionRecord>>(replay = 1)
    private val heldBack = object : CompletionRepository by FakeCompletionRepository() {
        override fun observeRecords(): Flow<List<CompletionRecord>> = records
    }

    private var koin: KoinApplication? = null

    @Before
    fun setUp() {
        stopKoin() // tests share the JVM-global Koin context
        koin = startKoin {
            modules(
                commonModule,
                module {
                    single<DailyRepository> { daily }
                    single<CompletionRepository> { heldBack }
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

    private fun setEntryContent() {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) { Host() }
        }
    }

    /** The app's entry provider in miniature: the stack's top key picks what is drawn. */
    @Composable
    private fun Host() {
        when (val top = navigator.state.currentStack.last()) {
            is GameKey -> GameEntry(top, PlayLayout.SinglePane, navigator)
            is ResultKey -> Text("result ${top.day} ${top.timeMs}", Modifier.testTag("result"))
            else -> Text("root")
        }
    }

    private fun solved(time: Long, hints: Int) = CompletionRecord(
        TestGames.Ref(alpha, PuzzleKey.Daily(day, 7)),
        time,
        hints,
        RecordStats(1, "{}"),
        isRecord = true,
    )

    @Test
    fun `a finished day opens its result at frame 0 without the board or a records read`() {
        val store = store()
        store.open(key)
        composeRule.waitForIdle()
        composeRule.runOnIdle { fakes[alpha].latest.win() }
        composeRule.waitForIdle()
        val result = ResultKey(alpha.value, day.toString(), FakeController.TIME_MS, 0)
        assertEquals("the live solve decides with no records read", OpenDecision.Finished(result), store.decide(key))

        composeRule.mainClock.autoAdvance = false
        setEntryContent()
        composeRule.onNodeWithTag("fake.board").assertDoesNotExist()
        composeRule.onNodeWithTag("result").assertDoesNotExist()

        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertEquals("the key is replaced by its result", listOf(RootKey, result), navigator.state.currentStack)
        composeRule.onNodeWithTag("result").assertExists()
        composeRule.onNodeWithTag("fake.board").assertDoesNotExist()
        composeRule.onNodeWithTag("game.loading").assertDoesNotExist()
    }

    @Test
    fun `a cold store shows the loading frame, then the result of a solved day`() {
        setEntryContent()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("game.loading").assertExists()
        composeRule.onNodeWithTag("fake.board").assertDoesNotExist()
        assertEquals("nothing is replaced before the read lands", listOf(RootKey, key), navigator.state.currentStack)

        composeRule.runOnIdle { records.tryEmit(listOf(solved(time = 61_000L, hints = 2))) }
        composeRule.waitForIdle()

        val result = ResultKey(alpha.value, day.toString(), 61_000L, 2)
        assertEquals(listOf(RootKey, result), navigator.state.currentStack)
        composeRule.onNodeWithTag("result").assertExists()
        composeRule.onNodeWithTag("fake.board").assertDoesNotExist()
        composeRule.onNodeWithTag("game.loading").assertDoesNotExist()
    }

    @Test
    fun `an unsolved day opens its board`() {
        records.tryEmit(emptyList())
        setEntryContent()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("fake.board").assertExists()
        composeRule.onNodeWithTag("game.loading").assertDoesNotExist()
        composeRule.onNodeWithTag("result").assertDoesNotExist()
        assertEquals(listOf(RootKey, key), navigator.state.currentStack)
    }

    private data object RootKey : NavKey
}
