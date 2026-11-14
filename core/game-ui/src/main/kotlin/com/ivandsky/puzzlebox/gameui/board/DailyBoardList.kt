package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
 * [style] says how it looks and moves (see [DailyBoardStyle]). With motion the top three places are medals, and a
 * row whose place changes glides to it while its place number rolls, one after another, an arriving row fades and
 * expands in and a leaving one fades and shrinks out, the pinned row and its divider too; under reduced motion every
 * change lands at once, the final place and its medal included. Rows are the same rows by their player or
 * connection, never by place or day: on another day's board the people on both days slide to their new places and
 * update in place, the others arrive and leave. A list alone is never between two boards, so a morphing style moves
 * it as an animated one.
 */
@Composable
fun DailyBoardList(
    lines: List<DailyBoardLine>,
    order: RankOrder,
    modifier: Modifier = Modifier,
    pinned: DailyBoardLine.Real? = null,
    style: DailyBoardStyle = DailyBoardStyle.defaults(),
) {
    BoardList(lines, order, style, modifier, pinned)
}

/**
 * [DailyBoardList], and what [DailyBoard] needs of it besides: a list that has just [landed] from between two boards
 * shows its rows at once, for they are already on screen, and while the board is between two ([scrub]) it draws the
 * two as one in the list's place instead of [lines] (see [ScrubbedBoardList]), and its dock is the viewer's row on its
 * way from the one to the other (see [DockScrub]).
 */
@Composable
internal fun BoardList(
    lines: List<DailyBoardLine>,
    order: RankOrder,
    style: DailyBoardStyle,
    modifier: Modifier = Modifier,
    pinned: DailyBoardLine.Real? = null,
    landed: Boolean = false,
    scrub: MorphParts? = null,
) {
    val animated = style.motion != DailyBoardStyle.Motion.Static
    val medals = style.medals
    val withPuzzleBoxRank = (lines + listOfNotNull(pinned)).placesDifferFromPuzzleBox()
    val self = if (scrub != null) null else pinned ?: lines.firstNotNullOfOrNull { line -> (line as? DailyBoardLine.Real)?.takeIf { it.row.isSelf } }
    val dockScrub = if (scrub != null && style.dock) remember(scrub, medals) { scrub.dock(medals) } else null
    DockedSelfRow(style.dock, self, order, withPuzzleBoxRank, medals, animated, style.backdrop, style.inlineMarks, modifier, dockScrub) { boardModifier ->
        if (scrub != null) {
            ScrubbedBoardList(scrub, medals, style.backdrop, boardModifier, style.inlineMarks)
        } else if (animated && !(rememberReducedMotion() || LocalInspectionMode.current)) {
            AnimatedBoardList(lines, pinned, order, withPuzzleBoxRank, style.backdrop, boardModifier, medals, style.inlineMarks, landed)
        } else if (lines.isNotEmpty() || pinned != null) {
            // The podium's medals belong to the LinkedIn account's board; here they are drawn at once, with no roll or flip.
            CompositionLocalProvider(LocalPlaceMotion provides if (animated) StillPlaces else null) {
                // Rows at rest never overlap, so they need no fill: one is a look the style asks for.
                val backdrop = style.backdrop.takeIf { style.filledRows }
                Column(
                    boardModifier.fillMaxWidth().testTag("daily_board.list"),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    lines.forEach { BoardSlot(backdrop) { BoardLine(it, order, withPuzzleBoxRank, medals, style.inlineMarks) } }
                    pinned?.let {
                        HorizontalDivider()
                        BoardSlot(backdrop, you = true) { BoardLine(it, order, withPuzzleBoxRank, medals, style.inlineMarks) }
                    }
                }
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
