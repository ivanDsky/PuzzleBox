package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.BoardPage
import com.ivandsky.puzzlebox.data.leaderboard.inSubmitWindow
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.domain.ranks.BoardLoad
import com.ivandsky.puzzlebox.domain.ranks.RankWindowBoards
import com.ivandsky.puzzlebox.domain.ranks.amongFriends
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.board.BoardRowValue
import com.ivandsky.puzzlebox.gameui.board.DailyBoardLine
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import com.ivandsky.puzzlebox.gameui.board.DailyBoardRowModel
import com.ivandsky.puzzlebox.gameui.board.GhostRowModel
import com.ivandsky.puzzlebox.history.HistoryDayRow
import com.ivandsky.puzzlebox.history.HistoryTestSupport
import com.ivandsky.puzzlebox.history.HistoryTestSupport.TANGO
import com.ivandsky.puzzlebox.history.HistoryTestSupport.ZIP
import com.ivandsky.puzzlebox.history.ranks.RanksSelection.Window
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
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

    private val nora = Identity("nora", "Nora", null)

    private fun historyRow(timeMs: Long, hints: Int = 0, day: Int = 0) = HistoryDayRow(
        day = monday.plusDays(day.toLong()),
        puzzleNumber = 1,
        gameId = ZIP,
        gameName = "Zip",
        record = HistoryTestSupport.record(monday.plusDays(day.toLong()), timeMs, hints),
    )

    private val lateBoard = boards(
        loaded(ref(0), entry("lia", 250), entry("max", 260, 1), entry("you", 300), entry("gwen", 330), entry("omar", 340)),
    )

    /** The card of [boards] as Nora sees it with a solve of [timeMs] whose day is [inWindow] or not. */
    private fun noraCard(timeMs: Long, inWindow: Boolean = false, boards: RankWindowBoards = lateBoard): List<DailyBoardRowModel> {
        val row = boards.unofficialRow(ZIP, RankOrder.TimeThenHints, nora, listOf(historyRow(timeMs))) { inWindow }
        return rows(boards.toGameBoardState(ZIP, RankOrder.TimeThenHints, Window.Day, "nora").withUnofficialRow(row))
    }

    private fun assertOfficialUntouched(rows: List<DailyBoardRowModel>) {
        assertEquals(listOf("lia", "max", "you", "gwen", "omar"), rows.filterNot { it.unofficial }.map { it.playerId })
        assertEquals(listOf(1, 2, 3, 4, 5), rows.filterNot { it.unofficial }.map { it.rank })
    }

    @Test
    fun `case 14a a late solve ahead of everyone is the unofficial first and moves nobody`() {
        val rows = noraCard(95)

        assertEquals(listOf("nora", "lia", "max", "you", "gwen", "omar"), rows.map { it.playerId })
        assertEquals(1, rows[0].rank)
        assertEquals(true, rows[0].unofficial)
        assertOfficialUntouched(rows)
    }

    @Test
    fun `case 14b a late solve between two players sits between them`() {
        val rows = noraCard(255)

        assertEquals(listOf("lia", "nora", "max", "you", "gwen", "omar"), rows.map { it.playerId })
        assertEquals(2, rows[1].rank)
        assertOfficialUntouched(rows)
    }

    @Test
    fun `case 14c a late solve equal to a player shares that place`() {
        val rows = noraCard(300)

        assertEquals(listOf("lia", "max", "nora", "you", "gwen", "omar"), rows.map { it.playerId })
        assertEquals(listOf(3, 3), listOf(rows[2].rank, rows[3].rank))
        assertOfficialUntouched(rows)
    }

    @Test
    fun `case 15 a solve past the window edge is unofficial ahead of the official first`() {
        val zip = boards(loaded(ref(0), entry("p", 50), entry("q", 60)))

        val rows = noraCard(40, boards = zip)

        assertEquals(listOf("nora", "p", "q"), rows.map { it.playerId })
        assertEquals(listOf(1, 1, 2), rows.map { it.rank })
    }

    @Test
    fun `inside the submit window the solve is not unofficial`() {
        assertEquals(listOf("lia", "max", "you", "gwen", "omar"), noraCard(95, inWindow = true).map { it.playerId })
    }

    @Test
    fun `the window boundary day is official yesterday and unofficial the day before`() {
        val calendar = FakeGameCalendar(monday.plusDays(1))

        fun row() = lateBoard.unofficialRow(ZIP, RankOrder.TimeThenHints, nora, listOf(historyRow(95))) { calendar.inSubmitWindow(ZIP, it) }

        assertNull(row())
        calendar.setAll(monday.plusDays(2))
        assertEquals(true, row()?.unofficial)
    }

    private fun ghost(name: String, timeMs: Long?, hintFree: Boolean = true) = GhostRowModel("ref-$name", name, timeMs, hintFree)

    /** The Friends card as the hub builds it (F18): the board among the viewer and [friends], then the ghosts over it. */
    private fun friendsCard(input: BoardGhostInput, friends: Set<String>, board: RankWindowBoards): RanksBoardSlice {
        val state = board.amongFriends(friends + "u1").toGameBoardState(TANGO, RankOrder.TimeThenHints, Window.Day, "u1")
        return RanksBoardSlice("Tango", RankOrder.TimeThenHints, state).inFriends(input)
    }

    private fun lines(slice: RanksBoardSlice) = (slice.state as DailyBoardState.Ranked).lines!!

    private fun named(line: DailyBoardLine) = when (line) {
        is DailyBoardLine.Real -> line.row.name to line.place
        is DailyBoardLine.Ghost -> line.row.name to line.place
    }

    private val caseFive = boards(
        loaded(
            ref(0, TANGO.value),
            entry("sam", 58_000, 1, TANGO.value),
            entry("lia", 62_000, 1, TANGO.value),
            entry("ria", 66_000, 0, TANGO.value),
            entry("u1", 70_000, 0, TANGO.value),
            entry("gwen", 96_000, 0, TANGO.value),
        ),
    )

    @Test
    fun `case 5 the card puts the ghost among the rows by time and says so`() {
        val slice = friendsCard(BoardGhostInput(listOf(ghost("Cal", 64_000, hintFree = false))), setOf("sam", "lia", "ria", "gwen"), caseFive)

        assertEquals(
            listOf("Sam" to 1, "Lia" to 2, "Cal" to 3, "Ria" to 4, "U1" to 5, "Gwen" to 6),
            lines(slice).map(::named),
        )
        assertEquals("with LinkedIn connections", slice.note)
    }

    @Test
    fun `a ghost takes a friend place, the friends numbered among themselves`() {
        val slice = friendsCard(BoardGhostInput(listOf(ghost("Cal", 64_000, hintFree = false))), setOf("ria"), caseFive)

        assertEquals(listOf("Cal" to 1, "Ria" to 2, "U1" to 3), lines(slice).map(::named))
        assertEquals(listOf("ria" to 1, "u1" to 2), rows(slice.state).map { it.playerId to it.rank })
    }

    @Test
    fun `a hidden-time ghost has no place and goes last`() {
        val slice = friendsCard(BoardGhostInput(listOf(ghost("Cal", null))), setOf("sam"), caseFive)

        assertEquals(listOf("Sam" to 1, "U1" to 2, "Cal" to null), lines(slice).map(::named))
    }

    @Test
    fun `no ghosts is the plain friends board, numbered among friends, with no note`() {
        val slice = friendsCard(BoardGhostInput.None, setOf("sam", "gwen"), caseFive)

        assertEquals(listOf("sam" to 1, "u1" to 2, "gwen" to 3), rows(slice.state).map { it.playerId to it.rank })
        assertNull((slice.state as DailyBoardState.Ranked).lines)
        assertNull(slice.note)
        assertEquals(false, slice.friendsAlone)
    }

    @Test
    fun `the unofficial row stays out of the ghost ranking and takes no place from anyone`() {
        val base = lateBoard.toGameBoardState(ZIP, RankOrder.TimeThenHints, Window.Day, "nora")
        val row = lateBoard.unofficialRow(ZIP, RankOrder.TimeThenHints, nora, listOf(historyRow(255))) { false }
        val slice = RanksBoardSlice("Zip", RankOrder.TimeThenHints, base.withUnofficialRow(row))
            .inFriends(BoardGhostInput(listOf(ghost("Cal", 255))))

        assertEquals(
            listOf("Lia" to 1, "Nora" to 2, "Cal" to 2, "Max" to 3, "You" to 4, "Gwen" to 5, "Omar" to 6),
            lines(slice).map(::named),
        )
    }

    @Test
    fun `a connection without a picture is still LinkedIn's and opens nothing, marked by the board's photos not its uid`() {
        val board = boards(loaded(ref(0), entry("sam", 58_000), entry("cal", 62_000)))
        val slice = RanksBoardSlice("Zip", RankOrder.TimeThenHints, state(board)).inFriendsCounted(mapOf("cal" to null))

        assertEquals(listOf("sam" to false, "cal" to true), rows(slice.state).map { it.playerId to it.fromLinkedIn })
        assertEquals(true, rows(slice.state).last().timeFromLinkedIn)
        assertNull(rows(slice.state).last().photo)
        assertEquals("with LinkedIn connections", slice.note)
    }
}
