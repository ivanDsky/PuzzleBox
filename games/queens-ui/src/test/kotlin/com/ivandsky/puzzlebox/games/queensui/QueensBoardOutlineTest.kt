package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class QueensBoardOutlineTest {

    /** One region, so every edge pixel near the border comes from the outline alone. */
    @Test
    fun `board outline is full-strength ink`() {
        val n = 4
        val model = QueensBoardModel(
            n = n,
            regions = List(n * n) { 0 },
            visuals = List(n * n) { QueensCellVisual.EMPTY },
            queens = emptyList(),
            violations = emptySet(),
            hintRegion = null,
            hintCells = emptySet(),
            hintCell = null,
            win = false,
        )
        val bitmap = ImageBitmap(200, 200)
        CanvasDrawScope().draw(Density(2f), LayoutDirection.Ltr, Canvas(bitmap), Size(200f, 200f)) {
            drawCellEdges(model, hintColor = Color.Red, ink = Color.Black)
        }
        val pixels = bitmap.toPixelMap()
        // 1.5dp at density 2 = 3px: pixels 0..2 are fully inside the outline stroke.
        assertEquals(1f, pixels[1, 100].alpha, 0.01f)
        assertEquals(1f, pixels[100, 1].alpha, 0.01f)
        assertEquals(1f, pixels[198, 100].alpha, 0.01f)
        assertEquals(1f, pixels[100, 198].alpha, 0.01f)
    }
}
