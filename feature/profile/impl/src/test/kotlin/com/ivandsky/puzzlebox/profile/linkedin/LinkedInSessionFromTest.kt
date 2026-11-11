package com.ivandsky.puzzlebox.profile.linkedin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** The cookie string → session rule: both cookies or nothing, the header verbatim, the csrf token unquoted. */
class LinkedInSessionFromTest {
    private val userAgent = "Mozilla/5.0 (Linux; Android 14) Test"

    @Test
    fun `both cookies give the header verbatim, the csrf token without quotes and the user agent`() {
        val cookies = "bcookie=\"v=2&abc\"; li_at=AQEDtoken; JSESSIONID=\"ajax:1234567890\"; lang=v=2&lang=en-us"

        val session = sessionFrom(cookies, userAgent)

        assertNotNull(session)
        assertEquals(cookies, session!!.cookieHeader)
        assertEquals("ajax:1234567890", session.csrfToken)
        assertEquals(userAgent, session.userAgent)
    }

    @Test
    fun `a csrf cookie stored without quotes is used as it is`() {
        assertEquals("ajax:42", sessionFrom("li_at=AQEDtoken; JSESSIONID=ajax:42", userAgent)?.csrfToken)
    }

    @Test
    fun `no cookie string gives no session`() {
        assertNull(sessionFrom(null, userAgent))
    }

    @Test
    fun `an empty cookie string gives no session`() {
        assertNull(sessionFrom("", userAgent))
    }

    @Test
    fun `li_at alone gives no session`() {
        assertNull(sessionFrom("li_at=AQEDtoken; lang=en", userAgent))
    }

    @Test
    fun `JSESSIONID alone gives no session`() {
        assertNull(sessionFrom("JSESSIONID=\"ajax:42\"; lang=en", userAgent))
    }

    @Test
    fun `an empty li_at gives no session`() {
        assertNull(sessionFrom("li_at=; JSESSIONID=\"ajax:42\"", userAgent))
    }

    @Test
    fun `an empty JSESSIONID gives no session`() {
        assertNull(sessionFrom("li_at=AQEDtoken; JSESSIONID=", userAgent))
    }

    @Test
    fun `a JSESSIONID of only quotes gives no session`() {
        assertNull(sessionFrom("li_at=AQEDtoken; JSESSIONID=\"\"", userAgent))
    }

    @Test
    fun `a value keeps everything after its first equals sign`() {
        val session = sessionFrom("li_at=AQED=to=ken==; JSESSIONID=\"ajax:4=2\"", userAgent)

        assertEquals("ajax:4=2", session?.csrfToken)
    }

    @Test
    fun `a cookie whose name only ends in li_at is not li_at`() {
        assertNull(sessionFrom("x_li_at=AQEDtoken; JSESSIONID=\"ajax:42\"", userAgent))
    }

    @Test
    fun `a cookie whose name only ends in JSESSIONID is not JSESSIONID`() {
        assertNull(sessionFrom("li_at=AQEDtoken; xJSESSIONID=\"ajax:42\"", userAgent))
    }

    @Test
    fun `the session never prints the li_at value`() {
        val sentinel = "SENTINEL-li_at-4f2a"

        val session = sessionFrom("li_at=$sentinel; JSESSIONID=\"ajax:42\"", userAgent)

        assertNotNull(session)
        assertFalse(session.toString().contains(sentinel))
    }
}
