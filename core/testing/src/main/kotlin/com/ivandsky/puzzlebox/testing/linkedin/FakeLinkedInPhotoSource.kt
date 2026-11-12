package com.ivandsky.puzzlebox.testing.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInPhotoSource
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred

/**
 * Scriptable [LinkedInPhotoSource]: every asked url is recorded in [calls], and a url with no scripted
 * photo answers null, the same as the real source for a failed download. [hold] keeps every fetch on the
 * wire, once recorded, until [release], so a test can overlap downloads.
 */
class FakeLinkedInPhotoSource : LinkedInPhotoSource {

    private val photos = ConcurrentHashMap<String, ByteArray>()
    private val recorded = CopyOnWriteArrayList<String>()

    @Volatile
    private var gate: CompletableDeferred<Unit>? = null

    /** Every url asked for, oldest first. */
    val calls: List<String> get() = recorded.toList()

    fun respond(url: String, photo: ByteArray) {
        photos[url] = photo
    }

    /** Fetches asked from now on stay unanswered until [release]. */
    fun hold() {
        gate = CompletableDeferred()
    }

    /** Answers the fetches kept on the wire by [hold], and every later one at once. */
    fun release() {
        gate?.complete(Unit)
        gate = null
    }

    override suspend fun fetch(url: String): ByteArray? {
        recorded += url
        gate?.await()
        return photos[url]
    }
}
