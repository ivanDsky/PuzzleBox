package com.ivandsky.puzzlebox.compete.create.lineup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.compete.create.DuelGame
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.model.GameId
import org.koin.compose.koinInject

private val TILE_MIN_HEIGHT = 56.dp
private const val DISABLED_ALPHA = 0.38f

/**
 * "Any puzzle of a game": one tile per game, [perRow] to a row, each calling [onPick] with its game. With [solved] (Past
 * you's: how many puzzles of each game the player has solved) a tile says its count and is off at zero, since nothing could be drawn.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GameTiles(games: List<DuelGame>, solved: Map<GameId, Int>?, perRow: Int, onPick: (GameId) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text("Any puzzle of a game", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            maxItemsInEachRow = perRow,
        ) {
            games.forEach { game ->
                GameTile(game, solved?.let { it[game.id] ?: 0 }, onClick = { onPick(game.id) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GameTile(game: DuelGame, solved: Int?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val brand = koinInject<GamePlayRegistry>().brandOf(game.id)
    val available = solved == null || solved > 0
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = TILE_MIN_HEIGHT).alpha(if (available) 1f else DISABLED_ALPHA).testTag("duel.lineup.sheet.game.${game.id.value}"),
        enabled = available,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(horizontal = Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            GameGlyph(brand, GameGlyphSize.Tile)
            Text(
                text = if (solved == null) game.name else "${game.name} · $solved solved",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
