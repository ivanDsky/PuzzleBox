package com.ivandsky.puzzlebox.games.tango.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.engine.TangoRules
import com.ivandsky.puzzlebox.games.tango.engine.TangoState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TangoPinnedFillTest {

    @Test
    fun `a fill is a finished grid that holds every given and satisfies every marker`() {
        for (n in TangoDraft.SIZES) for (seed in 1L..5L) {
            val answer = TangoDraftCuts.answer(n, seed)
            val draft = TangoDraftCuts.cut(n, answer, givens = n, markers = TangoDraftCuts.edgeCount(n) / 4, Random(seed))
            val pieces = TangoPuzzle(TangoDraft.variantOf(n), n, draft.givens, draft.markers)

            val grid = checkNotNull(TangoPinnedFill.fill(pieces, Random(seed), Budget.Unlimited)) { "n$n seed $seed: no fill" }

            assertTrue("n$n seed $seed: $grid", TangoRules.isComplete(TangoState(pieces, grid)))
            assertEquals("n$n seed $seed", draft.givens, draft.givens.keys.associateWith { grid[it] })
        }
    }
}
