package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.games.tango.editor.TangoDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TangoBoardSpec math floors (design-direction §10.1/§10.4/§10.7) on the computed
 * content worst cases: a 360 dp pane with 2× Spacing.lg margins = 328 dp board →
 * pitch 82 / 54.7 / 41.0 / 32.8 dp at n4 / n6 / n8 / n10.
 */
class TangoBoardSpecTest {

    private val pane360 = 360.dp
    private val margins = 16.dp * 2 // Spacing.lg both sides
    private val board328 = pane360 - margins
    private val pitchN6 = board328 / 6
    private val pitchN8 = board328 / 8
    private val pitchN10 = board328 / 10

    /** §10.1: n6 pitch ≥ 48 dp at a 360 dp pane. */
    @Test
    fun `n6 pitch clears the touch floor on a 360dp pane`() {
        assertTrue("pitch was $pitchN6", pitchN6 >= TangoBoardSpec.touchTargetFloor)
    }

    /** §10.4: glyph ≥ 0.60 × pitch at every editor size on a 360 dp phone. */
    @Test
    fun `glyph size stays at or above the n6 fraction at every n`() {
        for (n in TangoDraft.SIZES) {
            val pitch = board328 / n
            val glyph = TangoBoardSpec.glyphSize(pitch, n)
            assertTrue("n=$n glyph was $glyph", glyph >= pitch * 0.60f)
        }
    }

    /** The 328 dp phone board's pitches the KDoc floors are computed on. */
    @Test
    fun `phone pitches match the doc arithmetic`() {
        assertEquals(54.7f, pitchN6.value, 0.1f)
        assertEquals(41.0f, pitchN8.value, 0.1f)
        assertEquals(32.8f, pitchN10.value, 0.05f)
    }

    /** The smallest solid glyph ink, the sun disc, clears the floor at every size (n10: 0.66 × 32.8 × 0.48 = 10.4 dp). */
    @Test
    fun `glyph ink clears the legibility floor at every size`() {
        for (n in TangoDraft.SIZES) {
            val disc = TangoBoardSpec.glyphSize(board328 / n, n) * TangoBoardSpec.sunDiscFraction
            assertTrue("n=$n sun disc was $disc", disc >= TangoBoardSpec.glyphInkFloor)
        }
        assertEquals(21.6f, TangoBoardSpec.glyphSize(pitchN10, 10).value, 0.05f)
        assertEquals(10.4f, (TangoBoardSpec.glyphSize(pitchN10, 10) * TangoBoardSpec.sunDiscFraction).value, 0.05f)
        assertEquals(13.9f, (TangoBoardSpec.glyphSize(pitchN10, 10) * TangoBoardSpec.moonDiscFraction).value, 0.05f)
    }

    /** The airy-8 sun's farthest ray edge stays inside the glyph box: disc/2 + gap + length + width/2 ≤ 0.5. */
    @Test
    fun `sun rays stay inside the glyph box`() {
        assertTrue("sun extent was ${TangoBoardSpec.sunExtentFraction}", TangoBoardSpec.sunExtentFraction <= 0.5f)
    }

    /** §10.5 arithmetic: violation border rides colorScheme.error (6.46/9.84) — pinned in designsystem; here the dash geometry stays pitch-derived. */
    @Test
    fun `violation dash geometry is pitch-derived`() {
        assertEquals(pitchN6 * 0.06f, TangoBoardSpec.violationDashLength(pitchN6))
        assertEquals(pitchN6 * 0.06f, TangoBoardSpec.violationDashGap(pitchN6))
    }
}
