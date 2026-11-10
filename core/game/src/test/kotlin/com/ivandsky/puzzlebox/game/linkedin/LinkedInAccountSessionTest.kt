package com.ivandsky.puzzlebox.game.linkedin

import org.junit.Assert.assertFalse
import org.junit.Test

class LinkedInAccountSessionTest {

    @Test
    fun `toString shows none of the session values`() {
        val session = LinkedInAccountSession(
            cookieHeader = "li_at=SENTINEL-COOKIE; JSESSIONID=\"ajax:SENTINEL-CSRF\"",
            csrfToken = "ajax:SENTINEL-CSRF",
            userAgent = "SENTINEL-UA",
        )

        val text = session.toString()

        assertFalse(text.contains("SENTINEL-COOKIE"))
        assertFalse(text.contains("SENTINEL-CSRF"))
        assertFalse(text.contains("SENTINEL-UA"))
    }
}
