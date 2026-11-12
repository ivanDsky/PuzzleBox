package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import com.ivandsky.puzzlebox.game.replay.Lead
import com.ivandsky.puzzlebox.game.replay.Timeline

private const val NANOS_PER_MS = 1_000_000L

/**
 * Remembers a playback for the given sides and feeds it frame time while it is playing.
 * Leaving the composition pauses it.
 */
@Composable
fun rememberReplayPlayback(mine: Timeline?, theirs: Timeline?, finalLead: Lead? = null): ReplayPlayback {
    val playback = remember(mine, theirs, finalLead) { ReplayPlayback(mine, theirs, finalLead) }
    val playing by playback.state.collectAsState()
    DisposableEffect(playback) { onDispose { playback.pause() } }
    LaunchedEffect(playback, playing.playing) {
        if (!playing.playing) return@LaunchedEffect
        var lastNanos = withFrameNanos { it }
        while (true) {
            val nowNanos = withFrameNanos { it }
            val dtMs = (nowNanos - lastNanos) / NANOS_PER_MS
            // Only the whole milliseconds are handed over; the rest rolls into the next frame.
            lastNanos += dtMs * NANOS_PER_MS
            if (dtMs > 0) playback.advance(dtMs)
        }
    }
    return playback
}
