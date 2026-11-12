package com.ivandsky.puzzlebox.data.leaderboard

import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.game.TestGames.Ref
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Only a friend puzzle's record, inside the rules envelope, becomes a board entry. */
class CustomBoardEntriesTest {

    private val queens = GameId("queens")

    private fun record(
        ref: PuzzleRef = Ref(queens, PuzzleKey.Custom("K7Q2MX")),
        timeMs: Long = 102_000,
        hints: Int = 1,
        isRecord: Boolean = true,
        firstCompletedAt: Instant? = Instant.ofEpochMilli(1_700_000_000_000),
    ) = CompletionRecord(
        puzzleRef = ref,
        time = timeMs,
        hints = hints,
        stats = RecordStats(schemaVersion = 1, blob = "{}"),
        firstCompletedAt = firstCompletedAt,
        isRecord = isRecord,
    )

    @Test
    fun `a custom record maps to an entry under its custom ref key`() {
        val entry = CustomBoardEntries.fromRecord(record(), uid = "u1", displayName = "Ann")

        assertEquals(
            LeaderboardEntry(
                uid = "u1",
                displayName = "Ann",
                puzzleRef = "custom/queens/K7Q2MX",
                timeMs = 102_000,
                hints = 1,
                completedAtMs = 1_700_000_000_000,
            ),
            entry,
        )
    }

    @Test
    fun `a daily record never maps`() {
        val daily = record(ref = Ref(queens, PuzzleKey.Daily(LocalDate.of(2026, 10, 31), 592)))

        assertNull(CustomBoardEntries.fromRecord(daily, uid = "u1", displayName = "Ann"))
    }

    @Test
    fun `a play-again never maps`() {
        assertNull(CustomBoardEntries.fromRecord(record(isRecord = false), uid = "u1", displayName = "Ann"))
    }

    @Test
    fun `scores outside the rules envelope never map`() {
        assertNull(CustomBoardEntries.fromRecord(record(timeMs = 0), uid = "u1", displayName = "Ann"))
        assertNull(CustomBoardEntries.fromRecord(record(hints = -1), uid = "u1", displayName = "Ann"))
    }

    @Test
    fun `a record without a first-win stamp never maps`() {
        assertNull(CustomBoardEntries.fromRecord(record(firstCompletedAt = null), uid = "u1", displayName = "Ann"))
        assertNull(
            CustomBoardEntries.fromRecord(record(firstCompletedAt = Instant.EPOCH), uid = "u1", displayName = "Ann"),
        )
    }

    @Test
    fun `the display name is normalized as for dailies`() {
        assertEquals("Player", CustomBoardEntries.fromRecord(record(), uid = "u1", displayName = "  ")?.displayName)
        assertEquals(64, CustomBoardEntries.fromRecord(record(), uid = "u1", displayName = "x".repeat(80))?.displayName?.length)
        assertEquals("Ann", CustomBoardEntries.fromRecord(record(), uid = "u1", displayName = " Ann ")?.displayName)
    }
}
