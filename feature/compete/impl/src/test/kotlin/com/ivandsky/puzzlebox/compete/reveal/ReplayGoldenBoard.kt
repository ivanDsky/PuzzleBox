package com.ivandsky.puzzlebox.compete.reveal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.gameui.replay.GameReplayBoard
import com.ivandsky.puzzlebox.gameui.replay.ReplayBoardOptions
import com.ivandsky.puzzlebox.testing.replay.FixtureSession
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The replay fixtures' board: one dot per piece, filled for the placed ones. A short session is one row; a longer
 * one wraps into a square grid. A deliberate copy per feature: the shared testing module has no Compose, and test
 * sources are not shared across features.
 */
internal object ReplayGoldenBoard : GameReplayBoard {
    override val pieces = "pieces"

    @Composable
    override fun Board(frame: ReplayFrame, options: ReplayBoardOptions, modifier: Modifier) {
        val session = frame.session as FixtureSession
        val filled = MaterialTheme.colorScheme.primary
        val empty = MaterialTheme.colorScheme.outlineVariant
        val columns = if (session.total <= ROW_MAX) session.total else ceil(sqrt(session.total.toFloat())).toInt()
        val rows = ceil(session.total / columns.toFloat()).toInt()
        Canvas(modifier.fillMaxWidth().aspectRatio(columns / rows.toFloat())) {
            val cell = size.width / columns
            val radius = cell * DOT_FILL / 2
            repeat(session.total) { index ->
                val centre = Offset((index % columns + HALF) * cell, (index / columns + HALF) * cell)
                if (index < session.placed) drawCircle(filled, radius, centre)
                else drawCircle(empty, radius, centre, style = Stroke(min(radius / DOT_STROKE, MAX_STROKE_PX)))
            }
        }
    }

    private const val ROW_MAX = 16
    private const val DOT_FILL = 0.8f
    private const val DOT_STROKE = 4f
    private const val MAX_STROKE_PX = 4f
    private const val HALF = 0.5f
}
