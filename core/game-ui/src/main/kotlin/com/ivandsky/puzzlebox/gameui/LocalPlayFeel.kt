package com.ivandsky.puzzlebox.gameui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * PlayFeel (spec A19) as the play screens below read it: true when the feature is on. A game picks its new feel or
 * its legacy one from this; nothing provides it but the play host, and no provider reads as off.
 */
val LocalPlayFeel: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }
