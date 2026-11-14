package com.ivandsky.puzzlebox.gameui.board

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.DuelTone
import com.ivandsky.puzzlebox.designsystem.LinkedInBlue
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.tabular

private val FrameWidth = 1.dp

/** Tall enough for a name over its second line, so a row with the line and one without are the same size. */
internal val BoardRowMinHeight = 64.dp

/** The test tags of one kind of row: [root] frames it, [part] prefixes its parts and [detail] tags the second line. */
internal class BoardRowTags(val root: String, val part: String, val detail: String)

/**
 * The one row every board draws, so a PuzzleBox row and a connection row cannot drift apart: [photo] (or [name]'s
 * initial), then the [cells] that are the row's on this board: its place (null while it is hidden, "100+" beyond the
 * page), [name] with the small mark and detail lines under it when there are any, then the score. Framed; [self] puts
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
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("${tags.part}.name"),
            )
            RowCell(was.mark, cells.mark, scrub) { SmallLine(it, quiet, "${tags.part}.mark") }
            RowCell(was.detail, cells.detail, scrub) { SmallLine(it, quiet, tags.detail) }
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

// The small line under the name; nothing when the row has none.
@Composable
private fun SmallLine(text: String?, color: Color, tag: String) {
    if (text != null) Text(text, style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.testTag(tag))
}
