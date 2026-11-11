package com.ivandsky.puzzlebox.app.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag.LinkedInAccount
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import org.koin.compose.koinInject

/**
 * The app's one read of [LinkedInAccount]: read once per launch and saved, so a flip lands on
 * relaunch and a debug override never reshuffles a running screen. The shell's default passes
 * the value to the Play and Profile entries as a plain Boolean.
 */
@Composable
internal fun rememberLinkedIn(): Boolean {
    val flags = koinInject<ReleaseFlags>()
    return rememberSaveable { LinkedInAccount in flags.enabled.value }
}
