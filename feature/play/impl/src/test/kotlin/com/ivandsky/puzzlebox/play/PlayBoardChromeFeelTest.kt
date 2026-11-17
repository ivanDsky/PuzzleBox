package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.LocalPlayFeel
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.game.TestGames
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The local PlayBoardChrome provides inside its board equals the PlayFeel holder the graph bound (spec A19). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayBoardChromeFeelTest {

    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun tearDown() = stopKoin()

    @Test
    fun `the local reads true when PlayFeel is bound on`() {
        assertEquals(true, seenInsideBoard(enabled = true))
    }

    @Test
    fun `the local reads false when PlayFeel is bound off`() {
        assertEquals(false, seenInsideBoard(enabled = false))
    }

    private fun seenInsideBoard(enabled: Boolean): Boolean? {
        stopKoin() // tests share the JVM-global Koin context
        startKoin { modules(module { single { PlayFeel(enabled) } }) }
        val game = TestGames.alpha
        val fake = FakePlayUi(game)
        val controller = fake.createController(PlayEnvironment(CoroutineScope(Job()), onSessionChanged = {}, onWon = {}))
        (controller as FakeController).adoptToday(TestGames.puzzle(game), TestGames.Ref(game, PuzzleKey.Custom("draft-castle")))
        var seen: Boolean? = null
        val probe = FeelProbe(fake) { seen = it }
        composeRule.setContent {
            PlayBoardChrome(
                ui = probe,
                controller = controller,
                status = PlayBoardStatus.Ready,
                title = "Castle",
                label = null,
                pastDay = false,
                tutorial = TutorialGate.Closed,
                onHowToPlay = null,
                onTutorialFinished = {},
                layout = PlayLayout.SinglePane,
                onBack = {},
                onRetry = {},
                onSolved = { _, _ -> },
                overflow = emptyList(),
                sidePanel = null,
                modifier = Modifier,
            )
        }
        composeRule.waitForIdle()
        return seen
    }

    /** A game UI that reports the local it is drawn under, then draws the stand-in board. */
    private class FeelProbe(private val fake: FakePlayUi, private val onSeen: (Boolean) -> Unit) : GamePlayUi by fake {
        @Composable
        override fun Screen(
            controller: GamePlayController,
            status: PlayBoardStatus,
            layout: PlayLayout,
            sidePanel: (@Composable ColumnScope.() -> Unit)?,
            onRetry: () -> Unit,
            onSolved: (timeMs: Long, hintsUsed: Int) -> Unit,
            modifier: Modifier,
        ) {
            onSeen(LocalPlayFeel.current)
            fake.Screen(controller, status, layout, sidePanel, onRetry, onSolved, modifier)
        }
    }
}
