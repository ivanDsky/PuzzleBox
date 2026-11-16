package com.ivandsky.puzzlebox.games.zip.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Completion
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.FinishReadiness
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.Wall
import com.ivandsky.puzzlebox.games.zip.WallDirection
import com.ivandsky.puzzlebox.games.zip.ZipGenerator
import com.ivandsky.puzzlebox.games.zip.ZipTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Finish for me on Zip (spec E1): readiness, and completions of seeded drafts cut from unique
 * boards. Seeded and unbudgeted, so every run is the same.
 */
class ZipCompleterTest {

    @Test
    fun `Zip can be finished`() {
        assertTrue(EditorDraft.fresh(ZipEditor, ZipEditor.variants.first()).canFinish)
    }

    @Test
    fun `two walled dead ends block finishing and name both`() {
        val walls = setOf(Wall(Cell(20), WallDirection.RIGHT), Wall(Cell(23), WallDirection.RIGHT))

        assertEquals(FinishReadiness.Blocked(setOf(20, 24)), ZipCompleter.readiness(drawn(5, TOP_ROW_5, walls)))
    }

    @Test
    fun `one walled dead end can be where the path will stop`() {
        val walls = setOf(Wall(Cell(20), WallDirection.RIGHT))

        assertEquals(FinishReadiness.Ready, ZipCompleter.readiness(drawn(5, TOP_ROW_5, walls)))
    }

    @Test
    fun `a path that starts on the minority colour of an odd board is blocked at its start`() {
        assertEquals(FinishReadiness.Blocked(setOf(1)), ZipCompleter.readiness(drawn(5, listOf(1, 2, 3, 4, 9))))
    }

    @Test
    fun `a growing path is not held to the end colours of a finished one`() {
        // Seven cells on an even board: both ends of the stroke share a colour, which only a full path may not.
        assertEquals(FinishReadiness.Ready, ZipCompleter.readiness(drawn(6, listOf(0, 1, 2, 3, 4, 5, 11))))
    }

    @Test
    fun `end numbers of the wrong colours block a path-less board`() {
        val shared = ZipDraft(5, emptySet(), emptyList(), listOf(0, 12, 1))

        assertEquals(FinishReadiness.Blocked(setOf(0, 1)), ZipCompleter.readiness(shared))
    }

    @Test
    fun `a path one cell short of the minimum needs one more`() {
        val short = drawn(8, List(ZipCompleter.MIN_PATH - 1) { it })

        assertEquals(FinishReadiness.NeedsMore(IncompleteReason(ZipCompleter.PATH, 1)), ZipCompleter.readiness(short))
    }

    @Test
    fun `a blank board needs a path of the minimum length`() {
        val blank = ZipEditor.newDraft(ZipEditor.variants.first())

        assertEquals(
            FinishReadiness.NeedsMore(IncompleteReason(ZipCompleter.PATH, ZipCompleter.MIN_PATH)),
            ZipCompleter.readiness(blank),
        )
    }

    @Test
    fun `a path-less board with both end numbers is ready`() {
        assertEquals(FinishReadiness.Ready, ZipCompleter.readiness(ZipDraft(6, emptySet(), emptyList(), listOf(0, 5))))
    }

    @Test
    fun `every seeded draft completes`() {
        assertEquals(emptyList<String>(), cases.filter { it.completion !is Completion.Done }.map { it.name })
    }

    @Test
    fun `a completion keeps the drawn path as its first cells and covers every cell once`() {
        for ((name, draft, done) in completed()) {
            assertEquals(name, draft.path, done.path.take(draft.path.size))
            assertEquals(name, (0 until draft.n * draft.n).toList(), done.path.sorted())
        }
    }

    @Test
    fun `a completion keeps every number and numbers the path in its order`() {
        for ((name, draft, done) in completed()) {
            assertEquals(name, draft.waypoints, done.waypoints.filter { it in draft.waypoints })
            val positions = done.waypoints.map(done.path::indexOf)
            assertEquals(name, positions.sorted().distinct(), positions)
            assertEquals(name, listOf(0, done.path.lastIndex), listOf(positions.first(), positions.last()))
        }
    }

    @Test
    fun `a completion keeps every wall`() {
        for ((name, draft, done) in completed()) {
            assertTrue(name, done.walls.containsAll(draft.walls))
        }
    }

    @Test
    fun `a completion is unique and its one answer is the path it is drawn with`() {
        for ((name, _, done) in completed()) {
            assertEquals(name, Verdict.Unique(Solution(done.path)), ZipDraftAnalyzer.analyze(done, Budget.Unlimited))
        }
    }

    @Test
    fun `a finished board comes back as it is`() {
        val (_, _, finished) = completed().first()

        assertEquals(Completion.Done(finished), ZipCompleter.complete(finished, Random(1), Budget.Unlimited))
    }

    @Test
    fun `the same seed completes to the same board`() {
        val draft = cases.first { it.draft.n == 8 }.draft

        assertEquals(
            ZipCompleter.complete(draft, Random(7), Budget.Unlimited),
            ZipCompleter.complete(draft, Random(7), Budget.Unlimited),
        )
    }

    @Test
    fun `another seed completes to another board`() {
        val draft = cases.first { it.draft.n == 8 }.draft

        val first = ZipCompleter.complete(draft, Random(7), Budget.Unlimited)
        val second = ZipCompleter.complete(draft, Random(8), Budget.Unlimited)

        assertTrue("$first", first is Completion.Done)
        assertNotEquals(first, second)
    }

