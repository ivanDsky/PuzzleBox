package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.gameui.BoardGrid
import com.ivandsky.puzzlebox.gameui.replay.ReplayHintBadge

/**
 * A Queens board drawn from [model] alone: region fills, queens, marks, clash stripes and cell
 * edges. No input, no state holder; without a [LocalQueensMotion] the cells draw still.
 */
@Composable
internal fun QueensBoardFace(model: QueensBoardModel, modifier: Modifier = Modifier, hinted: Set<Int> = emptySet()) {
    QueensBoardFace(model, modifier, staggerFrom = null, hinted = hinted)
}

/**
 * The face with the live board's placement stagger origin; the interactive board owns [staggerFrom]. A cell in
 * [hinted] (a replay's hint targets; live play passes none) carries the replay hint badge at its top-right corner.
 */
@Composable
internal fun QueensBoardFace(model: QueensBoardModel, modifier: Modifier, staggerFrom: Int?, hinted: Set<Int> = emptySet()) {
    Box(modifier.aspectRatio(1f)) {
        BoardGrid(
            rows = model.n,
            cols = model.n,
            modifier = Modifier.matchParentSize(),
            cellContent = { row, col ->
                val idx = row * model.n + col
                QueensCell(model = model, idx = idx, staggerFrom = staggerFrom)
                if (idx in hinted) ReplayHintBadge(Modifier.align(Alignment.TopEnd))
            },
        )
        // §3.1 cell-edge layer (F-1): the region fills are opaque and sit ABOVE
        // BoardGrid's under-strokes, so cell boundaries must be drawn over the fills —
        // a hairline lattice on every cell edge and heavier ink on region boundaries.
        // Glyphs never reach cell edges, so the marks stay visually above this layer.
        val edgeInk = MaterialTheme.colorScheme.onSurface
        Canvas(Modifier.matchParentSize()) {
            drawCellEdges(model, edgeInk)
        }
    }
}
