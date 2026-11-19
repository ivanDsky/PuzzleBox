package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.model.GameId

/**
 * One card's story page: the one place a [RecapCard] becomes a page. A new card is a new variant, so this `when` stops
 * compiling until it has its page. [periodLabel] names the period; [gameName] is a game's display name.
 */
@Composable
internal fun RecapCardPage(
    card: RecapCard,
    periodLabel: String,
    gameName: (GameId) -> String,
    modifier: Modifier = Modifier,
) {
    when (card) {
        is RecapCard.Solved -> SolvedCardPage(card, periodLabel, gameName, modifier)
    }
}
