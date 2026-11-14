package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val KnobRadius = 4.dp
private val PlayheadWidth = 2.dp

/**
 * The draggable strip every replay seek bar is built on: the whole strip taps and drags, a playhead crosses it,
 * and it reads as a slider to a screen reader. [positionMs] is read only in the draw and semantics phases, so a
 * moving playhead redraws the strip and recomposes nothing. [bars] runs once per size, in the cache scope, to lay
 * the activity out; the draw lambda it returns draws it under the playhead, given the x the playhead is drawn at (the bars split their fill there),
 * and must not allocate.
 */
@Composable
internal fun ReplayScrubStrip(
    positionMs: () -> Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    height: Dp,
    modifier: Modifier = Modifier,
    bars: CacheDrawScope.() -> DrawScope.(needleX: Float) -> Unit,
) {
    val playheadColor = MaterialTheme.colorScheme.onSurface
    val seek by rememberUpdatedState(onSeek)
    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .testTag(ReplayTags.SEEK)
            .semantics {
                contentDescription = ReplayCopy.SEEK
                val clamped = positionMs().coerceIn(0, durationMs.coerceAtLeast(0))
                progressBarRangeInfo = ProgressBarRangeInfo(clamped.toFloat(), 0f..durationMs.coerceAtLeast(1).toFloat())
                setProgress { ms -> (durationMs > 0).also { if (it) seek(ms.toLong()) } }
            }
            .pointerInput(durationMs) {
                if (durationMs <= 0) return@pointerInput
                detectTapGestures { seek(msAt(it.x, size.width, KnobRadius.toPx(), durationMs)) }
            }
            .pointerInput(durationMs) {
                if (durationMs <= 0) return@pointerInput
                detectHorizontalDragGestures(onDragStart = { seek(msAt(it.x, size.width, KnobRadius.toPx(), durationMs)) }) { change, _ ->
                    change.consume()
                    seek(msAt(change.position.x, size.width, KnobRadius.toPx(), durationMs))
                }
            }
            .drawWithCache {
                val drawBars = bars()
                val knob = KnobRadius.toPx()
                val headWidth = PlayheadWidth.toPx()
                val headSize = Size(headWidth, size.height)
                val headCorner = CornerRadius(headWidth / 2)
                onDrawBehind {
                    val clamped = positionMs().coerceIn(0, durationMs.coerceAtLeast(0))
                    val fraction = if (durationMs > 0) clamped.toFloat() / durationMs else 0f
                    // The playhead rides a track inset by the knob radius so the knob stays whole at both ends.
                    val x = needleX(size.width, knob, fraction)
                    drawBars(x)
                    drawRoundRect(color = playheadColor, topLeft = Offset(x - headWidth / 2, 0f), size = headSize, cornerRadius = headCorner)
                    drawCircle(playheadColor, knob, Offset(x, knob))
                }
            },
    )
}

/** Where the playhead is drawn for a [fraction] of the replay on a strip [width] wide; the one source of truth for the needle x. */
internal fun needleX(width: Float, inset: Float, fraction: Float): Float = inset + (width - 2 * inset) * fraction

private fun msAt(x: Float, width: Int, inset: Float, durationMs: Long): Long =
    ((x - inset) / (width - 2 * inset) * durationMs).toLong().coerceIn(0, durationMs)
