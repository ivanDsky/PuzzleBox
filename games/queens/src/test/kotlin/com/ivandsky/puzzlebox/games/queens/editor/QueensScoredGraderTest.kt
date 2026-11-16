package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.game.editor.Difficulty.Level
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.GradingMode
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.SharedNeighboursRung
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Queens' scored difficulty (91): the score of a board's whole solve, read through the editor
 * the way the Studio reads it. The boards are the 5×5 label fixtures, the daily corpus and fills
 * frozen as digits; nothing is random.
 */
class QueensScoredGraderTest {

    private fun score(board: QueensDraft): Int? = QueensScoredGrader.grade(board, NO_ANSWER, Budget.Unlimited)?.score

    private fun scoreOf(size: Int, regions: String): Int = checkNotNull(score(QueensLevelBoards.board(size, regions))) { "no score for $regions" }

    @Test
    fun `the editor labels a board by its score under scored grading and by its hardest rung otherwise`() {
        val draft = EditorDraft(QueensEditor, DraftState(QueensLevelBoards.fixture("hard")))

        val scored = (draft.analyze(Budget.Unlimited, GradingMode.Scored) as Verdict.Unique).difficulty
        val legacy = (draft.analyze(Budget.Unlimited) as Verdict.Unique).difficulty

        assertEquals(Difficulty(Level.Easy, score = 100, hardestRung = SharedNeighboursRung), scored)
        assertEquals(Level.Hard, legacy?.level)
        assertNull(legacy?.score)
    }

    @Test
    fun `a score is the side times the weighted steps`() {
        // Easy: 5 queens, 4 of them the only obvious move. Medium adds 2 line locks. Hard and
        // Expert (their legacy labels) take 4 and 6 Medium steps and no harder one.
        val scores = listOf("easy", "medium", "hard", "expert").map { score(QueensLevelBoards.fixture(it)) }

        assertEquals(listOf(5 * (5 + 4), 5 * (5 + 3 + 2 * 3), 5 * (5 + 3 + 4 * 3), 5 * (5 + 2 + 6 * 3)), scores)
    }

    @Test
    fun `more steps of the same kind score higher`() {
        val oneLineLock = scoreOf(6, "255555255330225311211111241111441111")
        val threeMediumSteps = scoreOf(6, "445522445332335322335300333333113333")

        assertTrue("$oneLineLock < $threeMediumSteps", oneLineLock < threeMediumSteps)
    }

    @Test
    fun `a harder step in place of an easier one scores higher`() {
        val oneLineLock = scoreOf(6, "255555255330225311211111241111441111")
        val onePigeonhole = scoreOf(6, "133322133322113022511444555444555444")

        assertTrue("$oneLineLock < $onePigeonhole", oneLineLock < onePigeonhole)
    }

    @Test
    fun `more steps with a single obvious move score higher`() {
        val threeOfSix = scoreOf(6, "255555255550255511244311244111241111")
        val fiveOfSix = scoreOf(6, "255555255550245551245351445111445111")

        assertEquals(6 * (6 + 3) to 6 * (6 + 5), threeOfSix to fiveOfSix)
    }

    @Test
    fun `a bigger board with the same kind of solve scores higher`() {
        val five = scoreOf(5, "0000100021340223222232222")
        val seven = scoreOf(7, "0000000100000022330002233000444550044455004445506")

        assertEquals(5 * (5 + 4) to 7 * (7 + 4), five to seven)
    }

    @Test
    fun `a board the steps cannot finish has no level`() {
        // Regions mirror the columns, so two placements fit and no step can choose between them.
        assertNull(QueensScoredGrader.grade(QueensDraft(4, List(16) { it % 4 }), NO_ANSWER, Budget.Unlimited))
    }

    @Test
    fun `a spent budget gives no level`() {
        assertNull(QueensScoredGrader.grade(QueensLevelBoards.fixture("easy"), NO_ANSWER, Budget { true }))
    }

    @Test
    fun `the daily boards spread over all four levels, most of them in the middle two`() {
        val levels = QueensLevelBoards.dailies().map { checkNotNull(QueensScoredGrader.grade(it, Budget.Unlimited)).level }

        val percent = Level.entries.map { level -> levels.count { it == level } * 100 / levels.size }
        // Measured (QueensScoredMeasurement): 23, 41, 27 and 9 percent.
        assertEquals("$percent", listOf(true, true, true, true), percent.zip(listOf(15..30, 35..50, 20..35, 5..15)) { share, range -> share in range })
    }

    private companion object {
        /** The grader walks the board itself; it never reads the answer it is handed. */
        val NO_ANSWER = Solution(emptyList())
    }
}
