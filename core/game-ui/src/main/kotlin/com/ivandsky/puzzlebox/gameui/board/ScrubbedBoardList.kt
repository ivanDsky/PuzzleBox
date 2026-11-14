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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.ivandsky.puzzlebox.designsystem.ScrubCell
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.frosted

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
internal fun ScrubbedBoardList(
    parts: MorphParts,
    medals: Boolean,
    backdrop: Color,
    modifier: Modifier = Modifier,
    inlineMarks: Boolean = false,
) {
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
                    key(item.part.key) { ScrubSlot(item, medals, inlineMarks, progress) }
                }
            }
        },
        modifier = modifier.fillMaxWidth().testTag("daily_board.list").clearAndSetSemantics {},
    ) { measurables, constraints ->
        val rowConstraints = constraints.copy(minHeight = 0)
        // Each pass has its own, as a placement can run after the next pass has measured.
        val placeables = Array(measurables.size) { index ->
            measurables[index].measure(rowConstraints).also { items.heights[index] = it.height }
        }
        val height = items.stack(Spacing.sm.roundToPx()).at(progress())
        layout(constraints.maxWidth, height) {
            for (index in placeables.indices) {
                val placeable = placeables[index]
                val start = items.fromY[index]
                val travel = items.travel(index)
                val depth = items.depth(index)
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
private fun ScrubSlot(item: ScrubItem, medals: Boolean, inlineMarks: Boolean, progress: () -> Float) {
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
                    BoardLine(part.line, part.order, part.withPuzzleBoxRank, medals, inlineMarks)
                }
            }
            BoardPart.Divider -> HorizontalDivider()
            is BoardPart.Message -> ScrubCell((item.was as? BoardPart.Message)?.text ?: part.text, part.text, progress) {
                BoardMessage(it, part.tag)
            }
        }
    }
}
