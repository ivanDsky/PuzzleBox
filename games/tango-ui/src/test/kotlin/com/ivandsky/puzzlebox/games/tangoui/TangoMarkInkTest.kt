package com.ivandsky.puzzlebox.games.tangoui

import android.graphics.Color
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.render.TangoRendererModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The drawn `=`/`×` ink on a 328 dp phone board (a 360 dp pane less 2 × Spacing.lg), measured
 * from the rendered play board: the dark pixels inside the mark's badge disc. The mark font is
 * pitch × [TangoBoardSpec.markTextFraction]; this pins what that font really puts on screen.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w420dp-h900dp-xxxhdpi")
class TangoMarkInkTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `the 10x10 mark ink is at least 0_9 of the 8x8 mark ink`() {
        val ink = inkBySize(listOf(8, 10))
        TangoMarkerKind.entries.forEach { kind ->
            val n8 = ink.getValue(8 to kind)
            val n10 = ink.getValue(10 to kind)
            assertTrue("$kind n10 width ${n10.width} vs n8 ${n8.width}", n10.width >= n8.width * N10_TO_N8)
            assertTrue("$kind n10 height ${n10.height} vs n8 ${n8.height}", n10.height >= n8.height * N10_TO_N8)
        }
    }

    @Test
    fun `the 6x6 daily mark ink stays at its shipped size`() {
        val ink = inkBySize(listOf(6))
        TangoMarkerKind.entries.forEach { kind ->
            val shipped = N6_SHIPPED.getValue(kind)
            val drawn = ink.getValue(6 to kind)
            assertEquals("$kind n6 width", shipped.width.value, drawn.width.value, TOLERANCE_DP)
            assertEquals("$kind n6 height", shipped.height.value, drawn.height.value, TOLERANCE_DP)
        }
    }

    /** Ink box of each mark kind at each board width in [sizes], one composition. */
    private fun inkBySize(sizes: List<Int>): Map<Pair<Int, TangoMarkerKind>, DpSize> {
        val model = mutableStateOf<TangoRendererModel?>(null)
        rule.setContent {
            PuzzleBoxTheme { model.value?.let { TangoBoardRenderer().Content(it, Modifier.width(BOARD).testTag(TAG)) } }
        }
        return sizes.flatMap { n -> TangoMarkerKind.entries.map { n to it } }.associateWith { (n, kind) ->
            val cell = n + 1
            model.value = TangoRendererModel(
                n = n,
                cells = List(n * n) { TangoRendererModel.CellRender(null, isGiven = false) },
                markers = listOf(TangoRendererModel.MarkerRender(cell, TangoEdgeSide.RIGHT, kind)),
                rejection = null,
                status = TangoRendererModel.Status.IDLE,
                ghosts = emptyMap(),
            )
            rule.waitForIdle()
            inkAt(n, cell)
        }
    }

    /** Dark-pixel box inside the badge disc on [cell]'s right edge (the disc ring sits outside 0.12 pitch). */
    private fun inkAt(n: Int, cell: Int): DpSize {
        val bitmap = rule.onNodeWithTag(TAG).captureToImage().asAndroidBitmap()
        val density = rule.density.density
        val pitch = bitmap.width / n.toFloat()
        val cx = (cell % n + 1) * pitch
        val cy = (cell / n + 0.5f) * pitch
        val radius = pitch * 0.12f
        var minX = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var minY = Int.MAX_VALUE
        var maxY = Int.MIN_VALUE
        for (x in (cx - radius).toInt()..(cx + radius).toInt()) {
            for (y in (cy - radius).toInt()..(cy + radius).toInt()) {
                if ((x - cx) * (x - cx) + (y - cy) * (y - cy) > radius * radius) continue
                val p = bitmap.getPixel(x, y)
                if ((Color.red(p) + Color.green(p) + Color.blue(p)) / 3 < 128) {
                    minX = minOf(minX, x)
                    maxX = maxOf(maxX, x)
                    minY = minOf(minY, y)
                    maxY = maxOf(maxY, y)
                }
            }
        }
        assertTrue("no mark ink at n=$n", maxX >= minX)
        return DpSize(((maxX - minX + 1) / density).dp, ((maxY - minY + 1) / density).dp)
    }

    private companion object {
        const val TAG = "tango.ink.board"
        val BOARD = 328.dp

        /** The 10×10 mark reads about as legibly as the 8×8 one. */
        const val N10_TO_N8 = 0.9f

        /** Measured n6 ink (the daily, sized by hand in user QA round 4), at this test's xxxhdpi. */
        val N6_SHIPPED = mapOf(
            TangoMarkerKind.SAME to DpSize(8.25.dp, 6.0.dp),
            TangoMarkerKind.OPPOSITE to DpSize(7.75.dp, 7.75.dp),
        )
        const val TOLERANCE_DP = 0.3f
    }
}
