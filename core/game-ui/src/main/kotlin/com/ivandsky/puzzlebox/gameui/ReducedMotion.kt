package com.ivandsky.puzzlebox.gameui

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The system's reduced-motion signal (design-direction §11: promoted from feature/play
 * so both games share it). The app-root convention in PuzzleBoxApp: animator scale 0
 * collapses pattern motion (Zip next-target breath / hint-arrow fade, Tango violation
 * pulse / caption fades) to instant state changes. Guarded — preview/layoutlib
 * environments have no real settings table.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
}
