package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The face draws a board from the model alone: no state holder, no motion provided. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class QueensBoardFaceTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `face paints the model's region fill with no controller or motion`() {
        val model = QueensBoardModel.of(QueensUiFixtures.session())
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                QueensBoardFace(model, Modifier.size(320.dp))
            }
        }
        composeRule.waitForIdle()
        val image = composeRule.onRoot().captureToImage()
        val px = IntArray(image.width * image.height)
        image.readPixels(px)
        val centre = px[image.height / 8 * image.width + image.width / 8]
        assertEquals(
            QueensRegionPalette.fill(model.regions[0], dark = false).toArgb(),
            centre,
        )
    }
}
