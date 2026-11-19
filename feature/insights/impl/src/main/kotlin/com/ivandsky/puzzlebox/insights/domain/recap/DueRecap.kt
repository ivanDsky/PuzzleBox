package com.ivandsky.puzzlebox.insights.domain.recap

/**
 * The recap Today offers: [periodId] is what the recap's navigation key and a dismissal carry, [label] what the player
 * reads ("October 2026", "2026"). [yearly] says which release flag keeps it dark: the yearly or the monthly recap.
 */
data class DueRecap(val periodId: String, val label: String, val yearly: Boolean)
