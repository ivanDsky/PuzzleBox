package com.ivandsky.puzzlebox.app.play

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
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
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.ResultRoute
import com.ivandsky.puzzlebox.play.di.playModule
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
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
 * Ticket 23 over the real Zip game: the result route reads the store's live play —
 * solving stamps the badge in memory (fire-and-observe, never waited on) — and "Play
 * again" resets the SAME puzzle to a fresh session before handing back to navigation.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Plain Application: the real PuzzleBoxApplication's startKoin would race this class's own graph.
@Config(sdk = [34], application = android.app.Application::class)
class RealGamesResultRouteTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `result route reads the store's live play and replays the same puzzle`() {
        val daily = freshDailyForAllGames().apply {
            setHistory(ZipDefinition.id, listOf(ZipPlayFixture.entry(ZipDefinition.cadence.today())))
        }
        stopKoin() // tests share the JVM-global Koin context
        val koin = startKoin {
            modules(
                module {
                    single<DailyRepository> { daily }
                    single<CompletionRepository> { FakeCompletionRepository() }
                    single { GameRegistry(listOf(zipPlugin)) }
                    single { GamePlayRegistry(listOf(ZipPlayUi())) }
                    single<com.ivandsky.puzzlebox.data.repository.KeptSessionRepository> {
                        com.ivandsky.puzzlebox.testing.repository.FakeKeptSessionRepository()
                    }
                    factory { com.ivandsky.puzzlebox.domain.ObserveTodaySet(get(), get(), get()) }
                },
                playModule,
            )
        }
        val store = koin.koin.get<PlaySessionStore>()
        fun zip() = store.active.value!!.controller as ZipPlayController
        try {
            var replayed = false
            composeRule.setContent {
                PuzzleBoxTheme(darkTheme = false) {
                    ResultRoute(timeMs = 12_340, hintsUsed = 0, onPlayAgain = { replayed = true })
                }
            }
            // No live play yet (process-death route restore case): the frozen facts
            // from the key render, no badge can be claimed.
            composeRule.onNodeWithText("Solved!").assertExists()
            composeRule.onNodeWithTag("result.record_badge").assertDoesNotExist()

            // Bring the store to a solved play the way a play-through would.
            composeRule.runOnIdle { store.start(ZipDefinition.id, onSessionChanged = {})!!.load() }
            composeRule.waitForIdle()
            composeRule.runOnIdle {
                ZipPlayFixture.solution.forEach { cell ->
                    zip().onDragEvent(DragEvent.Extend(GridCell(cell.idx / 5, cell.idx % 5)))
                }
            }
            composeRule.waitForIdle()
            // The verdict is in memory: the game's first solve badge renders.
            composeRule.onNodeWithText("First solve").assertExists()

            // "Play again": same puzzle, fresh session, then navigation is handed back.
            composeRule.onNodeWithTag("result.play_again").performClick()
            composeRule.runOnIdle {
                assertTrue(replayed)
                assertTrue(zip().session?.path?.isEmpty() == true)
                assertTrue(zip().session?.isComplete == false)
            }
        } finally {
            koin.close()
        }
    }
}
