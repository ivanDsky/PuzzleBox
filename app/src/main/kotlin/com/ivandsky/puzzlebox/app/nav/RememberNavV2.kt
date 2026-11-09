package com.ivandsky.puzzlebox.app.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag.NavV2
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import org.koin.compose.koinInject

/**
 * Whether [NavV2] was on when this launch started: read once and saved, so a flip takes
 * effect on relaunch and a debug override never reshuffles the tabs under a running screen,
 * not even across a rotation that recreates the activity. The app's one read of the flag.
 */
@Composable
internal fun rememberNavV2(): Boolean {
    val flags = koinInject<ReleaseFlags>()
    return rememberSaveable { NavV2 in flags.enabled.value }
}
