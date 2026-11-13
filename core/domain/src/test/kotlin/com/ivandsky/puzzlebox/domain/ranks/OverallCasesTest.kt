package com.ivandsky.puzzlebox.domain.ranks

import com.ivandsky.puzzlebox.testing.repository.OverallCases
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs the shared Overall contract table under two JVM default zones: puzzle-day labels never shift. */
class OverallCasesTest {

    private val original = TimeZone.getDefault()

    @After
    fun restoreZone() = TimeZone.setDefault(original)

    private fun ranked(board: OverallCases.Board) = rankBoard(board.ref, board.entries, board.order, board.count)

    private fun check(case: OverallCases.Case) {
        val boards = case.boards.map(::ranked)
        for ((source, board) in case.boards.zip(boards)) {
            assertEquals("${case.name}: N of ${board.board}", source.count, board.n)
            assertEquals("${case.name}: truncated of ${board.board}", source.count > source.entries.size, board.truncated)
        }
        for (expected in case.solves) {
            val row = boards.first { it.board == expected.ref }.rows.first { it.entry.uid == expected.uid }
            assertEquals("${case.name}: rank of ${expected.uid}", expected.rank, row.rank)
            assertEquals("${case.name}: points of ${expected.uid}", expected.points, row.points, TOLERANCE)
        }
        for (window in case.windows) {
            val inWindow = boards.filter { it.board.day in window.from..window.to }
            val standings = overall(inWindow, case.viewerUid, case.viewerBeyondPage)
            val pinned = window.rows.map { it.uid }.toSet()
            val actual = standings.rows.filter { it.uid in pinned }
            assertEquals("${case.name} ${window.from}: players", window.rows.map { it.uid }, actual.map { it.uid })
            for ((expected, row) in window.rows.zip(actual)) {
                val label = "${case.name} ${window.from}: ${expected.uid}"
                assertEquals("$label place", expected.place, row.place)
                assertEquals("$label total", expected.total, row.total, TOLERANCE)
                assertEquals("$label games", expected.games, row.games)
                assertEquals("$label truncated", expected.truncated, row.truncated)
            }
            if (case.viewerUid != null && case.viewerUid !in pinned) {
                assertTrue("${case.name}: ${case.viewerUid} has no points", standings.rows.none { it.uid == case.viewerUid })
            }
        }
        for (probe in case.unofficial) {
            val board = case.boards.first { it.ref == probe.ref }
            assertEquals(
                "${case.name}: unofficial ${probe.timeMs}/${probe.hints}",
                probe.place,
                unofficialPlace(probe.timeMs, probe.hints, board.entries, board.order),
            )
        }
    }

    private fun runUnder(zone: String) {
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
        OverallCases.all.forEach(::check)
    }

    @Test
    fun `every case holds under UTC`() = runUnder("UTC")

    @Test
    fun `every case holds far east of UTC`() = runUnder("Pacific/Kiritimati")

    @Test
    fun `the table keeps the fifteen contract cases`() {
        assertEquals(15, OverallCases.all.size)
        assertFalse(OverallCases.all.any { it.windows.isEmpty() })
    }

    @Test
    fun `percentile points span 100 down to 100 over n`() {
        assertEquals(100.0, percentilePoints(1, 4), TOLERANCE)
        assertEquals(25.0, percentilePoints(4, 4), TOLERANCE)
        assertEquals(100.0 * 50 / 150, pointsBeyondPage(150), TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 1e-9
    }
}
