package com.ivandsky.puzzlebox.app.nav

import com.ivandsky.puzzlebox.model.ShareCode
import org.junit.Assert.assertEquals
import org.junit.Test

/** What an intent's URI opens: a friend's card, the code sheet with the link's text, or nothing. */
class FriendLinkTest {

    @Test
    fun `a share link opens its code`() {
        val code = FriendLink.Code(checkNotNull(ShareCode.parse("K7Q2MX")))
        listOf(
            "https://puzzlebox-f3356.web.app/p/K7Q2MX",
            "https://puzzlebox-f3356.web.app/p/K7Q2MX/",
            "https://puzzlebox-f3356.web.app/p/k7q-2mx",
            "https://puzzlebox-f3356.web.app/p/K7Q2MX?utm_source=chat",
            "https://PuzzleBox-F3356.web.app/p/K7Q2MX",
        ).forEach { uri -> assertEquals(uri, code, FriendLink.of(uri)) }
    }

    @Test
    fun `a share link whose code does not parse keeps the text after the path`() {
        listOf(
            "https://puzzlebox-f3356.web.app/p/K7Q2M" to "K7Q2M",
            "https://puzzlebox-f3356.web.app/p/K7Q2MXU" to "K7Q2MXU",
            "https://puzzlebox-f3356.web.app/p/K7Q2MU/" to "K7Q2MU",
            "https://puzzlebox-f3356.web.app/p/K7Q2M?utm_source=chat" to "K7Q2M",
            "https://puzzlebox-f3356.web.app/p/K7Q2M#top" to "K7Q2M",
            "https://puzzlebox-f3356.web.app/p/K7Q2M/extra" to "K7Q2M/extra",
            "https://puzzlebox-f3356.web.app/p/" to "",
        ).forEach { (uri, text) -> assertEquals(uri, FriendLink.Invalid(text), FriendLink.of(uri)) }
    }

    @Test
    fun `any other URI is not a friend link`() {
        listOf(
            null,
            "",
            "https://puzzlebox-f3356.web.app/",
            "https://puzzlebox-f3356.web.app/q/K7Q2MX",
            "http://puzzlebox-f3356.web.app/p/K7Q2MX",
            "https://example.com/p/K7Q2MX",
            "https://puzzlebox-f3356.web.app.evil.com/p/K7Q2MX",
            "https://puzzlebox-f3356.web.app:8443/p/K7Q2MX",
            "https://someone@puzzlebox-f3356.web.app/p/K7Q2MX",
            "puzzlebox://p/K7Q2MX",
        ).forEach { uri -> assertEquals("$uri", null, FriendLink.of(uri)) }
    }
}
