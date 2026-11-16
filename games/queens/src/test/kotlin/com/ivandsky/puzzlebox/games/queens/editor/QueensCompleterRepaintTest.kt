package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Completion
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.engine.solve.QueensSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Finish for me on drafts whose paint cannot all stay (ticket 63b): a board the author painted
 * by hand has other answers on the painted cells, so finishing it means recolouring some.
 * Seeded and unbudgeted, so every run is the same; the 2 s figures are `QueensPaintedMeasurement`'s.
 */
class QueensCompleterRepaintTest {

    @Test
    fun `the reported board finishes with every crown kept and one painted cell changed`() {
        assertEquals("the board as reported has several answers", 2, QueensSearch.solutions(QueensPuzzle(7, REPORTED.regions), limit = 2).size)

        val completion = QueensCompleter.complete(REPORTED, Random(1), Budget.Unlimited)

        assertTrue("$completion", completion is Completion.Done)
        val done = (completion as Completion.Done).draft
        assertEquals(emptyList<String>(), unsound(done))
        assertEquals(REPORTED.crowns, done.crowns)
        assertEquals("the fewest that finish it; the ticket allows four", 1, changed(REPORTED, done))
    }

    @Test
    fun `nine in ten fully painted boards with several answers finish at every size`() {
        assertEquals(emptyList<String>(), short(full))
    }

    @Test
    fun `nine in ten partly painted drafts finish at every size with and without crowns`() {
        assertEquals(emptyList<String>(), short(partial))
    }

    @Test
    fun `a repainted board has one answer the ladder reaches, whole regions and its crowns on the answer`() {
        val done = (full + partial).filter { it.done != null }
        assertTrue("no draft finished", done.isNotEmpty())

        assertEquals(emptyList<String>(), done.flatMap { press -> unsound(checkNotNull(press.done)).map { "${press.name}: $it" } })
    }

    @Test
    fun `a repaint keeps every crown that an answer of the board holds`() {
        val moved = (full + partial).filter { it.done != null && it.done.crowns != it.draft.crowns }

        assertEquals(emptyList<String>(), moved.map { it.name })
    }

    @Test
    fun `nine in ten drafts cut from a board with one answer keep every painted cell`() {
        // A fill exists for each; the few that need more tries than the fill gets are repainted.
        val cut = partial.filter { it.name.startsWith("unique") }
        val repainted = cut.filter { it.done != null && changed(it.draft, it.done) > 0 }

        assertTrue("${repainted.map { it.name }} of ${cut.size}", repainted.size * 10 <= cut.size)
    }

    @Test
    fun `a board one or two recoloured cells can finish is finished with that many`() {
        val boards = (5..6).flatMap { size -> Random(size).let { random -> List(4) { QueensPaintings.ambiguous(size, random) } } }
        val seen = HashSet<Int>()
        for ((index, board) in boards.withIndex()) for (crowned in listOf(true, false)) {
            val draft = QueensDraftCuts.painted(board, share = 1.0, crowned, Random(index))
            val fewest = fewestByBruteForce(draft)
            val done = (QueensCompleter.complete(draft, Random(index), Budget.Unlimited) as? Completion.Done)?.draft

            val name = "n${board.size} board $index crowned $crowned, $fewest by brute force"
            if (fewest != null) assertEquals(name, fewest, done?.let { changed(draft, it) })
            if (fewest == null && done != null) assertTrue(name, changed(draft, done) > 2)
            fewest?.let(seen::add)
        }
        assertEquals("the sample holds boards of both kinds", setOf(1, 2), seen)
    }

    @Test
    fun `the same seed repaints to the same board`() {
        val first = QueensCompleter.complete(REPORTED, Random(7), Budget.Unlimited)

        assertTrue("$first", first is Completion.Done)
        assertEquals(first, QueensCompleter.complete(REPORTED, Random(7), Budget.Unlimited))
    }

    @Test
    fun `other seeds repaint a board with several equally small fixes in other ways`() {
        val boards = (1..8).map { QueensCompleter.complete(QueensDraft(4, TWO_ANSWERS), Random(it), Budget.Unlimited) }

        assertTrue("$boards", boards.all { it is Completion.Done })
        assertNotEquals(1, boards.distinct().size)
    }

    @Test
    fun `a crown no answer can hold moves along its row and the other crown stays`() {
        // No four queens that stand apart include a corner, so the crown on cell 0 cannot stay.
        val draft = QueensDraft(4, TWO_ANSWERS, crowns = setOf(0, 7))

        val done = (QueensCompleter.complete(draft, Random(1), Budget.Unlimited) as Completion.Done).draft

        assertEquals(emptyList<String>(), unsound(done))
        assertEquals(setOf(1, 7), done.crowns)
    }

    @Test
    fun `a spent budget stops the repaint at its next poll`() {
        var polls = 0
        val budget = Budget { ++polls >= SPENT_AT }

        assertEquals(Completion.GaveUp, QueensCompleter.complete(REPORTED.copy(crowns = setOf(1)), Random(1), budget))
        assertTrue("$polls polls", polls in SPENT_AT..SPENT_AT + 3)
    }

