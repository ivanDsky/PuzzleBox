package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.rankRuleText

/** The board body: [title] ("<Game> · today", A15), the [order]'s rule caption, and the one [state] drawn. [signIn] fills the signed-out state. */
@Composable
fun DailyBoard(
    title: String,
    order: RankOrder,
    state: DailyBoardState,
    modifier: Modifier = Modifier,
    signIn: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("daily_board.title"))
        Text(
            rankRuleText(order),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("daily_board.caption"),
        )
        when (state) {
            DailyBoardState.Loading -> Line("Loading…", "daily_board.loading")
            DailyBoardState.Unavailable -> Line("Leaderboard unavailable right now.", "daily_board.unavailable")
            DailyBoardState.SignedOut -> signIn?.invoke()
            is DailyBoardState.Ranked -> Ranked(state, order)
        }
    }
}

@Composable
private fun Ranked(state: DailyBoardState.Ranked, order: RankOrder) {
    if (state.rows.isEmpty()) {
        Line("No one is on today's board yet.", "daily_board.empty")
    } else {
        state.rows.forEach { DailyBoardRow(it, order) }
    }
    state.note?.let { Line(it.text, "daily_board.note") }
}

@Composable
private fun Line(text: String, tag: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(tag),
    )
}
