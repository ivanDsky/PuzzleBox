package com.ivandsky.puzzlebox.insights.recap

import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard

/**
 * One page of the recap story: the card at [cardIndex] (the Share page sits at `cards.size`, past the last card) and
 * which part of it the page shows, [sub]. Fastest gives each game its own page, [sub] being its row; every other card is
 * one page with [sub] 0.
 */
internal data class RecapPage(val cardIndex: Int, val sub: Int = 0)

/** The story's pages in order: each card's (none for a Fastest card without games), then the Share page with [share]. */
internal fun pagesOf(cards: List<RecapCard>, share: Boolean): List<RecapPage> = buildList {
    cards.forEachIndexed { index, card ->
        if (card is RecapCard.Fastest) card.rows.indices.forEach { add(RecapPage(index, it)) } else add(RecapPage(index))
    }
    if (share) add(RecapPage(cards.size))
}
