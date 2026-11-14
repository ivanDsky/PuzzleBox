package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.gameui.replay.ReplayBoardOptions
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoReplay
import com.ivandsky.puzzlebox.games.tango.TangoReplayMove
import com.ivandsky.puzzlebox.games.tango.TangoSession
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasContentDescription
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Tango replay board shows a violation's mark only while [ReplayBoardOptions.showErrors] (spec S18 / A27). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class TangoReplayBoardShowErrorsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val initial = TangoSession(ref = null, puzzle = TangoPlayFixture.puzzle)

    // A sun at 2 makes three suns in row 0; the settle window has passed, so errors on show it.
    private val frame: ReplayFrame = run {
        val session = checkNotNull(TangoReplay.step(initial, TangoReplayMove.Place(2, TangoCell.SUN).toReplayMove(), 1_000L))
        ReplayFrame(session, initial, emptyList(), emptySet(), TangoReplay.factsOf(initial)(session), 1_000L, MotionTokens.SettleMillis, false)
    }

    private fun show(showErrors: Boolean) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                TangoReplayBoard.Board(frame, ReplayBoardOptions(animate = false, showErrors = showErrors), Modifier.size(320.dp))
            }
        }
        composeRule.waitForIdle()
    }

    private val violating = SemanticsMatcher("a cell stating a violation") {
        it.config.getOrNull(SemanticsProperties.StateDescription)?.startsWith("violates") == true
    }

    @Test fun `errors on mark the violating cells`() {
        show(showErrors = true)
        assertTrue(composeRule.onAllNodes(violating).fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun `errors off mark no cell`() {
        show(showErrors = false)
        composeRule.onAllNodes(violating).assertCountEquals(0)
    }
}
