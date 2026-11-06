package com.ivandsky.puzzlebox.designsystem

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The open visual choices the debug design settings switch at runtime (spec A7): the
 * accent palette, whether game glyphs default to solid, and the solve sweep's length in
 * ms. [Default] is what ships; release never changes it. Losing options are deleted
 * once a choice is made.
 */
data class DesignVariants(val accentSet: AccentSet, val glyphSolid: Boolean, val sweepMs: Int) {
    companion object {
        val Default = DesignVariants(
            accentSet = AccentSet.Vivid,
            glyphSolid = false,
            sweepMs = MotionTokens.SweepDuration,
        )
    }
}

/**
 * The active [DesignVariants], provided by [PuzzleBoxTheme] from [DesignVariantsStore].
 * Outside the theme it is [DesignVariants.Default].
 */
val LocalDesignVariants = staticCompositionLocalOf { DesignVariants.Default }
