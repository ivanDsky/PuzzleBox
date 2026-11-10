package com.ivandsky.puzzlebox.compete.share

import com.ivandsky.puzzlebox.compete.duel.CompeteTime
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteShareTest {

    // Saturday 14 Nov 2026, 18:00 in UTC: "Sat 18:00" in the US locale.
    private val endsAt: Instant = Instant.parse("2026-11-14T18:00:00Z")
    private val code = checkNotNull(ShareCode.parse("K7Q2MX"))

    private fun text(games: List<String>? = listOf("Zip", "Tango", "Queens")) = inviteText(
        inviter = "Ivan",
        bestOf = 3,
        games = games,
        endsAt = endsAt,
        zone = ZoneOffset.UTC,
        locale = Locale.US,
        code = code,
    )

    @Test
    fun inviteTextHasTheChallengeLineThenTheLinkThenTheCode() {
        val lines = text().lines()

        assertEquals(3, lines.size)
        assertEquals("Ivan challenges you: best of 3 — Zip, Tango, Queens · ends Sat 18:00", lines[0])
        assertEquals(code.matchLink, lines[1])
        assertEquals("Code ${code.display}", lines[2])
    }

    @Test
    fun inviteTextListsTheGamesInOrder() {
        val challenge = text(listOf("Tango", "Zip")).lines().first()

        assertTrue(challenge.contains("— Tango, Zip ·"))
    }

    @Test
    fun inviteTextOmitsTheGamesClauseWhenUnknown() {
        val challenge = text(games = null).lines().first()

        assertEquals("Ivan challenges you: best of 3 · ends Sat 18:00", challenge)
    }

    @Test
    fun inviteTextUsesTheMatchLinkNotThePuzzleLink() {
        val link = text().lines()[1]

        assertEquals(code.matchLink, link)
        assertTrue(link.contains("/m/"))
        assertFalse(link.contains("/p/"))
    }

    @Test
    fun aCreatedInviteEndsDeadlineHoursAfterTheInjectedNow() {
        val created = Instant.parse("2026-11-13T18:00:00Z")
        val time = CompeteTime(Clock.fixed(created, ZoneOffset.UTC), Locale.US)

        val challenge = createdInviteText("Ivan", 3, listOf("Zip"), deadlineHours = 24, code = code, time = time).lines().first()

        assertEquals("Ivan challenges you: best of 3 — Zip · ends Sat 18:00", challenge)
    }
}
