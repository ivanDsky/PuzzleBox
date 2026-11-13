package com.ivandsky.puzzlebox.testing.repository

import com.ivandsky.puzzlebox.data.visibility.ReplayOptInStamp

/** In-memory [ReplayOptInStamp]: [stamps] is the persisted state, so a test can read it or seed one from an earlier run. */
class FakeReplayOptInStamp : ReplayOptInStamp {

    val stamps = mutableMapOf<String, Long>()

    override suspend fun since(uid: String): Long? = stamps[uid]

    override suspend fun markIfAbsent(uid: String, atMs: Long) {
        stamps.putIfAbsent(uid, atMs)
    }

    override suspend fun clear(uid: String) {
        stamps.remove(uid)
    }
}
