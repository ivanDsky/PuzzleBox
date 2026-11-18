package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.ivandsky.puzzlebox.data.repository.GameSettingsRepository
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.HintCostLine
import com.ivandsky.puzzlebox.gameui.LocalHintCostLine
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.GameSetting
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeGameSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The board host's one-time hint cost line: stored per game under `hint_cost_seen`, and absent in the Studio sandbox. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HintCostHostTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val settings = FakeGameSettingsRepository()
    private val inputs = MutableStateFlow(0)

    @After
    fun tearDown() = stopKoin()

    private fun bind() {
        stopKoin() // tests share the JVM-global Koin context
        startKoin {
            modules(module {
                single<GameSettingsRepository> { settings }
            })
        }
    }

    private fun seen(game: GameId) = runBlocking { settings.flag(GameSetting(game, "hint_cost_seen", default = false)).first() }

    private fun lineIn(game: GameId): HintCostLine? {
        var line: HintCostLine? = null
        composeRule.setContent { HintCostHost(game, inputs) { line = LocalHintCostLine.current } }
        composeRule.waitForIdle()
        return line
    }

    @Test
    fun `an unseen game is offered an unseen line and nothing is written until it is marked`() {
        bind()
        assertEquals(false, lineIn(TestGames.alpha)?.seen?.invoke())
        assertEquals(false, seen(TestGames.alpha))
    }

    @Test
    fun `marking one game's line stores it for that game only`() {
        bind()
        lineIn(TestGames.alpha)!!.markSeen()
        composeRule.waitForIdle()
        assertEquals(true, seen(TestGames.alpha))
        assertEquals(false, seen(TestGames.beta))
    }

    @Test
    fun `a game that has shown it is offered a seen line`() {
        bind()
        runBlocking { settings.setFlag(GameSetting(TestGames.alpha, "hint_cost_seen", default = false), true) }
        assertEquals(true, lineIn(TestGames.alpha)?.seen?.invoke())
    }

    @Test
    fun `a graph with no settings store offers none`() {
        stopKoin()
        startKoin { modules(module {}) }
        assertNull(lineIn(TestGames.alpha))
    }

    @Test
    fun `the sandbox offers none even under a host that does`() {
        bind()
        val game = TestGames.alpha
        val fake = FakePlayUi(game)
        val controller = fake.createController(PlayEnvironment(CoroutineScope(Job()), onSessionChanged = {}, onWon = {}))
        (controller as FakeController).adoptToday(TestGames.puzzle(game), TestGames.Ref(game, PuzzleKey.Custom("draft-castle")))
        var offered: HintCostLine? = HintCostLine(seen = { false }, inputs = { 0 }, markSeen = {})
        val probe = object : GamePlayUi by fake {
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
                offered = LocalHintCostLine.current
            }
        }
        composeRule.setContent {
            CompositionLocalProvider(LocalHintCostLine provides HintCostLine(seen = { false }, inputs = { 0 }, markSeen = {})) {
                SandboxPlay(probe, controller, title = "Castle", label = "Test solve", layout = PlayLayout.SinglePane, onBack = {}, onSolved = {})
            }
        }
        composeRule.waitForIdle()
        assertNull(offered)
    }
}
