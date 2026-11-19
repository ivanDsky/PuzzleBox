package com.ivandsky.puzzlebox.insights.domain.recap

/** The recap Today offers: [periodId] is what the recap's navigation key and a dismissal carry, [label] what the player reads ("October 2026"). */
data class DueRecap(val periodId: String, val label: String)
