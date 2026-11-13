package com.ivandsky.puzzlebox.domain.ranks

import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.game.RankOrder
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** Per-game Week/Month standings (A36): average over days played, Strands hints first, tie shares the place. */
class GameWindowStandingsTest {

    private fun board(game: String, dayOfMonth: Int, order: RankOrder, vararg solves: Triple<String, Long, Int>): RankedBoard {
        val ref = LeaderboardRef(game, LocalDate.of(2026, 11, dayOfMonth))
        val entries = solves.map { (uid, seconds, hints) ->
            LeaderboardEntry(uid, uid, "$game/${ref.day}/1", seconds * 1_000, hints, 0)
        }
        return rankBoard(ref, entries, order, entries.size)
    }

    private fun s(uid: String, seconds: Long, hints: Int = 0) = Triple(uid, seconds, hints)

    private val time = RankOrder.TimeThenHints
    private val hints = RankOrder.HintsThenTime

    @Test
    fun `averages over the days a player played`() {
        val standings = gameWindowStandings(
            listOf(
                board("tango", 9, time, s("A", 40), s("B", 60)),
                board("tango", 10, time, s("A", 80, 2)),
            ),
            time,
        )
        val a = standings.rows.single { it.uid == "A" }
        assertEquals(60_000L, a.avgTimeMs)
        assertEquals(1.0, a.avgHints, 0.0)
        assertEquals(2, a.days)
        val b = standings.rows.single { it.uid == "B" }
        assertEquals(1, b.days)
    }

    @Test
    fun `one day of 50 s beats two days averaging 60 s`() {
        val standings = gameWindowStandings(
            listOf(
                board("tango", 9, time, s("A", 50), s("B", 40)),
                board("tango", 10, time, s("B", 80)),
            ),
            time,
        )
        assertEquals(listOf("A", "B"), standings.rows.map { it.uid })
        assertEquals(listOf(1, 2), standings.rows.map { it.place })
    }

    @Test
    fun `Strands ranks the average hints before the average time`() {
        val standings = gameWindowStandings(
            listOf(board("strands", 9, hints, s("A", 200), s("B", 100, 1), s("C", 90, 2), s("D", 300))),
            hints,
        )
        assertEquals(listOf("A", "D", "B", "C"), standings.rows.map { it.uid })
        assertEquals(listOf(1, 2, 3, 4), standings.rows.map { it.place })
    }

    @Test
    fun `equal averages share a place and list by uid`() {
        val standings = gameWindowStandings(
            listOf(
                board("tango", 9, time, s("B", 60), s("A", 60), s("C", 90)),
            ),
            time,
        )
        assertEquals(listOf("A", "B", "C"), standings.rows.map { it.uid })
        assertEquals(listOf(1, 1, 3), standings.rows.map { it.place })
    }

    @Test
    fun `equal time ties break on average hints`() {
        val standings = gameWindowStandings(
            listOf(board("tango", 9, time, s("A", 60, 1), s("B", 60))),
            time,
        )
        assertEquals(listOf("B", "A"), standings.rows.map { it.uid })
        assertEquals(listOf(1, 2), standings.rows.map { it.place })
    }
}
