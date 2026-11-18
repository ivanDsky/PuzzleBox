package com.ivandsky.puzzlebox.gameui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * ZipDotPulse as the Zip board reads it: true when the next number holds the trace colour and ripples a halo instead of
 * a separate ring blinking round it. Nothing provides it but the play host and the replay screens, and no provider
 * reads as off.
 */
val LocalZipDotPulse: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }
