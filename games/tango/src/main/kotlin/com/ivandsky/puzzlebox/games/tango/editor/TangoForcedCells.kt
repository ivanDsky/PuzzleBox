package com.ivandsky.puzzlebox.games.tango.editor

import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.engine.TangoHint
import com.ivandsky.puzzlebox.games.tango.engine.TangoRules
import com.ivandsky.puzzlebox.games.tango.engine.TangoState

/** Pure, read-only aid: how far the hint ladder carries a solver from the givens. */
object TangoForcedCells {
    /**
     * Every cell the existing hint ladder (TangoRules.hint: marker → triple → count → line) forces from
     * [draft]'s givens, walked until Stuck or full; never the givens themselves. Empty when the
     * givens break a rule. Places nothing.
     */
    fun of(draft: TangoDraft): Map<Int, TangoCell> {
        val puzzle = TangoPuzzle(
            VariantId("tango/n${TangoDraft.N}"), TangoDraft.N, draft.givens, draft.markers,
        )
        if (TangoClashLocator.conflicts(puzzle).isNotEmpty()) return emptyMap()
        val grid = MutableList<TangoCell?>(puzzle.cellCount) { puzzle.givens[it] }
        val forced = linkedMapOf<Int, TangoCell>()
        while (grid.any { it == null }) {
            val hint = TangoRules.hint(TangoState(puzzle, grid.toList()))
            if (hint !is TangoHint.Forced) break
            grid[hint.idx] = hint.value
            forced[hint.idx] = hint.value
        }
        return forced
    }
}
