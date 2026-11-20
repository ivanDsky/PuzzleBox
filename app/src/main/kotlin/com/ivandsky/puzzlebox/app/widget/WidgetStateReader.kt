package com.ivandsky.puzzlebox.app.widget

import android.util.Log
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.TodayEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The one read the widget draws from: the first value of Today's set and of the all-games stats, through [build], so the
 * widget and Today cannot disagree. Both reads start together and each gets [readLimitMs], which keeps the whole read
 * inside the receiver's roughly 10 seconds. No store holds a last good picture, so a failed or hung read is [read] = null
 * and the widget draws its empty state; it never throws, except that cancelling the caller propagates.
 */
class WidgetStateReader(
    private val todaySet: () -> Flow<List<TodayEntry>>,
    private val stats: () -> Flow<GameStats>,
    private val build: BuildWidgetState,
    private val readLimitMs: Long = READ_LIMIT_MS,
) {

    suspend fun read(): WidgetState? = try {
        coroutineScope {
            val entries = async { withTimeoutOrNull(readLimitMs) { todaySet().first() } }
            val gameStats = async { withTimeoutOrNull(readLimitMs) { stats().first() } }
            val today = entries.await()
            val all = gameStats.await()
            if (today == null || all == null) {
                Log.w(TAG, "widget read timed out after ${readLimitMs}ms")
                null
            } else {
                build(today, all)
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "widget read failed: ${e.message}")
        null
    }

    private companion object {
        /** The receiver has about 10 seconds; the rest is for the render. Same limit as the reminder's read. */
        const val READ_LIMIT_MS = 8_000L
        const val TAG = "WidgetStateReader"
    }
}
