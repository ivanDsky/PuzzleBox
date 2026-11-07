package com.ivandsky.puzzlebox.create

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode

private val PublishedRowMinHeight = 72.dp
private val Hairline = 1.dp

/** Title and tile once unpublished: still legible, plainly no longer live. */
private const val UnpublishedAlpha = 0.72f

/**
 * One published puzzle on the Create root (01 handoff, "Published row"): the game's glyph
 * tile, the title, "{Game} · your time 1:42", and at the end the share code on the game's
 * tint over "published 2 h ago". An unpublished puzzle keeps its row as a record: tile and
 * title fade, the meta says "Unpublished" and the code is struck on an outlined pill.
 * Tapping the row opens its published page through [onOpen].
 */
@Composable
fun PublishedRow(row: CreateUiState.PublishedRow, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = PublishedRowMinHeight)
            .clickable(onClickLabel = "Open published puzzle", onClick = onOpen)
            .testTag("create.published.row")
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val faded = if (row.unpublished) Modifier.alpha(UnpublishedAlpha) else Modifier
        Box(faded) { GameGlyph(row.brand, GameGlyphSize.Tile) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = faded,
            )
            Text(
                text = row.meta,
                style = MaterialTheme.typography.bodyMedium.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            CodeChip(row)
            Text(
                text = row.published,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** The share code on the game's tint; once unpublished, struck through on an outlined pill. */
@Composable
private fun CodeChip(row: CreateUiState.PublishedRow) {
    val accent = row.brand.accent.colors()
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = PillShape,
        color = if (row.unpublished) scheme.surface else accent.container,
        contentColor = if (row.unpublished) scheme.onSurfaceVariant else accent.onContainer,
        border = if (row.unpublished) BorderStroke(Hairline, scheme.outlineVariant) else null,
    ) {
        Text(
            text = row.code.display,
            style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = FontFamily.Monospace,
                textDecoration = if (row.unpublished) TextDecoration.LineThrough else null,
            ),
            modifier = Modifier
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                .testTag(if (row.unpublished) "create.published.row.code.struck" else "create.published.row.code"),
        )
    }
}

@PuzzleBoxPreviews
@Composable
private fun PublishedRowPreview() {
    PuzzleBoxTheme {
        Surface {
            Column {
                PublishedRow(
                    row = CreateUiState.PublishedRow(
                        code = checkNotNull(ShareCode.parse("K7Q2MX")),
                        gameId = GameId("queens"),
                        brand = GameBrand(AccentSlot.Violet, GameGlyphs.Queens),
                        title = "Sunday crowns",
                        meta = "Queens · your time 1:42",
                        published = "published 2 h ago",
                    ),
                    onOpen = {},
                )
                PublishedRow(
                    row = CreateUiState.PublishedRow(
                        code = checkNotNull(ShareCode.parse("B4N8TW")),
                        gameId = GameId("zip"),
                        brand = GameBrand(AccentSlot.Blue, GameGlyphs.Zip),
                        title = "A very long puzzle title that runs past the end of the row",
                        meta = "Zip · your time 12:05",
                        published = "published 3 d ago",
                    ),
                    onOpen = {},
                )
                PublishedRow(
                    row = CreateUiState.PublishedRow(
                        code = checkNotNull(ShareCode.parse("H3R9PD")),
                        gameId = GameId("queens"),
                        brand = GameBrand(AccentSlot.Violet, GameGlyphs.Queens),
                        title = "Taken down",
                        meta = "Unpublished · your time 2:10",
                        published = "published 5 d ago",
                        unpublished = true,
                    ),
                    onOpen = {},
                )
            }
        }
    }
}
