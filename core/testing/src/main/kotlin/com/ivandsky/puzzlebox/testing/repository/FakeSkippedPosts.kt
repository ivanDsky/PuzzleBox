package com.ivandsky.puzzlebox.testing.repository

import com.ivandsky.puzzlebox.data.visibility.SkippedPosts

/** In-memory [SkippedPosts]: [refs] is the persisted state, so a second watcher over the same instance stands in for a relaunch. */
class FakeSkippedPosts : SkippedPosts {

    val refs = mutableSetOf<Pair<String, String>>()

    override suspend fun contains(uid: String, puzzleRef: String): Boolean = (uid to puzzleRef) in refs

    override suspend fun add(uid: String, puzzleRef: String) {
        refs += uid to puzzleRef
    }
}
