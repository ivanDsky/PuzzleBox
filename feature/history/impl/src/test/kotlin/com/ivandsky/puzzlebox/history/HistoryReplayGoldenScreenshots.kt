package com.ivandsky.puzzlebox.history

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.game.replay.Timeline
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.TutorialStep
import com.ivandsky.puzzlebox.gameui.replay.GameReplayBoard
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.replay.ReplayFixtures
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * History's day-detail replay section at the two golden devices, light and dark: one replay (a short 12-move and a long
 * 64-move one) and the first solve and the latest with their labelled rows and Compare button. Goldens in `snapshots/replay/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class HistoryReplayPhoneGoldenScreenshots : HistoryReplayGoldenScreenshots("phone")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class HistoryReplayTabletGoldenScreenshots : HistoryReplayGoldenScreenshots("tablet")

abstract class HistoryReplayGoldenScreenshots(private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `one short light`() = capture("one-short", HistoryReplays.One(SHORT, ReplaySolve.Record), dark = false)
    @Test fun `one short dark`() = capture("one-short", HistoryReplays.One(SHORT, ReplaySolve.Record), dark = true)

    @Test fun `one long light`() = capture("one-long", HistoryReplays.One(LONG, ReplaySolve.Record), dark = false)
    @Test fun `one long dark`() = capture("one-long", HistoryReplays.One(LONG, ReplaySolve.Record), dark = true)

    @Test fun `first and latest light`() = capture("first-latest", HistoryReplays.FirstAndLatest(SHORT, LONG), dark = false)
    @Test fun `first and latest dark`() = capture("first-latest", HistoryReplays.FirstAndLatest(SHORT, LONG), dark = true)

    private fun capture(name: String, replays: HistoryReplays, dark: Boolean) {
        rule.showGolden(dark) {
            HistoryReplaySection(
                replays = replays,
                ui = GoldenReplayUi,
                onOpen = {},
                onCompare = {},
                modifier = Modifier.padding(SECTION_PADDING),
            )
        }
        rule.settleAndCapture("replay", name, device, dark)
    }

    private companion object {
        const val LONG_MOVES = 64
        const val LONG_STEP_MS = 64_000L
        val SECTION_PADDING = 16.dp
        val SHORT: Timeline = ReplayFixtures.timeline(ReplayFixtures.SHORT_MOVES)
        val LONG: Timeline = ReplayFixtures.timeline(LONG_MOVES, stepMs = LONG_STEP_MS)
    }
}

/** A game with a replay board, which is all the section reads from its play UI. */
private object GoldenReplayUi : GamePlayUi {
    override val gameId: GameId = ReplayFixtures.GAME
    override val replayBoard: GameReplayBoard? = ReplayGoldenBoard
    override val tutorial: List<TutorialStep> get() = emptyList()

    override fun createController(env: PlayEnvironment): GamePlayController = error("golden only")

    @Composable
    override fun Board(controller: GamePlayController, modifier: Modifier) = Unit

    @Composable
    override fun Screen(
        controller: GamePlayController,
        status: PlayBoardStatus,
        layout: PlayLayout,
        sidePanel: (@Composable ColumnScope.() -> Unit)?,
        onRetry: () -> Unit,
        onSolved: (timeMs: Long, hintsUsed: Int) -> Unit,
        modifier: Modifier,
    ) = Unit
}
