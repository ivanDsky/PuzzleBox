package com.ivandsky.puzzlebox.play

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.game.today
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.play.di.playModule
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import org.junit.After
import org.junit.Assert.assertEquals
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
 * The play route over stand-in games (feature:play:impl never sees a real game): the picker
 * is the fresh-entry state (A14), picking starts that game in place through its
 * [com.ivandsky.puzzlebox.gameui.GamePlayUi], a nav-carried [SessionBlob] restores
 * through the named game, and back releases the play. The same paths over the real
 * games live in the app's RealGamesPlayRouteTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayRouteTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val alpha = TestGames.alpha
    private val beta = TestGames.beta
    private val day = TestGames.definition(alpha).cadence.today()
    private val fakes = FakeGames(listOf(alpha, beta))

    private fun freshDaily() = FakeDailyRepository().apply {
        setToday(alpha, DailyToday.Fresh(TestGames.puzzle(alpha)))
        setToday(beta, DailyToday.Fresh(TestGames.puzzle(beta)))
        setHistory(alpha, listOf(DailyEntry(alpha, day, 7, "alpha/v1", 0L)))
    }

    private var koin: KoinApplication? = null

    private fun koinWith(
        daily: DailyRepository = freshDaily(),
        completions: CompletionRepository = FakeCompletionRepository(),
    ): KoinApplication {
        stopKoin() // tests share the JVM-global Koin context
        return startKoin {
            modules(
                module {
                    single { daily }
                    single { completions }
                    single<GameRegistry> { fakes.games }
                    single<GamePlayRegistry> { fakes.registry }
                },
                playModule,
            )
        }.also { koin = it }
    }

    @After
    fun tearDown() {
        koin?.close()
    }

    private var handedOff: List<Pair<Long, Int>> = emptyList()

    private fun setContent(restoredSession: ByteArray? = null, onBack: () -> Unit = {}) {
        handedOff = emptyList()
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                PlayRoute(
                    layout = PlayLayout.SinglePane,
                    onBack = onBack,
                    onSolved = { timeMs, hintsUsed -> handedOff = handedOff + (timeMs to hintsUsed) },
                    restoredSession = restoredSession,
                )
            }
        }
    }

    private fun store() = koin!!.koin.get<PlaySessionStore>()

    @Test
    fun `fresh entry shows the picker with every registered game`() {
        koinWith()
        setContent()
        composeRule.onNodeWithTag("play.picker").assertExists()
        composeRule.onNodeWithText("Pick a game").assertExists()
        composeRule.onNodeWithTag("play.picker_alpha").assertExists()
        composeRule.onNodeWithTag("play.picker_beta").assertExists()
        composeRule.onNodeWithText("Alpha").assertExists()
        composeRule.onNodeWithText("Beta").assertExists()
    }

    @Test
    fun `picking a game starts its screen in place and loads today`() {
        val daily = freshDaily()
        koinWith(daily)
        setContent()
        composeRule.onNodeWithTag("play.picker_alpha").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("alpha board 0").assertExists()
        composeRule.onNodeWithTag("play.picker").assertDoesNotExist()
        assertEquals(1, daily.todayCalls(alpha))
        assertEquals(TestGames.Ref(alpha, day, 7), fakes[alpha].latest.ref)
    }

    @Test
    fun `an unavailable board retries through the screen`() {
        val daily = freshDaily().apply { setToday(alpha, DailyToday.Unavailable(FetchError.Unavailable("offline"))) }
        koinWith(daily)
        setContent()
        composeRule.onNodeWithTag("play.picker_alpha").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("play.error").assertExists()

        daily.setToday(alpha, DailyToday.Fresh(TestGames.puzzle(alpha)))
        composeRule.onNodeWithText("Try again").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("alpha board 0").assertExists()
        assertEquals(2, daily.todayCalls(alpha))
    }

    @Test
    fun `a replay blob starts its game and restores the archived session without a fetch`() {
        val daily = freshDaily().apply { setToday(beta, DailyToday.Unavailable(FetchError.Throttled())) }
        koinWith(daily)
        val blob = FakeController.envelope(beta, moves = 3, complete = true, day = day.minusDays(30), number = 12)
        setContent(restoredSession = SessionBlob.encode(blob.game, blob.session))
        composeRule.waitForIdle()

        // Restored COMPLETED session → the win handoff fires into navigation once and
        // the archive was NEVER re-fetched (the blob carried the session).
        composeRule.onNodeWithText("beta board 3").assertExists()
        composeRule.onNodeWithTag("play.picker").assertDoesNotExist()
        assertEquals(1, handedOff.size)
        assertEquals(0, daily.todayCalls(beta))
    }

    @Test
    fun `the route redraws the live controller's moves`() {
        koinWith()
        setContent()
        composeRule.onNodeWithTag("play.picker_alpha").performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle { fakes[alpha].latest.move() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("alpha board 1").assertExists()
        assertTrue(store().active.value?.gameId == alpha)
    }

    @Test
    fun `back from a mid-game play releases the play to the picker`() {
        // Zip-shaped game (the original route test's game): no session is published
        // before the first move, so this still-composed test route has nothing to
        // re-adopt after back (production pops the route).
        fakes[alpha].publishesAdoptedSession = false
        koinWith()
        var back = 0
        setContent(onBack = { back++ })
        composeRule.onNodeWithTag("play.picker_alpha").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("alpha board 0").assertExists()

        // Device/system back through PlayRoute's BackHandler: the route pops AND the
        // play releases, so the next play entry picks again (A14).
        (composeRule as AndroidComposeTestRule<*, *>).activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()
        assertEquals(1, back)
        composeRule.onNodeWithTag("play.picker").assertExists()
        assertEquals(null, store().active.value)
    }
}
