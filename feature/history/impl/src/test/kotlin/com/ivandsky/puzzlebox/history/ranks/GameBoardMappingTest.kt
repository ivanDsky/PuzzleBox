package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.data.leaderboard.BoardPage
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.domain.ranks.BoardLoad
import com.ivandsky.puzzlebox.domain.ranks.RankWindowBoards
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.board.BoardRowValue
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import com.ivandsky.puzzlebox.history.HistoryTestSupport.TANGO
import com.ivandsky.puzzlebox.history.HistoryTestSupport.ZIP
import com.ivandsky.puzzlebox.history.ranks.RanksSelection.Window
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** One game's slice of the shared window boards as the kit's board: day timed, week and month averaged (A36). */
class GameBoardMappingTest {

    private val monday = LocalDate.of(2026, 11, 9)

    private fun ref(day: Int, game: String = ZIP.value) = LeaderboardRef(game, monday.plusDays(day.toLong()))

    private fun entry(uid: String, timeMs: Long, hints: Int = 0, game: String = ZIP.value) =
        LeaderboardEntry(uid, uid.replaceFirstChar(Char::uppercase), "$game/$monday/1", timeMs, hints, 1_700_000_000_000)

    private fun loaded(ref: LeaderboardRef, vararg entries: LeaderboardEntry, order: RankOrder = RankOrder.TimeThenHints) =
        BoardLoad.Loaded(ref, order, BoardPage(entries.toList(), entries.size), final = false)

    private fun boards(vararg loads: BoardLoad) = RankWindowBoards.Boards(loads.toList())

    private fun state(boards: RankWindowBoards?, kind: Window = Window.Day, game: GameId = ZIP, order: RankOrder = RankOrder.TimeThenHints) =
        boards.toGameBoardState(game, order, kind, selfUid = "u1")

    private fun rows(state: DailyBoardState) = (state as DailyBoardState.Ranked).rows

    @Test
    fun `day ranks the board by its order with timed rows and the viewer marked`() {
        val result = state(boards(loaded(ref(0), entry("u1", 75_000, 1), entry("maya", 62_000))))

        val rows = rows(result)
        assertEquals(listOf("Maya", "U1"), rows.map { it.name })
        assertEquals(listOf("maya", "u1"), rows.map { it.playerId })
        assertEquals(listOf(1, 2), rows.map { it.rank })
        assertEquals(listOf(false, true), rows.map { it.isSelf })
        assertEquals(BoardRowValue.Timed(75_000, 1), rows[1].scoreValue)
    }

    @Test
    fun `a day board of a hints-first game puts the fewest hints on top`() {
        val order = RankOrder.HintsThenTime
        val result = state(
            boards(loaded(ref(0), entry("slow", 90_000, 0), entry("fast", 30_000, 2), order = order)),
            order = order,
        )

        assertEquals(listOf("slow", "fast"), rows(result).map { it.playerId })
    }

    @Test
    fun `week averages each player's days and ranks by the average`() {
        val result = state(
            boards(
                loaded(ref(0), entry("maya", 60_000), entry("u1", 80_000)),
                loaded(ref(1), entry("maya", 100_000), entry("u1", 70_000)),
                loaded(ref(2), entry("jonas", 50_000)),
            ),
            kind = Window.Week,
        )

        val rows = rows(result)
        assertEquals(listOf("jonas", "u1", "maya"), rows.map { it.playerId })
        assertEquals(listOf(1, 2, 3), rows.map { it.rank })
        assertEquals(BoardRowValue.Average(75_000, 0.0), rows[1].scoreValue)
        assertEquals(true, rows[1].isSelf)
    }

    @Test
    fun `only this game's boards count`() {
        val result = state(
            boards(loaded(ref(0), entry("u1", 70_000)), loaded(ref(0, TANGO.value), entry("jonas", 40_000, game = TANGO.value))),
        )

        assertEquals(listOf("u1"), rows(result).map { it.playerId })
    }

    @Test
    fun `no answer yet is loading and signed out is signed out`() {
        assertEquals(DailyBoardState.Loading, state(null))
        assertEquals(DailyBoardState.SignedOut, state(RankWindowBoards.SignedOut))
        assertEquals(DailyBoardState.Loading, state(boards(BoardLoad.Loading(ref(0), RankOrder.TimeThenHints))))
    }

    @Test
    fun `an unreadable board is unavailable, an empty one is an empty ranking`() {
        assertEquals(DailyBoardState.Unavailable, state(boards(BoardLoad.Unavailable(ref(0), RankOrder.TimeThenHints))))
        assertEquals(DailyBoardState.Ranked(emptyList()), state(boards(loaded(ref(0)))))
        assertEquals(DailyBoardState.Ranked(emptyList()), state(boards()))
    }

    @Test
    fun `a week with days still out says how many are in`() {
        val partial = boards(loaded(ref(0), entry("u1", 70_000)), BoardLoad.Loading(ref(1), RankOrder.TimeThenHints))

        assertEquals(listOf("u1"), rows(state(partial, Window.Week)).map { it.playerId })
        assertEquals("1 of 2 days", partial.gameBoardNote(ZIP, Window.Week))
        assertNull(partial.gameBoardNote(ZIP, Window.Day))
        assertNull(boards(loaded(ref(0), entry("u1", 1))).gameBoardNote(ZIP, Window.Week))
        assertNull(null.gameBoardNote(ZIP, Window.Week))
    }
}
