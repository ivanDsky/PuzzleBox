package com.ivandsky.puzzlebox.compete.create.lineup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.lineup.PickablePuzzle
import com.ivandsky.puzzlebox.resultui.DifficultyLabel

/** A pickable puzzle's small print on one line: its size, its level with the mark beside the word, then its code, dot-separated. */
@Composable
internal fun PickerMetaLine(puzzle: PickablePuzzle, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.bodySmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val parts = buildList<@Composable () -> Unit> {
        puzzle.meta.ifEmpty { null }?.let { add { Text(it, style = style, color = color) } }
        puzzle.level?.let { add { DifficultyLabel(it, style, color) } }
        puzzle.code?.let { add { Text(it.display, style = style, color = color) } }
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        parts.forEachIndexed { index, part ->
            if (index > 0) Text("·", style = style, color = color)
            part()
        }
    }
}
