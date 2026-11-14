package com.ivandsky.puzzlebox.designsystem

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.platform.InspectableValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A cell whose text changes frosts the old text out and the new text in, by its caller's progress ([ScrubCell]) or on
 * its own clock ([MorphCell]). A cell whose text stays is drawn once and never frosts.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ScrubCellTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var progress by mutableFloatStateOf(0f)
    private var time by mutableStateOf("1:05")

    private fun scrubbed(from: String, to: String, at: Float) {
        progress = at
        composeRule.setContent { PuzzleBoxTheme(darkTheme = false) { ScrubCell(from, to, { progress }) { Text(it) } } }
    }

    /** A [MorphCell] on a paused clock, where two times that share [identity] are the same layer. */
    private fun morphing(identity: (String) -> Any? = { it }) {
        composeRule.setContent { PuzzleBoxTheme(darkTheme = false) { MorphCell(time, identity = identity) { Text(it) } } }
        composeRule.mainClock.autoAdvance = false
    }

    private fun changeTo(next: String, afterMs: Long) {
        time = next
        // The clock is stepped by hand, so flush the write before the next frame.
        Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeBy(afterMs)
    }

    private fun drawn(text: String): Int = composeRule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().size

    // Opacity is a layer property no semantics reports, and a host test has no pixels to read it from: every layer the
    // text is drawn in is asked for its alpha, and they multiply as they do on screen.
    private fun shown(text: String): Float {
        var at: LayoutInfo? = composeRule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().layoutInfo
        var opacity = 1f
        while (at != null) {
            for (info in at.getModifierInfo()) opacity *= layerAlpha(info.modifier)
            at = at.parentInfo
        }
        return opacity
    }

    private fun layerAlpha(modifier: Modifier): Float {
        val layer = (modifier as? InspectableValue)?.takeIf { it.nameFallback == "graphicsLayer" } ?: return 1f
        val block = layer.inspectableElements.firstOrNull { it.name == "block" }?.value ?: return 1f
        @Suppress("UNCHECKED_CAST")
        return GraphicsLayerScope().apply(block as GraphicsLayerScope.() -> Unit).alpha
    }

    @Test
    fun `a scrub that has not started shows the old text alone`() {
        scrubbed(from = "1:05", to = "0:58", at = 0f)

        assertEquals("old", 1f, shown("1:05"), 0f)
        assertEquals("new", 0f, shown("0:58"), 0f)
    }

    @Test
    fun `a finished scrub shows the new text alone`() {
        scrubbed(from = "1:05", to = "0:58", at = 1f)

        assertEquals("old", 0f, shown("1:05"), 0f)
        assertEquals("new", 1f, shown("0:58"), 0f)
    }

    // The morph's clock is 310 ms: the old text is gone in its first 180, the new one comes in over its last 280.
    // Half way is 155 ms: 1 - 155/180 of the old one is left, (155 - 30)/280 of the new one is there.
    @Test
    fun `half way the old text is nearly gone before the new one is half there`() {
        scrubbed(from = "1:05", to = "0:58", at = 0.5f)

        assertEquals("old", 0.139f, shown("1:05"), TOLERANCE)
        assertEquals("new", 0.446f, shown("0:58"), TOLERANCE)
    }

    @Test
    fun `the frost follows the progress as it moves`() {
        scrubbed(from = "1:05", to = "0:58", at = 0f)

        progress = 1f

        assertEquals("old", 0f, shown("1:05"), 0f)
        assertEquals("new", 1f, shown("0:58"), 0f)
    }

    @Test
    fun `a scrubbed cell whose text stays is drawn once, whole`() {
        scrubbed(from = "1:05", to = "1:05", at = 0.5f)

        assertEquals(1, drawn("1:05"))
        assertEquals(1f, shown("1:05"), 0f)
    }

    @Test
    fun `a new value frosts in while the old one frosts out`() {
        morphing()

        changeTo("0:58", afterMs = 100L)

        assertPartly("old", shown("1:05"))
        assertPartly("new", shown("0:58"))
    }

    @Test
    fun `a morph ends on the new value alone, whole`() {
        morphing()

        changeTo("0:58", afterMs = 1_000L)

        assertEquals(0, drawn("1:05"))
        assertEquals(1f, shown("0:58"), 0f)
    }

    @Test
    fun `a value that is the same layer changes its text without a frost`() {
        morphing(identity = { "time" })

        changeTo("0:58", afterMs = 100L)

        assertEquals(0, drawn("1:05"))
        assertEquals(1f, shown("0:58"), 0f)
    }

    private fun assertPartly(what: String, opacity: Float) =
        assertTrue("$what is part frosted: $opacity", opacity > 0f && opacity < 1f)

    private companion object {
        const val TOLERANCE = 0.001f
    }
}
