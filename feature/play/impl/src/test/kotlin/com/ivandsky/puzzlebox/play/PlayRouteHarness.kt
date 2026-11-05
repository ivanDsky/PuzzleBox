package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.data.repository.KeptSession
import com.ivandsky.puzzlebox.data.repository.KeptSessionRepository
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.game.today
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.di.playModule
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeKeptSessionRepository
import org.junit.After
import org.junit.Rule
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/**
 * The play route over stand-in games (feature:play:impl never sees a real game), shared by
 * [PlayRouteTest] (entering: picker, start, restore) and [PlayRouteExitTest] (leaving:
 * back, a new entry, disposal). The same paths over the real games live in the app's
 * RealGamesPlayRouteTest.
 */
abstract class PlayRouteHarness {

    @get:Rule
    val composeRule = createComposeRule()

    protected val alpha = TestGames.alpha
    protected val beta = TestGames.beta
    protected val day = TestGames.definition(alpha).cadence.today()
    protected val fakes = FakeGames(listOf(alpha, beta))

    protected fun freshDaily() = FakeDailyRepository().apply {
        setToday(alpha, DailyToday.Fresh(TestGames.puzzle(alpha)))
        setToday(beta, DailyToday.Fresh(TestGames.puzzle(beta)))
        setHistory(alpha, listOf(DailyEntry(alpha, day, 7, "alpha/v1", 0L)))
    }

    protected var koin: KoinApplication? = null

    protected fun koinWith(
        daily: DailyRepository = freshDaily(),
        completions: CompletionRepository = FakeCompletionRepository(),
        kept: KeptSessionRepository = FakeKeptSessionRepository(),
    ): KoinApplication {
        stopKoin() // tests share the JVM-global Koin context
        return startKoin {
            modules(
                module {
                    single { daily }
                    single { completions }
                    single { kept }
                    single<GameRegistry> { fakes.games }
                    single<GamePlayRegistry> { fakes.registry }
                    factory { ObserveTodaySet(games = get(), completions = get(), kept = get()) }
                },
                playModule,
            )
        }.also { koin = it }
    }

    @After
    fun tearDown() {
        koin?.close()
    }

    protected var handedOff: List<Pair<Long, Int>> = emptyList()

    // A new nav entry of the play route: navigation composes it afresh (new remember and
    // saveable state) over the same activity-scoped view model.
    protected var entry by mutableIntStateOf(0)

    // false = the route left composition with the activity still resumed (a tab switch).
    protected var shown by mutableStateOf(true)

    protected fun setContent(restoredSession: ByteArray? = null, onBack: () -> Unit = {}) {
        handedOff = emptyList()
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                if (shown) {
                    key(entry) {
                        PlayRoute(
                            layout = PlayLayout.SinglePane,
                            onBack = onBack,
                            onSolved = { timeMs, hintsUsed -> handedOff = handedOff + (timeMs to hintsUsed) },
                            restoredSession = restoredSession,
                        )
                    }
                }
            }
        }
    }

    protected fun store() = koin!!.koin.get<PlaySessionStore>()

    protected fun pressBack() {
        (composeRule as AndroidComposeTestRule<*, *>).activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()
    }

    protected fun pick(game: GameId) {
        composeRule.onNodeWithTag("play.picker_${game.value}").performClick()
        composeRule.waitForIdle()
    }

    protected fun keptSession(game: GameId, moves: Int, elapsedMs: Long): KeptSession {
        val session = FakeController.envelope(game, moves = moves, day = day, elapsedMs = elapsedMs)
        return KeptSession(game, day, elapsedMs, SessionBlob.encode(game, session.session))
    }
}
