package com.ivandsky.puzzlebox.gameui.hidden

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.HiddenBoardTokens

/**
 * Where a board of [rows] by [cols] sits on a puzzle's page: as wide as it is given and in the board's shape, but
 * never higher than [maxSide], by default [HiddenBoardTokens.MaxSide]. A loading board and the board itself take the
 * same room, given the same [maxSide].
 */
internal fun Modifier.hiddenBoardBounds(rows: Int, cols: Int, maxSide: Dp = HiddenBoardTokens.MaxSide): Modifier {
    val shape = cols.coerceAtLeast(1).toFloat() / rows.coerceAtLeast(1)
    return widthIn(max = maxSide * shape).fillMaxWidth().aspectRatio(shape)
}

/**
 * The flat, rounded frame around the board, at most [maxSide] high. It clips what it holds: the blurred board is
 * drawn larger than it.
 */
@Composable
internal fun HiddenBoardSlot(
    rows: Int,
    cols: Int,
    modifier: Modifier = Modifier,
    maxSide: Dp = HiddenBoardTokens.MaxSide,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier = modifier
            .hiddenBoardBounds(rows, cols, maxSide)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        content = content,
    )
}
