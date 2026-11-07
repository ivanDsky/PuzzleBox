package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LightBoardTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.tango.render.TangoRendererModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The hint cell (spec A14): an inset orange tile sitting on its own darker lip. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
class TangoHintTileTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the hint cell is an inset tile on its lip`() {
        val model = TangoRendererModel(
            n = 6,
            cells = List(36) { TangoRendererModel.CellRender(value = null, isGiven = false) },
            markers = emptyList(),
            rejection = null,
            status = TangoRendererModel.Status.PLAYING,
            hintCell = 14,
            hintWhy = null,
        )
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                TangoBoardRenderer().Content(model, Modifier.fillMaxWidth())
            }
        }

        val image = composeRule.onNodeWithContentDescription("Row 3, column 3", substring = true)
            .captureToImage().toPixelMap()
        val lipRow = image.height - 1 - with(composeRule.density) { 4.dp.toPx() }.toInt()
        assertEquals(LightBoardTokens.tangoHintFill, image[image.width / 2, image.height / 2])
        assertEquals(LightBoardTokens.tangoHintLip, image[image.width / 2, lipRow])
        assertNotEquals(LightBoardTokens.tangoHintFill, image[1, 1])
    }
}
