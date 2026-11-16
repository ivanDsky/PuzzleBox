package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Completion
import com.ivandsky.puzzlebox.game.editor.FinishReadiness
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.queens.QueensGenerator
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.engine.solve.QueensSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Finish for me on Queens (spec E1): readiness, and completions of seeded drafts cut from
 * unique boards. Seeded and unbudgeted, so every run is the same.
 */
class QueensCompleterTest {

    @Test
    fun `clashing crowns block finishing and name the crowns`() {
        val draft = QueensDraft(6, started(6, 6).regions, crowns = setOf(0, 3, 16))

        assertEquals(FinishReadiness.Blocked(setOf(0, 3)), QueensCompleter.readiness(draft))
    }

    @Test
    fun `one region short of the minimum needs one more`() {
        val short = started(8, QueensCompleter.minimumStarted(8) - 1)

        assertEquals(
            FinishReadiness.NeedsMore(IncompleteReason(QueensCompleter.STARTED, 1)),
            QueensCompleter.readiness(short),
        )
    }

    @Test
    fun `a draft at the minimum is ready`() {
        assertEquals(FinishReadiness.Ready, QueensCompleter.readiness(started(8, QueensCompleter.minimumStarted(8))))
    }

    @Test
    fun `the minimum is most of the regions at every size`() {
        for (size in 4..12) {
            val minimum = QueensCompleter.minimumStarted(size)
            assertTrue("size $size minimum $minimum", minimum > size / 2 && minimum <= size)
        }
    }

    @Test
    fun `every seeded draft completes`() {
        assertEquals(emptyList<String>(), cases.filter { it.completion !is Completion.Done }.map { it.name })
    }

    @Test
    fun `regions painted whole around queens that sit apart still get one queen each`() {
        val board = checkNotNull(QueensGenerator(Random(1001)).nextUnique(10))
        val draft = QueensDraftCuts.cut(board, started = 10, crowns = 0, Random(1001), whole = true)

        val completion = QueensCompleter.complete(draft, Random(1001), Budget.Unlimited)

        assertTrue("$completion", completion is Completion.Done)
    }

    @Test
    fun `a finished board comes back as it is`() {
        val (_, _, finished) = completed().first()

        assertEquals(Completion.Done(finished), QueensCompleter.complete(finished, Random(1), Budget.Unlimited))
    }

    @Test
    fun `a completion paints every cell and keeps every painted cell in its region`() {
        for ((name, draft, done) in completed()) {
            assertTrue(name, QueensDraft.UNPAINTED !in done.regions)
            val painted = draft.regions.indices.filter { draft.regions[it] != QueensDraft.UNPAINTED }
            assertEquals(name, painted.map { draft.regions[it] }, painted.map { done.regions[it] })
        }
    }

    @Test
    fun `a completion keeps every crown on a queen of its one answer`() {
        for ((name, draft, done) in completed()) {
            assertEquals(name, draft.crowns, done.crowns)
            val answer = (QueensDraftAnalyzer.analyze(done, Budget.Unlimited) as Verdict.Unique).solution.values
            assertTrue(name, answer.containsAll(done.crowns))
        }
    }

    @Test
    fun `a completion is unique and the hint ladder finishes it`() {
        for ((name, _, done) in completed()) {
            val verdict = QueensDraftAnalyzer.analyze(done, Budget.Unlimited)
            assertTrue("$name: $verdict", verdict is Verdict.Unique && !verdict.needsGuessing)
        }
    }

    @Test
    fun `a completion has no split region`() {
        for ((name, _, done) in completed()) {
            assertEquals(name, emptyMap<Int, List<Set<Int>>>(), QueensContiguity.splitRegions(done))
        }
    }

    @Test
    fun `two pieces of one region are joined through unpainted cells`() {
        val regions = MutableList(36) { QueensDraft.UNPAINTED }
        regions[0] = 0
        regions[5] = 0
        regions[30] = 1

        val done = QueensCompleter.complete(QueensDraft(6, regions), Random(1), Budget.Unlimited) as Completion.Done

        assertEquals(listOf(0, 0, 1), listOf(0, 5, 30).map { done.draft.regions[it] })
        assertEquals(emptyMap<Int, List<Set<Int>>>(), QueensContiguity.splitRegions(done.draft))
    }

    @Test
    fun `a crown on an unpainted cell stays on a queen of the answer`() {
        val regions = MutableList(36) { QueensDraft.UNPAINTED }
        regions[0] = 0
        val draft = QueensDraft(6, regions, crowns = setOf(14))

        val done = QueensCompleter.complete(draft, Random(1), Budget.Unlimited) as Completion.Done

        val answer = (QueensDraftAnalyzer.analyze(done.draft, Budget.Unlimited) as Verdict.Unique).solution.values
        assertEquals(setOf(14), done.draft.crowns)
        assertTrue("$answer", 14 in answer)
    }

    @Test
    fun `the same seed completes to the same board`() {
        val draft = cases.first { it.draft.size == 8 }.draft

        assertEquals(
            QueensCompleter.complete(draft, Random(7), Budget.Unlimited),
            QueensCompleter.complete(draft, Random(7), Budget.Unlimited),
        )
    }

