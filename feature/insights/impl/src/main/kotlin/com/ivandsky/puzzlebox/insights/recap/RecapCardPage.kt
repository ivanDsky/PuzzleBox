package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.model.GameId

/**
 * One card's story page: the one place a [RecapCard] becomes a page. A new card is a new variant, so this `when` stops
 * compiling until it has its page. [periodLabel] names the period; [gameName] is a game's display name;
 * [onWatchReplay] opens the replay of a Fastest solve row that has one.
 */
@Composable
internal fun RecapCardPage(
    card: RecapCard,
    periodLabel: String,
    gameName: (GameId) -> String,
    onWatchReplay: (RecapCard.Fastest.Row) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (card) {
        is RecapCard.Solved -> SolvedCardPage(card, periodLabel, gameName, modifier)
        is RecapCard.Fastest -> FastestCardPage(card, periodLabel, gameName, onWatchReplay, modifier)
        is RecapCard.Streak -> StreakCardPage(card, periodLabel, modifier)
        is RecapCard.HintFree -> HintFreeCardPage(card, periodLabel, modifier)
        is RecapCard.Moments -> MomentsCardPage(card, periodLabel, modifier)
        is RecapCard.TotalTime -> TotalTimeCardPage(card, periodLabel, modifier)
        is RecapCard.Friends -> FriendsCardPage(card, periodLabel, gameName, modifier)
    }
}
