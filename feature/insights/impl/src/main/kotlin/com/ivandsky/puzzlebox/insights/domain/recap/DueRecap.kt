package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.model.GameId

/**
 * The recap Today offers: [periodId] is what the recap's navigation key and a dismissal carry, [label] what the player
 * reads ("October 2026", "2026"). [yearly] says which release flag keeps it dark: the yearly or the monthly recap.
 * [solved] is the period's counted solves, [solvedByGame] each game's share of them and, for a year, [months] the count
 * of each month; the tile draws its number and art from them.
 */
data class DueRecap(
    val periodId: String,
    val label: String,
    val yearly: Boolean,
    val solved: Int = 0,
    val solvedByGame: List<Pair<GameId, Int>> = emptyList(),
    val months: List<Int> = emptyList(),
)
