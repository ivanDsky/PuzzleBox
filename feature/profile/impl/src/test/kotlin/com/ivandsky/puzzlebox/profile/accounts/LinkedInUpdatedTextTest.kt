package com.ivandsky.puzzlebox.profile.accounts

import org.junit.Assert.assertEquals
import org.junit.Test

/** The relative phrase after "Updated": coarse, singular where it is one, and "Updating…" when never synced. */
class LinkedInUpdatedTextTest {

    private val now = 1_800_000_000_000L
    private fun ago(ms: Long) = linkedInUpdatedText(updatedAtMs = now - ms, nowMs = now)

    @Test
    fun `under a minute is just now`() {
        assertEquals("Updated just now", ago(0))
        assertEquals("Updated just now", ago(59_999))
    }

    @Test
    fun `a clock behind the stored time still reads just now`() {
        assertEquals("Updated just now", linkedInUpdatedText(updatedAtMs = now + 5 * 60_000, nowMs = now))
    }

    @Test
    fun `minutes below an hour`() {
        assertEquals("Updated 1 min ago", ago(60_000))
        assertEquals("Updated 12 min ago", ago(12 * 60_000))
        assertEquals("Updated 59 min ago", ago(59 * 60_000 + 59_000))
    }

    @Test
    fun `hours below a day`() {
        assertEquals("Updated 1 hour ago", ago(60 * 60_000))
        assertEquals("Updated 3 hours ago", ago(3 * 60 * 60_000L + 20 * 60_000))
        assertEquals("Updated 23 hours ago", ago(23 * 60 * 60_000L + 59 * 60_000))
    }

    @Test
    fun `days from a day on`() {
        assertEquals("Updated 1 day ago", ago(24 * 60 * 60_000L))
        assertEquals("Updated 2 days ago", ago(2 * 24 * 60 * 60_000L + 5 * 60 * 60_000L))
    }

    @Test
    fun `never synced is Updating, not Updated Updating`() {
        assertEquals("Updating…", linkedInUpdatedText(updatedAtMs = null, nowMs = now))
    }
}
