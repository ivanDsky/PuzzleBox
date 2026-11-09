package com.ivandsky.puzzlebox.gameui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * A route's one line for the play screen's caption slot (e.g. "Duel vs Sam · puzzle 1 of 3").
 * It is the lowest-priority caption source: [PlayChrome] shows the game's violation, rejection
 * or hint caption while one is showing and this line otherwise. Null (no provider) leaves the
 * slot empty, so every game's chrome is unchanged until a route provides it.
 */
val LocalRouteCaption: ProvidableCompositionLocal<String?> = staticCompositionLocalOf { null }
