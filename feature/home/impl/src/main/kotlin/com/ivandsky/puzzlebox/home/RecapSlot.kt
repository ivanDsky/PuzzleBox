package com.ivandsky.puzzlebox.home

import com.ivandsky.puzzlebox.model.GameId

/**
 * What Today offers for a recap, built where recaps are known (the app) and hosted here as plain values: Home never
 * names an insights type. A null slot is no tile and reserves no space.
 */
sealed interface RecapSlot {

    /** A due recap is still loading inside a recap window: Home holds its place with a skeleton. */
    data object Loading : RecapSlot

    /**
     * The recap Today offers. [periodId] names it (`YYYY-MM`, `YYYY`) and [label] is the period as the player reads it
     * ("October 2026"); [yearly] draws a year. [solved] is its counted solves, [solvedByGame] each game's share of them
     * and [months] a year's count for each month. [opened]: the story was watched. [onOpen] opens the recap and
     * [onDismiss] hides its tile for that period only. [liftKey] is the nav key [onOpen] pushes, opaque here: the story
     * grows out of the tile it marks.
     */
    class Due(
        val periodId: String,
        val label: String,
        val yearly: Boolean,
        val solved: Int,
        val solvedByGame: List<Pair<GameId, Int>>,
        val months: List<Int>,
        val opened: Boolean,
        val onOpen: () -> Unit,
        val onDismiss: () -> Unit,
        val liftKey: Any,
    ) : RecapSlot
}
