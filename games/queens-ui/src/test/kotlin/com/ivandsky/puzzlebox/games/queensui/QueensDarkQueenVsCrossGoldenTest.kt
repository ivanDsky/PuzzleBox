package com.ivandsky.puzzlebox.games.queensui

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.QueensSession
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Ticket 1181: a dark 9×9 board with two queens and their auto crosses. The golden is
 * `snapshots/queens-9x9-compact-dark.png`; the sampled pixels pin that a queen's brightest
 * pixel stands clearly above an auto cross's against the same kind of fill.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class QueensDarkQueenVsCrossGoldenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `dark queens stand clearly above auto crosses`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val puzzle = QueensPuzzle(size = N, regions = List(N * N) { it / N })
        var session = QueensSession(ref = null, puzzle = puzzle)
        listOf(QUEEN_A, QUEEN_B).forEach { session = session.withQueenPlaced(Cell(it), QueensUiFixtures.T0) }
        val state = QueensPlayFixtures.boardState(session)
        val side = queensBoardSideFor(
            paneWidth = 360.dp, paneHeight = 640.dp, reservedTop = 56.dp, reservedBottom = 0.dp,
            cellsPerSide = N, maxSide = QueensBoardDefaults.cellMaxSize * N,
        )
        composeRule.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
            }
            CompositionLocalProvider(LocalConfiguration provides config) {
                PuzzleBoxTheme(darkTheme = true) { QueensPlayBoard(state, Modifier.size(side)) }
            }
        }
        composeRule.waitForIdle()
        val root = composeRule.onRoot()
        root.captureRoboImage(
            File(checkNotNull(System.getProperty("user.dir")))
                .resolve("snapshots/queens-9x9-compact-dark.png").canonicalFile.absolutePath,
        )
        val image = root.captureToImage()

        val queen = glyphContrast(image, QUEEN_A)
        val cross = glyphContrast(image, AUTO_CROSS)
        assertTrue("auto cross must still be visible, contrast $cross", cross >= 2.5f)
        assertTrue("queen $queen vs auto cross $cross", queen >= cross + MIN_QUEEN_LEAD)
    }

    // Brightest pixel in the cell's central window against the region fill: a thin glyph keeps its
    // full-coverage ink pixels there whatever the antialiasing does at the edges.
    private fun glyphContrast(image: ImageBitmap, cell: Int): Float {
        val pixels = image.toPixelMap()
        val cellPx = image.width / N.toFloat()
        val cx = ((cell % N) + 0.5f) * cellPx
        val cy = ((cell / N) + 0.5f) * cellPx
        val reach = (cellPx * 0.2f).toInt()
        var brightest = Color.Black
        for (y in (cy - reach).toInt()..(cy + reach).toInt()) for (x in (cx - reach).toInt()..(cx + reach).toInt()) {
            val c = pixels[x, y]
            if (QueensRegionPalette.contrast(c, Color.Black) > QueensRegionPalette.contrast(brightest, Color.Black)) brightest = c
        }
        return QueensRegionPalette.contrast(brightest, QueensRegionPalette.fill(cell / N, dark = true))
    }

    private companion object {
        const val N = 9
        const val QUEEN_A = 0
        const val QUEEN_B = 4 * N + 5
        // Row 0's far end: the first queen's row rules it out, so it carries an auto cross.
        const val AUTO_CROSS = 8

        /** A queen's contrast lead over an auto cross (WCAG ratio points). */
        const val MIN_QUEEN_LEAD = 1.5f
    }
}
