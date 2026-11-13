package com.ivandsky.puzzlebox.testing.repository

import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.game.RankOrder
import java.time.LocalDate

/**
 * The Overall scoring contract table (spec A9–A14, prototype 03 §8): boards of server entries
 * in, per-solve rank and points, Overall rows and device-only places out. Plain data over
 * core:data and core:game types; the domain test maps it onto the ranks functions. Rows a
 * case lists are the ones it pins (filler entries of the 100-cap cases are not listed), in
 * display order with their competition place. Completeness (missing boards) is not part of
 * Overall, so case 13 pins only what the loaded boards give.
 */
object OverallCases {

    /** One board: [entries] as read, [count] the server's size for the board. */
    data class Board(
        val ref: LeaderboardRef,
        val order: RankOrder,
        val entries: List<LeaderboardEntry>,
        val count: Int = entries.size,
    )

    data class SolveExpectation(val ref: LeaderboardRef, val uid: String, val rank: Int, val points: Double)

    data class Row(val uid: String, val place: Int, val total: Double, val games: Int, val truncated: Boolean = false)

    /** Overall over the boards whose day is in [from]..[to]. */
    data class Window(val from: LocalDate, val to: LocalDate, val rows: List<Row>)

    /** A device-only score probed against [ref]'s official entries. */
    data class UnofficialProbe(val ref: LeaderboardRef, val timeMs: Long, val hints: Int, val place: Int)

    data class Case(
        val name: String,
        val boards: List<Board>,
        val solves: List<SolveExpectation>,
        val windows: List<Window>,
        val viewerUid: String? = null,
        val viewerBeyondPage: Set<LeaderboardRef> = emptySet(),
        val unofficial: List<UnofficialProbe> = emptyList(),
    )

    private val time = RankOrder.TimeThenHints
    private val hints = RankOrder.HintsThenTime
    private val everyDay = LocalDate.of(2000, 1, 1)..LocalDate.of(2100, 1, 1)

    private fun day(month: Int, dayOfMonth: Int) = LocalDate.of(2026, month, dayOfMonth)

    private fun ref(game: String, day: LocalDate) = LeaderboardRef(game, day)

    /** An entry of [uid] at [seconds] s and [hints]; a LinkedIn import is `(seconds, 0 or 1)`. */
    private fun entry(ref: LeaderboardRef, uid: String, seconds: Long, hints: Int = 0) = LeaderboardEntry(
        uid = uid,
        displayName = uid,
        puzzleRef = "${ref.gameId}/${ref.day}/1",
        timeMs = seconds * 1_000,
        hints = hints,
        completedAtMs = 0,
    )

    private fun board(ref: LeaderboardRef, order: RankOrder, vararg solves: Triple<String, Long, Int>) =
        Board(ref, order, solves.map { (uid, seconds, h) -> entry(ref, uid, seconds, h) })

    private fun s(uid: String, seconds: Long, hints: Int = 0) = Triple(uid, seconds, hints)

    private fun solve(ref: LeaderboardRef, uid: String, rank: Int, points: Double) =
        SolveExpectation(ref, uid, rank, points)

    private fun window(vararg rows: Row) = listOf(Window(everyDay.start, everyDay.endInclusive, rows.toList()))

    private val third = 100.0 / 3
    private val d12 = day(11, 12)
    private val tango12 = ref("tango", d12)
    private val strands12 = ref("strands", d12)
    private val sudoku12 = ref("sudoku", d12)

    private val tangoWeek = (9..11).map { ref("tango", day(11, it)) }

    /** Rank 1..100 board of fillers `f<rank>` with the named uids standing at their ranks. */
    private fun fullPage(ref: LeaderboardRef, named: Map<Int, String>): List<LeaderboardEntry> =
        (1..100).map { rank -> entry(ref, named[rank] ?: "f$rank", rank.toLong()) }

    private val pageRef = tango12

    private fun truncatedCase(): Case = Case(
        name = "11 100-cap truncation: past the page scores the bound, counts, marks truncated",
        boards = listOf(Board(pageRef, time, fullPage(pageRef, mapOf(1 to "P", 40 to "Q", 100 to "R")), count = 150)),
        solves = listOf(
            solve(pageRef, "P", 1, 100.0),
            solve(pageRef, "Q", 40, 74.0),
            solve(pageRef, "R", 100, 34.0),
        ),
        windows = window(
            Row("P", 1, 100.0, 1),
            Row("Q", 40, 74.0, 1),
            Row("R", 100, 34.0, 1),
            Row("S", 101, 100.0 * 50 / 150, 1, truncated = true),
        ),
        viewerUid = "S",
        viewerBeyondPage = setOf(pageRef),
    )

