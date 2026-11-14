package com.ivandsky.puzzlebox.backend.firebase

import android.util.Log
import kotlin.coroutines.cancellation.CancellationException

/**
 * The start-up step that clears the Firestore disk cache when the last sign-out (or erase) left a mark. It runs once
 * per cold start, before any store reaches `FirebaseFirestore.getInstance()`; a start without a mark does nothing and
 * never touches Firestore. The mark is dropped only after the clear succeeded, so a failed clear retries next start.
 */
class PendingCacheClear internal constructor(
    private val mark: CacheClearMark,
    private val clearer: FirestoreCacheClearer,
) {
    suspend fun run() {
        if (!mark.isPending()) return
        try {
            clearer.clear()
            mark.drop()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Firestore cache clear failed; retrying at the next start", e)
        }
    }

    private companion object {
        const val TAG = "PendingCacheClear"
    }
}