    @Test
    fun `another seed completes to another board`() {
        val draft = cases.first { it.draft.size == 8 }.draft

        val first = QueensCompleter.complete(draft, Random(7), Budget.Unlimited)
        val second = QueensCompleter.complete(draft, Random(8), Budget.Unlimited)

        assertTrue("$first", first is Completion.Done)
        assertNotEquals(first, second)
    }

    @Test
    fun `a full board with two answers gives up`() {
        assertEquals(Completion.GaveUp, QueensCompleter.complete(QueensDraft(4, TWO_ANSWERS), Random(1), Budget.Unlimited))
    }

    @Test
    fun `a draft no repaint can make unique gives up`() {
        assertEquals(Completion.GaveUp, QueensCompleter.complete(STILL_TWO_ANSWERS, Random(1), Budget.Unlimited))
    }

    @Test
    fun `a run that cannot finish stops once the budget is spent`() {
        var polls = 0
        val budget = Budget { ++polls >= SPENT_AT }

        assertEquals(Completion.GaveUp, QueensCompleter.complete(STILL_TWO_ANSWERS, Random(1), budget))
        assertTrue("$polls polls", polls in SPENT_AT..SPENT_AT + 3)
    }

    @Test
    fun `a spent budget gives up on a draft that would complete`() {
        val case = cases.first { it.draft.size == 8 }

        assertTrue("${case.completion}", case.completion is Completion.Done)
        assertEquals(Completion.GaveUp, QueensCompleter.complete(case.draft, Random(case.seed), Budget { true }))
    }

    @Test
    fun `the budget stops the hint ladder between its deductions`() {
        val (_, _, finished) = completed().first { (_, draft, _) -> draft.size == 8 }
        var polls = 0
        // A finished board takes one attempt and its searches poll a few times; the ladder then needs a step per queen at least.
        val spentInTheLadder = Budget { ++polls >= SPENT_AT }

        assertEquals(Completion.GaveUp, QueensCompleter.complete(finished, Random(1), spentInTheLadder))
        assertEquals(Completion.Done(finished), QueensCompleter.complete(finished, Random(1), Budget.Unlimited))
    }

    @Test
    fun `clashing crowns give up`() {
        val draft = QueensDraft(6, started(6, 6).regions, crowns = setOf(0, 3))

        assertEquals(Completion.GaveUp, QueensCompleter.complete(draft, Random(1), Budget.Unlimited))
    }

    private fun completed(): List<Triple<String, QueensDraft, QueensDraft>> {
        val done = cases.mapNotNull { case -> (case.completion as? Completion.Done)?.let { Triple(case.name, case.draft, it.draft) } }
        assertTrue("no seeded draft completed", done.isNotEmpty())
        return done
    }

    private class Case(val name: String, val seed: Int, val draft: QueensDraft, val completion: Completion<QueensDraft>)

    private companion object {
        const val SPENT_AT = 10

        /** Regions mirror the columns, so both 4-queens placements {1, 7, 8, 14} and {2, 4, 11, 13} fit. */
        val TWO_ANSWERS = List(16) { it % 4 }

        /** [TWO_ANSWERS] with the corner unpainted: no answer has a queen there, so both always fit. */
        val STILL_TWO_ANSWERS = QueensDraft(4, listOf(QueensDraft.UNPAINTED) + TWO_ANSWERS.drop(1))

        /**
         * Sizes 4..10, two boards each, cut twice. "minimum": a generated board cut down to the
         * minimum of started regions, two of them crowned. "every region": that draft's
         * completion with every region cut back to a part of itself, so the draft can be
         * finished (the completion is one way) and the author has pinned something everywhere.
         */
        val cases: List<Case> by lazy {
            (4..10).flatMap { size -> listOf(size * 100, size * 100 + 1) }.flatMap { seed ->
                val board = checkNotNull(QueensGenerator(Random(seed)).nextUnique(seed / 100)) { "no board for seed $seed" }
                val minimum = case("minimum", seed, QueensDraftCuts.cut(board, QueensCompleter.minimumStarted(board.size), crowns = 2, Random(seed)))
                val finished = (minimum.completion as? Completion.Done)?.draft
                listOfNotNull(minimum, finished?.let { case("every region", seed, QueensDraftCuts.cut(puzzle(it), it.size, crowns = 0, Random(seed))) })
            }
        }

        fun case(cut: String, seed: Int, draft: QueensDraft): Case =
            Case("n${draft.size} seed $seed $cut", seed, draft, QueensCompleter.complete(draft, Random(seed), Budget.Unlimited))

        /** A finished board as the puzzle [QueensDraftCuts] cuts from, its answer included. */
        fun puzzle(finished: QueensDraft): QueensPuzzle {
            val bare = QueensPuzzle(finished.size, finished.regions)
            return bare.copy(solution = QueensSearch.solutions(bare, limit = 1).single())
        }

        /** [count] regions started, one cell each on the diagonal. */
        fun started(size: Int, count: Int): QueensDraft =
            QueensDraft(size, List(size * size) { if (it / size == it % size && it / size < count) it / size else QueensDraft.UNPAINTED })
    }
}
