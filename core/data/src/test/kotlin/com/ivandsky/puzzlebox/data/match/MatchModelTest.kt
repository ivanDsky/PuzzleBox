package com.ivandsky.puzzlebox.data.match

import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatchModelTest {

    private val zip = GameId("zip")
    private val code = ShareCode.parse("K7Q2MX")!!
    private val me = Player("u1", "Ann", null)
    private val them = Player("u2", "Bob", null)

    private fun match(hours: Int = 24, opponent: Player? = them) = Match(
        code = code,
        formatVersion = Match.FORMAT_VERSION,
        createdAt = Instant.parse("2026-10-01T10:00:00Z"),
        deadlineHours = hours,
        challenger = me,
        invitedUid = null,
        opponent = opponent,
        legs = emptyList(),
        includeFriends = false,
        cancelled = false,
        declined = false,
        rematchOf = null,
        progress = emptyMap(),
    )

    @Test
    fun `daily and custom refs round trip through their wire strings`() {
        val daily = LegRef.Daily(zip, LocalDate.of(2026, 10, 1), 224)
        val custom = LegRef.Custom(zip, code)

        assertEquals("zip/2026-10-01/224", daily.wire)
        assertEquals("custom/zip/K7Q2MX", custom.wire)
        assertEquals(daily, LegRef.parse(daily.wire))
        assertEquals(custom, LegRef.parse(custom.wire))
    }

    @Test
    fun `unreadable refs parse to null`() {
        val unreadable = listOf(
            "zip/2026-10-01",
            "zip/2026-13-45/224",
            "zip/not-a-date/224",
            "zip/2026-10-01/abc",
            "zip/2026-10-01/224/9",
            "zip/2026-10-01/-5",
            "zip/2026-10-01/0",
            "custom/zip/k7q2mx",
            "custom/zip/K7Q2M",
            "custom/zip/K7Q2MU",
            "custom/zip/K7Q2MXA",
            "custom//K7Q2MX",
            "custom/K7Q2MX",
            "",
        )
        unreadable.forEach { wire -> assertNull(wire, LegRef.parse(wire)) }
    }

    @Test
    fun `deadline is creation plus the chosen hours and closes after the grace`() {
        listOf(24 to "2026-10-02T10:00:00Z", 48 to "2026-10-03T10:00:00Z").forEach { (hours, deadline) ->
            val match = match(hours)

            assertEquals(Instant.parse(deadline), match.deadline)
            assertEquals(Instant.parse(deadline).plusSeconds(6 * 3600), match.closesAt)
        }
    }

    @Test
    fun `opponentOf names the other player`() {
        val match = match()

        assertEquals(them, match.opponentOf("u1"))
        assertEquals(me, match.opponentOf("u2"))
        assertNull(match.opponentOf("stranger"))
        assertNull(match(opponent = null).opponentOf("u1"))
    }
}
