package com.ivandsky.puzzlebox.compete.create.lineup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.lineup.PuzzleSource
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize

private val ROW_MIN_HEIGHT = 64.dp

/** One pickable puzzle: glyph, title with a NEW badge, who it is from and by, size and code, and Add, or a disabled Added. */
@Composable
internal fun PickerRow(entry: PickerEntry, brand: GameBrand, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val puzzle = entry.puzzle
    val exact = puzzle.entry
    Surface(
        modifier = modifier.fillMaxWidth().heightIn(min = ROW_MIN_HEIGHT).testTag("duel.lineup.pick.${exact.ref.wire}"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.md, top = Spacing.xs, bottom = Spacing.xs, end = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GameGlyph(brand, GameGlyphSize.Chip)
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Text(exact.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f, fill = false))
                    if (puzzle.isNew) NewBadge()
                }
                Text(byline(entry), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PickerMetaLine(puzzle)
            }
            FilledTonalButton(onClick = onAdd, enabled = !entry.added, modifier = Modifier.testTag("duel.lineup.pick.add.${exact.ref.wire}")) {
                Text(if (entry.added) "Added" else "Add")
            }
        }
    }
}

// A friend's label already names the friend, so the author is not said twice.
private fun byline(entry: PickerEntry): String {
    val exact = entry.puzzle.entry
    return listOfNotNull(exact.sourceLabel(), exact.author?.takeIf { exact.source != PuzzleSource.Friend }?.let { "by $it" }).joinToString(" · ")
}

@Composable
private fun NewBadge() {
    Surface(shape = MaterialTheme.shapes.extraSmall, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.testTag("duel.lineup.pick.new")) {
        Text(
            "NEW",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xs),
        )
    }
}
