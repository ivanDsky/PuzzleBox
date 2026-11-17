package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.koin.compose.currentKoinScope

/**
 * The TabletCrossfade flag as feature/play's DI read it once per graph (backlog #1118): with it on, the two-pane
 * Games right pane crossfades when another row is picked. [rememberPaneCrossfade] hands it to the library screen.
 */
internal class PaneCrossfade(val enabled: Boolean)

/** The graph's [PaneCrossfade]; a graph that binds none (an isolated route test, a golden host) reads as off. */
@Composable
internal fun rememberPaneCrossfade(): Boolean {
    val scope = currentKoinScope()
    return remember(scope) { scope.getOrNull<PaneCrossfade>()?.enabled == true }
}
