package com.ivandsky.puzzlebox.app.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.isEnabled
import org.koin.compose.currentKoinScope

/**
 * The app graph's source of the TabletCrossfade flag (ADR-0022): read once when the graph builds it, so the nav shell
 * only ever sees the Boolean.
 */
internal class TabletCrossfadeSource(flags: ReleaseFlags) {
    val enabled: Boolean = flags.isEnabled(ReleaseFlag.TabletCrossfade)
}

/** The graph's [TabletCrossfadeSource] value; a graph that binds none (a test graph) reads as off. */
@Composable
internal fun rememberTabletCrossfade(): Boolean {
    val scope = currentKoinScope()
    return remember(scope) { scope.getOrNull<TabletCrossfadeSource>()?.enabled == true }
}
