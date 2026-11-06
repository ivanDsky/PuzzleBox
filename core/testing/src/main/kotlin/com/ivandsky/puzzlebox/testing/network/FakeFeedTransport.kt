package com.ivandsky.puzzlebox.testing.network

import com.ivandsky.puzzlebox.game.FeedRequest
import com.ivandsky.puzzlebox.game.FeedTransport
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Scriptable [FeedTransport]: per-request scripted results ([respond]), every request's
 * [keyOf] recorded in order ([requests]). An unscripted request answers
 * [FetchError.Unavailable], the same as the real transport offline.
 *
 * Like the real ladder, a scripted body the request does not [FeedRequest.accepts] is no
 * answer at all ([FetchError.Unavailable]), so a game is never handed a body its own
 * extractor would have refused on the wire.
 *
 * Thread-safe: the repository calls [fetch] on its IO dispatcher while the test scripts and
 * reads on its own thread.
 */
class FakeFeedTransport : FeedTransport {

    private val responses = ConcurrentHashMap<String, FetchResult<String>>()
    private val requested = CopyOnWriteArrayList<String>()

    /** [keyOf] each request passed to [fetch], oldest first. */
    val requests: List<String> get() = requested.toList()

    /** Scripts every following [fetch] of a request keyed like [request] to answer [result]. */
    fun respond(request: FeedRequest, result: FetchResult<String>) {
        responses[keyOf(request)] = result
    }

    override suspend fun fetch(request: FeedRequest): FetchResult<String> {
        val key = keyOf(request)
        requested += key
        val result = responses[key] ?: return FetchResult.Err(FetchError.Unavailable("unscripted request $key"))
        if (result is FetchResult.Ok && !request.accepts(result.value)) {
            return FetchResult.Err(FetchError.Unavailable("$key body carried no ${request.objectName}"))
        }
        return result
    }

    companion object {
        /**
         * What the wire would tell two requests apart by: the route, plus the persisted
         * query's variables and id when the request names one (a games-by-id edition).
         */
        fun keyOf(request: FeedRequest): String =
            request.path + request.persistedQuery?.let { "?variables=${it.variables}&queryId=${it.queryId}" }.orEmpty()
    }
}
