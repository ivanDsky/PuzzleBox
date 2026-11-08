package com.ivandsky.puzzlebox.games.tangoui

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.engine.TangoState
import com.ivandsky.puzzlebox.games.tango.render.TangoRendererModel

/** The violating cell-sets of a grid, via the shared scan (no suppression applied). */
internal fun violatingSets(puzzle: TangoPuzzle, grid: List<TangoCell?>): Set<Set<Int>> =
    TangoSemantics.violations(TangoRendererModel.of(state = TangoState(puzzle, grid), hint = null))
        .mapTo(mutableSetOf()) { it.cells }
