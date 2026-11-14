package com.ivandsky.puzzlebox.history

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.game.replay.Timeline
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.TutorialStep
import com.ivandsky.puzzlebox.gameui.replay.GameReplayBoard
import com.ivandsky.puzzlebox.gameui.replay.ReplayUnavailable
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.replay.ReplayFixtures
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import com.ivandsky.puzzlebox.gameui.replay.ReplayTags
import com.ivandsky.puzzlebox.gameui.ResultColumnMaxWidth
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * History's day-detail replay section at the two golden devices, light and dark: one replay (a short 12-move and a long
 * 64-move one), the first solve and the latest with their labelled rows and Compare button, and the two
 * "no replay" lines. Goldens in `snapshots/replay/`.
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

    private fun columnMaxPx() = with(rule.density) { ResultColumnMaxWidth.roundToPx() }

    @Test fun `one short light`() = capture("one-short", HistoryReplays.One(SHORT, ReplaySolve.Record), dark = false)
    @Test fun `one short dark`() = capture("one-short", HistoryReplays.One(SHORT, ReplaySolve.Record), dark = true)

    @Test fun `one long light`() = capture("one-long", HistoryReplays.One(LONG, ReplaySolve.Record), dark = false)
    @Test fun `one long dark`() = capture("one-long", HistoryReplays.One(LONG, ReplaySolve.Record), dark = true)

    @Test fun `first and latest light`() = capture("first-latest", HistoryReplays.FirstAndLatest(SHORT, LONG), dark = false)
    @Test fun `first and latest dark`() = capture("first-latest", HistoryReplays.FirstAndLatest(SHORT, LONG), dark = true)

    /** VX-1: on a wide pane the replay rows and Compare stay in the 600 dp result column. */
    @Test fun `rows stay in the result column`() {
        rule.showGolden(dark = false) {
            HistoryReplaySection(HistoryReplays.FirstAndLatest(SHORT, LONG), GoldenReplayUi, onOpen = {}, onCompare = {})
        }
        val rows = rule.onAllNodesWithTag(ReplayTags.WATCH_ROW).fetchSemanticsNodes()
        assertTrue(rows.isNotEmpty() && rows.all { it.size.width <= columnMaxPx() })
        val c = rule.onNodeWithContentDescription("Compare first solve with latest").fetchSemanticsNode().size.width
        assertTrue("compare=$c max=${columnMaxPx()} rows=${rows.map { it.size.width }}", c <= columnMaxPx())
    }

    @Test fun `unavailable imported light`() = capture("unavailable-imported", HistoryReplays.Unavailable(ReplayUnavailable.ImportedFromLinkedIn), dark = false)
    @Test fun `unavailable imported dark`() = capture("unavailable-imported", HistoryReplays.Unavailable(ReplayUnavailable.ImportedFromLinkedIn), dark = true)

    @Test fun `unavailable light`() = capture("unavailable", HistoryReplays.Unavailable(ReplayUnavailable.NotRecorded), dark = false)
    @Test fun `unavailable dark`() = capture("unavailable", HistoryReplays.Unavailable(ReplayUnavailable.NotRecorded), dark = true)

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
