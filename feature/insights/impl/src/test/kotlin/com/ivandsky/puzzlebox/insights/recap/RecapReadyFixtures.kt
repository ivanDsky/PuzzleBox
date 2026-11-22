package com.ivandsky.puzzlebox.insights.recap

import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard

private val Ready = RecapUiState.Ready(
    period = RecapFixtures.november, title = "November 2026", cards = listOf(RecapFixtures.solved, RecapFixtures.secondSolved), initialPage = 0,
)

internal val RecapFixtures.ready: RecapUiState.Ready get() = Ready

/** A ready recap over [cards], opening on the first. */
internal fun readyOf(vararg cards: RecapCard) =
    RecapUiState.Ready(period = RecapFixtures.november, title = "November 2026", cards = cards.toList(), initialPage = 0)

/** A ready yearly recap titled [title] ("2026 so far", "2025") over [cards], opening on the first. */
internal fun readyYear(title: String, vararg cards: RecapCard) =
    RecapUiState.Ready(period = RecapFixtures.year2026, title = title, cards = cards.toList(), initialPage = 0)

/** This story opened on the first page of the card at [card], the Share page at `cards.size`; Fastest has a page per game. */
internal fun RecapUiState.Ready.onCard(card: Int) = copy(initialPage = pagesOf(cards, share = true).indexOfFirst { it.cardIndex == card })
