package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.key
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
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
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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
 * The GameKey route's tutorial (spec A11) over stand-in games: a game with unseen lessons
 * plays them before its board, finishing or skipping marks it seen, a seen game or one
 * without lessons shows the board at once, and How to play reopens the lessons over the
 * live play with its clock folded until the board returns. Lessons run in a sandbox:
 * nothing reaches the slot's play, its save or the records. Back leaves them unseen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameTutorialTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val alpha = TestGames.alpha
    private val beta = TestGames.beta
    private val day = TestGames.definition(alpha).cadence.today()
    private val fakes = FakeGames(listOf(alpha, beta))
    private val daily = FakeDailyRepository().apply {
        setToday(alpha, DailyToday.Fresh(TestGames.puzzle(alpha)))
        setToday(beta, DailyToday.Fresh(TestGames.puzzle(beta)))
        setHistory(alpha, listOf(DailyEntry(alpha, day, 7, "alpha/v1", 0L)))
    }
    private val completions = FakeCompletionRepository()
    private val saved = FakeSavedPlayRepository()
    private var solved = 0
    private var backs = 0
    private val entry = mutableIntStateOf(0)

    private var koin: KoinApplication? = null

    @After
    fun tearDown() {
        koin?.close()
    }

    private fun start(prefs: UserPreferencesRepository, game: GameId = alpha) {
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
                    single<GameCalendar> { FakeGameCalendar(day) }
                    single<UserPreferencesRepository> { prefs }
                    factory { LoadPastDay(get(), get()) }
                },
                playModule,
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

    private fun live() = koin!!.koin.get<PlaySessionStore>().peek(PlaySlot(alpha, day))!!

    private fun records() = runBlocking { completions.observeRecords().first() }

    /** Wins the open lesson and waits out its success beat. */
    private fun winLesson() {
        composeRule.runOnIdle { fakes[alpha].latest.win() }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(PAST_THE_BEAT_MS)
        composeRule.waitForIdle()
    }

    private fun click(tag: String) {
        composeRule.onNodeWithTag(tag).performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `a first play runs the lessons, then the board, and marks the game seen`() {
        fakes[alpha].teach(2)
        val prefs = FakeUserPreferencesRepository()
        start(prefs)

        composeRule.onNodeWithTag("tutorial.host").assertExists()
        composeRule.onNodeWithTag("play.how_to_play").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Step 1 of 2").assertExists()

        winLesson()
        composeRule.onNodeWithContentDescription("Step 2 of 2").assertExists()
        winLesson()
        composeRule.onNodeWithTag("tutorial.ready").assertExists()
        assertTrue(prefs.seen.isEmpty())

        click("tutorial.finish")
        composeRule.onNodeWithTag("tutorial.host").assertDoesNotExist()
        composeRule.onNodeWithText("alpha board 0").assertExists()
        assertEquals(setOf(alpha), prefs.seen)
        // The lessons' wins were the sandbox's: no record, no handoff, the slot's play untouched.
        assertTrue(records().isEmpty())
        assertEquals(0, solved)
        assertEquals(0, (live().controller as FakeController).moves)
    }

    @Test
    fun `skip marks the game seen and shows its board`() {
        fakes[alpha].teach(3)
        val prefs = FakeUserPreferencesRepository()
        start(prefs)

        click("tutorial.skip")

        composeRule.onNodeWithTag("tutorial.host").assertDoesNotExist()
        composeRule.onNodeWithText("alpha board 0").assertExists()
        assertEquals(setOf(alpha), prefs.seen)
    }

    @Test
    fun `a seen game shows its board at once and offers how to play`() {
        fakes[alpha].teach(2)
        start(FakeUserPreferencesRepository(seen = setOf(alpha)))

        composeRule.onNodeWithTag("tutorial.host").assertDoesNotExist()
        composeRule.onNodeWithText("alpha board 0").assertExists()
        composeRule.onNodeWithTag("play.how_to_play").assertExists()
    }

    @Test
    fun `a game without lessons shows its board and offers no how to play`() {
        start(FakeUserPreferencesRepository(), game = beta)

        composeRule.onNodeWithText("beta board 0").assertExists()
        composeRule.onNodeWithTag("tutorial.host").assertDoesNotExist()
        composeRule.onNodeWithTag("play.how_to_play").assertDoesNotExist()
    }

    @Test
    fun `how to play folds the live clock, keeps the play and resumes where it was`() {
        fakes[alpha].teach(2)
        start(FakeUserPreferencesRepository(seen = setOf(alpha)))
        val controller = fakes[alpha].latest
        composeRule.runOnIdle { controller.move() }
        composeRule.waitForIdle()
        composeRule.runOnIdle { controller.advance(5_000L) }

        click("play.how_to_play")
        composeRule.onNodeWithTag("tutorial.host").assertExists()
        assertNull(controller.clockWindow)
        composeRule.runOnIdle { controller.advance(10_000L) }
        assertEquals(5_000L, controller.displayedElapsedMs())

        winLesson()
        click("tutorial.skip")

        composeRule.onNodeWithText("alpha board 1").assertExists()
        assertSame(controller, live().controller)
        assertNotNull(controller.clockWindow)
        composeRule.runOnIdle { controller.advance(1_000L) }
        assertEquals(6_000L, controller.displayedElapsedMs())
        assertTrue(records().isEmpty())
        assertEquals(0, solved)
    }

    @Test
    fun `a recomposed entry keeps the open lesson step`() {
        fakes[alpha].teach(3)
        start(FakeUserPreferencesRepository())
        winLesson()
        composeRule.onNodeWithContentDescription("Step 2 of 3").assertExists()

        composeRule.runOnIdle { entry.intValue++ }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Step 2 of 3").assertExists()
    }

    @Test
    fun `up during the first-play lessons leaves the game with them unseen`() {
        fakes[alpha].teach(2)
        val prefs = FakeUserPreferencesRepository()
        start(prefs)

        click("game.up")

        assertEquals(1, backs)
        assertTrue(prefs.seen.isEmpty())
    }

    private companion object {
        /** Longer than the tutorial's success beat between lessons. */
        const val PAST_THE_BEAT_MS = 1_000L
    }
}
