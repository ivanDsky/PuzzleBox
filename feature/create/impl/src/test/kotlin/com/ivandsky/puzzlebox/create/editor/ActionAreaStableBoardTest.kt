package com.ivandsky.puzzlebox.create.editor

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.PublishBlock
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.PlayLayout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The phone's board slot sits above the action area, so an area whose height follows the draft's
 * readiness moves the board: a draft turning unique drops the "Not unique" line and, untested,
 * shows none. The area keeps one readiness line's height in every state.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class ActionAreaStableBoardTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var blocks by mutableStateOf(listOf(PublishBlock(PublishBlock.NOT_UNIQUE)))

    @Test
    fun `the board slot keeps its bounds as the draft turns unique, then tested`() {
        composeRule.setContent {
            PuzzleBoxTheme {
                StudioScreen(
                    title = "Untitled 3",
                    subtitle = "Zip 5×5",
                    saving = false,
                    verdict = Verdict.Checking,
                    layout = PlayLayout.SinglePane,
                    onBack = {},
                    onRename = {},
                    onRollTitle = { "Amber Falcon" },
                    onDuplicate = {},
                    onDelete = {},
                    board = { Box(it) },
                    toolStrip = { Box(it.height(96.dp)) },
                    actionArea = { ActionArea(passed = null, canTestSolve = true, onTestSolve = {}, blocks = blocks, modifier = it, keepReadinessLine = true) },
                )
            }
        }
        composeRule.waitForIdle()
        val notUnique = composeRule.onNodeWithTag("create.editor.board").getBoundsInRoot()

        composeRule.runOnIdle { blocks = listOf(PublishBlock(PublishBlock.NOT_TESTED)) }
        composeRule.waitForIdle()
        assertEquals("unique, untested", notUnique, composeRule.onNodeWithTag("create.editor.board").getBoundsInRoot())

        composeRule.runOnIdle { blocks = emptyList() }
        composeRule.waitForIdle()
        assertEquals("ready", notUnique, composeRule.onNodeWithTag("create.editor.board").getBoundsInRoot())
    }
}