    /** "size crowns: done of presses" for every size and crown choice where under nine in ten finished. */
    private fun short(presses: List<Press>): List<String> =
        presses.groupBy { "n${it.draft.size} ${if (it.draft.crowns.isEmpty()) "no crowns" else "crowned"}" }
            .filterValues { group -> group.count { it.done != null } * 10 < group.size * 9 }
            .map { (name, group) -> "$name: ${group.count { it.done != null }} of ${group.size}" }

    /** What [QueensDraftAnalyzer] and [QueensContiguity] hold against a finished [board]; empty when it is sound. */
    private fun unsound(board: QueensDraft): List<String> {
        val verdict = QueensDraftAnalyzer.analyze(board, Budget.Unlimited)
        return listOfNotNull(
            "not unique: $verdict".takeIf { verdict !is Verdict.Unique },
            "needs guessing".takeIf { (verdict as? Verdict.Unique)?.needsGuessing == true },
            "a crown off the answer".takeIf { (verdict as? Verdict.Unique)?.solution?.values?.containsAll(board.crowns) == false },
            "split regions ${QueensContiguity.splitRegions(board).keys}".takeIf { QueensContiguity.splitRegions(board).isNotEmpty() },
        )
    }

    /** 1 or 2 when that many recoloured cells can finish the full [draft] and fewer cannot; null otherwise. */
    private fun fewestByBruteForce(draft: QueensDraft): Int? {
        val n = draft.size
        fun finished(regions: List<Int>): Boolean =
            QueensSearch.solutions(QueensPuzzle(n, regions), limit = 2).size == 1 && unsound(draft.copy(regions = regions)).isEmpty()
        val changes = draft.regions.indices.flatMap { cell -> (0 until n).filter { it != draft.regions[cell] }.map { cell to it } }
        fun with(regions: List<Int>, change: Pair<Int, Int>) = regions.toMutableList().also { it[change.first] = change.second }
        if (changes.any { finished(with(draft.regions, it)) }) return 1
        val two = changes.any { first -> changes.any { second -> second.first > first.first && finished(with(with(draft.regions, first), second)) } }
        return if (two) 2 else null
    }

    private class Press(val name: String, val draft: QueensDraft, val done: QueensDraft?)

    private companion object {
        const val SPENT_AT = 10

        /** Regions mirror the columns, so both 4-queens placements {1, 7, 8, 14} and {2, 4, 11, 13} fit. */
        val TWO_ANSWERS = List(16) { it % 4 }

        /**
         * The board of the user report (build 1148): every cell painted, the author's seven
         * crowns one of its six answers, and Finish gave up on it.
         */
        val REPORTED = QueensDraft(
            size = 7,
            regions = listOf("TBBBBOO", "TRRRROO", "TTTRRDO", "TTRRDDD", "TTRYYGG", "TTRYGGG", "TTYYGGG").flatMap { row -> row.map { "TBRODYG".indexOf(it) } },
            crowns = setOf(3, 8, 20, 25, 28, 40, 44),
        )

        fun changed(draft: QueensDraft, done: QueensDraft): Int =
            draft.regions.indices.count { draft.regions[it] != QueensDraft.UNPAINTED && draft.regions[it] != done.regions[it] }

        fun press(name: String, draft: QueensDraft, seed: Int): Press =
            Press(name, draft, (QueensCompleter.complete(draft, Random(seed), Budget.Unlimited) as? Completion.Done)?.draft)

        /** Sizes 5..10, five hand-style paintings each, fully painted, with the whole intended answer crowned and with no crown. */
        val full: List<Press> by lazy {
            (5..10).flatMap { size ->
                val random = Random(size)
                List(5) { QueensPaintings.ambiguous(size, random) }.flatMapIndexed { index, board ->
                    listOf(true, false).map { crowned -> press("painting n$size board $index crowned $crowned", QueensDraftCuts.painted(board, 1.0, crowned, random), index) }
                }
            }
        }

        /**
         * Sizes 5..10, two paintings and two boards with one answer each, cut back to 40, 70 and
         * 95 percent painted, crowned and not.
         */
        val partial: List<Press> by lazy {
            (5..10).flatMap { size ->
                val random = Random(size)
                val boards = List(2) { "painting" to QueensPaintings.ambiguous(size, random) } + List(2) { "unique" to QueensPaintings.unique(size, random) }
                boards.flatMapIndexed { index, (source, board) ->
                    listOf(40, 70, 95).flatMap { share ->
                        listOf(true, false).map { crowned ->
                            val draft = QueensDraftCuts.painted(board, share / 100.0, crowned, Random(size * 1000 + share + index))
                            press("$source n$size board $index $share% crowned $crowned", draft, index)
                        }
                    }
                }
            }
        }
    }
}
