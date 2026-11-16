package com.ivandsky.puzzlebox.games.zip.editor

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.GradingMode
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.Wall
import com.ivandsky.puzzlebox.games.zip.WallDirection
import com.ivandsky.puzzlebox.games.zip.codec.ZipWireParser
import com.ivandsky.puzzlebox.games.zip.feed.TrailGameExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

/**
 * Zip's scored difficulty (ticket 94), read through the editor seam the Studio uses. The boards
 * are hand-built from one a person can check: the path runs along each row and turns down at its
 * end, and a wall under every other cell leaves it no other way. Opening a wall or taking a
 * number off gives the solver a wrong move to rule out. The score is ten times the points of
 * all wrong moves, per board side: so on 5×5, two for each point.
 */
class ZipScoredGraderTest {

    @Test
    fun `a board where every step has one move scores nothing and is Easy`() {
        assertEquals(Difficulty(Difficulty.Level.Easy, score = 0, hardestRung = null), scored(corridor(5)))
    }

    @Test
    fun `a wrong move that shuts a cell in at once is one point`() {
        // Without the wall under cell 0 the path may turn down there, and cell 1 is then a dead end.
        assertEquals(2, score(corridor(5, open = setOf(0))))
        assertEquals(2, score(corridor(5, open = setOf(1))))
    }

    @Test
    fun `a wrong move that takes a look ahead is two points`() {
        // Down from 0 no cell is shut in yet: it takes a step on to see the top rows cut off. Down from 1 shuts cell 2 in at once.
        assertEquals(6, score(corridor(5, open = setOf(0, 1))))
    }

    @Test
    fun `the more a wrong move takes to rule out the higher the score`() {
        val scores = listOf(setOf(0, 1), setOf(0, 1, 2), setOf(0, 1, 2, 3)).map { score(corridor(5, open = it))!! }

        assertEquals(scores.toString(), scores.sorted().distinct(), scores)
    }

    @Test
    fun `numbers that rule a wrong move out take its points off`() {
        // With 2 on cell 4 and 3 on cell 5 the path may not turn down from cell 0 onto the 3.
        val numbered = corridor(5, open = setOf(0, 1)).copy(waypoints = listOf(0, 4, 5, 24))

        assertEquals(2, score(numbered))
    }

    @Test
    fun `the same wrong moves score less on a larger board`() {
        // One point on 6×6 is ten sixths, rounded down.
        assertEquals(1, score(corridor(6, open = setOf(0))))
    }

    @Test
    fun `a board with no path drawn is scored on its answer`() {
        val drawn = corridor(5, open = setOf(0, 1))

        assertEquals(6, score(drawn.copy(path = emptyList())))
    }

    @Test
    fun `the captured dailies are Medium`() {
        val levels = listOf("zip-daily-592.json", "02-byid-gt6-1.json").map { scored(daily(it))?.level }

        assertEquals(listOf(Difficulty.Level.Medium, Difficulty.Level.Medium), levels)
    }

    @Test
    fun `the legacy check of a Zip board shows no level`() {
        val legacy = EditorDraft(ZipEditor, DraftState(corridor(5))).analyze(Budget.Unlimited) as Verdict.Unique

        assertNull(legacy.difficulty)
    }

    @Test
    fun `a spent budget leaves the board without a level`() {
        // Spent but not cancelled: the analyzer, which counts nodes, still settles; the grader does not start.
        val spent = object : Budget {
            override fun exhausted() = true
            override fun cancelled() = false
        }
        val board = EditorDraft(ZipEditor, DraftState(corridor(5, open = setOf(0))))

        assertEquals(2, (board.analyze(Budget.Unlimited, GradingMode.Scored) as Verdict.Unique).difficulty?.score)
        assertNull((board.analyze(spent, GradingMode.Scored) as Verdict.Unique).difficulty)
    }

    @Test
    fun `a budget spent part way through a long look ahead leaves the board without a level`() {
        var polls = 0
        val partWay = object : Budget {
            override fun exhausted() = ++polls > 1
            override fun cancelled() = false
        }
        val stroke = ZipDraft(7, emptySet(), emptyList(), emptyList()).let { it.stroke(listOf(0, 1)).apply(it) }
        val open = EditorDraft(ZipEditor, DraftState(stroke)).finish(Random(1), Budget.Unlimited, target = Difficulty.Level.Expert)!!

        assertEquals(Difficulty.Level.Expert, (open.analyze(Budget.Unlimited, GradingMode.Scored) as Verdict.Unique).difficulty?.level)
        assertNull((open.analyze(partWay, GradingMode.Scored) as Verdict.Unique).difficulty)
        assertEquals(2, polls)
    }

    private fun score(draft: ZipDraft): Int? = scored(draft)?.score

    private fun scored(draft: ZipDraft): Difficulty? =
        (EditorDraft(ZipEditor, DraftState(draft)).analyze(Budget.Unlimited, GradingMode.Scored) as Verdict.Unique).difficulty

    /** A captured LinkedIn board as the Studio would hold it: its walls and numbers, no path drawn. */
    private fun daily(fixture: String): ZipDraft {
        val body = ZipScoredGraderTest::class.java.getResourceAsStream("/fixtures/$fixture")!!.readBytes().decodeToString()
        val json = TrailGameExtractor.extract(body)?.trailGamePuzzleJson ?: body
        val board = (ZipWireParser.parse(json, LocalDate.of(2026, 1, 1)) as DecodeResult.Ok).value.puzzle.board
        return ZipDraft(board.n, board.walls, emptyList(), board.waypoints.map { it.idx })
    }

    /**
     * An [n]×[n] board whose path runs along row 0, turns down at its end, runs back along row 1
     * and so on, numbered at both ends. A wall sits under every cell the path does not turn down
     * from, except the cells in [open].
     */
    private fun corridor(n: Int, open: Set<Int> = emptySet()): ZipDraft {
        val path = (0 until n).flatMap { row -> (0 until n).map { row * n + it }.let { if (row % 2 == 0) it else it.reversed() } }
        val turns = path.zipWithNext().filter { (from, to) -> to == from + n }.map { it.first }
        val walls = (0 until n * (n - 1)).filter { it !in turns && it !in open }.map { Wall(Cell(it), WallDirection.DOWN) }
        return ZipDraft(n, walls.toSet(), path, listOf(path.first(), path.last()))
    }
}
