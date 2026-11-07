package com.ivandsky.puzzlebox.games.zip.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.Wall
import com.ivandsky.puzzlebox.games.zip.WallDirection
import com.ivandsky.puzzlebox.games.zip.ZipTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Draft verdicts on hand-checkable boards: the forced n=5 corridor board (one path) and an
 * open 3×3 corner to corner, which has exactly two Hamiltonian paths — rows-snake and columns-snake.
 */
class ZipDraftAnalyzerTest {

    private val corridorWalls: Set<Wall> =
        ZipTestSupport.n5WallCells.map { Wall(Cell(it), WallDirection.DOWN) }.toSet()
    private val corridorPath: List<Int> = ZipTestSupport.n5Solution.map { it.idx }

    private fun analyze(draft: ZipDraft): Verdict = ZipDraftAnalyzer.analyze(draft, Budget.Unlimited)

    private fun open3(waypoints: List<Int>, path: List<Int> = emptyList()) =
        ZipDraft(3, emptySet(), path, waypoints)

    @Test
    fun `a new draft is Incomplete on its waypoint count`() {
        for (variant in ZipEditor.variants) {
            val verdict = analyze(ZipEditor.newDraft(variant))

            assertEquals(Verdict.Incomplete(IncompleteReason(ZipDraftAnalyzer.WAYPOINTS, 0)), verdict)
        }
    }

    @Test
    fun `a single waypoint without a path is Incomplete on that count`() {
        val verdict = analyze(open3(waypoints = listOf(4)))

        assertEquals(Verdict.Incomplete(IncompleteReason(ZipDraftAnalyzer.WAYPOINTS, 1)), verdict)
    }

    @Test
    fun `a partial path is Incomplete on the cells still off it`() {
        val verdict = analyze(ZipDraft(5, corridorWalls, corridorPath.take(10), listOf(0, 9)))

        assertEquals(Verdict.Incomplete(IncompleteReason(ZipDraftAnalyzer.UNCOVERED, 15)), verdict)
    }

    @Test
    fun `a full path on a forced board is Unique with that path in order`() {
        val verdict = analyze(ZipDraft(5, corridorWalls, corridorPath, listOf(0, 12, 24)))

        assertEquals(Solution(corridorPath), (verdict as Verdict.Unique).solution)
    }

    @Test
    fun `a board with waypoints and no path is solved from the waypoints alone`() {
        val verdict = analyze(ZipDraft(5, corridorWalls, emptyList(), listOf(0, 12, 24)))

        assertEquals(Solution(corridorPath), (verdict as Verdict.Unique).solution)
    }

    @Test
    fun `two paths are Multiple with both and the cells whose position differs`() {
        val verdict = analyze(open3(listOf(0, 8)))

        val multiple = verdict as Verdict.Multiple
        assertEquals(
            setOf(Solution(listOf(0, 1, 2, 5, 4, 3, 6, 7, 8)), Solution(listOf(0, 3, 6, 7, 4, 1, 2, 5, 8))),
            setOf(multiple.solutionA, multiple.solutionB),
        )
        assertEquals(setOf(1, 2, 3, 5, 6, 7), multiple.differingCells)
    }

    @Test
    fun `a board no path covers is None with no conflicts yet`() {
        val verdict = analyze(open3(listOf(0, 1)))

        assertEquals(Verdict.None(emptySet()), verdict)
    }

    @Test
    fun `a spent budget on a solvable board is Undetermined`() {
        val verdict = ZipDraftAnalyzer.analyze(open3(listOf(0, 8)), Budget { true })

        assertEquals(Verdict.Undetermined, verdict)
    }

    @Test
    fun `reasonIds lists every reason the analyzer emits`() {
        assertTrue(ZipDraftAnalyzer.reasonIds == setOf(ZipDraftAnalyzer.UNCOVERED, ZipDraftAnalyzer.WAYPOINTS))
    }
}
