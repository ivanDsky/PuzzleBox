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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing

/** Right moves in a row that make a technique mastered (A5): the pips on the title row. */
private const val PIPS = 5

private val PipSize = 20.dp

/**
 * The compact title row above the board: the back arrow if [showBack], the technique's [name] over how many trained moves
 * are right in a row, and the pips that count them. "Mastered" replaces the count for a technique that is, and fills
 * every pip.
 */
@Composable
internal fun PracticeHeader(name: String, progress: TechniqueProgress, showBack: Boolean, onClose: () -> Unit) {
    val mastered = progress.mark == TechniqueProgress.Mark.Mastered
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showBack) BackButton(onClose)
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                if (mastered) "Mastered" else "${minOf(progress.inRow, PIPS)} of $PIPS in a row",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("train.count"),
            )
        }
        Pips(if (mastered) PIPS else minOf(progress.inRow, PIPS))
    }
}

/** [filled] of [PIPS] discs: the count the text beside them already says, so they are not read out. */
@Composable
private fun Pips(filled: Int) {
    Row(Modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
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
