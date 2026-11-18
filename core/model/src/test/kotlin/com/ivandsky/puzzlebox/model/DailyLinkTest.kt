package com.ivandsky.puzzlebox.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DailyLinkTest {
    private val zip = GameId("zip")
    private val day = LocalDate.parse("2026-11-18")
    private val link = "https://puzzlebox-f3356.web.app/p/zip/2026-11-18"

    @Test
    fun `of builds the link under the code-link prefix`() {
        assertEquals(link, DailyLink.of(zip, day))
        assertEquals(ShareCode.LINK_PREFIX + "queens/2026-01-05", DailyLink.of(GameId("queens"), LocalDate.parse("2026-01-05")))
    }

    @Test
    fun `parse reads back what of built`() {
        assertEquals(DailyRef(zip, day), DailyLink.parse(DailyLink.of(zip, day)))
    }

    @Test
    fun `parse accepts a trailing slash, any case, a query and a fragment`() {
        val accepted = listOf(
            "$link/",
            "HTTPS://PuzzleBox-F3356.Web.App/p/ZIP/2026-11-18",
            "$link?utm=x",
            "$link#top",
            "$link/?a=b#c",
        )
        accepted.forEach { assertEquals(it, DailyRef(zip, day), DailyLink.parse(it)) }
    }

    @Test
    fun `parse rejects every other form`() {
        val rejected = listOf(
            "http://puzzlebox-f3356.web.app/p/zip/2026-11-18",
            "https://example.com/p/zip/2026-11-18",
            "https://puzzlebox-f3356.web.app:8443/p/zip/2026-11-18",
            "https://user@puzzlebox-f3356.web.app/p/zip/2026-11-18",
            "https://puzzlebox-f3356.web.app/m/zip/2026-11-18",
            "https://puzzlebox-f3356.web.app/p/K7Q2MX",
            "https://puzzlebox-f3356.web.app/p/zip/2026-2-3",
            "https://puzzlebox-f3356.web.app/p/zip/2026-02-30",
            "https://puzzlebox-f3356.web.app/p//2026-11-18",
            "https://puzzlebox-f3356.web.app/p/zi-p/2026-11-18",
            "https://puzzlebox-f3356.web.app/p/zip/+12026-11-18",
            "https://puzzlebox-f3356.web.app/p/zip/2026-11-18/extra",
            "https://puzzlebox-f3356.web.app/p/zip/2026-11-18//",
            "https://puzzlebox-f3356.web.app/p/zip",
            "https://puzzlebox-f3356.web.app/p/z%69p/2026-11-18",
            "not a uri",
            "",
        )
        rejected.forEach { assertNull(it, DailyLink.parse(it)) }
    }

    @Test
    fun `a code link and a daily link never read as each other`() {
        val code = checkNotNull(ShareCode.parse("K7Q2MX"))
        assertNull(DailyLink.parse(code.link))
        assertNull(DailyLink.parse(code.matchLink))
        assertNull(ShareCode.fromLink(DailyLink.of(zip, day)))
        assertNull(ShareCode.fromLink(DailyLink.of(GameId("queens"), day)))
        assertNotNull(ShareCode.fromLink(code.link))
    }
}
