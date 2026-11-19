package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.model.GameId

/** What every recap fact provider reads: the period's [facts] and the app's [games] in the order a card lists them. */
internal data class RecapInput(
    val facts: PeriodFacts,
    val games: List<GameId>,
)
