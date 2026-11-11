package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.rankRuleText

private const val MaxGhosts = 20

/**
 * The board body: [title] ("<Game> · today"), the [order]'s rule caption, and the one [state] drawn.
 * [ghosts] are LinkedIn connections drawn unranked among the rows (or above [signIn] when signed out); [signIn] fills the signed-out state.
 */
@Composable
fun DailyBoard(
    title: String,
    order: RankOrder,
    state: DailyBoardState,
    modifier: Modifier = Modifier,
    ghosts: List<GhostRowModel> = emptyList(),
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
            DailyBoardState.SignedOut -> {
                Lines(emptyList(), ghosts, order)
                signIn?.invoke()
            }
            is DailyBoardState.Ranked -> Ranked(state, ghosts, order)
        }
    }
}

@Composable
private fun Ranked(state: DailyBoardState.Ranked, ghosts: List<GhostRowModel>, order: RankOrder) {
    if (state.rows.isEmpty() && ghosts.isEmpty()) Line("No one is on today's board yet.", "daily_board.empty")
    Lines(state.rows, ghosts, order)
    state.pinned?.let {
        HorizontalDivider()
        Box(Modifier.testTag("leaderboard.you")) { DailyBoardRow(it, order) }
    }
    state.note?.let { Line(it.text, "daily_board.note") }
}

@Composable
private fun Lines(rows: List<DailyBoardRowModel>, ghosts: List<GhostRowModel>, order: RankOrder) {
    val lines = remember(rows, ghosts, order) { mergeGhosts(rows, ghosts, order).capGhosts() }
    lines.forEach {
        when (it) {
            is DailyBoardLine.Real -> DailyBoardRow(it.row, order)
            is DailyBoardLine.Ghost -> GhostRow(it.row, order)
        }
    }
}

// Cut by merge order so the fastest ghosts stay and hidden times go first; real rows are never dropped.
private fun List<DailyBoardLine>.capGhosts(): List<DailyBoardLine> {
    var ghosts = 0
    return filter { it is DailyBoardLine.Real || ++ghosts <= MaxGhosts }
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
