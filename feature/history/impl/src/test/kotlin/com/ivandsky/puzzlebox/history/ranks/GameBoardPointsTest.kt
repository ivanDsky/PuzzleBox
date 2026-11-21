package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.BoardPage
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.linkedin.LinkedInGhost
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInBoardView
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayStatus
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayView
import com.ivandsky.puzzlebox.domain.ranks.BoardLoad
import com.ivandsky.puzzlebox.domain.ranks.RankWindowBoards
import com.ivandsky.puzzlebox.domain.ranks.amongFriends
import com.ivandsky.puzzlebox.domain.ranks.connectionUid
import com.ivandsky.puzzlebox.domain.ranks.percentilePoints
import com.ivandsky.puzzlebox.domain.ranks.pointsBeyondPage
import com.ivandsky.puzzlebox.domain.ranks.rankBoard
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.history.HistoryTestSupport
import com.ivandsky.puzzlebox.history.HistoryTestSupport.ZIP
import com.ivandsky.puzzlebox.domain.ranks.windowStandings
import com.ivandsky.puzzlebox.history.ranks.RanksSelection.Window
import com.ivandsky.puzzlebox.resultui.board.BoardRowValue
import com.ivandsky.puzzlebox.resultui.board.DailyBoardRowModel
import com.ivandsky.puzzlebox.resultui.board.DailyBoardState
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A game's Week and Month ranked by the sum of its daily place points (#1698): Overall's scoring, one game. */
class GameBoardPointsTest {

    private val monday = LocalDate.of(2026, 11, 9)
    private val ann = Identity("u1", "Ann", null)

    private fun ref(day: Int) = LeaderboardRef(ZIP.value, monday.plusDays(day.toLong()))

    private fun entry(uid: String, timeMs: Long) =
        LeaderboardEntry(uid, uid.replaceFirstChar(Char::uppercase), "${ZIP.value}/$monday/1", timeMs, 0, 1_700_000_000_000)

    private fun loaded(ref: LeaderboardRef, vararg entries: LeaderboardEntry, count: Int = entries.size) =
        BoardLoad.Loaded(ref, RankOrder.TimeThenHints, BoardPage(entries.toList(), count), final = false)

    private fun boards(vararg loads: BoardLoad) = RankWindowBoards.Boards(loads.toList())

    /** [boards] as Ann sees this game's [kind] board, her [records] telling which boards she solved officially. */
    private fun points(boards: RankWindowBoards, records: List<CompletionRecord> = emptyList(), kind: Window = Window.Week) =
        rows(boards.toGameBoardState(ZIP, RankOrder.TimeThenHints, kind, ann.uid) { windowStandings(it, ann, records) })

    private fun rows(state: DailyBoardState) = (state as DailyBoardState.Ranked).rows

    private fun DailyBoardRowModel.dayPoints() = scoreValue as BoardRowValue.DayPoints

    /** Every player's points summed over [boards]' days, each day ranked on its own. */
    private fun dayBoardSums(boards: RankWindowBoards): Map<String, Double> = (boards as RankWindowBoards.Boards).boards
        .filterIsInstance<BoardLoad.Loaded>()
        .flatMap { rankBoard(it.board, it.page.entries, it.order, it.page.count).rows }
        .groupBy({ it.entry.uid }, { it.points })
        .mapValues { (_, points) -> points.sum() }

    // The report: "a" won only an easy Monday; "b" won Tuesday to Thursday and was 3rd on Monday and Friday, slower days.
    private val reported = boards(
        loaded(ref(0), entry("a", 30_000), entry("c", 40_000), entry("b", 50_000)),
        loaded(ref(1), entry("b", 90_000), entry("c", 95_000)),
        loaded(ref(2), entry("b", 90_000), entry("c", 95_000)),
        loaded(ref(3), entry("b", 90_000), entry("c", 95_000)),
        loaded(ref(4), entry("c", 100_000), entry("d", 110_000), entry("b", 120_000)),
    )

    @Test
    fun `the reported week puts three wins and two 3rd places above one easy Monday win`() {
        val rows = points(reported)

        assertEquals(listOf("b", "c", "a", "d"), rows.map { it.playerId })
        assertEquals(listOf(1, 2, 3, 4), rows.map { it.rank })
        assertEquals(300.0 + 2 * percentilePoints(3, 3), rows[0].dayPoints().total, 1e-9)
        assertEquals(5, rows[0].dayPoints().days)
        assertEquals(BoardRowValue.DayPoints(100.0, days = 1, atLeast = false), rows[2].scoreValue)
    }

