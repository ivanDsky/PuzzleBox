package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.model.GameId

/**
 * One card's story page: the one place a [RecapCard] becomes a page. A new card is a new variant, so this `when` stops
 * compiling until it has its page. [periodLabel] names the period; [gameName] is a game's display name;
 * [onWatchReplay] opens the replay of a Fastest solve row that has one; a month card shows its [monthPlace], and the year
 * finale its [displayName] and an [onShare] that moves on to the Share page.
 */
@Composable
internal fun RecapCardPage(
    card: RecapCard,
    periodLabel: String,
    gameName: (GameId) -> String,
    onWatchReplay: (RecapCard.Fastest.Row) -> Unit,
    modifier: Modifier = Modifier,
    monthPlace: MonthPlace? = null,
    displayName: String? = null,
    onShare: () -> Unit = {},
) {
    when (card) {
        is RecapCard.Solved -> SolvedCardPage(card, periodLabel, gameName, modifier)
        is RecapCard.Month -> MonthCardPage(card, periodLabel, gameName, modifier, monthPlace)
        is RecapCard.Fastest -> FastestCardPage(card, periodLabel, gameName, onWatchReplay, modifier)
        is RecapCard.Streak -> StreakCardPage(card, periodLabel, modifier)
        is RecapCard.Moments -> MomentsCardPage(card, periodLabel, gameName, modifier)
        is RecapCard.TotalTime -> TotalTimeCardPage(card, periodLabel, modifier)
        is RecapCard.Friends -> FriendsCardPage(card, periodLabel, gameName, modifier)
        is RecapCard.YearFinale -> YearFinalePage(card, periodLabel, gameName, displayName, onShare, modifier)
    }
}
