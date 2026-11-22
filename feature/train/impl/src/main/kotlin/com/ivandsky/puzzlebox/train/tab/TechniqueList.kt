package com.ivandsky.puzzlebox.train.tab

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import com.ivandsky.puzzlebox.model.GameId

/** One chip of the game row: a game that has a trainer, with the name and brand the row draws it with. */
internal data class GameChip(val id: GameId, val label: String, val brand: GameBrand)

/**
 * The Train list: a row of the games that have a trainer, then the [selected] game's techniques in catalog order, each
 * with its mark. A tap on a game calls [onSelectGame]; a tap on a technique calls [onOpen]. [openId] is the technique
 * whose practice board is showing beside the list (tablet), drawn as the selected row; null on the phone.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TechniqueList(
    games: List<GameChip>,
    selected: GameId,
    techniques: List<TechniqueRow>,
    openId: TechniqueId?,
    onSelectGame: (GameId) -> Unit,
    onOpen: (TechniqueId) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.testTag("train.list"), contentPadding = PaddingValues(bottom = Spacing.lg)) {
        item(key = "games") {
            FlowRow(
                // The same side inset a list item keeps, so the chips line up with the rows' text.
                Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                games.forEach { game ->
                    FilterChip(
                        selected = game.id == selected,
                        onClick = { onSelectGame(game.id) },
                        label = { Text(game.label) },
                        leadingIcon = { GameGlyph(game.brand, GameGlyphSize.Inline) },
                        modifier = Modifier.testTag("train.game.${game.id.value}"),
                    )
                }
            }
        }
        items(techniques, key = { it.technique.id.value }) { row ->
            TechniqueRowItem(row, open = row.technique.id == openId, onClick = { onOpen(row.technique.id) })
        }
    }
}

@Composable
private fun TechniqueRowItem(row: TechniqueRow, open: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(row.technique.name) },
        supportingContent = { Text(row.technique.line) },
        trailingContent = { MarkLabel(row.progress) },
        colors = ListItemDefaults.colors(
            containerColor = if (open) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        ),
        modifier = Modifier
            .testTag("train.row.${row.technique.id.value}")
            .semantics { selected = open }
            .clickable(onClick = onClick),
    )
}

@Composable
private fun MarkLabel(progress: TechniqueProgress) {
    val color = when (progress.mark) {
        TechniqueProgress.Mark.New -> MaterialTheme.colorScheme.onSurfaceVariant
        TechniqueProgress.Mark.Practicing -> MaterialTheme.colorScheme.onSurface
        TechniqueProgress.Mark.Mastered -> MaterialTheme.colorScheme.primary
    }
    Text(markText(progress), style = MaterialTheme.typography.labelLarge, color = color)
}

/** The mark's word, and for Practicing the streak of right answers so far. */
internal fun markText(progress: TechniqueProgress): String = when (progress.mark) {
    TechniqueProgress.Mark.New -> "New"
    TechniqueProgress.Mark.Practicing -> if (progress.inRow > 0) "Practicing \u00B7 Streak ${progress.inRow}" else "Practicing"
    TechniqueProgress.Mark.Mastered -> "Mastered"
}
