package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The face draws region borders and the outline in the wall token at
 * the geometry's wall weight, and thin cell lines that stay lighter than the borders. The 4×4 fixture's regions are its rows, so every horizontal cell line is a region border and every
 * vertical one a plain cell line.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class QueensReadableBoardFaceTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val model = QueensBoardModel.of(QueensUiFixtures.session())

    private class Face(val width: Int, val pixels: IntArray, val wall: Color) {
        val cell: Int get() = width / 4

        operator fun get(x: Int, y: Int): Int = pixels[y * width + x]
    }

    private fun face(): Face {
        var wall = Color.Unspecified
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                wall = LocalBoardColors.current.boardWall
                QueensBoardFace(model, Modifier.size(320.dp))
            }
        }
        composeRule.waitForIdle()
        val image = composeRule.onRoot().captureToImage()
        val pixels = IntArray(image.width * image.height)
        image.readPixels(pixels)
        return Face(image.width, pixels, wall)
    }

    private fun Face.px(dp: Float): Int = (dp * width / 320f).toInt()

    @Test
    fun `a region border is the wall token at the wall weight`() {
        val face = face()
        // 1 dp off the border between rows 0 and 1, mid-cell: inside the 3 dp region wall on either side.
        assertEquals(face.wall.toArgb(), face[face.cell / 2, face.cell + face.px(1f)])
        assertEquals(face.wall.toArgb(), face[face.cell / 2, face.cell - face.px(1f)])
    }

    @Test
    fun `a region border is thinner than the board outline`() {
        val face = face()
        // 2 dp below the border between rows 0 and 1: the 3 dp region wall ends 1.5 dp off it, so this is row 1's bare fill.
        val fill = QueensRegionPalette.fill(model.regions[model.n], dark = false).toArgb()
        assertEquals(fill, face[face.cell / 2, face.cell + face.px(2f)])
    }

    @Test
    fun `the board outline is the wall token at the wall weight`() {
        val face = face()
        // 3 dp in from the left edge, mid-cell: inside a 4.5 dp outline, outside a 1.5 dp one.
        assertEquals(face.wall.toArgb(), face[face.px(3f), face.cell / 2])
    }

    @Test
    fun `a cell line inside a region is thin and lighter than the wall`() {
        val face = face()
        val fill = QueensRegionPalette.fill(model.regions[0], dark = false).toArgb()
        // The line between columns 0 and 1 of row 0, mid-height: tinted, but neither the wall nor the bare fill.
        val line = face[face.cell, face.cell / 2]
        assertEquals("the cell line vanished", false, line == fill)
        assertEquals("the cell line is as strong as a wall", false, line == face.wall.toArgb())
        // 1.5 dp off that line the fill is bare: the wall weight is for region borders only.
        assertEquals(fill, face[face.cell + face.px(1.5f), face.cell / 2])
    }
}
