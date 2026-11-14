package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.DayMorph
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.rankRuleText

private const val MaxGhosts = 20

/**
 * The board body: [title] ("<Game> · today"), the [caption] (the [order]'s rule unless a board says otherwise), and the one [state] drawn.
 * [ghosts] are LinkedIn connections drawn among the rows (or above [signIn] when signed out), each with its place in
 * one combined rank; a board with ghosts also tells each PuzzleBox row's own rank. [signIn] fills the signed-out state.
 * With [animated] the rows move, arrive and leave with motion (see [DailyBoardList]), on a change of day too, whether
 * the day's board is live or a saved one.
 * [isToday] says whether the board is today's, so an empty one names the day it shows.
 * [medals] makes the top three places medal rows and [dock] docks the viewer's own row to the edge of what scrolls the
 * board while it is out of view (see [DailyBoardList]). With [onRowClick] another player's official row is tappable and
 * reports its row; a board that passes none has rows that take no tap. With [morphText] a new [title] or [caption]
 * crossfades over the old one ([DayMorph]) instead of replacing it. [backdrop] is the colour every row that is not
 * the viewer's own is filled with, for a board that sits on something other than the theme's background; it may be
 * see-through. Null leaves the rows as the list draws them.
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
    isToday: Boolean = true,
    caption: String = rankRuleText(order),
    medals: Boolean = false,
    dock: Boolean = false,
    onRowClick: ((DailyBoardRowModel) -> Unit)? = null,
    morphText: Boolean = false,
    backdrop: Color? = null,
) {
    val rowsOn = backdrop ?: MaterialTheme.colorScheme.background
    CompositionLocalProvider(LocalBoardRowClick provides onRowClick, LocalBoardBackdrop provides (backdrop ?: LocalBoardBackdrop.current)) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (morphText) DayMorph(title) { Title(it) } else Title(title)
            if (morphText) DayMorph(caption) { Caption(it) } else Caption(caption)
            when (state) {
                DailyBoardState.Loading -> Line("Loading…", "daily_board.loading")
                DailyBoardState.Unavailable -> Line("Leaderboard unavailable right now.", "daily_board.unavailable")
                DailyBoardState.SignedOut -> {
                    DailyBoardList(rankWithGhosts(emptyList(), ghosts, order), order, animated = animated, backdrop = rowsOn, medals = medals, dock = dock)
                    signIn?.invoke()
                }
                is DailyBoardState.Ranked -> Ranked(state, ghosts, order, animated, isToday, medals, dock, rowsOn)
            }
        }
    }
}

@Composable
private fun Title(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("daily_board.title"))
}

@Composable
private fun Caption(caption: String) {
    Text(
        caption,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("daily_board.caption"),
    )
}

@Composable
private fun Ranked(
    state: DailyBoardState.Ranked,
    ghosts: List<GhostRowModel>,
    order: RankOrder,
    animated: Boolean,
    isToday: Boolean,
    medals: Boolean,
    dock: Boolean,
    backdrop: Color,
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
        backdrop = backdrop,
        medals = medals,
        dock = dock,
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
