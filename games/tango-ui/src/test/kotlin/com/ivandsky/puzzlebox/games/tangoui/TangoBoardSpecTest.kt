package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TangoBoardSpec math floors (design-direction §10.1/§10.4/§10.7) on the computed
 * content worst cases: a 360 dp pane with 2× Spacing.lg margins = 328 dp board →
 * pitch 54.7 dp at n6, 41.0 dp at n8.
 */
class TangoBoardSpecTest {

    private val pane360 = 360.dp
    private val margins = 16.dp * 2 // Spacing.lg both sides
    private val board328 = pane360 - margins
    private val pitchN6 = board328 / 6
    private val pitchN8 = board328 / 8

    /** §10.1: n6 pitch ≥ 48 dp at a 360 dp pane. */
    @Test
    fun `n6 pitch clears the touch floor on a 360dp pane`() {
        assertTrue("pitch was $pitchN6", pitchN6 >= TangoBoardSpec.touchTargetFloor)
    }

    /** §10.4: mark cap ≥ 10.3 dp at every supported n on a 360 dp phone. */
    @Test
    fun `mark height clears the legibility floor at n6 and n8`() {
        for (n in 6..8) {
            val pitch = board328 / n
            val mark = TangoBoardSpec.markSize(pitch, n)
            assertTrue("n=$n mark was $mark", mark >= TangoBoardSpec.markHeightFloor)
        }
    }

    /** §10.4: glyph ≥ 0.60 × pitch at every supported n on a 360 dp phone. */
    @Test
    fun `glyph size stays at or above the n6 fraction at every n`() {
        for (n in 6..8) {
            val pitch = board328 / n
            val glyph = TangoBoardSpec.glyphSize(pitch, n)
            assertTrue("n=$n glyph was $glyph", glyph >= pitch * 0.60f)
        }
    }

    /** Computed worst case from §1: n8 mark = 0.30 × 41.0 = 12.3 dp ≥ 10.3. */
    @Test
    fun `n8 worst case matches the doc arithmetic`() {
        assertEquals(12.3f, TangoBoardSpec.markSize(pitchN8, 8).value, 0.05f)
        assertEquals(54.7f, pitchN6.value, 0.1f)
        assertEquals(41.0f, pitchN8.value, 0.1f)
    }

    /** §10.7: chooser hit area never smaller than the cell, and ≥ 48 dp where the cell is. */
    @Test
    fun `chooser target is never smaller than the cell`() {
        for (n in 6..8) {
            val pitch = board328 / n
            val target = TangoBoardSpec.chooserTargetSize(pitch, n)
            assertTrue("n=$n target $target < cell $pitch", target >= pitch)
        }
        assertTrue(
            TangoBoardSpec.chooserTargetSize(pitchN6, 6) >= TangoBoardSpec.touchTargetFloor,
        )
    }

    /** §10.5 arithmetic: violation border rides colorScheme.error (6.46/9.84) — pinned in designsystem; here the dash geometry stays pitch-derived. */
    @Test
    fun `violation dash geometry is pitch-derived`() {
        assertEquals(pitchN6 * 0.06f, TangoBoardSpec.violationDashLength(pitchN6))
        assertEquals(pitchN6 * 0.06f, TangoBoardSpec.violationDashGap(pitchN6))
    }
}
