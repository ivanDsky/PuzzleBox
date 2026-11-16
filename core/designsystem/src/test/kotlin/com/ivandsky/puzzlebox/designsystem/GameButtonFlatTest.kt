package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// The flat game button (nav-rethink B1): with flat on, a Tall button is 64dp in all, its bottom
// edge is the face rather than a lip, a press scales and tints it but never sinks it, and a
// disabled one stays put as the muted wash; its cooldown ring hugs the face from the very top.
// Pixels are read at 1dp = 1px (the Robolectric default), a row in from the edge to dodge
// anti-aliasing.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class GameButtonFlatTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var teal: GameAccent

    private fun show(content: @Composable () -> Unit) {
        composeRule.setContent {
            PuzzleBoxTheme(flatSurfaces = true) {
                teal = AccentSlot.Teal.colors()
                Box(Modifier.background(Color.White)) { content() }
            }
        }
    }

    @Test
    fun `flat, a Tall game button measures exactly 64dp`() {
        show { GameButton(onClick = {}, icon = PuzzleIcons.Hint, label = "Hint", accent = AccentSlot.Teal, size = GameButtonSize.Tall, modifier = Modifier.testTag(BUTTON)) }

        composeRule.onNodeWithTag(BUTTON).assertHeightIsEqualTo(64.dp)
    }

    @Test
    fun `flat, the bottom edge is the face and no lip colour shows`() {
        show { GameButton(onClick = {}, icon = PuzzleIcons.Hint, label = "Hint", accent = AccentSlot.Teal, size = GameButtonSize.Tall, modifier = Modifier.testTag(BUTTON)) }
        composeRule.waitForIdle()

        val image = composeRule.onNodeWithTag(BUTTON).captureToImage()
        val bottom = image.toPixelMap()[image.width / 2, image.height - 2]

        assertEquals(teal.accent, bottom)
    }

    @Test
    fun `flat, a click still fires`() {
        var clicks = 0
        show { GameButton(onClick = { clicks++ }, icon = PuzzleIcons.Undo, label = "Undo", size = GameButtonSize.Tall, modifier = Modifier.testTag(BUTTON)) }

        composeRule.onNodeWithTag(BUTTON).performClick()
        composeRule.waitForIdle()

        assertEquals(1, clicks)
    }

    @Test
    fun `flat, a held press does not sink the face`() {
        show { GameButton(onClick = {}, icon = PuzzleIcons.Hint, label = "Hint", accent = AccentSlot.Teal, size = GameButtonSize.Tall, modifier = Modifier.testTag(BUTTON)) }

        composeRule.onNodeWithTag(BUTTON).performTouchInput { down(center) }
        composeRule.waitForIdle()

        // The 0.97 scale pulls the edge in by a pixel; a sink would leave the top 4 rows empty.
        val image = composeRule.onNodeWithTag(BUTTON).captureToImage()
        val top = image.toPixelMap()[image.width / 2, 3]
        assertTrue("the face is still under the top edge, got $top", top.alpha > 0.99f && top != Color.White)
    }

    @Test
    fun `flat, a disabled button does not fire and does not drop by the lip`() {
        var clicks = 0
        show { GameButton(onClick = { clicks++ }, icon = PuzzleIcons.Redo, label = "Redo", enabled = false, size = GameButtonSize.Tall, modifier = Modifier.testTag(BUTTON)) }
        composeRule.onNodeWithTag(BUTTON).performClick()
        composeRule.waitForIdle()

        val image = composeRule.onNodeWithTag(BUTTON).captureToImage()
        val top = image.toPixelMap()[image.width / 2, 1]

        assertEquals(0, clicks)
        assertTrue("the face starts at the top edge, got $top", top.alpha > 0.99f && top != Color.White)
    }

    @Test
    fun `flat, the cooldown ring hugs the face from the top edge`() {
        show {
            GameButton(
                onClick = {},
                icon = PuzzleIcons.Hint,
                label = "Hint",
                enabled = false,
                accent = AccentSlot.Teal,
                size = GameButtonSize.Tall,
                modifier = Modifier.testTag(BUTTON).cooldownRing({ 1f }, AccentSlot.Teal),
            )
        }
        composeRule.waitForIdle()

        val image = composeRule.onNodeWithTag(BUTTON).captureToImage()

        assertEquals(teal.accent, image.toPixelMap()[image.width / 2, 1])
    }

    private companion object {
        const val BUTTON = "button"
    }
}
