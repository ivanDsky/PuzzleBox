package com.ivandsky.puzzlebox.app

import com.ivandsky.puzzlebox.navigation.FirstScreenReady

/**
 * Whether the system splash stays up. It goes on the first of: the first screen is ready, a
 * local read failed, or [MAX_HOLD_MILLIS] since [startedAtMillis]; the time cap keeps a
 * stuck read from holding the app on a splash.
 *
 * [nowMillis] is a monotonic clock, injected so the cap is testable.
 */
class SplashReleasePolicy(
    private val firstScreen: FirstScreenReady,
    private val nowMillis: () -> Long,
    private val startedAtMillis: Long = nowMillis(),
) {

    @Volatile
    private var localReadFailed = false

    /** A local read failed: release now rather than wait on state that may never arrive. */
    fun onLocalReadFailed() {
        localReadFailed = true
    }

    fun keepSplash(): Boolean =
        !firstScreen.isReady && !localReadFailed && nowMillis() - startedAtMillis < MAX_HOLD_MILLIS

    companion object {
        const val MAX_HOLD_MILLIS = 1_000L
    }
}
