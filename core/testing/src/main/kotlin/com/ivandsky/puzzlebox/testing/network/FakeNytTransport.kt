package com.ivandsky.puzzlebox.testing.network

import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.game.NytTransport

/**
 * Scriptable [NytTransport]: per-path scripted results ([respond]), every requested path
 * recorded in order ([requests]). An unscripted path answers [FetchError.Unavailable],
 * the same as the real transport offline.
 */
class FakeNytTransport : NytTransport {

    private val responses = mutableMapOf<String, FetchResult<String>>()
    private val requested = mutableListOf<String>()

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
