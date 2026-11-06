package com.ivandsky.puzzlebox.network

import com.ivandsky.puzzlebox.network.di.networkModule
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.dsl.koinApplication

/** Latest-release mapping against MockWebServer: 2xx with both fields → value, everything else → null. */
class GitHubHttpReleaseTransportTest {

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var server: MockWebServer
    private lateinit var transport: GitHubReleaseTransport

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val client = OkHttpClient.Builder().callTimeout(500, TimeUnit.MILLISECONDS).build()
        val api = retrofit(server.url("/").toString(), client, json).create(GitHubReleasesApi::class.java)
        transport = GitHubHttpReleaseTransport(api, json)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueue(code: Int, body: String = "") =
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))

    @Test
    fun `200 returns tag and page url via the production factory`() = runBlocking {
        enqueue(200, """{"tag_name":"v0.15.0","html_url":"https://github.com/r/v0.15.0","draft":false}""")
        val real = gitHubReleaseTransport(server.url("/").toString(), json)

        assertEquals(GitHubRelease("v0.15.0", "https://github.com/r/v0.15.0"), real.latest())
        val request = server.takeRequest()
        assertEquals("/repos/ivanDsky/PuzzleBox/releases/latest", request.path)
        assertEquals("application/vnd.github+json", request.getHeader("Accept"))
    }

    @Test
    fun `200 with missing field is null`() = runBlocking {
        enqueue(200, """{"tag_name":"v0.15.0"}""")
        assertNull(transport.latest())
    }

    @Test
    fun `200 with blank field is null`() = runBlocking {
        enqueue(200, """{"tag_name":"","html_url":"https://x"}""")
        assertNull(transport.latest())
    }

    @Test
    fun `404 is null`() = runBlocking {
        enqueue(404, """{"message":"Not Found"}""")
        assertNull(transport.latest())
    }

    @Test
    fun `403 rate limit is null`() = runBlocking {
        enqueue(403, """{"message":"API rate limit exceeded"}""")
        assertNull(transport.latest())
    }

    @Test
    fun `500 is null`() = runBlocking {
        enqueue(500)
        assertNull(transport.latest())
    }

    @Test
    fun `malformed json is null`() = runBlocking {
        enqueue(200, "<html>not json")
        assertNull(transport.latest())
    }

    @Test
    fun `stalled response times out to null`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        assertNull(transport.latest())
    }

    @Test
    fun `network module binds GitHubHttpReleaseTransport`() {
        val koin = koinApplication { modules(networkModule) }.koin

        assertTrue(koin.get<GitHubReleaseTransport>() is GitHubHttpReleaseTransport)
    }
}
