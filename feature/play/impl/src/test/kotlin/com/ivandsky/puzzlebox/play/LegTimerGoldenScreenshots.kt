package com.ivandsky.puzzlebox.play

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.gameui.AdaptivePlayScaffold
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.LocalRouteCaption
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayChrome
import com.ivandsky.puzzlebox.gameui.PlayClockEffects
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.PlayOverflowItem
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.game.TestGames
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.After
import org.junit.Before
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
 * A self-duel leg's timer (ticket 12), phone and tablet, light and dark: time left toward past you's 2:14, over by
 * after it, the setting off (the plain timer), and the longest title with the biggest numbers. The phone shows the
 * caption under the bar and the label beside the fill; the tablet shows the side panel with the time card.
 * The board under it is the stand-in game's. Goldens in `snapshots/leg-timer/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class LegTimerGoldenScreenshotsPhone : LegTimerGoldenScreenshots("phone", PlayLayout.SinglePane)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class LegTimerGoldenScreenshotsTablet : LegTimerGoldenScreenshots("tablet", PlayLayout.TwoPane)

abstract class LegTimerGoldenScreenshots(private val device: String, private val layout: PlayLayout) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun bind() {
        stopKoin() // tests share the JVM-global Koin context
        startKoin { modules(module {}) } // no tuning source: the bar holds the timer on a phone, the panel on a tablet
    }

    @After
    fun unbind() = stopKoin()

    @Test fun `time left light`() = capture("time-left", dark = false, elapsedMs = 88_000L, target = 134.seconds)
    @Test fun `time left dark`() = capture("time-left", dark = true, elapsedMs = 88_000L, target = 134.seconds)
    @Test fun `over by light`() = capture("over-by", dark = false, elapsedMs = 142_000L, target = 134.seconds)
    @Test fun `over by dark`() = capture("over-by", dark = true, elapsedMs = 142_000L, target = 134.seconds)
    @Test fun `setting off light`() = capture("setting-off", dark = false, elapsedMs = 88_000L, target = null)
    @Test fun `setting off dark`() = capture("setting-off", dark = true, elapsedMs = 88_000L, target = null)

    @Test fun `longest light`() = longest(dark = false)
    @Test fun `longest dark`() = longest(dark = true)

    private fun longest(dark: Boolean) = capture(
        "longest", dark, elapsedMs = (25 * 60 + 12) * 1000L, target = (59 * 60 + 59).seconds,
        title = "Maya's extremely long Tango puzzle name", number = 6, count = 9, played = 5, hints = 3,
    )

    private fun capture(
        name: String,
        dark: Boolean,
        elapsedMs: Long,
        target: kotlin.time.Duration?,
        title: String = "Zip · Tuesday",
        number: Int = 2,
        count: Int = 3,
        played: Int = number - 1,
        hints: Int = 0,
    ) {
        val game = TestGames.alpha
        val ui = FakePlayUi(game)
        val controller = ui.createController(PlayEnvironment(CoroutineScope(Job()), onSessionChanged = {}, onWon = {})) as FakeController
        controller.adoptToday(TestGames.puzzle(game), TestGames.Ref(game, PuzzleKey.Custom("draft-castle")))
        controller.advance(elapsedMs)
        val leg = SelfLeg(target, played, count)
        val caption = "Duel vs Past you · puzzle $number of $count"
        rule.showGolden(dark) {
            CompositionLocalProvider(LocalRouteCaption provides caption) {
                PlayBoardChrome(
                    ui = ScaffoldedUi(ui),
                    controller = controller,
                    status = PlayBoardStatus.Ready,
                    title = title,
                    label = null,
                    pastDay = false,
                    tutorial = TutorialGate.Closed,
                    onHowToPlay = null,
                    onTutorialFinished = {},
                    layout = layout,
                    onBack = {},
                    onRetry = {},
                    onSolved = { _, _ -> },
                    overflow = listOf(PlayOverflowItem("Settings", checked = null, onClick = {})),
                    sidePanel = { clock, showTimer -> legSidePanel(leg, clock, { hints }, showTimer) },
                    modifier = Modifier,
                    targetMs = target?.inWholeMilliseconds,
                )
            }
        }
        rule.settleAndCapture("leg-timer", "leg-board-$name", device, dark)
    }
}

/** The stand-in game drawn the way the real games draw: in the adaptive scaffold, the chrome's caption slot beside the board. */
private class ScaffoldedUi(private val fake: FakePlayUi) : GamePlayUi by fake {
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
        PlayClockEffects(controller as FakeController)
        AdaptivePlayScaffold(
            layout = layout,
            modifier = modifier,
            board = { fake.Board(controller, Modifier) },
            side = { PlayChrome(clock = null, hints = null) },
            sidePanel = sidePanel,
        )
    }
}
