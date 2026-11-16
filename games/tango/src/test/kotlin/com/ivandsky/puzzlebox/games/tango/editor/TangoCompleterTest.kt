package com.ivandsky.puzzlebox.games.tango.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Completion
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.FinishReadiness
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.engine.TangoBoardGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Finish for me on Tango (spec E1): readiness, and completions of seeded drafts cut from the
 * answers of generated boards. Seeded and unbudgeted, so every run is the same.
 */
class TangoCompleterTest {

    @Test
    fun `Tango can be finished`() {
        assertTrue(EditorDraft.fresh(TangoEditor, TangoEditor.defaultVariant).canFinish)
    }

    @Test
    fun `givens that break a rule block finishing and name the cells`() {
        val draft = draft("SSS...", "......", "......", "......", "......", "......")

        assertEquals(FinishReadiness.Blocked(setOf(0, 1, 2)), TangoCompleter.readiness(draft))
    }

    @Test
    fun `a blank board needs the minimum of pieces`() {
        assertEquals(
            FinishReadiness.NeedsMore(IncompleteReason(TangoCompleter.PIECES, TangoCompleter.MIN_PIECES)),
            TangoCompleter.readiness(TangoDraft(6, emptyMap(), emptyList())),
        )
    }

    @Test
    fun `the minimum is met by givens alone and by markers alone`() {
        val answer = TangoDraftCuts.answer(6, seed = 600)

        for ((givens, markers) in listOf(TangoCompleter.MIN_PIECES to 0, 0 to TangoCompleter.MIN_PIECES)) {
            val draft = TangoDraftCuts.cut(6, answer, givens, markers, Random(600))
            assertEquals("$givens givens, $markers markers", FinishReadiness.Ready, TangoCompleter.readiness(draft))
        }
    }

    @Test
    fun `every seeded draft completes`() {
        assertEquals(emptyList<String>(), cases.filter { it.completion !is Completion.Done }.map { it.name })
    }

    @Test
    fun `a completion keeps the size, every marker and every given`() {
        for ((name, draft, done) in completed()) {
            assertEquals(name, draft.n, done.n)
            assertEquals(name, draft.markers, done.markers)
            assertEquals(name, draft.givens, done.givens.filterKeys { it in draft.givens })
        }
    }

    @Test
    fun `a completion is unique and the hint ladder finishes it`() {
        for ((name, _, done) in completed()) {
            val verdict = TangoDraftAnalyzer.analyze(done, Budget.Unlimited)
            assertTrue("$name: $verdict", verdict is Verdict.Unique && !verdict.needsGuessing)
        }
    }

    @Test
    fun `a board the hint ladder already finishes comes back as it is`() {
        val (_, _, finished) = completed().first()

        assertEquals(Completion.Done(finished), TangoCompleter.complete(finished, Random(1), Budget.Unlimited))
    }

    @Test
    fun `a board with one answer the hint ladder cannot reach gets givens until it can`() {
        val before = TangoDraftAnalyzer.analyze(NEEDS_GUESSING, Budget.Unlimited)
        assertTrue("$before", before is Verdict.Unique && before.needsGuessing)

        val completion = TangoCompleter.complete(NEEDS_GUESSING, Random(1), Budget.Unlimited)

        assertTrue("$completion", completion is Completion.Done)
        val done = (completion as Completion.Done).draft
        val after = TangoDraftAnalyzer.analyze(done, Budget.Unlimited)
        assertTrue("$after", after is Verdict.Unique && !after.needsGuessing)
        assertEquals(NEEDS_GUESSING.givens, done.givens.filterKeys { it in NEEDS_GUESSING.givens })
        assertEquals((before as Verdict.Unique).solution, (after as Verdict.Unique).solution)
    }

    @Test
    fun `the same seed completes to the same board`() {
        val draft = cases.first { it.draft.n == 8 }.draft

        val first = TangoCompleter.complete(draft, Random(7), Budget.Unlimited)

        assertTrue("$first", first is Completion.Done)
        assertEquals(first, TangoCompleter.complete(draft, Random(7), Budget.Unlimited))
    }

    @Test
    fun `another seed completes to another board`() {
        val draft = cases.first { it.draft.n == 8 }.draft

        val first = TangoCompleter.complete(draft, Random(7), Budget.Unlimited)
        val second = TangoCompleter.complete(draft, Random(8), Budget.Unlimited)

        assertTrue("$first", first is Completion.Done)
        assertNotEquals(first, second)
    }

    @Test
    fun `givens that break a rule give up`() {
        val draft = draft("SSS...", "......", "......", "......", "......", "......")

        assertEquals(Completion.GaveUp, TangoCompleter.complete(draft, Random(1), Budget.Unlimited))
    }

