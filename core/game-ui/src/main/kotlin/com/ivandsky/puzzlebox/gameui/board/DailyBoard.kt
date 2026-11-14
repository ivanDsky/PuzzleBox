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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.MorphCell
import com.ivandsky.puzzlebox.designsystem.ScrubCell
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.rankRuleText

// Whether the board was between two boards when it was last composed: the rows it lands on are already on screen.
private class Handover(var scrubbed: Boolean = false)

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
 * frosts over the old one ([MorphCell]) instead of replacing it. [backdrop] is the colour every row that is not
 * the viewer's own is filled with, for a board that sits on something other than the theme's background; it may be
 * see-through. Null leaves the rows as the list draws them. With [inlineMarks] a row's mark and detail are chips
 * beside its name instead of small lines under it (see [DailyBoardList]).
 *
 * While there is a [morph] an [animated] board draws the two boards it is between as one, as far along as the morph
 * says, instead of [state]: the rows on both travel and change in place, the rest frost in and out (see [BoardMorph]).
 * When the morph is gone the board lands on [state] with no motion of its own. Reduced motion ignores the morph.
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
    morph: BoardMorph? = null,
    inlineMarks: Boolean = false,
) {
    val rowsOn = backdrop ?: MaterialTheme.colorScheme.background
    val still = rememberReducedMotion() || LocalInspectionMode.current
    val scrub = morph.takeIf { animated && !still }
    val handover = remember { Handover() }
    val landed = scrub == null && handover.scrubbed
    SideEffect { handover.scrubbed = scrub != null }
    CompositionLocalProvider(
        LocalBoardRowClick provides onRowClick,
        LocalBoardBackdrop provides (backdrop ?: LocalBoardBackdrop.current),
        LocalInlineMarks provides inlineMarks,
    ) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            BoardText(title, scrub, BoardFace::title, morphText) { Title(it) }
            BoardText(caption, scrub, BoardFace::caption, morphText) { Caption(it) }
            val lines = remember(state, ghosts, order) { boardLines(state, ghosts, order) }
            // Between two boards the message and the note are parts of the one list, so they frost with the rows.
            if (scrub == null) {
                boardMessage(state, lines.isEmpty(), isToday)?.let {
                    if (animated && state is DailyBoardState.Ranked) FadingLine(it.text, it.tag, landed) else BoardMessage(it.text, it.tag)
                }
            }
            // An empty board's message is worded for the day, so the two boards' parts are cut here, once a morph.
            val parts = if (scrub == null) null else remember(scrub, isToday) { MorphParts(scrub, isToday) }
            if (parts != null || state is DailyBoardState.Ranked || state == DailyBoardState.SignedOut) {
                BoardList(
                    lines,
                    order,
                    pinned = pinnedLine(state),
                    animated = animated,
                    backdrop = rowsOn,
                    medals = medals,
                    dock = dock,
                    landed = landed,
                    scrub = parts,
                )
            }
            if (state == DailyBoardState.SignedOut) signIn?.invoke()
            if (scrub == null) (state as? DailyBoardState.Ranked)?.note?.let { BoardMessage(it.text, "daily_board.note") }
        }
    }
}

// A line of the board's text: scrubbed between the two boards' while there is a morph, frosting to a new value with morphText.
@Composable
private fun BoardText(value: String, morph: BoardMorph?, of: (BoardFace) -> String, morphText: Boolean, content: @Composable (String) -> Unit) {
    when {
        morph != null -> ScrubCell(of(morph.from), of(morph.to), morph.progress, content = content)
        morphText -> MorphCell(value, content = content)
        else -> content(value)
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

/** A line the board says under its caption: that it is loading, unavailable or empty, or the note under its rows. */
@Composable
internal fun BoardMessage(text: String, tag: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.testTag(tag),
    )
}

// The message of an emptied board fades in once the rows that left are gone; reduced motion shows it at once, and so
// does a board that has just landed on it from between two boards, where it was already drawn.
@Composable
private fun FadingLine(text: String, tag: String, landed: Boolean) {
    val instant = rememberReducedMotion() || LocalInspectionMode.current
    val alpha = remember { Animatable(if (instant || landed) 1f else 0f) }
    LaunchedEffect(instant) { if (!instant) alpha.animateTo(1f, BoardMotion.messageIn) }
    BoardMessage(text, tag, Modifier.graphicsLayer { this.alpha = alpha.value })
}
