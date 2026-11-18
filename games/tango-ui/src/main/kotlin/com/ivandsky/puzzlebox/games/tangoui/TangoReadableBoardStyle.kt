package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import com.ivandsky.puzzlebox.designsystem.BoardColors
import com.ivandsky.puzzlebox.designsystem.BoardGeometry
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.designsystem.LocalBoardGeometry

/**
 * What the shared board tokens decide on the Tango board: the frame (a wall in ink, inside the board's own bounds so
 * the board keeps its size) and the sign ink. A given is told by its sun or moon colour; no cell is tinted for being
 * one. Built from [LocalBoardColors] and [LocalBoardGeometry] alone, never from a release flag.
 */
@Immutable
internal class TangoReadableBoardStyle(
    private val colors: BoardColors,
    private val geometry: BoardGeometry,
) {
    /** The `=` and `×` signs: board ink, the strongest stroke on the board. */
    val signInk: Color get() = colors.boardInk

    /** Closes the mesh, drawn above the cells: a wall in wall ink at the shared wall weight. */
    val frame: Modifier = Modifier.drawWithCache { wallFrame() }

    /** Built once per board size. The cells are clipped to the wall's outer edge: a broken line's dashes sit on the cell edge and would show outside it. */
    private fun CacheDrawScope.wallFrame(): DrawResult {
        val weight = geometry.wallWeight.toPx()
        val stroke = Stroke(width = weight, join = StrokeJoin.Round)
        val outer = Path().apply {
            addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(weight / 2f)))
        }
        val inset = Offset(weight / 2f, weight / 2f)
        val centreLine = Size(size.width - weight, size.height - weight)
        return onDrawWithContent {
            val content = this
            clipPath(outer) { content.drawContent() }
            drawRect(colors.boardWall, topLeft = inset, size = centreLine, style = stroke)
        }
    }
}

/** The style for the board tokens in scope; a new one only when the theme changes. */
@Composable
internal fun rememberTangoBoardStyle(): TangoReadableBoardStyle {
    val colors = LocalBoardColors.current
    val geometry = LocalBoardGeometry.current
    return remember(colors, geometry) { TangoReadableBoardStyle(colors, geometry) }
}
