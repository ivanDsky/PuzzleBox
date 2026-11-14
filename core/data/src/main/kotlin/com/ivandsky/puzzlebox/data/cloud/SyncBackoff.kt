package com.ivandsky.puzzlebox.data.cloud

/**
 * The pure failure policy (ADR-0029 decision 4, A22): what a failed cloud call leaves the engine showing and when it
 * tries again on its own. Nothing here touches a row, a marker or a cursor.
 */
internal object SyncBackoff {
    const val FIRST_RETRY_MS = 60_000L
    const val MAX_RETRY_MS = 30 * 60_000L

    /** The state to show after a failed run, and the wait before an automatic retry ([retryAfterMs] null: wait for a trigger). */
    class Pause(val state: CloudSyncState, val retryAfterMs: Long?)

    /**
     * The pause after [failure] as the [attempt]th consecutive one (1 is the first). [lastAtMs] is the last successful
     * pass, or null. `Offline` while the network is down ([online] false) waits for a trigger; while the network is up
     * it is a call that timed out, shows `Offline` and retries like `Exhausted` and `Denied` do: after 1 min, doubling
     * to 30 min. `Ok` and `WrongAccount` are no failure of the cloud, so they decide nothing (null).
     */
    fun after(failure: CloudResult<*>, attempt: Int, lastAtMs: Long?, online: Boolean): Pause? = when (failure) {
        CloudResult.Offline -> Pause(CloudSyncState.Offline(lastAtMs), if (online) delayMs(attempt) else null)
        CloudResult.Exhausted -> Pause(CloudSyncState.Paused(PauseReason.Exhausted, lastAtMs), delayMs(attempt))
        CloudResult.Denied -> Pause(CloudSyncState.Paused(PauseReason.Denied, lastAtMs), delayMs(attempt))
        is CloudResult.Ok, CloudResult.WrongAccount -> null
    }

    /** The pause once the daily write cap is reached: resume at [retryAfterMs], the start of the next day. */
    fun capped(lastAtMs: Long?, retryAfterMs: Long): Pause =
        Pause(CloudSyncState.Paused(PauseReason.WriteCap, lastAtMs), retryAfterMs)

    private fun delayMs(attempt: Int): Long {
        val doublings = (attempt - 1).coerceIn(0, MAX_DOUBLINGS)
        return minOf(MAX_RETRY_MS, FIRST_RETRY_MS shl doublings)
    }

    /** 1 min doubled 5 times is 32 min, already past the cap; more doublings only risk overflow. */
    private const val MAX_DOUBLINGS = 5
}
