package com.ivandsky.puzzlebox.games.zipui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.github.takahirom.roborazzi.captureRoboImage
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.DragEvent
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * #1184: at the start state Undo has nothing to undo, so it is disabled and reads that way; once
 * the trace has a move it is enabled. The golden is the action bar at the start state, rendered
 * with `-Proborazzi.record=true` (untracked review captures in `snapshots/`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class ZipStartStateGoldenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun show(): ZipPlayController {
        val controller = ZipPlayController(PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {}))
        check(controller.adoptToday(ZipPlayFixture.envelope(), ZipPlayFixture.ref(LocalDate.of(2026, 10, 31))) == Adopt.Adopted)
        composeRule.setContent {
            PuzzleBoxTheme {
                ZipPlayScreen(controller, PlayBoardStatus.Ready, PlayLayout.SinglePane, null, {}, { _, _ -> })
            }
        }
        composeRule.waitForIdle()
        return controller
    }

    @Test
    fun `start state shows Undo disabled`() {
        show()
        composeRule.onNodeWithTag("play.undo").assertIsNotEnabled()
        val out = File(checkNotNull(System.getProperty("user.dir")))
            .resolve("snapshots/start-undo-disabled-light.png")
            .canonicalFile
            .also { checkNotNull(it.parentFile).mkdirs() }
        composeRule.onNodeWithTag("play.action_bar").captureRoboImage(out.absolutePath)
    }

    @Test
    fun `Undo enables once the trace has a move`() {
        val controller = show()
        composeRule.runOnIdle { listOf(0, 1, 2).forEach { controller.onDragEvent(DragEvent.Extend(GridCell(0, it))) } }
        composeRule.onNodeWithTag("play.undo").assertIsEnabled()
    }
}
