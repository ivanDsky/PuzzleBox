package com.ivandsky.puzzlebox.games.tango.engine.trainer

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStep

/**
 * One Trainer snippet cut from a walk: [gridBefore], the grid just before [step], the deduction of
 * the target technique the player is asked to find. The grid is as the walk had it, row-major, with
 * the puzzle's givens and every cell the walk filled so far; `null` is an empty cell.
 */
data class TangoSnippetCut(val gridBefore: List<TangoCell?>, val step: TangoStep.Deduction)
