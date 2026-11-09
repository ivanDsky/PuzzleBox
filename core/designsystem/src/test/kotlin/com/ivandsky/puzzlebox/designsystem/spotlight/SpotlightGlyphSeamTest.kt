package com.ivandsky.puzzlebox.designsystem.spotlight

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.designsystem.LightColorScheme
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/**
 * A fake game glyph that records every call; with [paint] it fills its whole DrawScope (ignoring its `size` argument,
 * as a careless glyph might) in [MARKER] so tests can find where the overlay put it.
 */
internal class RecordingGlyphs(private val paint: Boolean = false) : SpotlightGlyphs {
    data class Call(val cell: Int, val role: SpotlightRole, val size: Size, val color: Color)

    val calls = mutableListOf<Call>()

    override fun DrawScope.draw(cell: Int, role: SpotlightRole, size: Size, color: Color) {
        calls += Call(cell, role, size, color)
        if (paint) drawRect(MARKER)
    }

    companion object {
        val MARKER = Color.Magenta
    }
}

/** Which glyph calls the overlay makes per role through `spotlight.glyphs`, and what it draws without them. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SpotlightGlyphSeamTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val scheme = LightColorScheme

    /** Shows [spotlight] at its final frame (reduced motion) over a black 300dp board; returns a swap for its glyphs. */
    private fun show(spotlight: Spotlight): (SpotlightGlyphs) -> Unit {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        var current by mutableStateOf(spotlight)
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Box(Modifier.size(300.dp).background(Color.Black).testTag(BOARD)) { SpotlightOverlay(current) }
            }
        }
        composeRule.waitForIdle()
        return { glyphs -> current = current.copy(glyphs = glyphs) }
    }

    private fun capture(): ImageBitmap = composeRule.onNodeWithTag(BOARD).captureToImage()

    private fun cellSize(image: ImageBitmap, side: Int) = Size(image.width.toFloat() / side, image.height.toFloat() / side)

    private fun ImageBitmap.at(point: Offset): Color = toPixelMap()[point.x.roundToInt(), point.y.roundToInt()]

    /** Pixels of [color] in the top-left quarter of the centre cell of a 3×3 board: where the culprit badge sits. */
    private fun ImageBitmap.badgePixels(color: Color): Int {
        val map = toPixelMap()
        val cell = cellSize(this, 3)
        val x0 = cell.width.roundToInt()
        val y0 = cell.height.roundToInt()
        return (x0 until x0 + x0 / 2).sumOf { x -> (y0 until y0 + y0 / 2).count { y -> map[x, y] == color } }
    }

    @Test
    fun `target, excluded and culprit ask the glyph for their own cells in their own colours`() {
        val glyphs = RecordingGlyphs()
        val roles = mapOf(
            4 to SpotlightRole.TARGET,
            0 to SpotlightRole.EXCLUDED,
            2 to SpotlightRole.EXCLUDED,
            3 to SpotlightRole.EVIDENCE,
            8 to SpotlightRole.CULPRIT,
        )
        show(Spotlight(3, 3, roles, callout = null, glyphs = glyphs, playId = 0))
        glyphs.calls.clear()
        val cell = cellSize(capture(), 3)
        assertEquals(
            setOf(
                RecordingGlyphs.Call(4, SpotlightRole.TARGET, cell, SpotlightTokens.targetGhost(scheme)),
                RecordingGlyphs.Call(0, SpotlightRole.EXCLUDED, cell, SpotlightTokens.excluded(scheme)),
                RecordingGlyphs.Call(2, SpotlightRole.EXCLUDED, cell, SpotlightTokens.excluded(scheme)),
                RecordingGlyphs.Call(8, SpotlightRole.CULPRIT, cell, SpotlightTokens.culprit(scheme)),
            ),
            glyphs.calls.toSet(),
        )
    }

    @Test
    fun `a glyph that draws nothing still leaves the dim, the lit cell and the culprit badge`() {
        show(Spotlight(3, 3, mapOf(4 to SpotlightRole.CULPRIT), callout = null, glyphs = { _, _, _, _ -> }, playId = 0))
        val image = capture()
        val cell = cellSize(image, 3)
        assertNotEquals(0, image.badgePixels(SpotlightTokens.culprit(scheme)))
        assertEquals(Color.Black, image.at(Offset(cell.width * 1.7f, cell.height * 1.7f)))
        assertNotEquals(Color.Black, image.at(Offset(cell.width / 2, cell.height / 2)))
    }

    @Test
    fun `callout and badge land in the same place whatever the glyph draws`() {
        val roles = mapOf(4 to SpotlightRole.CULPRIT, 3 to SpotlightRole.EVIDENCE)
        val swap = show(Spotlight(3, 3, roles, SpotlightCallout("Wrong queen: hint again removes it.", setOf(4)), RecordingGlyphs(paint = true), playId = 0))
        val drawn = capture()
        val drawnCallout = composeRule.onNodeWithTag(SpotlightCalloutTag).getBoundsInRoot()
        swap { _, _, _, _ -> }
        composeRule.waitForIdle()
        val blank = capture()
        val cell = cellSize(blank, 3)
        assertEquals(drawnCallout, composeRule.onNodeWithTag(SpotlightCalloutTag).getBoundsInRoot())
        val badge = SpotlightTokens.culprit(scheme)
        assertNotEquals(0, blank.badgePixels(badge))
        assertEquals(drawn.badgePixels(badge), blank.badgePixels(badge))
        assertEquals(drawn.at(Offset(cell.width / 2, cell.height * 2.5f)), blank.at(Offset(cell.width / 2, cell.height * 2.5f)))
    }

    private companion object {
        const val BOARD = "board"
    }
}
