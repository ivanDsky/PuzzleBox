package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.width
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.testing.replay.ReplayFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** F17 and F18: the Skip idle toggle keeps one width on and off (its check slot is always laid out) and presses inside a pill. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SkipIdleButtonTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun show(indication: Indication? = null) {
        val playback = ReplayPlayback(ReplayFixtures.timeline(ReplayFixtures.SHORT_MOVES), null)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                val state by playback.state.collectAsState()
                val transport = @Composable { ReplayTransport(state, playback) }
                if (indication == null) transport() else CompositionLocalProvider(LocalIndication provides indication) { transport() }
            }
        }
        composeRule.mainClock.advanceTimeBy(500)
    }

    private fun bounds() = composeRule.onNodeWithTag(ReplayTags.SKIP_IDLE).getUnclippedBoundsInRoot()

    private fun width(): Dp = bounds().width

    @Test
    fun `the pill is as wide off as on, before and after the check fades`() {
        show()
        val on = width()

        composeRule.onNodeWithTag(ReplayTags.SKIP_IDLE).performClick()
        val offMidFade = width()
        composeRule.mainClock.advanceTimeBy(1_000)
        val off = width()

        assertEquals("on, as it opens", on, offMidFade)
        assertEquals("on against off", on, off)
        assertTrue("a real width: $on", on.value > 48f)
    }

    @Test
    fun `toggling twice returns to the same width`() {
        show()
        val on = width()

        repeat(2) {
            composeRule.onNodeWithTag(ReplayTags.SKIP_IDLE).performClick()
            composeRule.mainClock.advanceTimeBy(1_000)
        }

        assertEquals(on, width())
    }

    @Test
    fun `the press indication stays inside a pill, not the corners of the box`() {
        show(indication = FillIndication)
        // The indication is made on the first interaction, so hold a finger down and draw that frame.
        composeRule.onNodeWithTag(ReplayTags.SKIP_IDLE).performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(100)

        // The red box is the pressable box; the pill clips its corners out of it.
        val image = composeRule.onRoot().captureToImage().toPixelMap()
        val red = (0 until image.height).flatMap { y -> (0 until image.width).map { x -> x to y } }
            .filter { (x, y) -> image[x, y].copy(alpha = 1f) == Color.Red }
        assertTrue("the indication ran", FillIndication.created > 0)
        assertTrue("the indication drew", red.isNotEmpty())
        val (left, right) = red.minOf { it.first } to red.maxOf { it.first }
        val (top, bottom) = red.minOf { it.second } to red.maxOf { it.second }

        assertTrue("the middle of the top edge is pressed", (left + right) / 2 to top in red)
        listOf(left to top, right to top, left to bottom, right to bottom).forEach { corner ->
            assertTrue("the box corner $corner is clipped away", corner !in red)
        }
    }

    /** Draws red over the whole bounds it is given, as a ripple draws over its content, so the clip above it decides which pixels turn red. */
    private object FillIndication : IndicationNodeFactory {
        override fun create(interactionSource: InteractionSource): DelegatableNode {
            created++
            return object : Modifier.Node(), DrawModifierNode {
                override fun ContentDrawScope.draw() {
                    drawContent()
                    drawRect(Color.Red)
                }
            }
        }

        @Volatile var created = 0

        override fun equals(other: Any?) = other === this

        override fun hashCode() = 1
    }
}
