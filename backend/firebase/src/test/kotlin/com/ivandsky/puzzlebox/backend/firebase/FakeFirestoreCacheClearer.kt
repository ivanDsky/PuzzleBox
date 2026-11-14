package com.ivandsky.puzzlebox.backend.firebase

/** [FirestoreCacheClearer] that records [clears] and throws [failure] when set. */
internal class FakeFirestoreCacheClearer(var failure: Exception? = null) : FirestoreCacheClearer {
    var clears = 0

    override suspend fun clear() {
        clears++
        failure?.let { throw it }
    }
}
