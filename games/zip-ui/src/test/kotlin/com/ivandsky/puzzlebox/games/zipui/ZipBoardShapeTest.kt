package com.ivandsky.puzzlebox.games.zipui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Lessons sweep along Zip's rounded board, not a square frame over it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ZipBoardShapeTest {

    private val ui = ZipPlayUi()
    private val size = Size(300f, 300f)

    private fun controller() = ZipPlayController(PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {}))

    @Test
    fun `board shape is the rounded outline once a lesson is adopted`() {
        val step = ZipTutorial.steps.first()
        val c = controller()
        check(c.adoptToday(step.puzzle, step.ref) == Adopt.Adopted)
        val outline = ui.boardShape(c).createOutline(size, LayoutDirection.Ltr, Density(1f))
        assertTrue("expected a rounded generic outline, was $outline", outline is Outline.Generic)
        assertEquals(size.width, (outline as Outline.Generic).path.getBounds().width, 0.5f)
    }

    @Test
    fun `board shape is square before a session exists`() {
        assertEquals(RectangleShape, ui.boardShape(controller()))
    }
}
