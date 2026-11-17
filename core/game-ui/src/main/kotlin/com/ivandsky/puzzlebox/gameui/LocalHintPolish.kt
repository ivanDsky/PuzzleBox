package com.ivandsky.puzzlebox.gameui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * HintPolish (backlog #1172) as the play controls below read it: true when the Hint button is drawn as a peer of Undo
 * (the neutral well, no accent fill). Nothing provides it but the play host, and no provider reads as off.
 */
val LocalHintPolish: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }
