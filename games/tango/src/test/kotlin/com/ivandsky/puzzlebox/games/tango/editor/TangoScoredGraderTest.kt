package com.ivandsky.puzzlebox.games.tango.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Completion
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.game.editor.Difficulty.Level
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.GradingMode
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarker
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Tango's scored difficulty (93): the score weighs every step of the solve, so a board with more
 * steps, harder steps or more steps with one move on offer scores higher; the level is read from
 * the size's bands. The hand-built pairs are cut from one 4×4 answer:
 * ```
 * S S M M
 * M M S S
 * S M S M
 * M S M S
 * ```
 */
class TangoScoredGraderTest {

    @Test
    fun `a cell a marker gives scores under one three-in-a-row gives, and that under a line count`() {
        val byMarker = score(board(".SMM", "MMSS", "SMSM", "MSMS", marker = same(0)))
        val byTriple = score(board("SSMM", "MM.S", "SMSM", "MSMS"))
        val byCount = score(board("SSMM", "MMSS", "SMSM", "MSM."))

        assertTrue("marker $byMarker, three-in-a-row $byTriple", byMarker < byTriple)
        assertTrue("three-in-a-row $byTriple, line count $byCount", byTriple < byCount)
    }

    @Test
    fun `a board with one more cell to work out scores higher`() {
        val one = score(board("SSMM", "MMSS", "SMSM", "MSM."))
        val two = score(board("SSMM", "MMSS", "SMS.", "MSM."))

        assertTrue("one cell $one, two cells $two", one < two)
    }

    @Test
    fun `a cell only a line's fills give weighs more than a line count, and more on a longer line`() {
        val byCount = TangoScoredGrader.score(6, steps(lineFull = 1))
        val byFills = TangoScoredGrader.score(6, steps(lineFills = 1))

        assertTrue("line count $byCount, line fills $byFills", byCount < byFills)
        assertTrue(byFills < TangoScoredGrader.score(8, steps(lineFills = 1)))
    }

    @Test
    fun `a step with one move on offer adds to the score`() {
        val wide = TangoScoredGrader.score(6, steps(triple = 4, narrow = 0))
        val narrow = TangoScoredGrader.score(6, steps(triple = 4, narrow = 1))

        assertTrue("wide $wide, narrow $narrow", wide < narrow)
    }

    @Test
    fun `the same steps are an easier board on a bigger board`() {
        val steps = steps(triple = 20, lineFull = 12, lineFills = 3, narrow = 4)

        val small = TangoScoredGrader.difficulty(6, steps)!!
        val big = TangoScoredGrader.difficulty(10, steps)!!

        assertTrue("6×6 $small, 10×10 $big", small.level > big.level)
    }

    /**
     * The user's anchor: a board the legacy grader called Hard took about a minute, an Easy to
     * Medium time. The plain Finish makes such boards, Hard to the legacy grader for one line count.
     */
    @Test
    fun `boards the plain Finish makes are Hard to the legacy grader and Medium at most by score`() {
        val boards = List(20) { sample ->
            val draft = TangoDraftCuts.cut(6, TangoDraftCuts.answer(6, seed = 600L + sample), givens = 2, markers = 0, Random(sample))
            (TangoCompleter.complete(draft, Random(sample), Budget.Unlimited) as Completion.Done).draft
        }

        val legacy = boards.map { (TangoDraftAnalyzer.analyze(it, Budget.Unlimited) as Verdict.Unique).difficulty?.level }
        val scored = boards.map { grade(it)!!.level }

        assertEquals(List(20) { Level.Hard }, legacy)
        assertTrue("scored $scored", scored.all { it <= Level.Medium })
    }

    @Test
    fun `the real dailies spread over every level with Medium the most common`() {
        val levels = TangoDailyBoards.all.groupingBy { grade(it)!!.level }.eachCount()

        assertEquals(Level.entries.toSet(), levels.keys)
        assertTrue("$levels", levels.values.all { it >= TangoDailyBoards.all.size / 10 })
        assertEquals("$levels", Level.Medium, levels.maxBy { it.value }.key)
    }

    @Test
    fun `a full board has nothing to grade`() {
        assertNull(grade(board("SSMM", "MMSS", "SMSM", "MSMS")))
    }

    @Test
    fun `a graded board carries its score, the level of its size's bands and the hardest rung`() {
        val board = board("SSMM", "MMSS", "SMS.", "MSM.")

        val difficulty = grade(board)!!

        assertEquals(TangoScoreBands.of(4).levelOf(difficulty.score!!), difficulty.level)
        assertEquals(TangoRung.LINE_FULL, difficulty.hardestRung)
    }

    @Test
    fun `a spent budget gives no label`() {
        val board = TangoDailyBoards.all.first()

        assertNull(TangoScoredGrader.grade(board, solution(board), Budget { true }))
    }

    @Test
    fun `the editor grades by score when asked and by the hardest rung otherwise`() {
        val board = board("SSMM", "MMSS", "SMS.", "MSM.")
        val draft = EditorDraft(TangoEditor, DraftState(board))

        val scored = (draft.analyze(Budget.Unlimited, GradingMode.Scored) as Verdict.Unique).difficulty
        val legacy = (draft.analyze(Budget.Unlimited) as Verdict.Unique).difficulty

        assertEquals(grade(board), scored)
        assertEquals(Difficulty(TangoRung.LINE_FULL), legacy)
    }

    private fun steps(marker: Int = 0, triple: Int = 0, lineFull: Int = 0, lineFills: Int = 0, narrow: Int = 0) =
        TangoSolveTally(marker, triple, lineFull, lineFills, narrow, left = 0)

    private fun score(board: TangoDraft): Int = checkNotNull(grade(board)?.score) { "no score for $board" }

    private fun grade(board: TangoDraft): Difficulty? = TangoScoredGrader.grade(board, solution(board), Budget.Unlimited)

    private fun solution(board: TangoDraft) = (TangoDraftAnalyzer.analyze(board, Budget.Unlimited) as Verdict.Unique).solution

    private fun same(cell: Int) = TangoMarker(cell, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME)

    /** A draft from its rows (`S` sun, `M` moon, `.` empty) and its markers. */
    private fun board(vararg rows: String, marker: TangoMarker? = null): TangoDraft = TangoDraft(
        rows.size,
        rows.joinToString("").withIndex().filter { it.value != '.' }
            .associate { (idx, cell) -> idx to if (cell == 'S') TangoCell.SUN else TangoCell.MOON },
        listOfNotNull(marker),
    )
}
