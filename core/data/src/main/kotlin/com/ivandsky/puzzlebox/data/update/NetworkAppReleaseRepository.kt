package com.ivandsky.puzzlebox.data.update

import com.ivandsky.puzzlebox.network.GitHubReleaseTransport
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [AppReleaseRepository] over the GitHub transport, calling the network at most once per
 * [THROTTLE] per process, measured on [timeSource] so wall-clock changes cannot skip or
 * extend the window. Only successes are remembered, so a failed check retries next call.
 */
class NetworkAppReleaseRepository(
    private val transport: GitHubReleaseTransport,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : AppReleaseRepository {

    // One lock so concurrent callers share a single in-flight check.
    private val mutex = Mutex()
    private var remembered: AppRelease? = null
    private var checkedAt: TimeMark? = null

    override suspend fun latest(): AppRelease? = mutex.withLock {
        val last = checkedAt
        if (last != null && last.elapsedNow() < THROTTLE) return@withLock remembered
        val now = timeSource.markNow()
        val fetched = transport.latest()?.let { AppRelease(it.tagName, it.htmlUrl) }
        if (fetched != null) {
            remembered = fetched
            checkedAt = now
        }
        fetched
    }

    private companion object {
        val THROTTLE = 15.minutes
    }
}
