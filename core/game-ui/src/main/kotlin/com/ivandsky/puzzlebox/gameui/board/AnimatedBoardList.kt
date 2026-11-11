package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.RankOrder
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Rows move, open and close on the app's default spring, which does not overshoot: a leaderboard does not bounce. One
// spring for all three, so the rows around an opening or closing row keep pace with it.
internal object BoardMotion {
    // A slide is in pixels, an opening is a fraction of the row's height: each settles at a distance nobody sees.
    val slide = spring<Float>(MotionTokens.SpringDampingDefault, MotionTokens.SpringStiffnessDefault, visibilityThreshold = 0.5f)
    val open = spring<Float>(MotionTokens.SpringDampingDefault, MotionTokens.SpringStiffnessDefault, visibilityThreshold = 0.002f)

    // A row fades in as it opens, and out quicker than it closes, so it is gone before the rows meet.
    val fadeIn = tween<Float>(MotionTokens.EnterDuration, easing = MotionTokens.EasingEmphasizedDecelerate)
    val fadeOut = tween<Float>(MotionTokens.DurationShort, easing = MotionTokens.EasingStandard)
}

/**
 * [DailyBoardList]'s rows with motion: a row that changes its place in the list slides to it, an arriving row fades
 * and opens in, a leaving one fades and closes out. A row is the same row by its key (see [boardKeys]), on another
 * day too, so people on two days' boards slide and update in place while the others arrive and leave. The [pinned]
 * own row sits under a divider that is an item of its own.
 *
 * The list is laid out in its final places at once, so nothing but a slide or an opening re-lays it out: a slide only
 * moves the drawn row by a layer offset, a fade only changes its layer alpha, and the rows' content is not recomposed
 * by either.
 */
@Composable
internal fun AnimatedBoardList(
    lines: List<DailyBoardLine>,
    pinned: DailyBoardLine.Real?,
    order: RankOrder,
    withPuzzleBoxRank: Boolean,
    modifier: Modifier = Modifier,
) {
    val rows = remember { BoardRows() }
    val items = rows.update(lines, pinned)
    if (items.isEmpty()) return
    val scope = rememberCoroutineScope()
    Layout(
        content = {
            for (item in items) {
                key(item.key) { BoardItemSlot(item, item.line, item.pinned, item.present, rows, order, withPuzzleBoxRank) }
            }
        },
        modifier = modifier.fillMaxWidth().testTag("daily_board.list").withoutLeadingGap(Spacing.sm),
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
        layout(constraints.maxWidth, placeables.sumOf { it.height }) {
            var y = 0
            placeables.forEachIndexed { index, placeable ->
                items[index].placeAt(y, scope)
                placeable.place(0, y)
                y += placeable.height
            }
        }
    }
}

@Composable
private fun BoardItemSlot(
    item: BoardItem,
    line: DailyBoardLine?,
    pinned: Boolean,
    present: Boolean,
    rows: BoardRows,
    order: RankOrder,
    withPuzzleBoxRank: Boolean,
) {
    // An item starts closed and invisible and opens on the first frame, so its arrival is an opening and not a first sight.
    LaunchedEffect(present) {
        val target = if (present) 1f else 0f
        launch { item.alpha.animateTo(target, if (present) BoardMotion.fadeIn else BoardMotion.fadeOut) }
        item.open.animateTo(target, BoardMotion.open)
        if (!present) rows.drop(item)
    }
    Box(
        Modifier
            .graphicsLayer {
                alpha = item.alpha.value
                translationY = item.slide.value
            }
            .heightOpenedBy { item.open.value }
            .clipToBounds(),
    ) {
        // The gap sits above the item and is part of it, so it opens and closes with the item instead of
        // snapping when the item is dropped.
        Box(Modifier.padding(top = Spacing.sm)) {
            when {
                line == null -> HorizontalDivider()
                pinned -> Box(Modifier.testTag("leaderboard.you")) { BoardLine(line, order, withPuzzleBoxRank) }
                else -> BoardLine(line, order, withPuzzleBoxRank)
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

// Its content is laid out whole but takes only [fraction] of its height: the list grows and shrinks around it as it opens and closes.
private fun Modifier.heightOpenedBy(fraction: () -> Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, (placeable.height * fraction()).roundToInt()) { placeable.place(0, 0) }
}

// Every item carries the gap above it, so the first one's is not part of the list.
private fun Modifier.withoutLeadingGap(gap: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val trim = gap.roundToPx().coerceAtMost(placeable.height)
    layout(placeable.width, placeable.height - trim) { placeable.place(0, -trim) }
}
