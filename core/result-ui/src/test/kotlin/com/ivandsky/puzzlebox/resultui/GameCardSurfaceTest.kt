package com.ivandsky.puzzlebox.resultui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
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
import com.ivandsky.puzzlebox.gameui.GameBrand
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** [GameCardSurface] (nav-rethink B1): a flat face that owns its whole bounds, down to the bottom edge. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class GameCardSurfaceTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val brand = GameBrand.Neutral.copy(accent = AccentSlot.Teal)

    private lateinit var accent: GameAccent

    private fun setCard(offline: Boolean = false, onClick: () -> Unit = {}) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                accent = brand.accent.colors()
                Box(Modifier.background(BACKDROP).testTag(FRAME)) {
                    GameCardSurface(
                        colors = gameCardColors(brand, offline),
                        onClick = onClick,
                        modifier = Modifier.size(CARD_WIDTH.dp, CARD_HEIGHT.dp).testTag(CARD),
                        motif = {},
                        content = { Box(Modifier.size(20.dp).align(Alignment.TopStart).testTag(CONTENT)) },
                    )
                }
            }
        }
    }

    private fun clicksFor(offline: Boolean): Int {
        var clicks = 0
        setCard(offline) { clicks++ }
        composeRule.onNodeWithTag(CARD).assertIsEnabled().assertHasClickAction().performClick()
        composeRule.waitForIdle()
        return clicks
    }

    @Test
    fun `offline card is enabled and its click fires`() {
        assertEquals(1, clicksFor(offline = true))
    }

    @Test
    fun `online card click fires`() {
        assertEquals(1, clicksFor(offline = false))
    }

    @Test
    fun `card fills its full bounds down to the bottom edge`() {
        setCard()
        val image = composeRule.onNodeWithTag(FRAME).captureToImage().toPixelMap()
        val inset = with(composeRule.density) { GameCardCornerRadius.roundToPx() }
        val bottomRow = image.height - 1
        // Between the rounded corners every pixel of the last row is the fill.
        (inset until image.width - inset).forEach { x ->
            assertEquals("x=$x", accent.accent, image[x, bottomRow])
        }
    }

    @Test
    fun `card keeps the height its caller gave it`() {
        setCard()
        composeRule.onNodeWithTag(CARD).getUnclippedBoundsInRoot().let {
            assertEquals(CARD_HEIGHT.dp, it.height)
            assertEquals(CARD_WIDTH.dp, it.width)
        }
    }

    @Test
    fun `card content is laid out from the top of the full bounds`() {
        setCard()
        val card = composeRule.onNodeWithTag(CARD).getUnclippedBoundsInRoot()
        val content = composeRule.onNodeWithTag(CONTENT, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(card.top, content.top)
        assertEquals(card.left, content.left)
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
