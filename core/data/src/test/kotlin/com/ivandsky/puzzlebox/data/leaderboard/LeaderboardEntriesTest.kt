package com.ivandsky.puzzlebox.data.leaderboard

import com.ivandsky.puzzlebox.data.testing.TestGames.Ref
import com.ivandsky.puzzlebox.database.CompletionEntity
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure A3/A5 derivation rules (brief §1): only records (A5), only official-daily shape,
 * only sane scores — the exact envelope the Firestore rules re-validate server-side.
 */
class LeaderboardEntriesTest {

    private val day = LocalDate.of(2026, 10, 31)
    private val refKey = "zip/2026-10-31/592"
    private val zip = GameId("zip")

    private fun record(
        timeMs: Long = 95_000,
        hints: Int = 2,
        isRecord: Boolean = true,
        ref: PuzzleRef = Ref(zip, day, 592),
        firstCompletedAt: Instant? = Instant.ofEpochMilli(1_700_000_000_000),
    ) = CompletionRecord(
        puzzleRef = ref,
        time = timeMs,
        hints = hints,
        stats = RecordStats(schemaVersion = 1, blob = "{}"),
        firstCompletedAt = firstCompletedAt,
        isRecord = isRecord,
    )

    private fun entity(
        puzzleRef: String = refKey,
        timeMs: Long = 95_000,
        hints: Int = 2,
        isRecord: Boolean = true,
        firstCompletedAtMs: Long? = 1_700_000_000_000,
        completedAtMs: Long = 1_700_000_500_000,
    ) = CompletionEntity(
        puzzleRef = puzzleRef,
        timeMs = timeMs,
        hints = hints,
        statsVersion = 1,
        statsBlob = "{}",
        completedAtMs = completedAtMs,
        firstCompletedAtMs = firstCompletedAtMs,
        isRecord = isRecord,
    )

    // ----- fromRecord (submit-watcher path) -----

    @Test
    fun `record row derives an entry`() {
        val entry = LeaderboardEntries.fromRecord(record(), uid = "u1", displayName = "Ann")!!
        assertEquals("u1", entry.uid)
        assertEquals("Ann", entry.displayName)
        assertEquals(refKey, entry.puzzleRef)
        assertEquals(95_000L, entry.timeMs)
        assertEquals(2, entry.hints)
        assertEquals(1_700_000_000_000L, entry.completedAtMs)
    }

    @Test
    fun `replay rows are never leaderboard-eligible (A5)`() {
        assertNull(LeaderboardEntries.fromRecord(record(isRecord = false), "u1", "Ann"))
        assertNull(LeaderboardEntries.fromEntity(entity(isRecord = false), "u1", "Ann"))
    }

    @Test
    fun `record without the A4 first-win stamp derives nothing`() {
        assertNull(
            LeaderboardEntries.fromRecord(record(firstCompletedAt = null), "u1", "Ann"),
        )
    }

    @Test
    fun `unsane scores derive nothing (rules envelope)`() {
        assertNull(LeaderboardEntries.fromRecord(record(timeMs = 0), "u1", "Ann"))
        assertNull(LeaderboardEntries.fromRecord(record(timeMs = -1), "u1", "Ann"))
        assertNull(LeaderboardEntries.fromRecord(record(hints = -1), "u1", "Ann"))
        assertNull(LeaderboardEntries.fromEntity(entity(timeMs = 0), "u1", "Ann"))
        assertNull(LeaderboardEntries.fromEntity(entity(hints = -1), "u1", "Ann"))
    }

    @Test
    fun `non-daily refs derive nothing`() {
        // No puzzle number: not official-daily-shaped (Phase-2 custom boards later).
        assertNull(
            LeaderboardEntries.fromRecord(
                record(ref = Ref(zip, day, puzzleNumber = null)),
                "u1",
                "Ann",
            ),
        )
        // Any game's unnumbered ref is not daily-shaped either (keys are game-agnostic).
        assertNull(
            LeaderboardEntries.fromRecord(record(ref = UnregisteredRef()), "u1", "Ann"),
        )
    }

    // ----- fromEntity (cache/repair path) -----

    @Test
    fun `entity row prefers the first-win stamp for completedAt`() {
        val entry = LeaderboardEntries.fromEntity(entity(), uid = "u2", displayName = "Bo")!!
        assertEquals(1_700_000_000_000L, entry.completedAtMs)
    }

