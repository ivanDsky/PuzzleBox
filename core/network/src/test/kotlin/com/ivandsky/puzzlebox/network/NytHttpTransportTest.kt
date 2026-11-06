package com.ivandsky.puzzlebox.network

import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.game.NytTransport
import com.ivandsky.puzzlebox.network.di.networkModule
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.dsl.koinApplication

/** NYT status mapping (ADR-0014) against MockWebServer: 2xx → Ok, 404 → NotFound, rest → Unavailable. */
class NytHttpTransportTest {

    private lateinit var server: MockWebServer
    private lateinit var transport: NytHttpTransport

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val client = OkHttpClient.Builder().callTimeout(500, TimeUnit.MILLISECONDS).build()
        val retrofit = retrofit(
            baseUrl = server.url("/").toString(),
            client = client,
            json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
        )
        transport = NytHttpTransport(retrofit.create(NytApi::class.java))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun unavailable(result: FetchResult<String>) =
        (result as FetchResult.Err).error as FetchError.Unavailable

    @Test
    fun `200 returns the body and requests the path verbatim`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":1}"""))

        val result = transport.get("svc/strands/v2/2026-11-05.json")

        assertEquals(FetchResult.Ok("""{"id":1}"""), result)
        assertEquals("/svc/strands/v2/2026-11-05.json", server.takeRequest().path)
    }

    @Test
    fun `404 is NotFound`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("nope"))

        val error = (transport.get("svc/strands/v2/2020-01-01.json") as FetchResult.Err).error

        assertTrue(error is FetchError.NotFound)
        assertTrue((error as FetchError.NotFound).detail!!.contains("404"))
    }

    @Test
    fun `500 is Unavailable`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))

        assertTrue(unavailable(transport.get("x.json")).detail!!.contains("500"))
    }

    @Test
    fun `call timeout is Unavailable`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        assertTrue(unavailable(transport.get("x.json")).detail!!.contains("transport"))
    }

    @Test
    fun `dropped connection is Unavailable`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        assertTrue(unavailable(transport.get("x.json")).detail!!.contains("transport"))
    }

    @Test
    fun `empty 200 is Unavailable`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))

        assertTrue(unavailable(transport.get("x.json")).detail!!.contains("empty"))
    }

    @Test
    fun `garbage 200 body passes through - parsing is the source's job`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html>not json"))

        assertEquals(FetchResult.Ok("<html>not json"), transport.get("x.json"))
    }

    @Test
    fun `network module binds NytHttpTransport as NytTransport`() {
        val koin = koinApplication { modules(networkModule) }.koin

        assertTrue(koin.get<NytTransport>() is NytHttpTransport)
    }
}
