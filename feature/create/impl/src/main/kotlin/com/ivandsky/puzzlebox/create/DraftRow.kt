package com.ivandsky.puzzlebox.create

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
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
 * "{Game} · {size}" and " · imported" for an imported draft, "edited 2 h ago", the background
 * verdict chip once the row has one, and the ⋮ button. Tapping the row opens the draft's Studio
 * through [onOpen]; ⋮ or a long press asks for its actions sheet through [onActions].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DraftRow(row: CreateUiState.DraftRow, onOpen: () -> Unit, onActions: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = DraftRowMinHeight)
            .combinedClickable(onClick = onOpen, onLongClick = onActions, onLongClickLabel = "Draft actions")
            .testTag("create.draft.row")
            .padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.md, bottom = Spacing.md),
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
            DraftMetaLine(row.meta, row.imported)
            Text(
                text = row.edited,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        row.verdict?.let { VerdictChip(it) }
        IconButton(onClick = onActions, modifier = Modifier.testTag("create.draft.more")) {
            Icon(PuzzleIcons.More, contentDescription = "Draft actions")
        }
    }
}

/** Shown after an imported draft's meta: the view model carries the fact, the UI owns the copy. */
private const val IMPORTED_SUFFIX = " · imported"

/**
 * The meta line, with an imported draft's " · imported" kept apart as its own tagged text: a
 * long "{Game} · {size}" ellipsizes first and the suffix always stays at the line's end.
 */
@Composable
internal fun DraftMetaLine(meta: String, imported: Boolean) {
    Row {
        Text(
            text = meta,
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (imported) {
            Text(
                text = IMPORTED_SUFFIX,
                modifier = Modifier.testTag("create.draft.imported"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
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
                        brand = GameBrand(AccentSlot.Violet, GameGlyphs.Queens),
                        title = "Sunday crowns",
                        meta = "Queens · 8×8",
                        edited = "edited 2 h ago",
                        verdict = Verdict.Unique(Solution(emptyList())),
                    ),
                    onOpen = {},
                    onActions = {},
                )
                DraftRow(
                    row = CreateUiState.DraftRow(
                        id = "b",
                        gameId = GameId("zip"),
                        brand = GameBrand(AccentSlot.Blue, GameGlyphs.Zip),
                        title = "A very long draft title that runs past the end of the row",
                        meta = "Zip · 7×7",
                        edited = "edited 3 d ago",
                        verdict = Verdict.Checking,
                        imported = true,
                    ),
                    onOpen = {},
                    onActions = {},
                )
            }
        }
    }
}
