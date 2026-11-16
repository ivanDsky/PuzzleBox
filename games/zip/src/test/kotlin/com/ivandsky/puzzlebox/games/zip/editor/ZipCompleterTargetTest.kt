package com.ivandsky.puzzlebox.games.zip.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Completion
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.game.editor.Difficulty.Level
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.FinishReadiness
import com.ivandsky.puzzlebox.game.editor.GradingMode
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.Wall
import com.ivandsky.puzzlebox.games.zip.WallDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Finish for me aimed at a level (ticket 94), through the editor seam the Studio uses. The
 * drafts are what an author starts from: a two-cell stroke on a blank board. Seeded and with no
 * clock, so a run is the same on any machine; the hit rates under the Studio's real budget are
 * `ZipScoreMeasurement`'s.
 */
class ZipCompleterTargetTest {

    @Test
    fun `with no target the board is the plain run's for the same seed`() {
        for ((seed, draft) in drafts(8).withIndex()) {
            val plain = Random(seed).let { random ->
                val path = checkNotNull(ZipPathExtension(draft, Budget.Unlimited).extend(random))
                ZipPinnedRepair(draft, path, random, Budget.Unlimited).unique()
            }

            assertEquals(Completion.Done(checkNotNull(plain)), ZipCompleter.complete(draft, Random(seed), Budget.Unlimited, target = null))
        }
    }

    @Test
    fun `at least eight of ten drafts finish at each level on 6x6 and 7x7`() {
        val misses = listOf(6, 7).flatMap { size ->
            Level.entries.mapNotNull { target ->
                val hits = finished(size, target).count { levelOf(it) == target }
                "$size×$size $target: $hits of $DRAFTS".takeIf { hits < 8 }
            }
        }

        assertEquals(emptyList<String>(), misses)
    }

    @Test
    fun `a board finished at a level keeps the author's stroke and is unique on its path`() {
        for (size in listOf(6, 7)) for (target in Level.entries) {
            for ((draft, done) in drafts(size).zip(finished(size, target))) {
                assertEquals(draft.path, done.path.take(draft.path.size))
                assertEquals(Verdict.Unique(Solution(done.path)), ZipDraftAnalyzer.analyze(done, Budget.Unlimited))
            }
        }
    }

    @Test
    fun `a harder level leaves fewer numbers and walls on the board`() {
        for (size in listOf(6, 7)) {
            val numbers = Level.entries.map { target -> finished(size, target).sumOf { it.waypoints.size } }
            val pieces = Level.entries.map { target -> finished(size, target).sumOf { it.waypoints.size + it.walls.size } }

            assertEquals("$size×$size numbers and walls $pieces", pieces.sortedDescending().distinct(), pieces)
            assertTrue("$size×$size numbers $numbers", numbers.last() < numbers.first() && numbers[2] < numbers[1])
        }
    }

    @Test
    fun `a level out of reach gives the closest board, not a give-up`() {
        // A 5×5 board has too few wrong moves for Expert. Three drafts: a miss runs every try.
        val reached = drafts(5).take(3).mapIndexed { seed, draft ->
            (ZipCompleter.complete(draft, Random(seed), Budget.Unlimited, Level.Expert) as? Completion.Done)?.let { levelOf(it.draft) }
        }

        assertEquals(listOf(Level.Hard, Level.Hard, Level.Hard), reached)
    }

    @Test
    fun `a board the author finished comes back as it is at any level`() {
        // Every wall and number is the author's, so there is nothing to take off or to add.
        val path = (0 until 5).flatMap { row -> (0 until 5).map { row * 5 + it }.let { if (row % 2 == 0) it else it.reversed() } }
        val walls = (0 until 20).filter { it !in setOf(4, 5, 14, 15) }.map { Wall(Cell(it), WallDirection.DOWN) }.toSet()
        val whole = ZipDraft(5, walls, path, listOf(0, 24))

        for (target in Level.entries) {
            assertEquals(Completion.Done(whole), ZipCompleter.complete(whole, Random(1), Budget.Unlimited, target))
        }
    }

    @Test
    fun `the same seed and level finish to the same board`() {
        val draft = drafts(7).first()

        assertEquals(
            ZipCompleter.complete(draft, Random(7), Budget.Unlimited, Level.Hard),
            ZipCompleter.complete(draft, Random(7), Budget.Unlimited, Level.Hard),
        )
    }

    @Test
    fun `a spent budget gives up at any level`() {
        val draft = drafts(6).first()

        for (target in Level.entries) {
            assertEquals(Completion.GaveUp, ZipCompleter.complete(draft, Random(1), Budget { true }, target))
        }
    }

    @Test
    fun `a run stops soon after its budget is spent part way`() {
        val draft = drafts(7).first()
        var plain = 0
        ZipCompleter.complete(draft, Random(1), { plain++ < 0 }, target = null)
        // Enough for the first board and a little of the work on it: far too few for an Expert one.
        val spentAfter = plain + 20
        var polls = 0
        val budget = Budget { ++polls > spentAfter }

        val completion = ZipCompleter.complete(draft, Random(1), budget, Level.Expert)

        assertTrue("$completion", completion is Completion.Done)
        assertTrue("$polls polls, spent after $spentAfter", polls in spentAfter + 1..spentAfter + 4)
    }

    private fun levelOf(board: ZipDraft): Difficulty.Level? =
        (EditorDraft(ZipEditor, DraftState(board)).analyze(Budget.Unlimited, GradingMode.Scored) as Verdict.Unique).difficulty?.level

    private companion object {
        const val DRAFTS = 10

        private val runs = HashMap<Pair<Int, Level>, List<ZipDraft>>()

        /** Ten two-cell strokes Finish is offered on, the same ten for a size. */
        fun drafts(size: Int): List<ZipDraft> {
            val random = Random(size)
            return generateSequence { ZipDraftCuts.walked(size, ZipCompleter.MIN_PATH, random) }
                .filter { ZipCompleter.readiness(it) == FinishReadiness.Ready }
                .take(DRAFTS).toList()
        }

        /** The ten drafts of [size] finished at [target] through the seam, draft k with seed k. Every one finishes. */
        fun finished(size: Int, target: Level): List<ZipDraft> = runs.getOrPut(size to target) {
            drafts(size).mapIndexed { seed, draft ->
                checkNotNull(EditorDraft(ZipEditor, DraftState(draft)).finish(Random(seed), Budget.Unlimited, target = target)).state.draft
            }
        }
    }
}
