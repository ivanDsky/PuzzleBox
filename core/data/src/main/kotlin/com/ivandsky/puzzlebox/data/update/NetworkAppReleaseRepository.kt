package com.ivandsky.puzzlebox.data.update

import com.ivandsky.puzzlebox.network.GitHubReleaseTransport
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [AppReleaseRepository] over the GitHub transport, calling the network at most once per
 * [THROTTLE] per process. Only successes are remembered, so a failed check retries next call.
 */
class NetworkAppReleaseRepository(
    private val transport: GitHubReleaseTransport,
    private val clock: Clock = Clock.systemUTC(),
) : AppReleaseRepository {

    // One lock so concurrent callers share a single in-flight check.
    private val mutex = Mutex()
    private var remembered: AppRelease? = null
    private var checkedAt: Instant? = null

    override suspend fun latest(): AppRelease? = mutex.withLock {
        val now = clock.instant()
        val last = checkedAt
        if (last != null && Duration.between(last, now) < THROTTLE) return@withLock remembered
        val fetched = transport.latest()?.let { AppRelease(it.tagName, it.htmlUrl) }
        if (fetched != null) {
            remembered = fetched
            checkedAt = now
        }
        fetched
    }

    private companion object {
        val THROTTLE: Duration = Duration.ofMinutes(15)
    }
}
