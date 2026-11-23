package com.ivandsky.puzzlebox.designsystem.recap

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import com.ivandsky.puzzlebox.designsystem.MotionTokens

/** Each diagonal of the mosaic starts this long after the one before it. */
internal const val MOSAIC_STEP_MS = 42

/** One cell's own stamp, from its start to landed. */
internal const val MOSAIC_CELL_MS = 360

/** The whole wave on one clock: the last diagonal's start, plus its cell's own time. */
internal const val MOSAIC_WAVE_MS = (MOSAIC_SIDE - 1) * 2 * MOSAIC_STEP_MS + MOSAIC_CELL_MS

private const val STAMP_OVERSHOOT = 1.6f

/**
 * How far the cell at [row], [col] has stamped in when the whole wave is at [wave] (0..1 of [MOSAIC_WAVE_MS]): 0 before
 * its diagonal starts, 1 once it has landed. The mosaic animates one number and every cell reads its own share of it at
 * draw time, so a plate costs one animation and no composition per frame, not one transition per cell.
 */
internal fun mosaicCellProgress(wave: Float, row: Int, col: Int): Float {
    val elapsedMs = wave * MOSAIC_WAVE_MS - (row + col) * MOSAIC_STEP_MS
    return (elapsedMs / MOSAIC_CELL_MS).coerceIn(0f, 1f)
}

/** A stamping cell's size: from [MotionTokens.StampFromScale] down to 1, easing past it a touch like the stamp spring. */
internal fun mosaicStampScale(progress: Float): Float {
    val from = MotionTokens.StampFromScale
    return from + (1f - from) * StampSettle.transform(progress)
}

/** A stamping cell's opacity: in over the first half of its own time. */
internal fun mosaicStampAlpha(progress: Float): Float = FastOutSlowInEasing.transform((progress * 2f).coerceIn(0f, 1f))

private val StampSettle = Easing { x ->
    if (x <= 0f) 0f else if (x >= 1f) 1f else {
        val t = x - 1f
        1f + (STAMP_OVERSHOOT + 1f) * t * t * t + STAMP_OVERSHOOT * t * t
    }
}
