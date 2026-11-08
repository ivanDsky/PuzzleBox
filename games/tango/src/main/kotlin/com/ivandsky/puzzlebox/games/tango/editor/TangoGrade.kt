package com.ivandsky.puzzlebox.games.tango.editor

/**
 * How far the hint ladder gets on a board: the [hardest] rung any forced move used (null when
 * the givens already fill the board), and whether the walk got [stuck] before the board was full.
 */
data class TangoGrade(val hardest: TangoRung?, val stuck: Boolean)
