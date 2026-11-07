package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.game.RendererModel
import com.ivandsky.puzzlebox.gameui.BoardGrid
import com.ivandsky.puzzlebox.games.queens.render.QueensRendererModel

/**
 * Maps [QueensRendererModel] onto the shared game-ui [BoardGrid] (zip ZipBoardRenderer
 * precedent): one tint per region id, a queen glyph on placed cells. Pure render — the
 * minimal skeleton surface for queens-01's DoD; marks/provenance visuals (A10–A12) and
 * the judged visual direction (senior-ux stage) extend this class.
 */
class QueensBoardRenderer {

    @Composable
    fun Content(model: RendererModel, modifier: Modifier) {
        val queens = model as? QueensRendererModel
            ?: error("QueensBoardRenderer expects QueensRendererModel, got ${model::class.simpleName}")
        Box(modifier.aspectRatio(1f)) {
            BoardGrid(
                rows = queens.n,
                cols = queens.n,
                modifier = Modifier.matchParentSize(),
                cellContent = { row, col -> Cell(queens, row, col) },
            )
        }
    }
}

/** Region tint from a stable per-id palette; alpha keeps the theme dominant. */
private fun regionColor(region: Int): Color {
    val hues = listOf(
        Color(0xFFE57373), Color(0xFFF06292), Color(0xFFBA68C8), Color(0xFF7986CB),
        Color(0xFF64B5F6), Color(0xFF4DB6AC), Color(0xFFAED581), Color(0xFFFFD54F),
        Color(0xFFFF8A65), Color(0xFF90A4AE), Color(0xFFA1887F), Color(0xFF81C784),
    )
    return hues[region % hues.size].copy(alpha = 0.35f)
}

@Composable
private fun Cell(model: QueensRendererModel, row: Int, col: Int) {
    val idx = row * model.n + col
    val region = model.regions.getOrNull(idx) ?: return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(regionColor(region)),
        contentAlignment = Alignment.Center,
    ) {
        if (idx in model.queens) {
            Icon(
                imageVector = GameGlyphs.Queens,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize(QueensBoardDefaults.queenHeightFraction)
                    .testTag(QUEEN_TAG),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Test tag of the queen mark painted on placed cells. */
internal const val QUEEN_TAG = "queens.renderer.queen"
