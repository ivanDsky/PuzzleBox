package com.ivandsky.puzzlebox.compete.create.lineup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.compete.create.DuelGame
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PanePolicy
import com.ivandsky.puzzlebox.model.GameId
import org.koin.compose.koinInject

private val TILE_MIN_HEIGHT = 56.dp
private const val TILES_PER_ROW = 2
private val TWO_PANE_MAX_WIDTH = 960.dp
private const val TILES_PANE_WEIGHT = 2f
private const val SEARCH_PANE_WEIGHT = 3f
private const val DISABLED_ALPHA = 0.38f

/**
 * "Add puzzle N" or, from a row, "Change puzzle N": one tile per game for an "Any" puzzle of it, and [exactPuzzles], the
 * slot for picking one exact puzzle. One column on a phone, with the chips [exactPuzzles] draws between its field and its
 * results. Where the app splits panes (the single rule in [PanePolicy]) the sheet widens and splits: the tiles and the
 * [filters] on the left, the exact-puzzle search on the right, told through `filtersInline = false` not to draw the chips
 * again. A tile calls [onPickGame]; the sheet closes through [onDismiss] (a swipe, the scrim) or when the caller drops it.
 *
 * With [solved] (Past you's sheet: how many puzzles of each game the player has solved) a tile says its count and is off
 * at zero, since nothing could be drawn; [exactPuzzles] is then the search over those solved puzzles, split off the same way.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun AddPuzzleSheet(
    position: Int,
    changing: Boolean,
    games: List<DuelGame>,
    onPickGame: (GameId) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    filters: @Composable () -> Unit,
    exactPuzzles: @Composable (filtersInline: Boolean) -> Unit,
    solved: Map<GameId, Int>? = null,
) {
    val window = LocalWindowInfo.current.containerSize
    val twoPane = with(LocalDensity.current) { PanePolicy.isTwoPane(window.width.toDp(), window.height.toDp()) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag("duel.lineup.sheet"),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = twoPane),
        sheetMaxWidth = if (twoPane) TWO_PANE_MAX_WIDTH else BottomSheetDefaults.SheetMaxWidth,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = if (changing) "Change puzzle $position" else "Add puzzle $position",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (twoPane) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    Column(Modifier.weight(TILES_PANE_WEIGHT), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        GameTiles(games, solved, onPickGame)
                        filters()
                    }
                    Column(Modifier.weight(SEARCH_PANE_WEIGHT)) { exactPuzzles(false) }
                }
            } else {
                GameTiles(games, solved, onPickGame)
                exactPuzzles(true)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameTiles(games: List<DuelGame>, solved: Map<GameId, Int>?, onPickGame: (GameId) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text("Any puzzle of a game", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            maxItemsInEachRow = TILES_PER_ROW,
        ) {
            games.forEach { game ->
                GameTile(game, solved?.let { it[game.id] ?: 0 }, onClick = { onPickGame(game.id) }, modifier = Modifier.weight(1f))
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
