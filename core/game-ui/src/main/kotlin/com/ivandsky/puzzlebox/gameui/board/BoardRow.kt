package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.DuelTone
import com.ivandsky.puzzlebox.designsystem.LinkedInBlue
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.tabular

private val FrameWidth = 1.dp
private val ChipInset = 2.dp

// What a long name keeps of its line when the chips beside it would take it all; the chips give way past that.
private val NameKeeps = 56.dp

/**
 * Tall enough for a name over one small line, so a row with the line and one without are the same size. With inline
 * marks every row is one line, so every row is this height (the winner's medal row aside).
 */
internal val BoardRowMinHeight = 64.dp

/** The test tags of one kind of row: [root] frames it, [part] prefixes its parts and [detail] tags the detail line or chip. */
internal class BoardRowTags(val root: String, val part: String, val detail: String)

/**
 * The one row every board draws, so a PuzzleBox row and a connection row cannot drift apart: [photo] (or [name]'s
 * initial), then the [cells] that are the row's on this board: its place (null while it is hidden, "100+" beyond the
 * page), [name] with the small mark and detail lines under it when there are any, then the score. With
 * [inlineMarks] the mark and the detail are chips beside the name on the same line instead: the name ellipsizes
 * before a chip does, and a chip never makes the row taller. Framed; [self] puts
 * it on the secondary container and [connection] frames it in LinkedIn blue. [linkedIn] puts the LinkedIn dot on the
 * photo. [muted] greys the score, for a time that is hidden. [scoreTime] lets the score count to a new time where the
 * board moves. A medal row wears its tint over whatever the row sits on and keeps its own minimum height.
 *
 * Between two boards (see [LocalRowScrub]) the row is one row that changes on the way: its place rolls, its fill and
 * height follow the place, and its other cells frost over from what they were, all read where the row is laid out
 * and drawn, so the way there recomposes nothing.
 */
@Composable
internal fun BoardRow(
    tags: BoardRowTags,
    name: String,
    photo: String?,
    cells: RowCells,
    modifier: Modifier = Modifier,
    scoreTime: ScoreTime? = null,
    self: Boolean = false,
    connection: Boolean = false,
    linkedIn: Boolean = false,
    muted: Boolean = false,
    inlineMarks: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val content = if (self) scheme.onSecondaryContainer else scheme.onSurface
    val quiet = if (self) scheme.onSecondaryContainer else scheme.onSurfaceVariant
    val base = if (self) scheme.secondaryContainer else LocalBoardBackdrop.current
    val scrub = LocalRowScrub.current
    val was = scrub?.from ?: cells
    Row(
        modifier = modifier
            .testTag(tags.root)
            .fillMaxWidth()
            .rowFill(base, was, cells, scrub?.progress, MaterialTheme.shapes.small)
            .border(FrameWidth, if (connection) LinkedInBlue else scheme.outlineVariant, MaterialTheme.shapes.small)
            .rowMinHeight(was.minHeight, cells.minHeight, scrub?.progress)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoardAvatar(
            name,
            photo,
            part = tags.part,
            tone = if (self) DuelTone.Me else DuelTone.Them,
            ring = if (self) scheme.secondaryContainer else scheme.surface,
            linkedIn = linkedIn,
        )
        // "100+" is wider than a number cell: the name gives way to it.
        Box(if (cells.beyondPage || was.beyondPage) Modifier.widthIn(min = RankCellSize) else Modifier.width(RankCellSize)) {
            if (scrub == null) {
                RankPlace(cells.place, quiet, "${tags.part}.rank", cells.beyondPage, cells.placeMedal)
            } else {
                ScrubbedPlace(was, cells, quiet, "${tags.part}.rank", scrub.progress)
            }
        }
        if (inlineMarks) {
            NameLine(Modifier.weight(1f)) {
                RowName(name, content, tags)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    RowCell(was.mark, cells.mark, scrub) { Chip(it, self, "${tags.part}.mark") }
                    RowCell(was.detail, cells.detail, scrub) { Chip(it, self, tags.detail) }
                }
            }
        } else {
            Column(Modifier.weight(1f)) {
                RowName(name, content, tags)
                RowCell(was.mark, cells.mark, scrub) { SmallLine(it, quiet, "${tags.part}.mark") }
                RowCell(was.detail, cells.detail, scrub) { SmallLine(it, quiet, tags.detail) }
            }
        }
        RowCell(was.score, cells.score, scrub, Alignment.CenterEnd) {
            BoardScoreText(
                it,
                scoreTime.takeIf { scrub == null },
                style = MaterialTheme.typography.titleMedium.tabular(),
                color = if (muted) quiet else content,
                modifier = Modifier.testTag("${tags.part}.score"),
            )
        }
    }
}

@Composable
private fun RowName(name: String, color: Color, tags: BoardRowTags) {
    Text(
        name,
        style = MaterialTheme.typography.bodyLarge,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag("${tags.part}.name"),
    )
}

// The small line under the name; nothing when the row has none.
@Composable
private fun SmallLine(text: String?, color: Color, tag: String) {
    if (text != null) Text(text, style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.testTag(tag))
}

// The name and the chips beside it ([content]'s two children, in that order) on one line as tall as the name alone.
// The chips take the room they need and the name ellipsizes into the rest, down to [NameKeeps] (or all of a shorter name).
@Composable
private fun NameLine(modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { (name, chips), constraints ->
        val gap = Spacing.xs.roundToPx()
        // What is left of the line beside [used]; a line of no set width (an intrinsic measure) has no end to leave.
        fun beside(used: Int) =
            Constraints(maxWidth = if (constraints.hasBoundedWidth) (constraints.maxWidth - used).coerceAtLeast(0) else Constraints.Infinity)
        val kept = minOf(name.maxIntrinsicWidth(constraints.maxHeight), NameKeeps.roundToPx())
        val marks = chips.measure(beside(kept + gap))
        val taken = if (marks.width > 0) marks.width + gap else 0
        val text = name.measure(beside(taken))
        layout(if (constraints.hasBoundedWidth) constraints.maxWidth else text.width + taken, text.height) {
            text.placeRelative(0, 0)
            marks.placeRelative(text.width + gap, (text.height - marks.height) / 2)
        }
    }
}

// One of the row's small marks as a chip; nothing when the row has none. The viewer's own row is tinted, so its chip sits on the plain surface.
@Composable
private fun Chip(text: String?, self: Boolean, tag: String) {
    if (text == null) return
    val scheme = MaterialTheme.colorScheme
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = scheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .testTag(tag)
            .background(if (self) scheme.surface else scheme.surfaceContainerHighest, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = Spacing.xs, vertical = ChipInset),
    )
}
