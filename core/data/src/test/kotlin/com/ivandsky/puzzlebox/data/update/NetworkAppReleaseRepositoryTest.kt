package com.ivandsky.puzzlebox.data.update

import com.ivandsky.puzzlebox.network.GitHubRelease
import com.ivandsky.puzzlebox.network.GitHubReleaseTransport
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkAppReleaseRepositoryTest {

    private class MutableClock(var now: Instant = Instant.parse("2026-11-06T12:00:00Z")) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
        fun advance(d: Duration) { now = now.plus(d) }
    }

    private class CountingTransport(var result: GitHubRelease?) : GitHubReleaseTransport {
        var calls = 0
        override suspend fun latest(): GitHubRelease? { calls++; return result }
    }

    private val clock = MutableClock()
    private val transport = CountingTransport(GitHubRelease("v1.2.0", "https://example.com/v1.2.0"))
    private val repo = NetworkAppReleaseRepository(transport, clock)

    @Test
    fun `first call hits the transport and maps the release`() = runTest {
        assertEquals(AppRelease("v1.2.0", "https://example.com/v1.2.0"), repo.latest())
        assertEquals(1, transport.calls)
    }

    @Test
    fun `second call inside 15 minutes returns the remembered value without a call`() = runTest {
        repo.latest()
        clock.advance(Duration.ofMinutes(14).plusSeconds(59))
        transport.result = GitHubRelease("v9", "u")
        assertEquals("v1.2.0", repo.latest()?.tag)
        assertEquals(1, transport.calls)
    }

    @Test
    fun `call after 15 minutes hits the transport again`() = runTest {
        repo.latest()
        clock.advance(Duration.ofMinutes(15))
        transport.result = GitHubRelease("v1.3.0", "u")
        assertEquals("v1.3.0", repo.latest()?.tag)
        assertEquals(2, transport.calls)
    }

    @Test
    fun `null result is not cached and is retried on the next call`() = runTest {
        transport.result = null
        assertNull(repo.latest())
        transport.result = GitHubRelease("v1.2.0", "u")
        assertEquals("v1.2.0", repo.latest()?.tag)
        assertEquals(2, transport.calls)
    }
}
