package com.ivandsky.puzzlebox.games.tango.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Completion
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.engine.TangoBoardGenerator
import com.ivandsky.puzzlebox.games.tango.engine.TangoCountRule
import com.ivandsky.puzzlebox.games.tango.engine.TangoHint
import com.ivandsky.puzzlebox.games.tango.engine.TangoLineRule
import com.ivandsky.puzzlebox.games.tango.engine.TangoMarkerRule
import com.ivandsky.puzzlebox.games.tango.engine.TangoRules
import com.ivandsky.puzzlebox.games.tango.engine.TangoState
import com.ivandsky.puzzlebox.games.tango.engine.TangoTripleRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * [TangoSolveWalk] against the hint ladder it restates: on real dailies and on generated boards of
 * every size it must count the same steps per rung, the same narrow steps and the same cells left
 * as a walk of `TangoRules.hint` that asks the rule objects for every move on offer.
 */
class TangoSolveWalkTest {

    @Test
    fun `every daily tallies as the hint ladder walks it`() {
        val wrong = TangoDailyBoards.all.withIndex().filter { (_, board) -> walk(board) != byHintLadder(board) }
            .map { (at, board) -> "daily $at: ${walk(board)} but the ladder gives ${byHintLadder(board)}" }

        assertEquals(emptyList<String>(), wrong.take(3))
    }

    @Test
    fun `finished boards of every size tally as the hint ladder walks them`() {
        for (n in TangoDraft.SIZES) {
            for (seed in 0 until 12) {
                val answer = TangoDraftCuts.answer(n, seed = n * 100L + seed)
                val draft = TangoDraftCuts.cut(n, answer, givens = seed % 4, markers = n * (seed % 3), Random(seed))
                val board = (TangoCompleter.complete(draft, Random(seed), Budget.Unlimited) as Completion.Done).draft

                assertEquals("n$n seed $seed", byHintLadder(board), walk(board))
                assertEquals("n$n seed $seed", 0, walk(board)?.left)
            }
        }
    }

    @Test
    fun `boards the ladder cannot finish stop with the same cells left`() {
        var stuck = 0
        for (n in TangoDraft.SIZES) {
            for (seed in TangoBoardGenerator.CORPUS_SEEDS) {
                val minimal = TangoBoardGenerator.boards(n, seed).minimal
                val board = TangoDraft(n, minimal.givens, minimal.markers)
                val tally = walk(board)

                assertEquals("n$n seed $seed", byHintLadder(board), tally)
                if (tally!!.left > 0) stuck++
            }
        }
        assertTrue("no generated board got the ladder stuck", stuck > 0)
    }

    @Test
    fun `a full board takes no steps`() {
        val answer = TangoDraftCuts.answer(6, seed = 600)
        val full = TangoDraft(6, answer.withIndex().associate { (idx, cell) -> idx to cell }, emptyList())

        assertEquals(TangoSolveTally(marker = 0, triple = 0, lineFull = 0, lineFills = 0, narrow = 0, left = 0), walk(full))
    }

    @Test
    fun `one walker tallies board after board`() {
        val boards = TangoDailyBoards.all.take(20).map { it.copy(markers = emptyList()) }
        val walker = TangoSolveWalk(6, emptyList())

        assertEquals(boards.map(::byHintLadder), boards.map { walker.tally(it.givens, Budget.Unlimited) })
    }

    @Test
    fun `a spent budget stops the walk without a tally`() {
        val board = TangoDailyBoards.all.first()
        var polls = 0

        assertNull(TangoSolveWalk(board.n, board.markers).tally(board.givens, Budget { ++polls > 3 }))
        assertEquals(4, polls)
    }

    private fun walk(board: TangoDraft): TangoSolveTally? = TangoSolveWalk(board.n, board.markers).tally(board.givens, Budget.Unlimited)

    /**
     * The reference: `TangoRules.hint` placed move by move, as [TangoGrader] walks. A step is narrow
     * when the rules of its kind (marker, three-in-a-row and line count together, else line fills)
     * force one cell only and it is not the last empty cell.
     */
    private fun byHintLadder(board: TangoDraft): TangoSolveTally {
        val puzzle = TangoPuzzle(TangoDraft.variantOf(board.n), board.n, board.givens, board.markers)
        val grid = MutableList<TangoCell?>(puzzle.cellCount) { puzzle.givens[it] }
        val steps = IntArray(4)
        var narrow = 0
        var left = grid.count { it == null }
        while (left > 0) {
            val hint = TangoRules.hint(TangoState(puzzle, grid.toList())) as? TangoHint.Forced ?: break
            val simple = TangoMarkerRule.forced(puzzle, grid) + TangoTripleRule.forced(puzzle, grid) + TangoCountRule.forced(puzzle, grid)
            val onOffer = simple.ifEmpty { TangoLineRule.forced(puzzle, grid) }.map { it.idx }.toSet()
            if (onOffer.size == 1 && left > 1) narrow++
            steps[
                when (hint.why) {
                    is TangoHint.Why.MarkerBinding -> 0
                    is TangoHint.Why.ThreeInARow -> 1
                    is TangoHint.Why.LineFull -> 2
                    is TangoHint.Why.LineFills -> 3
                },
            ]++
            grid[hint.idx] = hint.value
            left--
        }
        return TangoSolveTally(steps[0], steps[1], steps[2], steps[3], narrow, left)
    }
}
