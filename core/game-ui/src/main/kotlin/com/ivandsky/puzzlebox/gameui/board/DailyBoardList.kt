package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.game.RankOrder

private val DividerHeight = 1.dp

/**
 * [lines] as the board's rows, [Spacing.sm] apart, with the player's own [pinned] row set apart under them by a
 * divider when it is outside them. One list for every board, so the game page and the play screen's times card draw
 * the same rows. A list whose places differ from the PuzzleBox ranks (connections among the rows, or a row at a
 * LinkedIn time) names each row's PuzzleBox rank.
 *
 * With [animated] the top three places are medals, and a row whose place changes glides to it while its place number
 * rolls, one after another, an arriving row fades and expands in and a leaving one fades and shrinks out, the pinned
 * row and its divider too; under reduced motion every change lands at once, the final place and its medal included. Rows are the
 * same rows by their player or connection, never by place or day: on another day's board the people on both days
 * slide to their new places and update in place, the others arrive and leave. Animated rows are opaque, on [backdrop],
 * the colour the list sits on. Without [animated] the list is drawn plainly. A [settled] list (a saved board: its rows
 * are final and arrive in one order) never moves: it is drawn as under reduced motion, rows at their final places and
 * the podium's medals included.
 */
@Composable
fun DailyBoardList(
    lines: List<DailyBoardLine>,
    order: RankOrder,
    modifier: Modifier = Modifier,
    pinned: DailyBoardLine.Real? = null,
    animated: Boolean = false,
    settled: Boolean = false,
    backdrop: Color = MaterialTheme.colorScheme.background,
) {
    val withPuzzleBoxRank = (lines + listOfNotNull(pinned)).placesDifferFromPuzzleBox()
    if (animated && !settled && !(rememberReducedMotion() || LocalInspectionMode.current)) {
        AnimatedBoardList(lines, pinned, order, withPuzzleBoxRank, backdrop, modifier)
        return
    }
    if (lines.isEmpty() && pinned == null) return
    // The podium's medals belong to the LinkedIn account's board; here they are drawn at once, with no roll or flip.
    CompositionLocalProvider(LocalPlaceMotion provides if (animated) StillPlaces else null) {
        Column(modifier.fillMaxWidth().testTag("daily_board.list"), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            lines.forEach { BoardLine(it, order, withPuzzleBoxRank) }
            pinned?.let {
                HorizontalDivider()
                Box(Modifier.testTag("leaderboard.you")) { BoardLine(it, order, withPuzzleBoxRank) }
            }
        }
    }
}

/** The height of [rows] lines of [DailyBoardList], plus a divider and a [pinned] row under them: what a caller reserves. */
fun dailyBoardListHeight(rows: Int, pinned: Boolean): Dp {
    val drawn = rows + if (pinned) 1 else 0
    val gaps = (drawn - 1).coerceAtLeast(0) + if (pinned) 1 else 0
    return BoardRowMinHeight * drawn + Spacing.sm * gaps + if (pinned) DividerHeight else 0.dp
}
