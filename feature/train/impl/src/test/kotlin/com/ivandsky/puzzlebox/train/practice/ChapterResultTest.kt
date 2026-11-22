package com.ivandsky.puzzlebox.train.practice

import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress
import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress.Mark
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.gameui.trainer.PracticeStatus
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import com.ivandsky.puzzlebox.train.practice.PracticeRig.Companion.LINE_LOCK
import com.ivandsky.puzzlebox.train.practice.PracticeRig.Companion.NAKED_SINGLE
import com.ivandsky.puzzlebox.train.practice.PracticeRig.Companion.QUEENS
import com.ivandsky.puzzlebox.train.practice.PracticeRig.Companion.puzzle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** What a chapter's result page says: the tally of the run's verdicts and statuses, and the mastery against the mark the run began with. */
class ChapterResultTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val device = listOf(puzzle(1), puzzle(2))

    private fun rig(
        catalog: List<String> = listOf("naked-single", "line-lock"),
        seed: PracticeRig.() -> Unit = {},
    ) = PracticeRig(emptyList(), device = device, catalog = catalog, opens = LINE_LOCK, attempt = null).apply {
        seed()
        vm.start(1)
    }

    private fun settle() = main.testDispatcher.scheduler.apply {
        advanceTimeBy(0)
        runCurrent()
    }

    private fun PracticeRig.turn(vararg verdicts: TrainerVerdict) {
        verdicts.forEach { board.turn(it) }
        settle()
    }

    private fun PracticeRig.say(status: PracticeStatus) {
        board.say(status)
        settle()
    }

    private fun PracticeRig.end() {
        say(PracticeStatus.Done(null))
        vm.skip()
        settle()
    }

    private val right = TrainerVerdict.Right

    private fun PracticeRig.result(): ChapterResult = (state as PracticeState.Result).result

    private fun PracticeRig.master(id: TechniqueId) = runBlocking { progress.update(QUEENS, id) { TechniqueProgress(Mark.Mastered, 5, 1L) } }

    /** Bug caught: counts that miss a verdict, mix up Right with Show me, or a row that names the wrong kind of board. */
    @Test
    fun `the result tallies the run's verdicts and statuses board by board`() {
        val rig = rig()
        rig.turn(right, right, right)
        rig.end()
        rig.say(PracticeStatus.Easier("Naked single", standInSpotlight()))
        rig.turn(right)
        rig.end()
        rig.turn(right, TrainerVerdict.ShowMe)
        rig.say(PracticeStatus.Shown(standInSpotlight()))
        rig.end()
        rig.turn(right, right, TrainerVerdict.Wrong, right)
        rig.end()
        rig.turn(right)
        rig.end()

        val result = rig.result()
        assertEquals(5, result.boards)
        assertEquals(5, result.boardsDone)
        assertEquals(9, result.rights)
        assertEquals(1, result.shown)
        assertEquals(1, result.slips)
        assertEquals(0, result.undone)
        assertEquals(5, result.best)
        assertEquals(
            listOf(
                BoardRow(1, BoardRowKind.Clean, 3),
                BoardRow(2, BoardRowKind.Slip, 1),
                BoardRow(3, BoardRowKind.Shown, 1),
                BoardRow(4, BoardRowKind.Clean, 3),
                BoardRow(5, BoardRowKind.Clean, 1),
            ),
            result.rows,
        )
    }

    /** Bug caught: moves undone as Wrong never tallied, so a board full of mistakes reads "All right". */
    @Test
    fun `moves undone are counted per board and make the board's row an undone row`() {
        val rig = rig()
        rig.say(PracticeStatus.Wrong("Two queens would touch.", attempt = 1))
        rig.say(PracticeStatus.Wrong("Two queens would touch.", attempt = 2))
        rig.say(PracticeStatus.Wrong("Not in the solution.", attempt = 3))
        rig.end()
        rig.turn(right)
        rig.end()
        repeat(3) { rig.turn(right); rig.end() }

        val result = rig.result()
        assertEquals(3, result.undone)
        assertEquals(BoardRow(1, BoardRowKind.Undone, 0, undone = 3), result.rows[0])
        assertEquals(BoardRow(2, BoardRowKind.Clean, 1), result.rows[1])
    }

    /** Bug caught: a status that repeats being dropped as equal, so a second identical undo is not counted. */
    @Test
    fun `an undone move repeated for the same reason is counted again`() {
        val rig = rig()
        rig.say(PracticeStatus.Wrong("Two queens would touch.", attempt = 1))
        rig.say(PracticeStatus.FindNextMove)
        rig.say(PracticeStatus.Wrong("Two queens would touch.", attempt = 2))
        rig.end()
        repeat(4) { rig.turn(right); rig.end() }

        assertEquals(2, rig.result().rows[0].undone)
    }

    /** Bug caught: an undone board showing the milder slip row, or hiding a Show me. */
    @Test
    fun `a board with undone moves and a slip is an undone board, and Show me still wins`() {
        val rig = rig()
        rig.say(PracticeStatus.Easier("Naked single", standInSpotlight()))
        rig.say(PracticeStatus.Wrong("x", attempt = 1))
        rig.end()
        rig.say(PracticeStatus.Wrong("x", attempt = 2))
        rig.say(PracticeStatus.Shown(standInSpotlight()))
        rig.end()
        repeat(3) { rig.turn(right); rig.end() }

        val rows = rig.result().rows
        assertEquals(BoardRowKind.Undone, rows[0].kind)
        assertEquals(BoardRowKind.Shown, rows[1].kind)
        assertEquals(1, rows[1].undone)
    }

    /** Bug caught: a board that went wrong in two ways showing the milder row, or a streak capped at the mastery count. */
    @Test
    fun `a board with a slip and a Show me is a Show me board, and the best run is not capped at five`() {
        val rig = rig()
        rig.say(PracticeStatus.Easier("Naked single", standInSpotlight()))
        rig.say(PracticeStatus.Shown(standInSpotlight()))
        rig.end()
        repeat(4) { rig.turn(right, right, right); rig.end() }

        val result = rig.result()
        assertEquals(BoardRowKind.Shown, result.rows.first().kind)
        assertEquals(12, result.best)
        assertEquals(12, result.rights)
    }

    /** Bug caught: a result built before the last verdict is recorded, so the last Right is missing from the page and the mastery. */
    @Test
    fun `the last board's verdict is in the result and the technique is newly mastered by it`() {
        val rig = rig()
        repeat(4) { rig.turn(right); rig.end() }
        rig.progress.stored.let { assertEquals(4, it[QUEENS]?.get(LINE_LOCK)?.inRow) }
        rig.turn(right)
        rig.end()

        val result = rig.result()
        assertEquals(ChapterOutcome.NewlyMastered, result.outcome)
        assertEquals(5, result.rights)
        assertTrue(result.mastered)
    }

    /** Bug caught: a technique that was mastered before reading as newly mastered, so the page congratulates again. */
    @Test
    fun `a technique that was mastered before the chapter is still mastered`() {
        val rig = rig(seed = { master(LINE_LOCK) })
        repeat(5) { rig.turn(right); rig.end() }

        assertEquals(ChapterOutcome.StillMastered, rig.result().outcome)
    }

    /** Bug caught: a chapter that ends below five in a row reading as mastered. */
    @Test
    fun `a chapter with a wrong move in the middle is not mastered`() {
        val rig = rig()
        rig.turn(right); rig.end()
        rig.turn(right); rig.end()
        rig.turn(TrainerVerdict.Wrong); rig.end()
        rig.turn(right); rig.end()
        rig.turn(right); rig.end()

        val result = rig.result()
        assertEquals(ChapterOutcome.NotMastered, result.outcome)
        assertFalse(result.mastered)
        assertEquals(2, result.best)
    }

    /** Bug caught: a next technique where the catalog has none, or all-mastered claimed while an earlier technique is open. */
    @Test
    fun `the last technique has no next and says all mastered only when every one is`() {
        val some = rig()
        repeat(5) { some.turn(right); some.end() }
        assertNull(some.result().next)
        assertEquals(2, some.result().techniques)
        assertFalse(some.result().allMastered)

        val all = rig(seed = { master(NAKED_SINGLE) })
        repeat(5) { all.turn(right); all.end() }
        assertTrue(all.result().allMastered)
    }

    /** Bug caught: Practice again carrying the last run's counts or its mastery into the new one. */
    @Test
    fun `practice again tallies a new run against the mastery it left`() {
        val rig = rig()
        repeat(5) { rig.turn(right); rig.end() }
        assertEquals(ChapterOutcome.NewlyMastered, rig.result().outcome)

        rig.vm.practiceAgain()
        settle()
        assertEquals(1, rig.vm.chapter.value.board)
        repeat(5) { rig.turn(right, right); rig.end() }

        val again = rig.result()
        assertEquals(ChapterOutcome.StillMastered, again.outcome)
        assertEquals(10, again.rights)
        assertEquals(5, again.boardsDone)
    }
}
