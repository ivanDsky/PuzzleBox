package com.ivandsky.puzzlebox.designsystem.spotlight

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The callout's action pill: what it shows, how tall it is, what it takes taps for, and how the bubble grows around it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SpotlightCalloutActionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var clicks = 0

    private val action get() = SpotlightAction(PLACE_IT) { clicks++ }

    @Before
    fun noMotion() {
        // Reduced motion lands the callout at its final frame at once, so bounds and taps are the settled ones.
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    private fun spotlight(action: SpotlightAction?) = Spotlight(
        rows = 6,
        cols = 6,
        roles = mapOf(14 to SpotlightRole.EVIDENCE, 15 to SpotlightRole.EVIDENCE, 16 to SpotlightRole.TARGET),
        callout = SpotlightCallout("Last cell: one open cell left.", setOf(14, 15, 16), action),
        glyphs = DiscGlyphs,
        playId = 0,
    )

    /** One 6×6 board of [boardSide] per entry of [actions], stacked, each under its own overlay. */
    private fun show(vararg actions: SpotlightAction?, boardSide: Dp = BOARD_SIDE) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Column {
                    actions.forEach { Box(Modifier.size(boardSide).testTag(BOARD)) { SpotlightOverlay(spotlight(it)) } }
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `an action shows its label as a pill at least 48 dp tall`() {
        show(action)

        composeRule.onNodeWithText(PLACE_IT).assertIsDisplayed()
        assertTrue(composeRule.onNodeWithTag(SpotlightActionTag).getBoundsInRoot().height >= 48.dp)
    }

    @Test
    fun `a click on the pill runs onClick once`() {
        show(action)

        composeRule.onNodeWithTag(SpotlightActionTag).performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun `a tap on the board or the bubble text beside the pill runs nothing`() {
        show(action)

        composeRule.onAllNodesWithTag(BOARD)[0].performTouchInput { click(Offset(2f, 2f)) }
        composeRule.onNodeWithTag(SpotlightCalloutTag).performTouchInput { click(Offset(8f, 8f)) }

        assertEquals(0, clicks)
    }

    @Test
    fun `no action draws no pill`() {
        show(null)

        assertTrue(composeRule.onAllNodesWithTag(SpotlightActionTag).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `the bubble with a pill grows by under one text line over the same bubble without`() {
        show(null, action)
        val (plain, withPill) = composeRule.onAllNodesWithTag(SpotlightCalloutTag).fetchSemanticsNodes()
            .map { it.boundsInRoot.height }

        assertTrue("with $withPill, without $plain", withPill > plain)
        assertTrue("with $withPill, without $plain", withPill - plain <= with(composeRule.density) { SpotlightTokens.CalloutLineHeight.toPx() })
    }

    @Test
    fun `on a narrow board the text keeps its width and the pill drops under it, inside the bubble`() {
        show(action, boardSide = NARROW_SIDE)

        val bubble = composeRule.onNodeWithTag(SpotlightCalloutTag).getBoundsInRoot()
        val pill = composeRule.onNodeWithTag(SpotlightActionTag).getBoundsInRoot()
        assertTrue("pill $pill outside bubble $bubble", pill.left >= bubble.left && pill.right <= bubble.right)
        assertTrue("pill $pill outside bubble $bubble", pill.top >= bubble.top && pill.bottom <= bubble.bottom)
        assertTrue("bubble $bubble is not taller than the pill $pill", bubble.height >= pill.height + SpotlightTokens.CalloutLineHeight.value.dp)
    }

    private companion object {
        const val BOARD = "board"
        val BOARD_SIDE = 312.dp
        val NARROW_SIDE = 192.dp
    }

    private data object DiscGlyphs : SpotlightGlyphs {
        override fun DrawScope.draw(cell: Int, role: SpotlightRole, size: Size, color: Color) {
            drawCircle(color, radius = size.minDimension * 0.3f)
        }
    }
}
