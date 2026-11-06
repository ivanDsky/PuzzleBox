package com.ivandsky.puzzlebox.games.queensui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.queens.QueensVariants
import com.ivandsky.puzzlebox.games.queens.editor.QueensDraft
import com.ivandsky.puzzlebox.games.queens.editor.QueensEditor
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * fix-e2e: on a small phone (Robolectric's default 320×470 dp window) the Studio leaves an
 * 8×8 board far narrower than its ring; the ring must keep its full size, stay in the window
 * and take the pick.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QueensSelectionRingTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `a ring wider than a small board still takes the pick`() {
        val edits = ArrayList<EditorDraft<*>>()
        val controller = QueensEditorController(EditorDraft.fresh(QueensEditor, QueensVariants.variant(SIZE).id)) { edits += it }
        rule.setContent {
            PuzzleBoxTheme {
                Box(Modifier.padding(top = 80.dp).size(120.dp)) { QueensEditorBoard(controller, Modifier.fillMaxSize()) }
            }
        }

        rule.onNodeWithTag("queens.editor.cell.0", useUnmergedTree = true).performClick()
        rule.onNodeWithTag("queens.editor.ring.slot.1").performClick()

        assertEquals(1, edits.size)
        assertEquals(1, (edits.single().state.draft as QueensDraft).regions[0])
    }

    @Test
    fun `the ring stays inside a board it fits in`() {
        val frame = BoardFrame(IntSize(400, 400), Offset(0f, 300f), IntSize(400, 900))
        assertEquals(Offset(0f, 0f), ringTopLeft(Offset(10f, 10f), 200f, frame))
        assertEquals(Offset(200f, 200f), ringTopLeft(Offset(390f, 390f), 200f, frame))
    }

    @Test
    fun `a ring wider than the board is centred on it, then pushed back inside the window`() {
        val frame = BoardFrame(IntSize(58, 58), Offset(131f, 80f), IntSize(320, 470))
        // Centred on the board across; down from the window's top edge, which it would cross.
        assertEquals(Offset(29f - 112f, -80f), ringTopLeft(Offset(4f, 4f), 224f, frame))
    }

    private companion object {
        const val SIZE = 8
    }
}
