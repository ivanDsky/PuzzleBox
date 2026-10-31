package com.ivandsky.puzzlebox.app.debug

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

import com.ivandsky.puzzlebox.designsystem.Spacing

// Spec ceiling: "no preview exceeds 480dp tall" — the cap is a maximum, never a fixed
// height; content shorter than it takes only the height it needs.
internal val PreviewMaxHeight: Dp = 480.dp

private val CheckerCell: Dp = 8.dp

/**
 * Shared preview canvas: alternating two-tone checkerboard plus a 1dp outline so the
 * previewed region is unambiguous against the app background in BOTH themes (spec —
 * "contrasting background + 1dp border"). Reused by the browser's inline previews
 * (ticket showkase-v2-02) and the detail panes (ticket 04, A8); no motion of its own.
 *
 * Containment (A6): the box wraps content but never grows past [PreviewMaxHeight].
 * Content renders in an internal scroll viewport, so oversized/full-screen components
 * are fully contained — clipped at the cap and draggable internally before the gesture
 * reaches the list. Previews that fit never claim drags (a scrollable with nothing to
 * scroll passes gestures through), so list scrolling stays untouched.
 *
 * [widthHint]/[heightHint] come from the Showkase-declared preview sizes
 * (`ShowkaseBrowserComponent.widthDp/heightDp`); they act as additional caps, never
 * minimums, and the height hint can't lift the 480dp ceiling.
 */
@Composable
internal fun PreviewBackdrop(
    modifier: Modifier = Modifier,
    widthHint: Dp? = null,
    heightHint: Dp? = null,
    contentPadding: PaddingValues = PaddingValues(Spacing.md),
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.small
    val checkerBase = MaterialTheme.colorScheme.surfaceContainerLowest
    val checkerAlt = MaterialTheme.colorScheme.surfaceContainerHigh
    Box(
        modifier
            // Clip first so checker and border follow the shape; checker draws behind,
            // border on top of it.
            .clip(shape)
            .drawBehind {
                drawRect(checkerBase)
                val cell = CheckerCell.toPx()
                if (cell < 1f) return@drawBehind
                val cols = (size.width / cell).toInt() + 1
                val rows = (size.height / cell).toInt() + 1
                val cellSize = Size(cell, cell)
                for (row in 0 until rows) {
                    // Offset alternate rows by one cell for the checkerboard stagger.
                    for (col in (row % 2) until cols step 2) {
                        drawRect(
                            checkerAlt,
                            topLeft = Offset(col * cell, row * cell),
                            size = cellSize,
                        )
                    }
                }
            }
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .widthIn(max = widthHint ?: Dp.Unspecified)
            .heightIn(max = heightHint?.coerceAtMost(PreviewMaxHeight) ?: PreviewMaxHeight)
            .verticalScroll(rememberScrollState())
            // Padding sits inside the scroll so it travels with the content.
            .padding(contentPadding),
        contentAlignment = contentAlignment,
        content = content,
    )
}
