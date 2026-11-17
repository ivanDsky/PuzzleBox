package com.ivandsky.puzzlebox.create.editor

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The STUDIO tag drops before the game label clips; mdpi, so one pixel is one dp. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w800dp-h800dp-mdpi")
class StudioSubtitleLineTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `with room for everything the tag, whole label and save state all show`() {
        show(WIDE)
        assertTrue(placed(TAG))
    }

    @Test
    fun `one pixel short of the tag the label stays whole and the tag is gone`() {
        show(WIDE)
        val withoutTag = (right("create.studio.saved") - left(LABEL)).dp
        val whole = labelWidth()
        show(withoutTag + 2.dp)
        assertFalse("tag shown beside a label that cannot also fit", placed(TAG))
        assertEquals("label clipped before the tag dropped", whole, labelWidth(), 0.5f)
        assertTrue(placed(LABEL))
        assertTrue(placed("create.studio.saved"))
    }

    @Test
    fun `narrower still the label ellipsizes with no tag, and the save state stays`() {
        show(WIDE)
        val withoutTag = (right("create.studio.saved") - left(LABEL)).dp
        val whole = labelWidth()
        show(withoutTag - 30.dp)
        assertFalse(placed(TAG))
        assertTrue("label should ellipsize", labelWidth() < whole - 1f)
        assertTrue(placed("create.studio.saved"))
    }

    private var lineWidth by mutableStateOf(WIDE)
    private var shown = false

    private fun show(width: Dp) {
        lineWidth = width
        if (!shown) {
            shown = true
            composeRule.setContent {
                PuzzleBoxTheme { SubtitleLine(subtitle = LABEL_TEXT, saving = false, modifier = Modifier.width(lineWidth)) }
            }
        }
        composeRule.waitForIdle()
    }

    private fun node(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true)

    private fun placed(tag: String) = node(tag).fetchSemanticsNode().layoutInfo.isPlaced

    private fun left(tag: String) = node(tag).fetchSemanticsNode().boundsInRoot.left

    private fun right(tag: String) = node(tag).fetchSemanticsNode().boundsInRoot.right

    private fun labelWidth() = right(LABEL) - left(LABEL)

    private companion object {
        const val TAG = "create.studio.tag"
        const val LABEL = "create.studio.label"
        const val LABEL_TEXT = "Queens 12×12 · Unique"
        val WIDE = 700.dp
    }
}
