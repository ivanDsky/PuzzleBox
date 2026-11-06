package com.ivandsky.puzzlebox.create

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.create.ui.VerdictChip
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import com.ivandsky.puzzlebox.model.GameId

private val DraftRowMinHeight = 72.dp

/**
 * One draft on the Create root (01 handoff, "Draft row"): the game's glyph tile, the title,
 * "{Game} · {size}[ · imported]", "edited 2 h ago", and the background verdict chip once the
 * row has one. Tapping it opens the draft's Studio through [onOpen].
 */
@Composable
fun DraftRow(row: CreateUiState.DraftRow, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = DraftRowMinHeight)
            .clickable(onClick = onOpen)
            .testTag("create.draft.row")
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameGlyph(row.brand, GameGlyphSize.Tile, Modifier.testTag("create.draft.glyph"))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = row.meta,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = row.edited,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        row.verdict?.let { VerdictChip(it) }
    }
}

@PuzzleBoxPreviews
@Composable
private fun DraftRowPreview() {
    PuzzleBoxTheme {
        Surface {
            Column {
                DraftRow(
                    row = CreateUiState.DraftRow(
                        id = "a",
                        gameId = GameId("queens"),
                        brand = GameBrand.Neutral.copy(accent = AccentSlot.Teal),
                        title = "Sunday crowns",
                        meta = "Queens · 8×8",
                        edited = "edited 2 h ago",
                        verdict = Verdict.Unique(Solution(emptyList())),
                    ),
                    onOpen = {},
                )
                DraftRow(
                    row = CreateUiState.DraftRow(
                        id = "b",
                        gameId = GameId("zip"),
                        brand = GameBrand.Neutral,
                        title = "A very long draft title that runs past the end of the row",
                        meta = "Zip · 7×7 · imported",
                        edited = "edited 3 d ago",
                        verdict = Verdict.Checking,
                    ),
                    onOpen = {},
                )
            }
        }
    }
}
