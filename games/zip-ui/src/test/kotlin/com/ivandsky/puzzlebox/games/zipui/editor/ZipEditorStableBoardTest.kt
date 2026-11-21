package com.ivandsky.puzzlebox.games.zipui.editor

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.zip.editor.ZipDraft
import com.ivandsky.puzzlebox.games.zip.editor.ZipEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The editor's board stays put: switching Path and Numbers & walls never moves or resizes it, the
 * next-step hint never names the step the author is on, and an empty path gets one coach line.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h700dp-420dpi")
class ZipEditorStableBoardTest {

    @get:Rule
    val rule = createComposeRule()

    private fun show(path: List<Int>): ZipEditorController {
        val blank = EditorDraft.fresh(ZipEditor, ZipEditor.variants.first())
        val board = (blank.state.draft as ZipDraft).copy(path = path, waypoints = listOfNotNull(path.firstOrNull(), path.lastOrNull()))
        val controller = ZipEditorController(EditorDraft(ZipEditor, DraftState(board))) {}
        rule.setContent { PuzzleBoxTheme { ZipEditorBoard(controller, Modifier.fillMaxSize()) } }
        return controller
    }

    private fun boardBounds() = rule.onNodeWithTag("zip.editor.board").getBoundsInRoot()

    @Test
    fun `the board keeps its bounds across the Path and Numbers and walls steps`() {
        show(SNAKE)
        val onPath = boardBounds()

        rule.onNodeWithTag("zip.editor.step.numbersandwalls").performClick()
        rule.waitForIdle()
        assertEquals("Numbers & walls", onPath, boardBounds())
    }

    @Test
    fun `the hint never offers the step the author is on`() {
        val board = ZipDraft(5, emptySet(), SNAKE, listOf(0, 24))

        assertEquals(ZipEditorStep.NumbersAndWalls, ZipEditorHintState.of(board, ZipEditorStep.Path).next)
        assertNull(ZipEditorHintState.of(board, ZipEditorStep.NumbersAndWalls).next)
    }

    @Test
    fun `an empty path shows one coach line that goes once a stroke starts`() {
        val controller = show(emptyList())
        rule.onNodeWithTag("zip.editor.coach").assertExists()
        rule.onNodeWithTag("zip.editor.reverse").assertDoesNotExist()

        controller.beginStroke(0)
        controller.strokeTo(1)
        controller.endStroke()
        rule.waitForIdle()

        rule.onNodeWithTag("zip.editor.coach").assertDoesNotExist()
        rule.onNodeWithTag("zip.editor.reverse").assertExists()
    }

    private companion object {
        val SNAKE: List<Int> = (0 until 5).flatMap { row ->
            (if (row % 2 == 0) 0 until 5 else 4 downTo 0).map { row * 5 + it }
        }
    }
}
