package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Completion
import com.ivandsky.puzzlebox.games.queens.QueensGenerator
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/**
 * Finish for me with no target is the completer as it was before it could aim for a level (91):
 * the same seeded draft finishes to the same board, cell for cell. The boards below were recorded
 * on origin/main @ f54211b, before [QueensCompleter] read its target. Catches a change to the
 * no-target path: another attempt order, another draw from the random, another first board.
 */
class QueensCompleterLegacyPinTest {

    @Test
    fun `no target finishes each seeded draft to the board it gave before levels`() {
        val boards = RECORDED.keys.associateWith { size ->
            val board = checkNotNull(QueensGenerator(Random(size * 7)).nextUnique(size))
            val draft = QueensDraftCuts.cut(board, QueensCompleter.minimumStarted(size), crowns = 1, Random(size))
            val done = QueensCompleter.complete(draft, Random(size), Budget.Unlimited) as Completion.Done
            done.draft.regions.joinToString("") to done.draft.crowns
        }

        assertEquals(RECORDED, boards)
    }

    private companion object {
        val RECORDED = mapOf(
            5 to ("0000022003200031004411144" to setOf(10)),
            6 to ("000001000111300241350441550444550004" to setOf(11)),
            8 to ("0002222370222233774424334444241344444413444414114566111145551111" to setOf(51)),
            9 to ("033322666333322666334322266444342565444444555741555555777755555877775555888855555" to setOf(67)),
        )
    }
}
