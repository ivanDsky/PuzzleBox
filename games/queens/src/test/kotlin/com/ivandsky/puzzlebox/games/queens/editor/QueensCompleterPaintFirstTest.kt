package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Completion
import com.ivandsky.puzzlebox.game.editor.Difficulty.Level
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlin.random.Random

/**
 * Finish for me pressed with a level, the way the Studio presses it (ticket 107): the author's
 * paint comes first and the crowns second. A crown moves before a painted cell changes colour,
 * and an empty cell takes a colour the author has not used before it grows a painted region.
 * Seeded and unbudgeted, so every run is the same.
 */
class QueensCompleterPaintFirstTest {

    @Test
    fun `a draft that one moved crown finishes keeps every painted cell and moves the crown along its row`() {
        val done = finish(CROWN_OFF, Level.Easy, seed = 1)

        assertEquals(emptyList<String>(), unsound(done))
        assertEquals("painted cells changed", emptyList<Int>(), recoloured(CROWN_OFF, done))
        assertEquals("the crown stays in its row", listOf(0), done.crowns.map { it / CROWN_OFF.size })
        assertNotEquals(CROWN_OFF.crowns, done.crowns)
    }

    @Test
    fun `a fully painted board with one answer keeps its paint and moves a crown that is off the answer`() {
        val board = QueensPaintings.unique(6, Random(1))
        val draft = QueensDraft(6, board.regions, crowns = setOf(5))

        val done = finish(draft, Level.Medium, seed = 1)

        assertEquals(board.regions, done.regions)
        assertEquals("the crown on 5 moves to the answer's queen on 4", setOf(4), done.crowns)
    }

    @Test
    fun `a strip left empty beside painted regions takes the unused colour`() {
        val board = QueensPaintings.unique(6, Random(1))
        val level = checkNotNull(QueensScoredGrader.grade(QueensPuzzle(6, board.regions), Budget.Unlimited)).level

        for (colour in listOf(0, 3)) {
            val draft = QueensDraft(6, board.regions.map { if (it == colour) QueensDraft.UNPAINTED else it })
            for (seed in 1..4) {
                val done = finish(draft, level, seed)

                assertEquals("colour $colour, seed $seed", board.regions, done.regions)
            }
        }
    }

    @Test
    fun `empty space left for two unused colours is shared between them and no painted region grows`() {
        val board = QueensPaintings.unique(7, Random(1))
        // The level of the board the draft was cut from, so the press stops at an early board; a
        // level these fills cannot reach has an unbudgeted press look through thousands of them.
        val level = checkNotNull(QueensScoredGrader.grade(QueensPuzzle(7, board.regions), Budget.Unlimited)).level
        val unused = setOf(3, 4)
        val draft = QueensDraft(7, board.regions.map { if (it in unused) QueensDraft.UNPAINTED else it })

        for (seed in 1..4) {
            val done = finish(draft, level, seed)

            assertEquals("seed $seed", emptyList<String>(), unsound(done))
            assertEquals("seed $seed: painted cells changed", emptyList<Int>(), recoloured(draft, done))
            val grown = draft.regions.indices.filter { draft.regions[it] == QueensDraft.UNPAINTED && done.regions[it] !in unused }
            assertEquals("seed $seed: empty cells that joined a painted region", emptyList<Int>(), grown)
        }
    }

    @Test
    fun `a level only a grown painted region reaches is passed up for the closest one the unused colours reach`() {
        val painted = setOf(0, 2, 4)

        val done = finish(SEEDED, Level.Expert, seed = 5)

        assertEquals(emptyList<String>(), unsound(done))
        assertEquals(SEEDED.crowns, done.crowns)
        assertEquals("painted cells changed", emptyList<Int>(), recoloured(SEEDED, done))
        val grown = SEEDED.regions.indices.filter { SEEDED.regions[it] == QueensDraft.UNPAINTED && done.regions[it] in painted }
        assertEquals("empty cells that joined a painted region", emptyList<Int>(), grown)
        assertEquals(Level.Easy, QueensScoredGrader.grade(QueensPuzzle(SEEDED.size, done.regions), Budget.Unlimited)?.level)
    }

    private fun finish(draft: QueensDraft, target: Level, seed: Int): QueensDraft =
        (QueensCompleter.complete(draft, Random(seed), Budget.Unlimited, target) as Completion.Done).draft

    /** The painted cells of [draft] that [done] holds in another region. */
    private fun recoloured(draft: QueensDraft, done: QueensDraft): List<Int> =
        draft.regions.indices.filter { draft.regions[it] != QueensDraft.UNPAINTED && draft.regions[it] != done.regions[it] }

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

    private companion object {
        /**
         * A 6×6 with one answer, [4, 6, 14, 23, 25, 33], cut back to eight cells in ten, and the
         * author's one crown a cell to the right of the answer's queen in the top row. No fill of
         * the empty cells has one answer through cell 5; with the crown on cell 4 the paint stays.
         */
        val CROWN_OFF = QueensDraft(
            size = 6,
            regions = "000011.2000..22..32223.344.333445533".map { if (it == '.') QueensDraft.UNPAINTED else it.digitToInt() },
            crowns = setOf(5),
        )

        /**
         * A 5×5 as an author seeds it: three of the five colours started, one crown. A fill that
         * grows the painted regions reaches Medium (before 107 a press for Expert ended there);
         * the fills that give every empty cell to the two unused colours reach Easy.
         */
        val SEEDED = QueensDraft(
            size = 5,
            regions = ".00.......2........4...44".map { if (it == '.') QueensDraft.UNPAINTED else it.digitToInt() },
            crowns = setOf(10),
        )
    }
}
