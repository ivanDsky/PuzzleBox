package com.ivandsky.puzzlebox.train.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing

/** Right moves in a row that make a technique mastered (A5): the pips on the title row. */
private const val PIPS = 5

private val PipSize = 20.dp

/**
 * The compact title row above the board: the back arrow if [showBack], the [chapter]'s technique name over how many
 * trained moves are right in a row and which board of the chapter is up ("Board 2 of 5"), and the pips that count the
 * moves. "Mastered" replaces the count for a technique that is, and fills every pip.
 */
@Composable
internal fun PracticeHeader(chapter: PracticeChapter, progress: TechniqueProgress, showBack: Boolean, onClose: () -> Unit) {
    val mastered = progress.mark == TechniqueProgress.Mark.Mastered
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showBack) BackButton(onClose)
        Column(Modifier.weight(1f)) {
            Text(
                chapter.technique.name,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    if (mastered) "Mastered" else "${minOf(progress.inRow, PIPS)} of $PIPS in a row",
                    style = MaterialTheme.typography.bodySmall,
                    color = quiet,
                    modifier = Modifier.testTag("train.count"),
                )
                Text(
                    "Board ${chapter.board} of ${chapter.boards}",
                    style = MaterialTheme.typography.bodySmall,
                    color = quiet,
                    modifier = Modifier.testTag("train.board"),
                )
            }
        }
        Pips(if (mastered) PIPS else minOf(progress.inRow, PIPS))
    }
}

/** [filled] of [PIPS] discs, one image to a screen reader: "3 of 5 right in a row". */
@Composable
private fun Pips(filled: Int) {
    Row(Modifier.clearAndSetSemantics { contentDescription = "$filled of $PIPS right in a row" }, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        repeat(PIPS) { index ->
            val on = index < filled
            Box(
                Modifier
                    .size(PipSize)
                    .clip(CircleShape)
                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
        }
    }
}

/** What the technique is, in its catalog [line], under the board: a lightbulb and an eyebrow, never a hint of this board's move. */
@Composable
internal fun IdeaBlock(line: String, modifier: Modifier = Modifier) {
    Row(modifier.testTag("train.idea"), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Icon(PuzzleIcons.Hint, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column {
            Text("The idea", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
