package com.ivandsky.puzzlebox.compete.duel

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class EndsAtTest {

    // Saturday 14 Nov 2026, 18:00 UTC.
    private val deadline: Instant = Instant.parse("2026-11-14T18:00:00Z")

    @Test
    fun endsAtFormatsInTheGivenZoneAndLocale() {
        assertEquals("Sat 18:00", formatEnds(deadline, ZoneOffset.UTC, Locale.US))
        // 18:00 UTC is already Sunday at +8.
        assertEquals("Sun 02:00", formatEnds(deadline, ZoneOffset.ofHours(8), Locale.US))
        assertEquals("Sat 13:00", formatEnds(deadline, ZoneId.of("America/New_York"), Locale.US))
        assertEquals("sam. 18:00", formatEnds(deadline, ZoneOffset.UTC, Locale.FRANCE).replace("\u202f", " "))
    }
}
