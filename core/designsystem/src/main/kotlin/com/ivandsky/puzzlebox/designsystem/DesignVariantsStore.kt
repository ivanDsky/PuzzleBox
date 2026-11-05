package com.ivandsky.puzzlebox.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Holds the app-wide [DesignVariants] as Compose state, so a change re-themes every
 * [PuzzleBoxTheme] live. Only the debug design settings write it; release always
 * reads [DesignVariants.Default].
 */
object DesignVariantsStore {
    var current by mutableStateOf(DesignVariants.Default)
}