    @Test
    fun `entity row falls back to completedAt when the stamp is missing`() {
        val entry = LeaderboardEntries.fromEntity(
            entity(firstCompletedAtMs = null),
            uid = "u2",
            displayName = "Bo",
        )!!
        assertEquals(1_700_000_500_000L, entry.completedAtMs)
    }

    @Test
    fun `entity with non-daily key derives nothing`() {
        assertNull(LeaderboardEntries.fromEntity(entity(puzzleRef = "zip/2026-10-31"), "u1", "Ann"))
        assertNull(LeaderboardEntries.fromEntity(entity(puzzleRef = "zip/not-a-day/5"), "u1", "Ann"))
    }

    // ----- board identity -----

    @Test
    fun `board id round-trips`() {
        val board = LeaderboardRef.fromPuzzleRefKey(refKey)!!
        assertEquals(LeaderboardRef("zip", day), board)
        assertEquals("zip_2026-10-31", board.boardId)
        assertEquals(board, LeaderboardRef.fromBoardId(board.boardId))
    }

    @Test
    fun `malformed board ids and keys derive nothing`() {
        assertNull(LeaderboardRef.fromBoardId("zip"))
        assertNull(LeaderboardRef.fromBoardId("zip_not-a-date"))
        assertNull(LeaderboardRef.fromBoardId("zip_2026-13-99"))
        assertNull(LeaderboardRef.fromPuzzleRefKey("zip/2026-10-31"))
        assertNull(LeaderboardRef.fromPuzzleRefKey("zip/2026-10-31/0"))
        assertNull(LeaderboardRef.fromPuzzleRefKey("zip/not-a-day/592"))
        assertNull(LeaderboardRef.fromPuzzleRefKey("zip/2026-10-31/592/extra"))
    }

    @Test
    fun `A3 ordering is time then hints, lower is better`() {
        val entries = listOf(
            LeaderboardEntry("a", "A", refKey, timeMs = 200_000, hints = 0, completedAtMs = 1),
            LeaderboardEntry("b", "B", refKey, timeMs = 100_000, hints = 5, completedAtMs = 2),
            LeaderboardEntry("c", "C", refKey, timeMs = 100_000, hints = 1, completedAtMs = 3),
        ).sortedWith(leaderboardOrder)
        assertEquals(listOf("c", "b", "a"), entries.map { it.uid })
    }

    @Test
    fun `snapshot envelope is versioned (A19)`() {
        val snapshot = LeaderboardSnapshot(LeaderboardRef("zip", day), entries = emptyList())
        assertEquals(1, snapshot.formatVersion)
        assertEquals(1, LeaderboardSnapshot.FORMAT_VERSION)
    }

    @Test
    fun `empty or blank display names fall back to Player`() {
        // Google may deliver an empty/blank display name (null maps to "" upstream);
        // the rules would reject it and the player would be invisible on every board.
        val blank = LeaderboardEntries.fromRecord(record(), uid = "u1", displayName = "   ")
        assertEquals("Player", blank!!.displayName)

        val empty = LeaderboardEntries.fromEntity(entity(), uid = "u1", displayName = "")
        assertEquals("Player", empty!!.displayName)
    }

    @Test
    fun `overlong display names truncate to the rules limit`() {
        val long = LeaderboardEntries.fromRecord(record(), uid = "u1", displayName = "x".repeat(80))
        assertEquals(64, long!!.displayName.length)

        val entitySide = LeaderboardEntries.fromEntity(entity(), uid = "u1", displayName = "y".repeat(65))
        assertEquals(64, entitySide!!.displayName.length)
    }

    @Test
    fun `normal display names pass through untouched`() {
        val normal = LeaderboardEntries.fromRecord(record(), uid = "u1", displayName = "Ann")
        assertEquals("Ann", normal!!.displayName)
    }
}

/** A ref shape no registered game ever authors — exercises the preserve-or-drop path. */
private class UnregisteredRef : PuzzleRef {
    override val gameId: com.ivandsky.puzzlebox.model.GameId =
        com.ivandsky.puzzlebox.model.GameId("unregistered")
    override val date: java.time.LocalDate = java.time.LocalDate.of(2026, 10, 31)
    override val puzzleNumber: Int? = null
}
