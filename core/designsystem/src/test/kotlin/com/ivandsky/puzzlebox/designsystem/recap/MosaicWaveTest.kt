package com.ivandsky.puzzlebox.designsystem.recap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The mosaic's stamp wave: one 0..1 clock, each cell reading its own progress from it. */
class MosaicWaveTest {

    @Test
    fun `no cell has started at the start and every cell has landed at the end`() {
        for (row in 0 until MOSAIC_SIDE) for (col in 0 until MOSAIC_SIDE) {
            assertEquals(0f, mosaicCellProgress(0f, row, col), 0f)
            assertEquals(1f, mosaicCellProgress(1f, row, col), 0f)
        }
    }

    @Test
    fun `the wave runs down the diagonal, a cell never ahead of one nearer the corner`() {
        for (wave in listOf(0.2f, 0.5f, 0.8f)) {
            assertTrue(mosaicCellProgress(wave, 0, 0) >= mosaicCellProgress(wave, 2, 1))
            assertTrue(mosaicCellProgress(wave, 2, 1) >= mosaicCellProgress(wave, 4, 4))
            assertEquals(mosaicCellProgress(wave, 1, 3), mosaicCellProgress(wave, 3, 1), 0f)
        }
    }

    @Test
    fun `a cell only moves forward as the wave advances`() {
        val steps = (0..20).map { it / 20f }
        for (row in 0 until MOSAIC_SIDE) for (col in 0 until MOSAIC_SIDE) {
            val progress = steps.map { mosaicCellProgress(it, row, col) }
            assertEquals(progress.sorted(), progress)
        }
    }

    @Test
    fun `the wave takes the last cell's 42 ms step per diagonal plus one cell's own time`() {
        assertEquals((MOSAIC_SIDE - 1) * 2 * 42 + MOSAIC_CELL_MS, MOSAIC_WAVE_MS)
        // At the instant the last cell starts, it has made no progress; one cell time later it has landed.
        val lastStart = (MOSAIC_SIDE - 1) * 2 * 42f / MOSAIC_WAVE_MS
        assertEquals(0f, mosaicCellProgress(lastStart, MOSAIC_SIDE - 1, MOSAIC_SIDE - 1), 1e-5f)
    }

    @Test
    fun `a stamping cell lands from 1_15 times its size and fades in`() {
        assertEquals(1.15f, mosaicStampScale(0f), 1e-6f)
        assertEquals(1f, mosaicStampScale(1f), 0f)
        assertEquals(0f, mosaicStampAlpha(0f), 0f)
        assertEquals(1f, mosaicStampAlpha(1f), 0f)
        assertTrue(mosaicStampAlpha(0.25f) in 0.01f..0.99f)
    }
}
