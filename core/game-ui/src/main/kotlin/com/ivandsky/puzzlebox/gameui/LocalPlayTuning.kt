package com.ivandsky.puzzlebox.gameui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The play tuning for the screens below. Null (nothing provides it: previews, the tutorial host)
 * keeps the legacy layout.
 */
val LocalPlayTuning: ProvidableCompositionLocal<PlayTuning?> = staticCompositionLocalOf { null }
