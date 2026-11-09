package com.ivandsky.puzzlebox.designsystem

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Fires once when the user taps the selected tab while it sits at its root. A tab root
 * listens in a `LaunchedEffect` and scrolls its list to the top. The event is fire and
 * forget: no replay, dropped when nothing listens, so a root that composes later never
 * scrolls for an old tap. The app provides the live flow; the default never fires.
 */
val LocalTabReselect = staticCompositionLocalOf<SharedFlow<Unit>> { MutableSharedFlow() }
