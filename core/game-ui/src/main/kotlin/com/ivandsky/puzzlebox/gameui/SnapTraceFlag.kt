package com.ivandsky.puzzlebox.gameui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags

/**
 * Whether the traced line on Zip and Strands is drawn the Snap way (no live tip, no pop,
 * 70 ms grow). Game boards read only this local; [SnapTraceHost] is the one place that reads
 * the release flag. Outside a host it is off.
 */
val LocalSnapTrace = staticCompositionLocalOf { false }

/** Provides [LocalSnapTrace] from [ReleaseFlag.SnapTrace] to [content]; a debug override applies live. */
@Composable
fun SnapTraceHost(releaseFlags: ReleaseFlags, content: @Composable () -> Unit) {
    val enabled by releaseFlags.enabled.collectAsState()
    CompositionLocalProvider(LocalSnapTrace provides (ReleaseFlag.SnapTrace in enabled), content = content)
}
