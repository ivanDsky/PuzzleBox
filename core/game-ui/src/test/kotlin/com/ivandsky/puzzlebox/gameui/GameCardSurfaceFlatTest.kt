package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameAccent
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.colors
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** [GameCardSurface] with flat surfaces on (nav-rethink B1): the lip's space belongs to the face. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class GameCardSurfaceFlatTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val brand = GameBrand.Neutral.copy(accent = AccentSlot.Teal)

    private lateinit var accent: GameAccent

    private fun setFlatCard(onClick: () -> Unit = {}) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false, flatSurfaces = true) {
                accent = brand.accent.colors()
                Box(Modifier.background(BACKDROP).testTag(FRAME)) {
                    GameCardSurface(
                        colors = gameCardColors(brand, offline = false),
                        onClick = onClick,
                        modifier = Modifier.size(CARD_WIDTH.dp, CARD_HEIGHT.dp).testTag(CARD),
                        motif = {},
                        content = { Box(Modifier.size(20.dp).align(Alignment.TopStart).testTag(CONTENT)) },
                    )
                }
            }
        }
    }

    @Test
    fun `flat card fills its full bounds down to the bottom edge`() {
        setFlatCard()
        val image = composeRule.onNodeWithTag(FRAME).captureToImage().toPixelMap()
        val inset = with(composeRule.density) { GameCardCornerRadius.roundToPx() }
        val bottomRow = image.height - 1
        // Between the rounded corners every pixel of the last row is the fill.
        (inset until image.width - inset).forEach { x ->
            assertEquals("x=$x", accent.accent, image[x, bottomRow])
        }
    }

    @Test
    fun `flat card shows none of the lip colour`() {
        setFlatCard()
        val image = composeRule.onNodeWithTag(FRAME).captureToImage().toPixelMap()
        val lipPixels = (0 until image.height).sumOf { y -> (0 until image.width).count { x -> image[x, y] == accent.lip } }
        assertEquals(0, lipPixels)
    }

    @Test
    fun `flat card keeps the height its caller gave it`() {
        setFlatCard()
        composeRule.onNodeWithTag(CARD).getUnclippedBoundsInRoot().let {
            assertEquals(CARD_HEIGHT.dp, it.height)
            assertEquals(CARD_WIDTH.dp, it.width)
        }
    }

    @Test
    fun `flat card content is laid out from the top of the full bounds`() {
        setFlatCard()
        val card = composeRule.onNodeWithTag(CARD).getUnclippedBoundsInRoot()
        val content = composeRule.onNodeWithTag(CONTENT, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(card.top, content.top)
        assertEquals(card.left, content.left)
    }

    @Test
    fun `flat card click fires`() {
        var clicks = 0
        setFlatCard { clicks++ }
        composeRule.onNodeWithTag(CARD).assertHasClickAction().performClick()
        composeRule.waitForIdle()
        assertEquals(1, clicks)
    }

    private companion object {
        const val CARD = "card"
        const val FRAME = "frame"
        const val CONTENT = "content"
        const val CARD_WIDTH = 184
        const val CARD_HEIGHT = 120
        val BACKDROP = Color.Magenta
    }
}