    private fun exactCapCase(): Case {
        val entries = (1..100).map { rank ->
            entry(pageRef, if (rank == 1) "U" else if (rank == 100) "T" else "f$rank", rank.toLong())
        }
        return Case(
            name = "12 exactly 100 entries stay exact",
            boards = listOf(Board(pageRef, time, entries)),
            solves = listOf(solve(pageRef, "T", 100, 1.0), solve(pageRef, "U", 1, 100.0)),
            windows = window(Row("U", 1, 100.0, 1), Row("T", 100, 1.0, 1)),
        )
    }

    private val tango10 = ref("tango", day(11, 10))
    private val tango9 = ref("tango", day(11, 9))
    private val sudokuBoard = board(
        sudoku12, time,
        s("Lia", 250), s("Max", 260, 1), s("You", 300), s("Gwen", 330), s("Omar", 340),
    )
    private val zip10 = ref("zip", day(11, 10))
    private val soloDays = listOf(day(10, 31), day(11, 8), day(11, 9), day(11, 15), day(11, 16)).map { ref("tango", it) }

    val all: List<Case> = listOf(
        Case(
            name = "1 lone solver",
            boards = listOf(board(tango12, time, s("A", 60))),
            solves = listOf(solve(tango12, "A", 1, 100.0)),
            windows = window(Row("A", 1, 100.0, 1)),
        ),
        Case(
            name = "2 four, no ties",
            boards = listOf(board(tango12, time, s("A", 50), s("B", 60), s("C", 70), s("D", 80))),
            solves = listOf(
                solve(tango12, "A", 1, 100.0), solve(tango12, "B", 2, 75.0),
                solve(tango12, "C", 3, 50.0), solve(tango12, "D", 4, 25.0),
            ),
            windows = window(Row("A", 1, 100.0, 1), Row("B", 2, 75.0, 1), Row("C", 3, 50.0, 1), Row("D", 4, 25.0, 1)),
        ),
        Case(
            name = "3 tie shares rank, hints break the tie",
            boards = listOf(board(tango12, time, s("A", 60), s("B", 60), s("C", 60, 1), s("D", 90))),
            solves = listOf(
                solve(tango12, "A", 1, 100.0), solve(tango12, "B", 1, 100.0),
                solve(tango12, "C", 3, 50.0), solve(tango12, "D", 4, 25.0),
            ),
            windows = window(Row("A", 1, 100.0, 1), Row("B", 1, 100.0, 1), Row("C", 3, 50.0, 1), Row("D", 4, 25.0, 1)),
        ),
        Case(
            name = "4 LinkedIn only tie-breaks",
            boards = listOf(board(tango12, time, s("You", 62), s("Ria", 62), s("Lia", 62, 1))),
            solves = listOf(
                solve(tango12, "You", 1, 100.0), solve(tango12, "Ria", 1, 100.0), solve(tango12, "Lia", 3, third),
            ),
            windows = window(Row("Ria", 1, 100.0, 1), Row("You", 1, 100.0, 1), Row("Lia", 3, third, 1)),
        ),
        Case(
            name = "5 LinkedIn hint ambiguity; the ghost is not on the server board",
            boards = listOf(
                board(tango12, time, s("Sam", 58, 1), s("Lia", 62, 1), s("Ria", 66), s("You", 70), s("Gwen", 96)),
            ),
            solves = listOf(
                solve(tango12, "Sam", 1, 100.0), solve(tango12, "Lia", 2, 80.0), solve(tango12, "Ria", 3, 60.0),
                solve(tango12, "You", 4, 40.0), solve(tango12, "Gwen", 5, 20.0),
            ),
            windows = window(
                Row("Sam", 1, 100.0, 1), Row("Lia", 2, 80.0, 1), Row("Ria", 3, 60.0, 1),
                Row("You", 4, 40.0, 1), Row("Gwen", 5, 20.0, 1),
            ),
            viewerUid = "You",
        ),
        Case(
            name = "6 Strands ranks hints first",
            boards = listOf(board(strands12, hints, s("A", 200), s("B", 100, 1), s("C", 90, 2), s("D", 300))),
            solves = listOf(
                solve(strands12, "A", 1, 100.0), solve(strands12, "D", 2, 75.0),
                solve(strands12, "B", 3, 50.0), solve(strands12, "C", 4, 25.0),
            ),
            windows = window(Row("A", 1, 100.0, 1), Row("D", 2, 75.0, 1), Row("B", 3, 50.0, 1), Row("C", 4, 25.0, 1)),
        ),
        Case(
            name = "7 Overall mixes both orders",
            boards = listOf(
                board(tango12, time, s("A", 50), s("B", 60), s("C", 70)),
                board(strands12, hints, s("A", 100, 1), s("B", 200), s("C", 300)),
            ),
            solves = listOf(
                solve(tango12, "A", 1, 100.0), solve(tango12, "B", 2, 2 * third), solve(tango12, "C", 3, third),
                solve(strands12, "B", 1, 100.0), solve(strands12, "C", 2, 2 * third), solve(strands12, "A", 3, third),
            ),
            windows = window(Row("B", 1, 100 + 2 * third, 2), Row("A", 2, 100 + third, 2), Row("C", 3, 100.0, 2)),
        ),
        Case(
            name = "8 grinder vs sniper: volume beats quality",
            boards = listOf(
                board(tangoWeek[0], time, s("S", 40), s("X", 50), s("G", 60), s("Y", 70)),
                board(tangoWeek[1], time, s("X", 50), s("G", 60), s("Y", 70)),
                board(tangoWeek[2], time, s("X", 50), s("G", 60), s("Y", 70)),
            ),
            solves = listOf(
                solve(tangoWeek[0], "S", 1, 100.0), solve(tangoWeek[0], "X", 2, 75.0),
                solve(tangoWeek[0], "G", 3, 50.0), solve(tangoWeek[0], "Y", 4, 25.0),
                solve(tangoWeek[1], "X", 1, 100.0), solve(tangoWeek[1], "G", 2, 2 * third),
                solve(tangoWeek[1], "Y", 3, third),
            ),
            windows = window(
                Row("X", 1, 275.0, 3), Row("G", 2, 550.0 / 3, 3), Row("S", 3, 100.0, 1), Row("Y", 4, 275.0 / 3, 3),
            ),
        ),
        Case(
            name = "9 equal totals share a place",
            boards = listOf(board(tango9, time, s("A", 60)), board(tango10, time, s("B", 60))),
            solves = listOf(solve(tango9, "A", 1, 100.0), solve(tango10, "B", 1, 100.0)),
            windows = window(Row("A", 1, 100.0, 1), Row("B", 1, 100.0, 1)),
        ),
        Case(
            name = "10 week and month follow puzzle-day labels",
            boards = soloDays.map { board(it, time, s("A", 60)) },
            solves = soloDays.map { solve(it, "A", 1, 100.0) },
            windows = listOf(
                Window(day(11, 9), day(11, 15), listOf(Row("A", 1, 200.0, 2))),
                Window(day(11, 1), day(11, 30), listOf(Row("A", 1, 400.0, 4))),
                Window(day(10, 1), day(10, 31), listOf(Row("A", 1, 100.0, 1))),
            ),
        ),
        truncatedCase(),
        exactCapCase(),
        Case(
            name = "13 missing board adds nothing",
            boards = listOf(board(tango9, time, s("P", 60))),
            solves = listOf(solve(tango9, "P", 1, 100.0)),
            windows = window(Row("P", 1, 100.0, 1)),
            viewerUid = "P",
        ),
        Case(
            name = "14 late archive play is device-only",
            boards = listOf(sudokuBoard),
            solves = listOf(
                solve(sudoku12, "Lia", 1, 100.0), solve(sudoku12, "Max", 2, 80.0), solve(sudoku12, "You", 3, 60.0),
                solve(sudoku12, "Gwen", 4, 40.0), solve(sudoku12, "Omar", 5, 20.0),
            ),
            windows = window(
                Row("Lia", 1, 100.0, 1), Row("Max", 2, 80.0, 1), Row("You", 3, 60.0, 1),
                Row("Gwen", 4, 40.0, 1), Row("Omar", 5, 20.0, 1),
            ),
            viewerUid = "Nora",
            unofficial = listOf(
                UnofficialProbe(sudoku12, 95_000, 0, place = 1),
                UnofficialProbe(sudoku12, 255_000, 0, place = 2),
                UnofficialProbe(sudoku12, 300_000, 0, place = 3),
            ),
        ),
        Case(
            name = "15 official window edge: the +2 solve stays device-only",
            boards = listOf(board(zip10, time, s("P", 50), s("Q", 60))),
            solves = listOf(solve(zip10, "P", 1, 100.0), solve(zip10, "Q", 2, 50.0)),
            windows = window(Row("P", 1, 100.0, 1), Row("Q", 2, 50.0, 1)),
            viewerUid = "R",
            unofficial = listOf(UnofficialProbe(zip10, 40_000, 0, place = 1)),
        ),
    )
}
