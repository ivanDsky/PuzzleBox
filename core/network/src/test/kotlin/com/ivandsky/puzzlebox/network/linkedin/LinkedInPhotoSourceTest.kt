package com.ivandsky.puzzlebox.network.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInPhotoSource
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The photo client's safety rules, over the factory the app runs. The network is replaced by a scripted
 * answer that also records every request, so a refusal shows as "nothing was sent".
 */
class LinkedInPhotoSourceTest {

    private val sent = CopyOnWriteArrayList<Request>()
    private var status = 200
    private var type = "image/jpeg"
    private var body = JPEG
    private var failure: IOException? = null

    private val source: LinkedInPhotoSource = linkedInPhotoSource(
        Interceptor { chain ->
            sent += chain.request()
            failure?.let { throw it }
            answer(chain)
        },
    )

    private fun answer(chain: Interceptor.Chain): Response =
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(status)
            .message("scripted")
            .body(body.toResponseBody(type.toMediaType()))
            .build()

    @Test
    fun `an https photo on the media host is one GET with no cookie and no authorization`() = runBlocking {
        val photo = source.fetch(PHOTO)

        assertArrayEquals(JPEG, photo)
        val request = sent.single()
        assertEquals("GET", request.method)
        assertEquals(PHOTO, request.url.toString())
        assertNull(request.header("Cookie"))
        assertNull(request.header("Authorization"))
        assertNull(request.header("csrf-token"))
    }

    @Test
    fun `a url that is not https or not on the media host is refused without a request`() = runBlocking {
        val refused = listOf(
            "http://media.licdn.com/dms/image/a.jpg",
            "https://www.linkedin.com/voyager/api/graphql",
            "https://licdn.com/dms/image/a.jpg",
            "https://media.licdn.com.example.test/dms/image/a.jpg",
            "https://media.licdn.com@example.test/dms/image/a.jpg",
            "https://example.test/media.licdn.com/a.jpg",
            "file:///data/data/app/secret",
            "not a url",
            "",
        )

        refused.forEach { assertNull("$it is refused", source.fetch(it)) }

        assertTrue("nothing was sent", sent.isEmpty())
    }

    @Test
    fun `an answer that is not an image is dropped`() = runBlocking {
        type = "text/html"
        body = "<html>login</html>".toByteArray()

        assertNull(source.fetch(PHOTO))
    }

    @Test
    fun `an answer over the size cap is dropped and one at the cap is kept`() = runBlocking {
        body = ByteArray(CAP + 1)
        assertNull(source.fetch(PHOTO))

        body = ByteArray(CAP)
        assertEquals(CAP, source.fetch(PHOTO)?.size)
    }

    @Test
    fun `an error status or a redirect is dropped`() = runBlocking {
        listOf(403, 404, 302, 500).forEach { code ->
            status = code
            assertNull("$code is dropped", source.fetch(PHOTO))
        }
    }

    @Test
    fun `a failed connection is dropped, not thrown`() = runBlocking {
        failure = IOException("no route")

        assertNull(source.fetch(PHOTO))
        assertEquals("one GET, no retry", 1, sent.size)
    }

    private companion object {
        const val PHOTO = "https://media.licdn.com/dms/image/v2/FAKE0001/profile-displayphoto-shrink_100_100/0/fake-100?e=1&t=a"
        const val CAP = 512 * 1024
        val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)
    }
}