    @Test
    fun `a path whose only way on lies deep still completes`() {
        // Cell 20 is shut in on three sides, so the path has to end there: found some 40,000 nodes into one walk.
        val hemmed = drawn(8, listOf(12, 13, 21, 29, 28, 36, 37, 45))

        val completion = ZipCompleter.complete(hemmed, Random(58), Budget.Unlimited)

        assertTrue("$completion", completion is Completion.Done)
        assertEquals(20, (completion as Completion.Done).draft.path.last())
    }

    @Test
    fun `a path that shuts a cell out gives up`() {
        val aroundTheCorner = listOf(1, 7, 6, 12, 18, 24)

        assertEquals(Completion.GaveUp, ZipCompleter.complete(drawn(6, aroundTheCorner), Random(1), Budget.Unlimited))
        assertTrue(ZipCompleter.complete(drawn(6, listOf(0) + aroundTheCorner), Random(1), Budget.Unlimited) is Completion.Done)
    }

    @Test
    fun `a path drawn through a wall gives up`() {
        val walled = drawn(6, TOP_ROW_6, setOf(Wall(Cell(2), WallDirection.RIGHT)))

        assertEquals(Completion.GaveUp, ZipCompleter.complete(walled, Random(1), Budget.Unlimited))
        assertTrue(ZipCompleter.complete(drawn(6, TOP_ROW_6), Random(1), Budget.Unlimited) is Completion.Done)
    }

    @Test
    fun `dead ends that block finishing give up`() {
        val walls = setOf(Wall(Cell(30), WallDirection.RIGHT), Wall(Cell(34), WallDirection.RIGHT))

        assertEquals(Completion.GaveUp, ZipCompleter.complete(drawn(6, TOP_ROW_6, walls), Random(1), Budget.Unlimited))
        assertTrue(ZipCompleter.complete(drawn(6, TOP_ROW_6, walls.take(1).toSet()), Random(1), Budget.Unlimited) is Completion.Done)
    }

    @Test
    fun `a spent budget gives up on a draft that would complete`() {
        val case = cases.first { it.draft.n == 8 }

        assertTrue("${case.completion}", case.completion is Completion.Done)
        assertEquals(Completion.GaveUp, ZipCompleter.complete(case.draft, Random(case.seed), Budget { true }))
    }

    @Test
    fun `a run stops once the budget is spent part way`() {
        val case = cases.first { it.draft.n == 8 }
        var polls = 0
        val budget = Budget { ++polls >= SPENT_AT }

        assertTrue("${case.completion}", case.completion is Completion.Done)
        assertEquals(Completion.GaveUp, ZipCompleter.complete(case.draft, Random(case.seed), budget))
        assertTrue("$polls polls", polls in SPENT_AT..SPENT_AT + 2)
    }

    private fun completed(): List<Triple<String, ZipDraft, ZipDraft>> {
        val done = cases.mapNotNull { case -> (case.completion as? Completion.Done)?.let { Triple(case.name, case.draft, it.draft) } }
        assertTrue("no seeded draft completed", done.isNotEmpty())
        return done
    }

    private class Case(val name: String, val seed: Int, val draft: ZipDraft, val completion: Completion<ZipDraft>)

    private companion object {
        const val SPENT_AT = 3
        val TOP_ROW_5 = listOf(0, 1, 2, 3, 4)
        val TOP_ROW_6 = listOf(0, 1, 2, 3, 4, 5)

        /** [path] stroked on a blank board with [walls], numbered at both ends as the Studio does. */
        fun drawn(n: Int, path: List<Int>, walls: Set<Wall> = emptySet()): ZipDraft {
            val blank = ZipDraft(n, walls, emptyList(), emptyList())
            return blank.stroke(path).apply(blank)
        }

        /**
         * Sizes 5..8, two generated boards each, cut three ways. "path": the first cells of the
         * board's path at the minimum length, one of them numbered, with two of its walls.
         * "ends": no path, the two end numbers and two walls, as a bare shared board.
         * "numbers": no path, every number of the board and all its walls.
         *
         * Keep the size 7 seeds: the generator's walk has no node limit, and with a seed that starts
         * it on the minority colour of a 7×7 board it does not come back.
         */
        val cases: List<Case> by lazy {
            (5..8).flatMap { size -> listOf(size * 100, size * 100 + 1) }.flatMap { seed ->
                val size = seed / 100
                val board = checkNotNull(ZipGenerator(Random(seed)).nextUnique(ZipTestSupport.variantOf(size))) { "no board for seed $seed" }
                listOf(
                    case("path", seed, ZipDraftCuts.drawn(board, ZipCompleter.MIN_PATH, marks = 1, walls = 2, Random(seed))),
                    case("ends", seed, ZipDraftCuts.numbered(board, inner = 0, walls = 2, Random(seed))),
                    case("numbers", seed, ZipDraftCuts.numbered(board, inner = size * size, walls = size * size, Random(seed))),
                )
            }
        }

        fun case(cut: String, seed: Int, draft: ZipDraft): Case =
            Case("n${draft.n} seed $seed $cut", seed, draft, ZipCompleter.complete(draft, Random(seed), Budget.Unlimited))
    }
}
