package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * initial), [place] (null while it is hidden), [name] with its small [detail] line when there is one, then [score]. Framed; [self] puts it on
 * the secondary container and [connection] frames it in LinkedIn blue. [linkedIn] puts the LinkedIn dot on the photo.
 * [muted] greys the score, for a time that is hidden. [scoreTime] lets the score count to a new time where the board moves.
 */
@Composable
internal fun BoardRow(
    tags: BoardRowTags,
    name: String,
    photo: String?,
    place: Int?,
    detail: String?,
    score: String,
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
    Row(
        modifier = modifier
            .testTag(tags.root)
            .fillMaxWidth()
            .then(if (self) Modifier.background(scheme.secondaryContainer, MaterialTheme.shapes.small) else Modifier)
            .border(FrameWidth, if (connection) LinkedInBlue else scheme.outlineVariant, MaterialTheme.shapes.small)
            .defaultMinSize(minHeight = BoardRowMinHeight)
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
        Box(Modifier.width(RankCellSize)) { RankPlace(place, quiet, "${tags.part}.rank") }
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("${tags.part}.name"),
            )
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = quiet,
                    modifier = Modifier.testTag(tags.detail),
                )
            }
        }
        BoardScoreText(
            score,
            scoreTime,
            style = MaterialTheme.typography.titleMedium.tabular(),
            color = if (muted) quiet else content,
            modifier = Modifier.testTag("${tags.part}.score"),
        )
    }
}
