package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.rankRuleText

private const val MaxGhosts = 20

/**
 * The board body: [title] ("<Game> · today"), the [order]'s rule caption, and the one [state] drawn.
 * [ghosts] are LinkedIn connections drawn among the rows (or above [signIn] when signed out), each with its place in
 * one combined rank; a board with ghosts also tells each PuzzleBox row's own rank. [signIn] fills the signed-out state.
 * With [animated] the rows move, arrive and leave with motion (see [DailyBoardList]), on a change of day too, unless
 * the board is [settled] (a saved board), whose rows are drawn at their final places and never move.
 * [isToday] says whether the board is today's, so an empty one names the day it shows.
 */
@Composable
fun DailyBoard(
    title: String,
    order: RankOrder,
    state: DailyBoardState,
    modifier: Modifier = Modifier,
    ghosts: List<GhostRowModel> = emptyList(),
    signIn: (@Composable () -> Unit)? = null,
    animated: Boolean = false,
    settled: Boolean = false,
    isToday: Boolean = true,
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
                DailyBoardList(rankWithGhosts(emptyList(), ghosts, order), order, animated = animated, settled = settled)
                signIn?.invoke()
            }
            is DailyBoardState.Ranked -> Ranked(state, ghosts, order, animated, settled, isToday)
        }
    }
}

@Composable
private fun Ranked(
    state: DailyBoardState.Ranked,
    ghosts: List<GhostRowModel>,
    order: RankOrder,
    animated: Boolean,
    settled: Boolean,
    isToday: Boolean,
) {
    val lines = remember(state, ghosts, order) { (state.lines ?: rankWithGhosts(state.rows, ghosts, order)).capGhosts() }
    if (lines.isEmpty()) {
        val message = if (isToday) "No one is on today's board yet." else "No one is on this day's board yet."
        if (animated) FadingLine(message, "daily_board.empty") else Line(message, "daily_board.empty")
    }
    DailyBoardList(
        lines,
        order,
        pinned = state.pinned?.let { DailyBoardLine.Real(it, state.pinnedPlace ?: it.rank) },
        animated = animated,
        settled = settled,
    )
    state.note?.let { Line(it.text, "daily_board.note") }
}

// Cut by merge order so the fastest ghosts stay and hidden times go first; real rows are never dropped.
private fun List<DailyBoardLine>.capGhosts(): List<DailyBoardLine> {
    var ghosts = 0
    return filter { it is DailyBoardLine.Real || ++ghosts <= MaxGhosts }
}

@Composable
private fun Line(text: String, tag: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.testTag(tag),
    )
}

// The message of an emptied board fades in once the rows that left are gone; reduced motion shows it at once.
@Composable
private fun FadingLine(text: String, tag: String) {
    val instant = rememberReducedMotion() || LocalInspectionMode.current
    val alpha = remember { Animatable(if (instant) 1f else 0f) }
    LaunchedEffect(instant) { if (!instant) alpha.animateTo(1f, BoardMotion.messageIn) }
    Line(text, tag, Modifier.graphicsLayer { this.alpha = alpha.value })
}
