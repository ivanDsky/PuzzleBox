package com.ivandsky.puzzlebox.train.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.trainer.PracticeStatus

/** The status band's height: fixed, so the board below it never moves whatever the status says. */
private val BandHeight = 96.dp

/** What the band shows for one status: an [icon], a [title], the [why] line, and the colors it is drawn in. */
private class Band(val icon: ImageVector, val title: String, val why: String, val container: Color, val content: Color)

/**
 * The one status band attached to the board's top edge (spec §2.2): the standing instruction, or the outcome of the
 * last move with its one-line reason. Right is the primary container, an undone Wrong move the error container, an
 * easier move the tertiary one, and the rest the quiet well. A polite live region, so a screen reader reads each
 * outcome as it lands. [chapter] names the trained technique, which the instruction and the Right line name, and the
 * board the Done line says finished.
 */
@Composable
internal fun StatusBand(status: PracticeStatus, chapter: PracticeChapter, onGotIt: () -> Unit, modifier: Modifier = Modifier) {
    val band = band(status, chapter)
    val shape = MaterialTheme.shapes.medium.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize)
    Row(
        modifier
            .fillMaxSize()
            .clip(shape)
            .background(band.container)
            .border(1.dp, LocalBoardColors.current.boardWall, shape)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .testTag("train.status"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(band.icon, contentDescription = null, tint = band.content)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(band.title, style = MaterialTheme.typography.titleSmall, color = band.content)
            Text(band.why, style = MaterialTheme.typography.bodySmall, color = band.content, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        if (status is PracticeStatus.Easier || status is PracticeStatus.Shown) {
            TextButton(onClick = onGotIt, Modifier.testTag("train.gotIt")) { Text("Got it", color = band.content) }
        }
    }
}

@Composable
private fun band(status: PracticeStatus, chapter: PracticeChapter): Band {
    val name = chapter.technique.name
    val scheme = MaterialTheme.colorScheme
    val quiet = scheme.surfaceContainerHigh to scheme.onSurface
    return when (status) {
        PracticeStatus.FindNextMove ->
            Band(PuzzleIcons.Info, "Find the next move", "Play it on the board. Moves that use $name count.", quiet.first, quiet.second)
        is PracticeStatus.KeepGoing -> Band(
            PuzzleIcons.Info,
            "Keep going",
            "${status.left} more in this step.",
            scheme.primaryContainer,
            scheme.onPrimaryContainer,
        )
        is PracticeStatus.Right -> Band(
            PuzzleIcons.Check,
            "Right",
            if (status.trained) {
                "That uses $name, so it counts. Find the next move."
            } else {
                "An easier technique makes that, so it does not count toward $name. Find the next move."
            },
            scheme.primaryContainer,
            scheme.onPrimaryContainer,
        )
        is PracticeStatus.Easier -> Band(
            PuzzleIcons.Hint,
            "An easier move exists",
            "That fits the puzzle, but it is undone. Look for ${status.technique} first. Then tap Got it.",
            scheme.tertiaryContainer,
            scheme.onTertiaryContainer,
        )
        is PracticeStatus.Wrong -> Band(PuzzleIcons.Undo, "Not there, undone", status.why, scheme.errorContainer, scheme.onErrorContainer)
        is PracticeStatus.Shown -> Band(PuzzleIcons.Info, "Here is the next move", "Look at the board, then tap Got it.", quiet.first, quiet.second)
        is PracticeStatus.Done ->
            Band(PuzzleIcons.Check, "Done", "Board ${chapter.board} of ${chapter.boards} finished.", scheme.primaryContainer, scheme.onPrimaryContainer)
    }
}

/**
 * [band] attached to the top edge of [board]: the board is measured first, within the space given less the band, and the
 * band is then as wide as the board and [BandHeight] tall, so the two read as one frame whatever shape the game's board
 * takes.
 */
@Composable
internal fun BandOverBoard(band: @Composable () -> Unit, board: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Layout(content = { Box { band() }; Box { board() } }, modifier = modifier) { measurables, constraints ->
        val bandHeight = BandHeight.roundToPx()
        val room = if (constraints.hasBoundedHeight) (constraints.maxHeight - bandHeight).coerceAtLeast(0) else Constraints.Infinity
        val boardPlaceable = measurables[1].measure(constraints.copy(minWidth = 0, minHeight = 0, maxHeight = room))
        val bandPlaceable = measurables[0].measure(Constraints.fixed(boardPlaceable.width, bandHeight))
        layout(boardPlaceable.width, bandHeight + boardPlaceable.height) {
            bandPlaceable.placeRelative(0, 0)
            boardPlaceable.placeRelative(0, bandHeight)
        }
    }
}
