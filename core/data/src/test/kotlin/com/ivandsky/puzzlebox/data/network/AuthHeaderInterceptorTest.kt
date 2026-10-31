package com.ivandsky.puzzlebox.data.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AuthHeaderInterceptorTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun newClient(source: AuthHeaderSource): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(AuthHeaderInterceptor(source)).build()

    @Test
    fun `attaches configured headers to outgoing request`() {
        server.enqueue(MockResponse().setBody("{}"))
        val source = AuthHeaderSource { mapOf("Authorization" to "Bearer token", "X-Csrf-Token" to "csrf") }
        newClient(source).newCall(Request.Builder().url(server.url("/puzzle")).build()).execute().close()

        val recorded = server.takeRequest()
        assertEquals("Bearer token", recorded.getHeader("Authorization"))
        assertEquals("csrf", recorded.getHeader("X-Csrf-Token"))
    }

    @Test
    fun `signed-out source sends no auth headers`() {
        server.enqueue(MockResponse().setBody("{}"))
        newClient(AuthHeaderSource.NONE).newCall(Request.Builder().url(server.url("/puzzle")).build()).execute().close()

        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
        assertNull(recorded.getHeader("X-Csrf-Token"))
    }
}
