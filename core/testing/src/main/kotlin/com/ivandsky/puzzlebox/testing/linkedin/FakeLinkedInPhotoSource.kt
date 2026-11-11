package com.ivandsky.puzzlebox.testing.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInPhotoSource
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Scriptable [LinkedInPhotoSource]: every asked url is recorded in [calls], and a url with no scripted
 * photo answers null, the same as the real source for a failed download.
 */
class FakeLinkedInPhotoSource : LinkedInPhotoSource {

    private val photos = ConcurrentHashMap<String, ByteArray>()
    private val recorded = CopyOnWriteArrayList<String>()

    /** Every url asked for, oldest first. */
    val calls: List<String> get() = recorded.toList()

    fun respond(url: String, photo: ByteArray) {
        photos[url] = photo
    }

    override suspend fun fetch(url: String): ByteArray? {
        recorded += url
        return photos[url]
    }
}
