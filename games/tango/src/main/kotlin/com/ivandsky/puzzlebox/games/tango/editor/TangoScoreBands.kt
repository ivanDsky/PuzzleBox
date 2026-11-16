package com.ivandsky.puzzlebox.games.tango.editor

import com.ivandsky.puzzlebox.game.editor.LevelBands

/**
 * Where each level starts on [TangoScoredGrader]'s score, per board width: a bigger board has more
 * cells to place, so the same score is an easier board there. The grader and [TangoTargetFinish]
 * both read these, so Finish aims at the level the grader will show.
 *
 * The 6×6 numbers are set on the 756 real dailies and on what Finish makes
 * (`TangoDifficultyMeasurement`): a board the plain Finish gives, which takes about a minute, sits
 * at the top of Easy or in Medium, about half of the dailies are Medium and one in eight is Expert.
 * The other widths keep the same score per cell (1.67, 2.78 and 3.47). A 4×4 board without markers
 * tops out in Medium, and Finish returns that board when asked for more.
 */
object TangoScoreBands {
    private val bands: Map<Int, LevelBands> = mapOf(
        4 to LevelBands(medium = 27, hard = 44, expert = 56),
        6 to LevelBands(medium = 60, hard = 100, expert = 125),
        8 to LevelBands(medium = 107, hard = 178, expert = 222),
        10 to LevelBands(medium = 167, hard = 278, expert = 347),
    )

    /** The bands of an [n] × [n] board; [n] is one of [TangoDraft.SIZES]. */
    fun of(n: Int): LevelBands = requireNotNull(bands[n]) { "no Tango bands for width $n" }
}
