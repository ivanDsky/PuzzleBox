package com.ivandsky.puzzlebox.games.queensui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.designsystem.LocalDarkTheme
import com.ivandsky.puzzlebox.games.queens.editor.QueensDraft
import com.ivandsky.puzzlebox.games.queensui.QueensBoardDefaults
import com.ivandsky.puzzlebox.games.queensui.QueensRegionPalette
import com.ivandsky.puzzlebox.games.queensui.drawQueen
import com.ivandsky.puzzlebox.games.queensui.drawX

/**
 * Cell [idx]'s answer-crown layer over its fill: the play board's crown when [crowned]
 * (tag `queens.editor.cell.<idx>.crown`), else its automatic × when [crossed] (tag
 * `.autoX`; dimmed to [QueensEditorDefaults.ghostedXAlpha] when [ghosted], so the other
 * answer's ghost crown reads over it), both in the region's on-ink
 * ([crownInk]; over the clash wash when [clash]).
 * Draws nothing on a plain cell.
 */
@Composable
internal fun QueensCrownLayer(
    idx: Int,
    region: Int,
    crowned: Boolean,
    crossed: Boolean,
    ghosted: Boolean,
    clash: Boolean,
) {
    if (!crowned && !crossed) return
    val dark = LocalDarkTheme.current
    val scheme = MaterialTheme.colorScheme
    val clashWash = LocalBoardColors.current.editorClashFill.takeIf { clash }
    val ink = remember(region, dark, scheme, clashWash) {
        val fill = if (region == QueensDraft.UNPAINTED) scheme.surfaceContainerHighest else QueensRegionPalette.fill(region, dark)
        if (region == QueensDraft.UNPAINTED && clashWash == null) scheme.onSurface
        else crownInk(fill, clashWash, scheme.onSurface, scheme.scrim, scheme.surface)
    }
    val crownPath = remember { Path() }
    Canvas(
        Modifier
            .fillMaxSize()
            .testTag(
                when {
                    crowned -> "queens.editor.cell.$idx.crown"
                    else -> "queens.editor.cell.$idx.autoX"
                },
            ),
    ) {
        if (crowned) {
            drawQueen(ink, QueensBoardDefaults.queenHeightFraction, alpha = 1f, path = crownPath)
        } else {
            val alpha = if (ghosted) QueensEditorDefaults.ghostedXAlpha else 1f
            drawX(ink, QueensBoardDefaults.crossWeight.toPx(), QueensBoardDefaults.crossLengthFraction, alpha = alpha)
        }
    }
}

/**
 * The crown and ×'s ink on a cell with [fill]: the region's on-ink, or, under the clash
 * [wash] (drawn beneath the crown), the highest-contrast of [onSurface], [scrim] and
 * [surface] over the washed fill, so the crown keeps its 4.5:1 there too.
 */
internal fun crownInk(fill: Color, wash: Color?, onSurface: Color, scrim: Color, surface: Color): Color {
    if (wash == null) return QueensRegionPalette.onInkFor(fill, onSurface, scrim)
    val washed = wash.compositeOver(fill)
    return listOf(onSurface, scrim, surface).maxBy { QueensRegionPalette.contrast(it, washed) }
}
