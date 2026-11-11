package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.RankOrder
import kotlinx.coroutines.delay

// Rows move, open and close on the app's default spring, which does not overshoot: a leaderboard does not bounce. One
// spring for all three, so the rows around an opening or closing row keep pace with it.
private val Move = BoundsTransform { _, _ ->
    spring(MotionTokens.SpringDampingDefault, MotionTokens.SpringStiffnessDefault, Rect.VisibilityThreshold)
}

// That spring has all but settled by then, and a row is dropped no sooner.
private const val SETTLE_MILLIS = 400L

// A row fades in as it opens, and out quicker than it closes, so it is gone before the rows meet.
private val FadeIn = tween<Float>(MotionTokens.EnterDuration, easing = MotionTokens.EasingEmphasizedDecelerate)
private val FadeOut = tween<Float>(MotionTokens.DurationShort, easing = MotionTokens.EasingStandard)

/**
 * [DailyBoardList]'s rows with motion: a row whose place changes slides to it, an arriving row fades and expands in,
 * a leaving one fades and shrinks out. [keys] name each of [lines] (the pinned row last in them, when [hasPinned]), so
 * a row is the same row at another place.
 */
@Composable
internal fun AnimatedBoardList(
    keys: List<String>,
    lines: List<DailyBoardLine>,
    hasPinned: Boolean,
    order: RankOrder,
    withPuzzleBoxRank: Boolean,
    modifier: Modifier = Modifier,
) {
    val rows = remember { BoardRows() }
    val items = rows.update(keys, lines, hasPinned)
    if (items.isEmpty()) return
    LookaheadScope {
        Column(modifier.fillMaxWidth().testTag("daily_board.list").withoutLeadingGap(Spacing.sm)) {
            for (item in items) {
                key(item.key) {
                    BoardItemSlot(item, item.line, item.pinned, item.present, rows, order, withPuzzleBoxRank, this@LookaheadScope)
                }
            }
        }
    }
}

@Composable
private fun BoardItemSlot(
    item: BoardItem,
    line: DailyBoardLine,
    pinned: Boolean,
    present: Boolean,
    rows: BoardRows,
    order: RankOrder,
    withPuzzleBoxRank: Boolean,
    scope: LookaheadScope,
) {
    // An item starts closed and opens on the first frame, so its arrival is an opening and not a first sight.
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(present) {
        open = present
        if (!present) {
            delay(SETTLE_MILLIS)
            rows.drop(item)
        }
    }
    val alpha by animateFloatAsState(if (open) 1f else 0f, if (open) FadeIn else FadeOut, label = "boardRowAlpha")
    Box(
        Modifier
            .animateBounds(scope, modifier = Modifier.closedUnless(open), boundsTransform = Move)
            .clipToBounds()
            .graphicsLayer { this.alpha = alpha },
    ) {
        // The gap sits above the item and is part of it, so it opens and closes with the item instead of
        // snapping when the item is dropped.
        Box(Modifier.padding(top = Spacing.sm)) {
            if (pinned && line is DailyBoardLine.Real) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    HorizontalDivider()
                    Box(Modifier.testTag("leaderboard.you")) { BoardLine(line, order, withPuzzleBoxRank) }
                }
            } else {
                BoardLine(line, order, withPuzzleBoxRank)
            }
        }
    }
}

/** One line drawn as every board draws it: a PuzzleBox row or a connection. */
@Composable
internal fun BoardLine(line: DailyBoardLine, order: RankOrder, withPuzzleBoxRank: Boolean) {
    when (line) {
        is DailyBoardLine.Real -> DailyBoardRow(line.row, order, place = line.place, showPuzzleBoxRank = withPuzzleBoxRank)
        is DailyBoardLine.Ghost -> GhostRow(line.row, line.place, order)
    }
}

// Its content is laid out whole but takes no height while closed; the bounds animation grows and shrinks the gap.
private fun Modifier.closedUnless(open: Boolean): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, if (open) placeable.height else 0) { placeable.place(0, 0) }
}

// Every item carries the gap above it, so the first one's is not part of the list.
private fun Modifier.withoutLeadingGap(gap: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val trim = gap.roundToPx().coerceAtMost(placeable.height)
    layout(placeable.width, placeable.height - trim) { placeable.place(0, -trim) }
}
