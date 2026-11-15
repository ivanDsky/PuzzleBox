package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.cloud.CloudSync
import com.ivandsky.puzzlebox.testing.cloud.FakeCloudSync
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.key
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
import com.ivandsky.puzzlebox.model.GameId
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
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/**
 * The GameKey route's tutorial harness: the route over stand-in games, Koin wired per test.
 * Concrete tests pick the seen-read behaviour they need through [start].
 */
abstract class GameTutorialFixture {

    @get:Rule
    val composeRule = createComposeRule()

    protected val alpha = TestGames.alpha
    protected val beta = TestGames.beta
    private val day = TestGames.definition(alpha).cadence.today()
    protected val fakes = FakeGames(listOf(alpha, beta))
    private val daily = FakeDailyRepository().apply {
        setToday(alpha, DailyToday.Fresh(TestGames.puzzle(alpha)))
        setToday(beta, DailyToday.Fresh(TestGames.puzzle(beta)))
        setHistory(alpha, listOf(DailyEntry(alpha, day, 7, "alpha/v1", 0L)))
    }
    private val completions = FakeCompletionRepository()
    private val saved = FakeSavedPlayRepository()
    protected var solved = 0
    protected var backs = 0
    protected val entry = mutableIntStateOf(0)

    private var koin: KoinApplication? = null

    @After
    fun tearDown() {
        koin?.close()
    }

    protected fun start(prefs: UserPreferencesRepository, game: GameId = alpha) {
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
                    single<ReceivedPuzzleRepository> { FakeReceivedPuzzleRepository() }
                    single<SharedPuzzleRepository> { FakeSharedPuzzleRepository() }
                    single<MatchRepository> { FakeMatchRepository() }
                    factory { FinishLeg(FakeMatchResultOutbox(), FakeIdentityService()) { Instant.EPOCH } }
                    single<GameCalendar> { FakeGameCalendar(day) }
                    single<UserPreferencesRepository> { prefs }
                    factory { LoadPastDay(get(), get()) }
                },
                module { single<CloudSync> { FakeCloudSync() } },
                playModule,
                hubOffFriendDay,
            )
        }
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                // A new key is a new entry owner's composition; the VM store stays the test's.
                key(entry.intValue) {
                    GameRoute(
                        key = GameKey(game.value),
                        layout = PlayLayout.SinglePane,
                        onBack = { backs++ },
                        onSolved = { _, _, _ -> solved++ },
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    protected fun live() = koin!!.koin.get<PlaySessionStore>().peek(PlaySlot.Daily(alpha, day))!!

    protected fun records() = runBlocking { completions.observeRecords().first() }

    /** Wins the open lesson and waits out its success beat. */
    protected fun winLesson() {
        composeRule.runOnIdle { fakes[alpha].latest.win() }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(PAST_THE_BEAT_MS)
        composeRule.waitForIdle()
    }

    protected fun click(tag: String) {
        composeRule.onNodeWithTag(tag).performClick()
        composeRule.waitForIdle()
    }

    private companion object {
        /** Longer than the tutorial's success beat between lessons. */
        const val PAST_THE_BEAT_MS = 1_000L
    }
}
