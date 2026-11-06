package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.model.GameId

// Previews of GameTile at the phone grid's tile width: every status, then the longest
// values (an hour-long in-progress clock, a hints-first best with a four-digit number).

private val TileWidth = 172.dp

internal fun previewCardModel(status: TodayStatus?, accent: AccentSlot = AccentSlot.Teal) = GameCardModel(
    game = GameId("mini-sudoku"),
    name = "Mini Sudoku",
    brand = GameBrand.Neutral.copy(accent = accent),
    status = status,
    rankOrder = RankOrder.TimeThenHints,
    puzzleNumber = 411,
    best = BestScore(95_000, hints = 0),
)

@Composable
private fun TileGrid(models: List<GameCardModel>, tile: @Composable (GameCardModel) -> Unit) {
    PuzzleBoxTheme {
        Column(
            modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            models.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    row.forEach { tile(it) }
                }
            }
        }
    }
}

@PuzzleBoxPreviews
@Composable
internal fun GameTileStatusesPreview() {
    TileGrid(
        listOf(
            previewCardModel(TodayStatus.NotStarted, AccentSlot.Teal),
            previewCardModel(TodayStatus.InProgress(192_000L), AccentSlot.Pink),
            previewCardModel(TodayStatus.Solved(42_000L), AccentSlot.Violet),
            previewCardModel(TodayStatus.Offline, AccentSlot.Teal),
            previewCardModel(null, AccentSlot.Pink),
            previewCardModel(TodayStatus.NotStarted, AccentSlot.Violet).copy(puzzleNumber = null, best = null),
        ),
    ) { GameTile(it, onClick = {}, modifier = Modifier.width(TileWidth)) }
}

@PuzzleBoxPreviews
@Composable
internal fun GameTileLongestPreview() {
    TileGrid(
        listOf(
            previewCardModel(TodayStatus.InProgress(3_723_000L), AccentSlot.Pink).copy(
                name = "Mini Sudoku Classic",
                puzzleNumber = 1_234,
                best = BestScore(3_723_000L, hints = 3),
                rankOrder = RankOrder.HintsThenTime,
            ),
            previewCardModel(TodayStatus.Solved(3_723_000L), AccentSlot.Violet).copy(puzzleNumber = 1_234),
        ),
    ) { GameTile(it, onClick = {}, modifier = Modifier.width(TileWidth)) }
}
