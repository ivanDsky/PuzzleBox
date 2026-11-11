package com.ivandsky.puzzlebox.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.SummaryLinkRow
import com.ivandsky.puzzlebox.model.GameId

/**
 * What Today shows of content owned elsewhere (ADR-0027): one summary row each, with a
 * chevron, that reports its tap. A null field means that row is not offered.
 * [onOpenRank] gets the game of the rank row; the duels row shows only above zero [duels].
 */
class HomeSummaries(
    val onOpenRank: ((GameId) -> Unit)?,
    val duels: HomeDuelsSummary?,
)

/** The player's place on the board of the game they solved last today; [position] is 1-based. */
internal data class TodayRank(val gameId: GameId, val gameName: String, val position: Int)

/**
 * The summary rows: today's rank when [HomeUiState.rank] is set and [HomeSummaries.onOpenRank]
 * is offered, and the duels waiting on the player when the count is above zero. Emits nothing
 * at all when neither shows, so the list keeps no gap. The rows enter as list item [index].
 */
@Composable
internal fun HomeSummaryRows(state: HomeUiState, summaries: HomeSummaries, index: Int, modifier: Modifier = Modifier) {
    val rank = state.rank
    val onOpenRank = summaries.onOpenRank
    val duelsSummary = summaries.duels
    val duels = duelsSummary?.needYou?.invoke() ?: 0
    val showRank = rank != null && onOpenRank != null
    if (!showRank && duels <= 0) return
    Column(modifier.staggerIn(index), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        if (rank != null && onOpenRank != null) {
            SummaryLinkRow(
                text = "Today's rank: #${rank.position} on ${rank.gameName}",
                modifier = Modifier.testTag("summary.leaderboard"),
                onClick = { onOpenRank(rank.gameId) },
            )
        }
        if (duelsSummary != null && duels > 0) {
            SummaryLinkRow(
                text = if (duels == 1) "1 duel needs you" else "$duels duels need you",
                modifier = Modifier.testTag("summary.duels"),
                onClick = duelsSummary.onOpen,
            )
        }
    }
}
