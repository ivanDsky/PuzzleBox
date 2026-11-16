package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Completion
import com.ivandsky.puzzlebox.game.editor.Difficulty.Level
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.GradingMode
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.queens.QueensGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Finish for me aiming for a level (91), through the editor the way the Studio calls it: finish
 * with a target, then read the level back with scored grading. Seeded and unbudgeted unless the
 * test is about the budget, so every run is the same.
 */
class QueensCompleterTargetTest {

    /** A draft cut at the minimum of started regions from the seeded [size] board, one crown on it. */
    private fun cut(size: Int): QueensDraft {
        val board = checkNotNull(QueensGenerator(Random(size * 7)).nextUnique(size))
        return QueensDraftCuts.cut(board, QueensCompleter.minimumStarted(size), crowns = 1, Random(size))
    }

    private fun finish(draft: QueensDraft, target: Level?, budget: Budget = Budget.Unlimited): QueensDraft? =
        EditorDraft(QueensEditor, DraftState(draft)).finish(Random(draft.size), budget, target = target)?.state?.draft

    private fun verdict(board: QueensDraft): Verdict.Unique =
        EditorDraft(QueensEditor, DraftState(board)).analyze(Budget.Unlimited, GradingMode.Scored) as Verdict.Unique

    @Test
    fun `an 8x8 draft finishes at each level asked for`() {
        val draft = cut(8)

        val levels = Level.entries.map { target -> finish(draft, target)?.let { verdict(it).difficulty?.level } }

        assertEquals(Level.entries, levels)
    }

    @Test
    fun `a board finished to a level keeps the paint and the crowns and needs no guessing`() {
        val draft = cut(8)
        val painted = draft.regions.indices.filter { draft.regions[it] != QueensDraft.UNPAINTED }

        for (target in Level.entries) {
            val done = checkNotNull(finish(draft, target)) { "$target" }

            assertEquals("$target", painted.map { draft.regions[it] }, painted.map { done.regions[it] })
            assertEquals("$target", draft.crowns, done.crowns)
            assertEquals("$target", emptyMap<Int, List<Set<Int>>>(), QueensContiguity.splitRegions(done))
            val verdict = verdict(done)
            assertTrue("$target: $verdict", !verdict.needsGuessing && verdict.solution.values.containsAll(done.crowns))
        }
    }

    @Test
    fun `a level out of reach finishes at the closest one`() {
        // A 4×4 never scores past Easy and a 5×5 never reaches Hard.
        val levels = listOf(4 to Level.Expert, 5 to Level.Expert).map { (size, target) -> finish(cut(size), target)?.let { verdict(it).difficulty?.level } }

        assertEquals(listOf(Level.Easy, Level.Medium), levels)
    }

    @Test
    fun `a budget spent once a first board is found returns that board`() {
        val draft = cut(8)
        var pollsToFirst = 0
        val first = finish(draft, target = null, Budget { pollsToFirst++; false })
        var polls = 0

        val done = finish(draft, Level.Expert, Budget { ++polls > pollsToFirst })

        assertEquals(checkNotNull(first), done)
    }

    @Test
    fun `a draft only a repaint finishes is repainted the same way whatever the level`() {
        val draft = REPAINTED
        val any = finish(draft, target = null)
        assertTrue("a fill finished it: the draft must be one that needs a repaint", changed(draft, checkNotNull(any)) > 0)
        assertEquals(List(Level.entries.size) { any }, Level.entries.map { finish(draft, it) })
    }

    @Test
    fun `the fully painted board of the user report is still repaired in one cell with a level asked for`() {
        val done = QueensCompleter.complete(REPORTED, Random(1), Budget.Unlimited, Level.Hard)

        assertEquals(QueensCompleter.complete(REPORTED, Random(1), Budget.Unlimited), done)
        assertEquals(1, changed(REPORTED, (done as Completion.Done).draft))
    }

    /** The painted cells of [draft] that [done] holds in another region. */
    private fun changed(draft: QueensDraft, done: QueensDraft): Int =
        draft.regions.indices.count { draft.regions[it] != QueensDraft.UNPAINTED && draft.regions[it] != done.regions[it] }

    private companion object {
        /** A hand-style 6×6 painting with several answers, 95 percent painted: no fill of the rest has one answer. */
        val REPAINTED: QueensDraft = QueensDraftCuts.painted(QueensPaintings.ambiguous(6, Random(6)), share = 0.95, crowned = false, Random(6))

        /** The board of the user report (build 1148), as in `QueensCompleterRepaintTest`: every cell painted, seven crowns, six answers. */
        val REPORTED = QueensDraft(
            size = 7,
            regions = listOf("TBBBBOO", "TRRRROO", "TTTRRDO", "TTRRDDD", "TTRYYGG", "TTRYGGG", "TTYYGGG").flatMap { row -> row.map { "TBRODYG".indexOf(it) } },
            crowns = setOf(3, 8, 20, 25, 28, 40, 44),
        )
    }
}
