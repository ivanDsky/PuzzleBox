package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ivandsky.puzzlebox.game.playTimeMs
import com.ivandsky.puzzlebox.games.tango.TangoSession
import java.time.Instant

/** The running timer window (F2 §2: active foreground play only); folds into [TangoSession.elapsedMs] on pause/background. */
internal class TangoRunClock(private val now: () -> Instant) {
    /** Compose state on purpose: the clock loop keys off the window, so every change must be observable. */
    var since: Instant? by mutableStateOf(null)
        private set
    private var nowMs by mutableStateOf(0L)

    fun start() {
        since = now()
    }

    fun stop() {
        since = null
    }

    /** Opens a window only when none is running. */
    fun resume() {
        if (since == null) start()
    }

    fun tick() {
        nowMs = now().toEpochMilli()
    }

    /** What TimerBar draws right now (banked + running window). Never negative. */
    fun displayedElapsedMs(bankedMs: Long): Long =
        playTimeMs(bankedMs, since, Instant.ofEpochMilli(nowMs))

    /** Folds the running window into the session's banked elapsed and stops the clock. */
    fun fold(target: TangoSession): TangoSession {
        val rs = since ?: return target
        since = null
        return target.copy(elapsedMs = playTimeMs(target.elapsedMs, rs, now()))
    }
}
