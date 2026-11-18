package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.cloud.CloudSync
import com.ivandsky.puzzlebox.testing.cloud.FakeCloudSync
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.key
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.repository.DailyRepository

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
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.api.CustomGameKey
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
import com.ivandsky.puzzlebox.testing.repository.FakeSelfMatchRepository
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
 * Concrete tests pick the seen-read behaviour they need through [start]. [custom] runs the same
 * fixtures over a friend's puzzle.
 */
abstract class GameTutorialFixture(
    /** Opens the key as a friend's puzzle ([CustomGameKey], [CustomGameViewModel]) instead of a daily. */
    private val custom: Boolean = false,
) {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    protected val alpha = TestGames.alpha
    protected val beta = TestGames.beta
    protected val day = TestGames.definition(alpha).cadence.today()
    protected val fakes = FakeGames(listOf(alpha, beta))
    private val daily = FakeDailyRepository().apply {
        setToday(alpha, TestGames.puzzle(alpha))
        setToday(beta, TestGames.puzzle(beta))
        setHistory(alpha, listOf(DailyEntry(alpha, day, 7, "alpha/v1", 0L)))
    }
    private val received = FakeReceivedPuzzleRepository()
    private val code = checkNotNull(ShareCode.parse("K7Q2MX"))
    private val completions = FakeCompletionRepository()
    protected val saved = FakeSavedPlayRepository()
    protected var solved = 0
    protected var backs = 0
    protected val entry = mutableIntStateOf(0)

    /** What the stand-in rules lookup returns; null (a game with no rules) by default, so How to play opens the tutorial. */
    internal var rulesCopy: RulesSheetCopy? = null

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
                    single<ReceivedPuzzleRepository> { received }
                    single<SharedPuzzleRepository> { FakeSharedPuzzleRepository() }
                    single<MatchRepository> { FakeMatchRepository() }
                    factory { FinishLeg(FakeMatchResultOutbox(), FakeSelfMatchRepository(), FakeIdentityService()) { Instant.EPOCH } }
                    single<GameCalendar> { FakeGameCalendar(day) }
                    single<UserPreferencesRepository> { prefs }
                    factory { LoadPastDay(get(), get()) }
                },
                module { single<CloudSync> { FakeCloudSync() } },
                playModule,
                hubOffFriendDay,
                module { single { HowToPlayRules { rulesCopy } } },
            )
        }
        if (custom) runBlocking { received.upsert(friendPuzzle(game)) }
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                // A new key is a new entry owner's composition; the VM store stays the test's.
                key(entry.intValue) {
                    if (custom) {
                        GameRoute(
                            key = CustomGameKey(game.value, code.value),
                            layout = PlayLayout.SinglePane,
                            onBack = { backs++ },
                            onSolved = { _, _, _ -> solved++ },
                        )
                    } else {
                        GameRoute(
                            key = GameKey(game.value),
                            layout = PlayLayout.SinglePane,
                            onBack = { backs++ },
                            onSolved = { _, _, _ -> solved++ },
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    protected fun live() = koin!!.koin.get<PlaySessionStore>()
        .peek(if (custom) PlaySlot.Custom(alpha, code.value) else PlaySlot.Daily(alpha, day))!!

    private fun friendPuzzle(game: GameId) = ReceivedPuzzle(
        code = code,
        gameId = game,
        variant = TestGames.variant(game),
        sharedFormatVersion = 1,
        puzzleBytes = TestGames.puzzle(game).payload,
        title = "Castle",
        authorName = "Ivan",
        note = "",
        authorTimeMs = 102_000L,
        authorHints = 0,
        authorUid = "author",
        openedAt = Instant.parse("2026-11-04T09:00:00Z"),
        unpublished = false,
        postState = PostState.Pending,
        postAttempts = 0,
        difficulty = null,
    )

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
