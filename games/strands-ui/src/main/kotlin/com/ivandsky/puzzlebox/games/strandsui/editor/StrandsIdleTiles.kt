package com.ivandsky.puzzlebox.games.strandsui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft
import com.ivandsky.puzzlebox.games.strandsui.board.StrandsBoardSpec
import com.ivandsky.puzzlebox.games.strandsui.board.drawTokenSquare

/**
 * An empty tile on each [idle] cell (`strands.editor.cell.empty`), so a fresh draft shows a
 * grid instead of a void. Drawn in the board's underlay, under its connectors and letters:
 * the live line, its leaning end included, always paints over a tile, and a letter on an
 * unclaimed cell sits on its tile.
 */
@Composable
internal fun StrandsIdleTiles(idle: Set<Int>, modifier: Modifier = Modifier) {
    val fill = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.outline
    Column(modifier) {
        repeat(StrandsDraft.ROWS) { row ->
            Row(Modifier.fillMaxWidth().weight(1f)) {
                repeat(StrandsDraft.COLS) { col ->
                    val tile = if (row * StrandsDraft.COLS + col in idle) {
                        Modifier.drawBehind { drawEmptyTile(fill, outline) }.testTag("strands.editor.cell.empty")
                    } else {
                        Modifier
                    }
                    Box(Modifier.fillMaxHeight().weight(1f).then(tile))
                }
            }
        }
    }
}

/** A filled tile in the play token's rounded square: the empty look of an unclaimed cell. */
private fun DrawScope.drawEmptyTile(fill: Color, outline: Color) {
    val half = size.minDimension / 2f * StrandsBoardSpec.TokenFraction
    drawTokenSquare(fill, half)
    drawTokenSquare(outline, half, style = Stroke(1.dp.toPx()))
}
