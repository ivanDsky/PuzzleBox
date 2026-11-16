package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.Dp
import com.ivandsky.puzzlebox.designsystem.BoardColors
import com.ivandsky.puzzlebox.designsystem.BoardGeometry
import com.ivandsky.puzzlebox.designsystem.BoardTokens
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.designsystem.LocalBoardGeometry

/**
 * What the shared board tokens decide on the Tango board (nav-rethink B3): the frame, how a given is told from the
 * player's own entries, and the sign ink. Built from [LocalBoardColors] and [LocalBoardGeometry] alone, never from a
 * release flag. Under the legacy tokens every value is the board as it was drawn before them, pixel for pixel; under
 * the readable tokens the frame is a wall in ink, a given wears an ink outline and no cell is tinted for being a given.
 */
@Immutable
internal class TangoReadableBoardStyle(
    private val colors: BoardColors,
    private val geometry: BoardGeometry,
    private val legacyGivenWash: Color,
) {
    /**
     * The one token that tells the two looks apart: the readable geometry draws the board edge as a wall. Tango has
     * no inner walls, so its frame is the only wall it draws.
     */
    private val readable: Boolean get() = geometry.wallOutline

    /** The `=` and `×` signs: board ink, the strongest stroke on the board. */
    val signInk: Color get() = colors.boardInk

    /** The outline a given's glyph wears, at [GivenOutlineWeight]; unspecified when a given is told by its wash. */
    val givenOutline: Color get() = if (readable) colors.boardInk else Color.Unspecified

    /**
     * The legacy given wash over the whole cell (user QA 2026-11-02: a preplaced cell reads as disabled, not dotted).
     * Unspecified under the readable tokens: there a given is told by ink ([givenOutline]) and no cell carries a tint.
     */
    val givenWash: Color get() = if (readable) Color.Unspecified else legacyGivenWash

    /**
     * Closes the mesh, drawn above the cells. Legacy: a double grid line (user QA 2026-11-02: without an outer border
     * the top row reads as glyphs floating above the board). Readable: a wall in wall ink at the shared wall weight,
     * inside the board's own bounds so the board keeps its size.
     */
    val frame: Modifier = if (readable) {
        Modifier.drawWithCache { wallFrame() }
    } else {
        Modifier.border(width = BoardTokens.boardGridLineWeight * 2, color = colors.boardGridLine)
    }

    /** Built once per board size. The cells are clipped to the wall's outer edge: a broken line's dashes sit on the cell edge and would show outside it. */
    private fun CacheDrawScope.wallFrame(): DrawResult {
        val weight = geometry.wallWeight.toPx()
        val rounded = geometry.roundedWallCaps
        val stroke = Stroke(width = weight, join = if (rounded) StrokeJoin.Round else StrokeJoin.Miter)
        val outer = Path().apply {
            addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(if (rounded) weight / 2f else 0f)))
        }
        val inset = Offset(weight / 2f, weight / 2f)
        val centreLine = Size(size.width - weight, size.height - weight)
        return onDrawWithContent {
            val content = this
            clipPath(outer) { content.drawContent() }
            drawRect(colors.boardWall, topLeft = inset, size = centreLine, style = stroke)
        }
    }

    companion object {
        /** The ink outline around a given's glyph: the grid line's weight, so the board has one thin stroke. */
        val GivenOutlineWeight: Dp = BoardTokens.boardGridLineWeight
    }
}

/** The style for the board tokens in scope; a new one only when the theme changes. */
@Composable
internal fun rememberTangoBoardStyle(): TangoReadableBoardStyle {
    val colors = LocalBoardColors.current
    val geometry = LocalBoardGeometry.current
    val givenWash = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = TangoBoardSpec.givenTintAlpha)
    return remember(colors, geometry, givenWash) { TangoReadableBoardStyle(colors, geometry, givenWash) }
}