    @Test
    fun `markers that cannot all hold give up`() {
        for (n in TangoDraft.SIZES) {
            val draft = TangoDraft(n, emptyMap(), TangoBoardGenerator.parityTrap(n).markers.canonicalMarkers())

            assertEquals("n$n", Completion.GaveUp, TangoCompleter.complete(draft, Random(1), Budget.Unlimited))
        }
    }

    @Test
    fun `givens no board can hold are ready but give up`() {
        assertEquals(FinishReadiness.Ready, TangoCompleter.readiness(NO_BOARD))
        assertEquals(Completion.GaveUp, TangoCompleter.complete(NO_BOARD, Random(1), Budget.Unlimited))
    }

    @Test
    fun `a spent budget gives up on a draft that would complete`() {
        val case = cases.first { it.draft.n == 8 }

        assertTrue("${case.completion}", case.completion is Completion.Done)
        assertEquals(Completion.GaveUp, TangoCompleter.complete(case.draft, Random(case.seed), Budget { true }))
    }

    @Test
    fun `a run gives up at the very poll that finds the budget spent`() {
        val case = cases.first { it.draft.n == 8 }
        var total = 0
        val unspent = TangoCompleter.complete(case.draft, Random(case.seed), Budget { total++; false })
        assertEquals(case.completion, unspent)
        assertTrue("$total polls", total > 1)

        for (spentAt in 1..total) {
            var polls = 0
            val completion = TangoCompleter.complete(case.draft, Random(case.seed), Budget { ++polls >= spentAt })

            assertEquals("spent at poll $spentAt of $total", Completion.GaveUp, completion)
            assertEquals("spent at poll $spentAt of $total", spentAt, polls)
        }
    }

    @Test
    fun `the budget is polled between the hint ladder's deductions`() {
        val (_, _, finished) = completed().first { (_, draft, _) -> draft.n == 10 }
        val empty = finished.n * finished.n - finished.givens.size
        var polls = 0

        TangoCompleter.complete(finished, Random(1), Budget { polls++; false })

        assertTrue("$polls polls, $empty cells for the ladder to fill", polls >= empty)
    }

    private fun completed(): List<Triple<String, TangoDraft, TangoDraft>> {
        val done = cases.mapNotNull { case -> (case.completion as? Completion.Done)?.let { Triple(case.name, case.draft, it.draft) } }
        assertTrue("no seeded draft completed", done.isNotEmpty())
        return done
    }

    private class Case(val name: String, val seed: Long, val draft: TangoDraft, val completion: Completion<TangoDraft>)

    private companion object {
        /** `notation/tango-needs-guessing.txt`: one answer, and the hint ladder stalls before the board is full. */
        val NEEDS_GUESSING = draft("S....S", ".M..S.", "...SM.", ".....S", "..M...", "....M.")

        /**
         * No two givens clash, yet column 0 fills with moons, rows 2 and 3 then need suns in
         * columns 2 and 3, column 1 needs suns on top, and row 0 would be three suns.
         */
        val NO_BOARD = draft("S.S.", "S...", ".M..", ".M..")

        /**
         * Every editor size, two generated answers each, cut four ways: the minimum as givens,
         * the minimum as markers, n/2 of each, and two givens with markers on a third of the edges.
         */
        val cases: List<Case> by lazy {
            TangoDraft.SIZES.flatMap { n -> listOf(n * 100L, n * 100L + 1).map { seed -> n to seed } }.flatMap { (n, seed) ->
                val answer = TangoDraftCuts.answer(n, seed)
                listOf(
                    "minimum of givens" to TangoDraftCuts.cut(n, answer, givens = TangoCompleter.MIN_PIECES, markers = 0, Random(seed)),
                    "minimum of markers" to TangoDraftCuts.cut(n, answer, givens = 0, markers = TangoCompleter.MIN_PIECES, Random(seed)),
                    "half a line of each" to TangoDraftCuts.cut(n, answer, givens = n / 2, markers = n / 2, Random(seed)),
                    "markers on a third of the edges" to TangoDraftCuts.cut(n, answer, givens = 2, markers = TangoDraftCuts.edgeCount(n) / 3, Random(seed)),
                ).map { (cut, draft) ->
                    Case("n$n seed $seed $cut", seed, draft, TangoCompleter.complete(draft, Random(seed), Budget.Unlimited))
                }
            }
        }

        /** A givens-only draft from its rows: `S` sun, `M` moon, `.` empty. */
        fun draft(vararg rows: String): TangoDraft = TangoDraft(
            rows.size,
            rows.joinToString("").withIndex().filter { it.value != '.' }
                .associate { (idx, cell) -> idx to if (cell == 'S') TangoCell.SUN else TangoCell.MOON },
            emptyList(),
        )
    }
}
