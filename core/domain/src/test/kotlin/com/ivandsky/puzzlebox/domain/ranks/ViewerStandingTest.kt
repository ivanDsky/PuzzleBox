package com.ivandsky.puzzlebox.domain.ranks

import com.ivandsky.puzzlebox.data.leaderboard.BoardPage
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.game.TestGames
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** 03 handoff cases 14 (late archive play, three variants) and 15 (the submit window's edge). */
class ViewerStandingTest {

    private val day = LocalDate.of(2026, 11, 12)
    private val order = RankOrder.TimeThenHints

    private fun entry(uid: String, timeMs: Long, hints: Int = 0) =
        LeaderboardEntry(uid, uid, "sudoku/$day/1", timeMs, hints, 1_700_000_000_000)

    private fun board(vararg entries: LeaderboardEntry) = BoardPage(entries.toList(), entries.size)

    private fun solve(timeMs: Long, hints: Int = 0) = CompletionRecord(
        puzzleRef = TestGames.Ref(GameId("sudoku"), PuzzleKey.Daily(day, 1)),
        time = timeMs,
        hints = hints,
        stats = RecordStats(1, "{}"),
    )

    private val sudoku = board(entry("lia", 250, 0), entry("max", 260, 1), entry("you", 300), entry("gwen", 330), entry("omar", 340))

    private fun late(timeMs: Long, hints: Int = 0) = viewerStanding(solve(timeMs, hints), inWindow = false, sudoku, "nora", order)

    @Test
    fun `case 14a a late solve faster than everyone is unofficial first`() {
        assertEquals(ViewerStanding.Unofficial(1), late(95))
    }

    @Test
    fun `case 14b a late solve between two players sits between them`() {
        assertEquals(ViewerStanding.Unofficial(2), late(255))
    }

    @Test
    fun `case 14c a late solve equal to a player shares that place`() {
        assertEquals(ViewerStanding.Unofficial(3), late(300))
    }

    @Test
    fun `case 15 a solve past the window edge is unofficial at the place it would rank`() {
        val zip = board(entry("p", 50), entry("q", 60))

        assertEquals(ViewerStanding.Unofficial(1), viewerStanding(solve(40), inWindow = false, zip, "r", order))
    }

    @Test
    fun `a solve inside the window that is not on the board yet is nothing to mark`() {
        assertEquals(ViewerStanding.None, viewerStanding(solve(95), inWindow = true, sudoku, "nora", order))
    }

    @Test
    fun `a row on the board is official even for a day outside the window`() {
        assertEquals(ViewerStanding.Official, viewerStanding(solve(300), inWindow = false, sudoku, "you", order))
    }

    @Test
    fun `no solve or no account is none`() {
        assertEquals(ViewerStanding.None, viewerStanding(null, inWindow = false, sudoku, "nora", order))
        assertEquals(ViewerStanding.None, viewerStanding(solve(95), inWindow = false, sudoku, null, order))
    }
}
