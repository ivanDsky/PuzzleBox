package com.ivandsky.puzzlebox.app.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.isEnabled
import org.koin.compose.currentKoinScope

/**
 * The app graph's TabletCrossfade flag, read once per graph (ADR-0022): the nav shell hands it to [navMotion]. A graph
 * without ReleaseFlags (a test graph) reads as off.
 */
@Composable
internal fun rememberTabletCrossfade(): Boolean {
    val scope = currentKoinScope()
    return remember(scope) { scope.getOrNull<ReleaseFlags>()?.isEnabled(ReleaseFlag.TabletCrossfade) == true }
}
