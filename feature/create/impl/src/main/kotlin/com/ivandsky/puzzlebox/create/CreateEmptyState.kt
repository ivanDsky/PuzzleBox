package com.ivandsky.puzzlebox.create

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import com.ivandsky.puzzlebox.model.GameId

/** Title plus a two-line rule, so every game row stands the same height. */
private val GameRowMinHeight = 84.dp
private const val RuleMaxLines = 2

/**
 * The first-time Create root (spec A3; 01 handoff "Empty (first time)", option C): with no
 * drafts at all it lists [games] inline, each with its glyph tile, one-line rule and default
 * size, so one tap on a game starts a draft of it at that size through [onStart].
 */
@Composable
fun CreateEmptyState(
    games: List<CreateUiState.NewGame>,
    onStart: (gameId: GameId, variant: VariantId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .testTag("create.empty"),
    ) {
        Column(
            Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = "Make a puzzle for someone",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Pick a game, build the board, and share a short code. It saves as a draft " +
                    "while you work — nothing is shared until you publish.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        games.forEach { game ->
            GameStartRow(
                game = game,
                tagRoot = "create.empty",
                onClick = { onStart(game.id, game.defaultSize.variant) },
                chevron = false,
            )
        }
    }
}

/**
 * One game to start, in the empty state and the New puzzle sheet alike (01 handoff, "New
 * puzzle sheet"): glyph tile · name over its one-line rule · default size, then an optional
 * chevron when the tap leads on to a size step. Tags hang off [tagRoot]: `.game.<id>` on the
 * row, `.glyph.<id>`, `.rule.<id>` and `.default.<id>` on its parts.
 */
@Composable
internal fun GameStartRow(
    game: CreateUiState.NewGame,
    tagRoot: String,
    onClick: () -> Unit,
    chevron: Boolean,
) {
    val id = game.id.value
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = GameRowMinHeight)
            .clickable(onClick = onClick)
            .testTag("$tagRoot.game.$id")
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameGlyph(game.brand, GameGlyphSize.Tile, Modifier.testTag("$tagRoot.glyph.$id"))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = game.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            game.rule?.let { rule ->
                Text(
                    text = rule,
                    modifier = Modifier.testTag("$tagRoot.rule.$id"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = RuleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = game.defaultSize.label,
            modifier = Modifier.testTag("$tagRoot.default.$id"),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (chevron) {
            Icon(PuzzleIcons.Chevron, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Stand-in games for this module's previews, with their real rule lines and two size ranges. */
internal val PreviewGames: List<CreateUiState.NewGame> = listOf(
    previewGame("queens", "Queens", AccentSlot.Violet, (4..12).toList()),
    previewGame("zip", "Zip", AccentSlot.Orange, (5..8).toList()),
    previewGame("tango", "Tango", AccentSlot.Teal, listOf(6)),
)

private fun previewGame(id: String, name: String, accent: AccentSlot, sizes: List<Int>): CreateUiState.NewGame {
    val all = sizes.map { CreateUiState.Size(VariantId("$id/$it"), "$it×$it") }
    return CreateUiState.NewGame(
        id = GameId(id),
        name = name,
        brand = GameBrand.Neutral.copy(accent = accent),
        rule = gameRuleLine(GameId(id)),
        defaultSize = all[all.size / 2],
        sizes = all,
    )
}

@PuzzleBoxPreviews
@Composable
private fun CreateEmptyStatePreview() {
    PuzzleBoxTheme {
        Surface {
            CreateEmptyState(games = PreviewGames, onStart = { _, _ -> })
        }
    }
}
