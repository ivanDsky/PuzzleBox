package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.util.lerp
import com.ivandsky.puzzlebox.designsystem.ScrubCell
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.frosted
import kotlin.math.abs

// A part on only one of the two boards frosts out over the first half of the way and in over the second.
private const val Half = 0.5f

/** The parts of the two boards of [morph], each as [BoardFace.parts] gives them, and how far from the one to the other the board is. */
internal class MorphParts(morph: BoardMorph, isToday: Boolean) {
    val from: List<BoardPart> = morph.from.parts(isToday)
    val to: List<BoardPart> = morph.to.parts(isToday)
    val progress: () -> Float = morph.progress
}

/** Frosts out what only the board being left has, over the first half of the way to the other. */
internal fun Modifier.frostedOut(progress: () -> Float): Modifier = frosted { 1f - progress() / Half }

// One part of the two boards drawn as one: its place in each board's order, -1 on a board it is not on. [part] is the
// arriving board's when it is on both, and [was] the one it was on the board it leaves.
private class ScrubItem(val part: BoardPart, val was: BoardPart?, val from: Int, val to: Int) {
    val both: Boolean get() = from >= 0 && to >= 0
}

// The parts of both boards, the arriving board's first, with the order each board stacks them in and where each sits
// on either board (kept here so a frame of the scrub allocates nothing).
private class ScrubItems(from: List<BoardPart>, to: List<BoardPart>) {
    val all: List<ScrubItem>
    val fromOrder: IntArray
    val toOrder: IntArray
    val fromY: IntArray
    val toY: IntArray

    init {
        val before = HashMap<String, Int>(from.size)
        from.forEachIndexed { index, part -> before[part.key] = index }
        val arriving = to.mapIndexed { index, part ->
            val at = before[part.key] ?: -1
            ScrubItem(part, from.getOrNull(at), at, index)
        }
        val kept = arriving.mapTo(HashSet()) { it.part.key }
        val leaving = from.mapIndexedNotNull { index, part -> if (part.key in kept) null else ScrubItem(part, null, index, -1) }
        all = arriving + leaving
        fromOrder = all.indices.filter { all[it].from >= 0 }.sortedBy { all[it].from }.toIntArray()
        toOrder = all.indices.filter { all[it].to >= 0 }.sortedBy { all[it].to }.toIntArray()
        fromY = IntArray(all.size)
        toY = IntArray(all.size)
    }

    /** Stacks each board's parts under each other and returns the two boards' heights; a part on one board stays where that board has it. */
    fun stack(placeables: List<Placeable>, gap: Int): Pair<Int, Int> {
        val fromHeight = stack(fromOrder, fromY, placeables, gap)
        val toHeight = stack(toOrder, toY, placeables, gap)
        all.forEachIndexed { index, item ->
            if (item.from < 0) fromY[index] = toY[index] else if (item.to < 0) toY[index] = fromY[index]
        }
        return fromHeight to toHeight
    }

    // Every part carries the gap above it, so the first one's is not part of the list.
    private fun stack(order: IntArray, ys: IntArray, placeables: List<Placeable>, gap: Int): Int {
        var y = -gap
        for (index in order) {
            ys[index] = y
            y += placeables[index].height
        }
        return y.coerceAtLeast(0)
    }
}

/**
 * The two boards of a morph, [parts], drawn as one list, as far from the one to the other as it says (see [BoardMorph]).
 * A part on both is one item: it travels from where the one board has it to where the other does, over the parts it
 * passes, and a row among them rolls its place and frosts its other cells over on the way (see [RowScrub]). A part on
 * only one board stays where that board has it and frosts out over the first half of the way or in over the second.
 * The list is as tall as the board it is nearer to, in proportion.
 *
 * The progress is read where the list is laid out and drawn only: a frame of the scrub moves layers and, when the two
 * boards differ in height, re-lays the list out without measuring a row again. Nothing is recomposed and nothing is
 * clipped. The list says nothing to assistive tech while it is between two boards.
 */
@Composable
internal fun ScrubbedBoardList(parts: MorphParts, medals: Boolean, backdrop: Color, modifier: Modifier = Modifier) {
    val items = remember(parts) { ScrubItems(parts.from, parts.to) }
    val progress = parts.progress
    Layout(
        content = {
            // No row here reports to the dock, and none rolls or counts on its own clock: the scrub is the clock.
            CompositionLocalProvider(
                LocalSelfRowDock provides null,
                LocalBoardBackdrop provides backdrop,
                LocalPlaceMotion provides StillPlaces,
            ) {
                for (item in items.all) {
                    key(item.part.key) { ScrubSlot(item, medals, progress) }
                }
            }
        },
        modifier = modifier.fillMaxWidth().testTag("daily_board.list").clearAndSetSemantics {},
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
        val (fromHeight, toHeight) = items.stack(placeables, Spacing.sm.roundToPx())
        val height = if (fromHeight == toHeight) toHeight else lerp(fromHeight, toHeight, progress())
        layout(constraints.maxWidth, height) {
            placeables.forEachIndexed { index, placeable ->
                val start = items.fromY[index]
                val travel = items.toY[index] - start
                // A part on its way is drawn over the ones it passes, the further it goes the higher; one that frosts is under them all.
                val depth = if (!items.all[index].both) -1f else if (travel != 0) 1f + abs(travel) else 0f
                if (travel == 0) {
                    placeable.place(0, start, depth)
                } else {
                    placeable.placeWithLayer(0, start, depth) { translationY = travel * progress() }
                }
            }
        }
    }
}

@Composable
private fun ScrubSlot(item: ScrubItem, medals: Boolean, progress: () -> Float) {
    val frost = when {
        item.both -> Modifier
        item.to >= 0 -> Modifier.frosted { (progress() - Half) / Half }
        else -> Modifier.frostedOut(progress)
    }
    Box(frost.padding(top = Spacing.sm)) {
        when (val part = item.part) {
            is BoardPart.Row -> {
                val old = item.was as? BoardPart.Row
                val scrub = remember(old, medals, progress) {
                    old?.let { RowScrub(it.line.cells(it.order, it.withPuzzleBoxRank, medals), progress) }
                }
                CompositionLocalProvider(LocalRowScrub provides scrub) {
                    BoardLine(part.line, part.order, part.withPuzzleBoxRank, medals)
                }
            }
            BoardPart.Divider -> HorizontalDivider()
            is BoardPart.Message -> ScrubCell((item.was as? BoardPart.Message)?.text ?: part.text, part.text, progress) {
                BoardMessage(it, part.tag)
            }
        }
    }
}
