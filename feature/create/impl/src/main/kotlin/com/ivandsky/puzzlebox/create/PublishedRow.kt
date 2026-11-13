package com.ivandsky.puzzlebox.create

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import java.text.NumberFormat

private val PublishedRowMinHeight = 72.dp
private val Hairline = 1.dp

/** Title and tile once unpublished: still legible, plainly no longer live. */
private const val UnpublishedAlpha = 0.72f

/**
 * One published puzzle on the Create root (01 handoff, "Published row"): the game's glyph
 * tile, the title, "3 solves · Author 1:42" ("No solves yet" at zero, the author time alone
 * until a count is first fetched), and at the end the share code on the game's tint over
 * "published 2 h ago". An unpublished puzzle keeps its row as a record: tile and title fade,
 * the meta reads "Unpublished · 3 solves" and the code is struck on an outlined pill.
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
            MetaLine(row)
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

/**
 * The solve count beside the row's meta: "3 solves · Author 1:42", or "Unpublished · 3 solves"; the count alone when
 * the row has no meta.
 * A fresh count replaces the cached one with a **Slide up + Fade** (DurationShort; a fade
 * under reduced motion), so a change reads as an update rather than a jump.
 */
@Composable
private fun MetaLine(row: CreateUiState.PublishedRow) {
    val style = MaterialTheme.typography.bodyMedium.tabular()
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val meta: @Composable (Modifier) -> Unit = { modifier ->
        Text(row.meta, modifier, color = color, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        val solves = row.solves
        if (solves == null) {
            meta(Modifier.weight(1f, fill = false))
            return@Row
        }
        if (row.meta.isEmpty()) {
            // A legacy row has no author time to show beside the count.
            SolvesCount(solves, Modifier)
        } else if (row.unpublished) {
            Text("${row.meta} · ", color = color, style = style, maxLines = 1)
            SolvesCount(solves, Modifier.weight(1f, fill = false))
        } else {
            SolvesCount(solves, Modifier)
            Text(" · ", color = color, style = style, maxLines = 1)
            meta(Modifier.weight(1f, fill = false))
        }
    }
}

/** "No solves yet", "1 solve", "12,480 solves"; rolls to a new count. */
@Composable
private fun SolvesCount(solves: Int, modifier: Modifier) {
    val reduced = rememberReducedMotion()
    val duration = if (reduced) MotionTokens.ReducedFadeDuration else MotionTokens.DurationShort
    AnimatedContent(
        targetState = solves,
        modifier = modifier,
        transitionSpec = {
            val enter = fadeIn(tween(duration))
            val exit = fadeOut(tween(duration))
            if (reduced) {
                enter togetherWith exit
            } else {
                (slideInVertically(tween(duration)) { it / 2 } + enter) togetherWith (slideOutVertically(tween(duration)) { -it / 2 } + exit)
            }
        },
        label = "solvesCount",
    ) { count ->
        Text(
            text = solvesLabel(count),
            style = MaterialTheme.typography.bodyMedium.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(if (count == 0) "create.published.row.solves.none" else "create.published.row.solves"),
        )
    }
}

private fun solvesLabel(count: Int): String = when (count) {
    0 -> "No solves yet"
    1 -> "1 solve"
    else -> "${NumberFormat.getIntegerInstance().format(count)} solves"
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
                        meta = "Author 1:42",
                        solves = 3,
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
                        meta = "Author 12:05",
                        solves = 0,
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
                        meta = "Unpublished",
                        solves = 12_480,
                        published = "published 5 d ago",
                        unpublished = true,
                    ),
                    onOpen = {},
                )
                PublishedRow(
                    row = CreateUiState.PublishedRow(
                        code = checkNotNull(ShareCode.parse("M2W7QX")),
                        gameId = GameId("zip"),
                        brand = GameBrand(AccentSlot.Blue, GameGlyphs.Zip),
                        title = "Just published",
                        meta = "Author 0:58",
                        published = "published just now",
                    ),
                    onOpen = {},
                )
            }
        }
    }
}
