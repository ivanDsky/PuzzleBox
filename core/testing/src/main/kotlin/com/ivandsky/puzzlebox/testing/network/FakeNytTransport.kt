package com.ivandsky.puzzlebox.testing.network

import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.game.NytTransport
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Scriptable [NytTransport]: per-path scripted results ([respond]), every requested path
 * recorded in order ([requests]). An unscripted path answers [FetchError.Unavailable],
 * the same as the real transport offline.
 *
 * Thread-safe: the repository calls [get] on its IO dispatcher while the test scripts and
 * reads on its own thread.
 */
class FakeNytTransport : NytTransport {

    private val responses = ConcurrentHashMap<String, FetchResult<String>>()
    private val requested = CopyOnWriteArrayList<String>()

    /** Paths passed to [get], oldest first. */
    val requests: List<String> get() = requested.toList()

    /** Scripts every following [get] of [path] to answer [result]. */
    fun respond(path: String, result: FetchResult<String>) {
        responses[path] = result
    }

    override suspend fun get(path: String): FetchResult<String> {
        requested += path
        return responses[path] ?: FetchResult.Err(FetchError.Unavailable("unscripted path $path"))
    }
}
