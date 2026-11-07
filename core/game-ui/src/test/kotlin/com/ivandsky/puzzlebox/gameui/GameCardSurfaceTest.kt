package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class GameCardSurfaceTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val brand = GameBrand.Neutral.copy(accent = AccentSlot.Teal)

    private lateinit var accent: GameAccent

    private fun setCard(offline: Boolean, onClick: () -> Unit = {}) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                accent = brand.accent.colors()
                // The card's own node bounds are its face; the frame captures the lip below it.
                Box(Modifier.background(BACKDROP).testTag(FRAME)) {
                    GameCardSurface(
                        colors = gameCardColors(brand, offline),
                        onClick = onClick,
                        modifier = Modifier.size(184.dp, 156.dp).testTag(CARD),
                        motif = {},
                        content = {},
                    )
                }
            }
        }
    }

    /** The centre-column pixels half a lip above the bottom edge, and two lips above it. */
    private fun lipAndFace(): Pair<Color, Color> {
        val image = composeRule.onNodeWithTag(FRAME).captureToImage().toPixelMap()
        val (lipRow, faceRow) = with(composeRule.density) {
            image.height - (GameCardLipDepth / 2).roundToPx() to image.height - (GameCardLipDepth * 2).roundToPx()
        }
        return image[image.width / 2, lipRow] to image[image.width / 2, faceRow]
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
    fun `online card face sits on a same-hue lip along its bottom edge`() {
        setCard(offline = false)
        assertEquals(accent.lip to accent.accent, lipAndFace())
    }

    @Test
    fun `offline card sits flat`() {
        setCard(offline = true)
        assertEquals(BACKDROP, lipAndFace().first)
    }

    private companion object {
        const val CARD = "card"
        const val FRAME = "frame"
        val BACKDROP = Color.Magenta
    }
}
