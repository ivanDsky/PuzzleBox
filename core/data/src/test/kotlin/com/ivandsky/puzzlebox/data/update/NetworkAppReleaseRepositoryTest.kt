package com.ivandsky.puzzlebox.data.update

import com.ivandsky.puzzlebox.network.GitHubRelease
import com.ivandsky.puzzlebox.network.GitHubReleaseTransport
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkAppReleaseRepositoryTest {

    private class CountingTransport(var result: GitHubRelease?) : GitHubReleaseTransport {
        var calls = 0
        override suspend fun latest(): GitHubRelease? { calls++; return result }
    }

    private val time = TestTimeSource()
    private val transport = CountingTransport(GitHubRelease("v1.2.0", "https://example.com/v1.2.0"))
    private val repo = NetworkAppReleaseRepository(transport, time)

    @Test
    fun `first call hits the transport and maps the release`() = runTest {
        assertEquals(AppRelease("v1.2.0", "https://example.com/v1.2.0"), repo.latest())
        assertEquals(1, transport.calls)
    }

    @Test
    fun `second call inside 15 minutes returns the remembered value without a call`() = runTest {
        repo.latest()
        time += 14.minutes + 59.seconds
        transport.result = GitHubRelease("v9", "u")
        assertEquals("v1.2.0", repo.latest()?.tag)
        assertEquals(1, transport.calls)
    }

    @Test
    fun `call after 15 minutes hits the transport again`() = runTest {
        repo.latest()
        time += 15.minutes
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

    @Test
    fun `failed check after the window returns null, not the stale remembered value - Ruling 05`() = runTest {
        repo.latest()
        time += 15.minutes
        transport.result = null
        assertNull(repo.latest())
        assertEquals(2, transport.calls)
    }

    @Test
    fun `concurrent calls share one in-flight transport call - R7`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val slow = object : GitHubReleaseTransport {
            var calls = 0
            override suspend fun latest(): GitHubRelease {
                calls++
                gate.await()
                return GitHubRelease("v1.2.0", "https://example.com/v1.2.0")
            }
        }
        val shared = NetworkAppReleaseRepository(slow, time)

        val first = async { shared.latest() }
        val second = async { shared.latest() }
        runCurrent()
        gate.complete(Unit)

        val expected = AppRelease("v1.2.0", "https://example.com/v1.2.0")
        assertEquals(expected, first.await())
        assertEquals(expected, second.await())
        assertEquals(1, slow.calls)
    }
}