    @Test
    fun `a skipped day adds nothing, so playing one more day never lowers a place`() {
        val twoDays = arrayOf(loaded(ref(0), entry("x", 50_000), entry("y", 50_000)), loaded(ref(1), entry("x", 50_000), entry("y", 50_000)))
        assertEquals(listOf(1, 1), points(boards(*twoDays)).map { it.rank })

        val rows = points(boards(*twoDays, loaded(ref(2), entry("z", 10_000), entry("x", 99_000))))

        assertEquals(listOf("x" to 1, "y" to 2, "z" to 3), rows.map { it.playerId to it.rank })
        assertEquals(BoardRowValue.DayPoints(200.0, days = 2, atLeast = false), rows[1].scoreValue)
        assertEquals(3, rows[0].dayPoints().days)
    }

    @Test
    fun `equal totals share a place`() {
        val rows = points(
            boards(
                loaded(ref(0), entry("p", 10_000), entry("q", 20_000)),
                loaded(ref(1), entry("q", 10_000), entry("p", 20_000)),
                loaded(ref(2), entry("r", 10_000), entry("s", 20_000), entry("t", 30_000)),
            ),
        )

        assertEquals(listOf(1, 1, 3, 4, 5), rows.map { it.rank })
        assertEquals(setOf("p", "q"), rows.take(2).map { it.playerId }.toSet())
    }

    @Test
    fun `totals are the day boards' points summed, Global and among friends with their LinkedIn connections`() {
        val global = boards(
            loaded(ref(0), entry("u1", 60_000), entry("maya", 70_000), entry("zed", 50_000)),
            loaded(ref(1), entry("maya", 65_000), entry("u1", 80_000), entry("zed", 40_000)),
        )
        val cal = LinkedInGhost("ref-cal", "Cal", 70_000, hintFree = true, flawless = false)
        val saved = mapOf(ref(1) to LinkedInDayView(LinkedInDayStatus.Idle, LinkedInBoardView.Shown(listOf(cal))))
        val friends = global.amongFriends(setOf("u1", "maya"), saved, ann)

        for (window in listOf(global, friends)) {
            val sums = dayBoardSums(window)
            val rows = points(window, kind = Window.Month)
            assertEquals(sums.keys, rows.map { it.playerId }.toSet())
            rows.forEach { assertEquals(it.playerId, sums.getValue(it.playerId), it.dayPoints().total, 1e-9) }
        }
        assertEquals(listOf("maya", "u1", connectionUid("ref-cal")), points(friends).map { it.playerId })
    }

    @Test
    fun `past a full page with an official solve the viewer scores the floor, at least, and reads 100+ when it is below the top 100`() {
        val page = (1..BoardPage.PAGE_SIZE).map { entry("p$it", it * 1_000L) }.toTypedArray()
        val official = HistoryTestSupport.record(monday).copy(firstCompletedAt = Instant.parse("2026-11-09T08:00:00Z"), isRecord = true)

        val alone = points(boards(loaded(ref(0), *page, count = 400)), listOf(official)).single { it.isSelf }

        assertEquals(BoardRowValue.DayPoints(pointsBeyondPage(400), days = 1, atLeast = true), alone.scoreValue)
        assertEquals("Ann", alone.name)
        assertEquals(101, alone.rank)
        assertTrue(alone.beyondPage)

        val week = points(boards(loaded(ref(0), *page, count = 400), loaded(ref(1), entry("p1", 1_000), entry("u1", 2_000))), listOf(official))
        val you = week.single { it.isSelf }
        assertEquals(pointsBeyondPage(400) + percentilePoints(2, 2), you.dayPoints().total, 1e-9)
        assertEquals(2, you.dayPoints().days)
        assertTrue(you.dayPoints().atLeast)
        assertFalse(you.beyondPage)
    }

    @Test
    fun `without an official solve the viewer is not placed past the page`() {
        val page = (1..BoardPage.PAGE_SIZE).map { entry("p$it", it * 1_000L) }.toTypedArray()

        assertTrue(points(boards(loaded(ref(0), *page, count = 400))).none { it.isSelf })
    }

    @Test
    fun `a friend's LinkedIn-only day sums into their one row, matched by name as on a day`() {
        val global = boards(
            loaded(ref(0), entry("u1", 60_000), entry("maya", 70_000)),
            loaded(ref(1), entry("u1", 80_000)),
        )
        val mayaOnLinkedIn = LinkedInGhost("ref-maya", " MAYA ", 50_000, hintFree = true, flawless = false)
        val saved = mapOf(ref(1) to LinkedInDayView(LinkedInDayStatus.Idle, LinkedInBoardView.Shown(listOf(mayaOnLinkedIn))))

        val friends = global.amongFriends(setOf("u1", "maya"), saved, ann)
        val rows = points(friends, kind = Window.Month)

        assertEquals(setOf("maya", "u1"), rows.map { it.playerId }.toSet())
        val maya = rows.single { it.playerId == "maya" }
        assertEquals(2, maya.dayPoints().days)
        assertEquals("Maya", maya.name)
    }
}
