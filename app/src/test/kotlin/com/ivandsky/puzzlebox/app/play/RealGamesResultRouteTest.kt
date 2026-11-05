package com.ivandsky.puzzlebox.app.play

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.domain.GetReplaySession
import com.ivandsky.puzzlebox.domain.ObservePlayProgress
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.today
import com.ivandsky.puzzlebox.gameui.DragEvent
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.zipPlugin
import com.ivandsky.puzzlebox.games.zipui.ZipPlayController
import com.ivandsky.puzzlebox.games.zipui.ZipPlayUi
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.ResultRoute
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import com.ivandsky.puzzlebox.play.di.playModule
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Over the real Zip game: the result route reads its slot's live play — solving stamps
 * the badge in memory (fire-and-observe, never waited on) — and "Play again" hands back
 * to navigation, whose reopen of the same day resets the SAME puzzle to a fresh session.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Plain Application: the real PuzzleBoxApplication's startKoin would race this class's own graph.
@Config(sdk = [34], application = android.app.Application::class)
class RealGamesResultRouteTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `result route reads its slot's live play and play again reopens a fresh attempt`() {
        val today = ZipDefinition.cadence.today()
        val daily = freshDailyForAllGames().apply {
            setHistory(ZipDefinition.id, listOf(ZipPlayFixture.entry(today)))
        }
        stopKoin() // tests share the JVM-global Koin context
        val koin = startKoin {
            modules(
                commonModule,
                module {
                    single<DailyRepository> { daily }
                    single<CompletionRepository> { FakeCompletionRepository() }
                    single { GameRegistry(listOf(zipPlugin)) }
                    single { GamePlayRegistry(listOf(ZipPlayUi())) }
                    single<SavedPlayRepository> { FakeSavedPlayRepository() }
                    single<GameCalendar> { FakeGameCalendar(today) }
                    factory { GetReplaySession(get(), get()) }
                    factory { ObservePlayProgress(get(), get(), get(), get()) }
                },
                playModule,
            )
        }
        val store = koin.koin.get<PlaySessionStore>()
        val gameKey = GameKey(ZipDefinition.id.value, today.toString())
        fun zip() = store.peek(PlaySlot(ZipDefinition.id, today))!!.controller as ZipPlayController
        try {
            // Bring the slot to a solved play the way a play-through would.
            composeRule.runOnIdle { store.open(gameKey) }
            composeRule.waitUntil { zip().session != null }
            composeRule.runOnIdle {
                ZipPlayFixture.solution.forEach { cell ->
                    zip().onDragEvent(DragEvent.Extend(GridCell(cell.idx / 5, cell.idx % 5)))
                }
            }
            composeRule.waitForIdle()
            // The route speaks for the live play only for the solve it holds (F2): key it by that solve.
            val solved = composeRule.runOnIdle { checkNotNull(zip().session) }
            var replayed = false
            composeRule.setContent {
                PuzzleBoxTheme(darkTheme = false) {
                    ResultRoute(
                        key = ResultKey(ZipDefinition.id.value, today.toString(), solved.elapsedMs, solved.hintsUsed),
                        onPlayAgain = { replayed = true },
                        onNext = {},
                        onDone = {},
                    )
                }
            }
            // The verdict is in memory: the badge renders; the key's facts show.
            composeRule.onNodeWithText("Solved!").assertExists()
            composeRule.onNodeWithTag("result.record_badge").assertExists()

            // "Play again": navigation is handed back; the game's reopen is a fresh session.
            composeRule.onNodeWithTag("result.playAgain").performClick()
            composeRule.runOnIdle {
                assertTrue(replayed)
                store.open(gameKey)
                assertTrue(zip().session?.path?.isEmpty() == true)
                assertTrue(zip().session?.isComplete == false)
            }
        } finally {
            stopKoin() // close() alone leaves the JVM-global context registered for later tests
        }
    }
}
