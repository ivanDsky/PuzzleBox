package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.domain.linkedin.LinkedInBoardRead
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/** The words under a Friends day board for when its LinkedIn part was read. */
class RanksFreshnessLineTest {

    private val now = Instant.parse("2026-11-17T14:30:00Z")

    private fun line(readAt: String, complete: Boolean = false) =
        freshnessLine(LinkedInBoardRead(Instant.parse(readAt).toEpochMilli(), complete), now, ZoneOffset.UTC, Locale.US)

    @Test
    fun `under a minute says just now`() {
        assertEquals("LinkedIn updated just now", line("2026-11-17T14:29:30Z"))
    }

    @Test
    fun `a read stamped after now says just now, never a negative age`() {
        assertEquals("LinkedIn updated just now", line("2026-11-17T14:35:00Z"))
    }

    @Test
    fun `under an hour counts minutes`() {
        assertEquals("LinkedIn updated 5 min ago", line("2026-11-17T14:25:00Z"))
        assertEquals("LinkedIn updated 59 min ago", line("2026-11-17T13:31:00Z"))
    }

    @Test
    fun `an hour or more the same day says the clock time`() {
        assertEquals("LinkedIn updated at 12:05", line("2026-11-17T12:05:00Z"))
    }

    @Test
    fun `an earlier day says the date and the time`() {
        assertEquals("LinkedIn updated Nov 16, 14:05", line("2026-11-16T14:05:00Z"))
    }

    @Test
    fun `a board read after its day ended says final whatever its age`() {
        assertEquals("LinkedIn final · read Nov 17, 00:12", line("2026-11-17T00:12:00Z", complete = true))
        assertEquals("LinkedIn final · read Nov 17, 14:29", line("2026-11-17T14:29:30Z", complete = true))
    }
}
