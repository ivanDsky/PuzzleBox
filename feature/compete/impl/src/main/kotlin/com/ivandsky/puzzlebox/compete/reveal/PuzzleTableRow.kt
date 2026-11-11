package com.ivandsky.puzzlebox.compete.reveal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.DuelTone
import com.ivandsky.puzzlebox.designsystem.ResultBeats
import com.ivandsky.puzzlebox.designsystem.beatDelayMs
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.resultBeat
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize

private val TabletRowMinHeight = 72.dp
private const val TickAfterRowMs = 300

/**
 * One puzzle of the table: the game's tile, "{Game} #{n}" over the basis line in the game's accent, each player's time
 * cell and the margin bar. On a tablet that is one line of columns; on a phone the tile and name sit over a second line
 * with the margin bar and the two time cells, the cells in the same columns as the header's. Row [index] sets its place
 * in the entrance stagger.
 */
@Composable
internal fun PuzzleTableRow(row: PuzzleRow, brand: GameBrand, metrics: TableMetrics, index: Int, modifier: Modifier = Modifier) {
    val tickDelay = beatDelayMs(ResultBeats.Blocks, index, rememberReducedMotion()) + TickAfterRowMs
    val mine = row.cell(Side.Mine)
    val theirs = row.cell(Side.Theirs)
    val cells: @Composable () -> Unit = {
        PuzzleTimeCell(row.mine, row.myHints, mine, DuelTone.Me, metrics.compact, tickDelay, Modifier.width(metrics.timeWidth))
        PuzzleTimeCell(row.theirs, row.theirHints, theirs, DuelTone.Them, metrics.compact, tickDelay, Modifier.width(metrics.timeWidth))
    }
    val bar: @Composable (Modifier) -> Unit = { MarginBar(row.margin, row.winner, index, metrics.marginLabelWidth, it) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .resultBeat(ResultBeats.Blocks, index)
            .heightIn(min = if (metrics.compact) 0.dp else TabletRowMinHeight)
            .padding(horizontal = metrics.sidePadding, vertical = 10.dp),
        verticalAlignment = if (metrics.compact) Alignment.Top else Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.columnGap),
    ) {
        if (metrics.compact) {
            PhoneBody(row, brand, bar, cells, metrics)
        } else {
            Row(
                modifier = Modifier.weight(PuzzleCellWeight),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(metrics.columnGap),
            ) {
                GameGlyph(brand, GameGlyphSize.Tile)
                PuzzleName(row, brand, wide = true, Modifier.weight(1f))
            }
            cells()
            bar(Modifier.weight(1f))
        }
    }
}

@Composable
private fun RowScope.PhoneBody(
    row: PuzzleRow,
    brand: GameBrand,
    bar: @Composable (Modifier) -> Unit,
    cells: @Composable () -> Unit,
    metrics: TableMetrics,
) {
    GameGlyph(brand, GameGlyphSize.Tile)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PuzzleName(row, brand, wide = false, Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.columnGap)) {
            bar(Modifier.weight(1f))
            cells()
        }
    }
}

/** The game's title over its basis line ("12 s faster", "Fewer hints · 0 vs 2"). */
@Composable
private fun PuzzleName(row: PuzzleRow, brand: GameBrand, wide: Boolean, modifier: Modifier) {
    Column(modifier) {
        Text(
            text = row.title,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = if (wide) 17.sp else 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = row.basis,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold),
            color = brand.accent.colors().onContainer,
            maxLines = if (wide) 1 else 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A side's part in the puzzle: the winner, a tie, a missing result, or the loser. */
private fun PuzzleRow.cell(side: Side): CellResult {
    val time = if (side == Side.Mine) mine else theirs
    return when {
        winner == side -> CellResult.Won
        time == NO_RESULT || time == GAVE_UP -> CellResult.Missing
        tied -> CellResult.Tied
        else -> CellResult.Lost
    }
}
