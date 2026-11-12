package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
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

private val KnobRadius = 4.dp
private val PlayheadWidth = 2.dp

/**
 * The draggable strip every replay seek bar is built on: the whole strip taps and drags, a playhead crosses it,
 * and it reads as a slider to a screen reader. [bars] draws the activity under the playhead; it gets the played
 * fraction of the replay.
 */
@Composable
internal fun ReplayScrubStrip(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    height: Dp,
    modifier: Modifier = Modifier,
    bars: DrawScope.(playedFraction: Float) -> Unit,
) {
    val playheadColor = MaterialTheme.colorScheme.onSurface
    val seek by rememberUpdatedState(onSeek)
    val clamped = positionMs.coerceIn(0, durationMs.coerceAtLeast(0))
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .testTag(ReplayTags.SEEK)
            .semantics {
                contentDescription = ReplayCopy.SEEK
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
            },
    ) {
        val fraction = if (durationMs > 0) clamped.toFloat() / durationMs else 0f
        bars(fraction)
        // The playhead rides a track inset by the knob radius so the knob stays whole at both ends.
        val x = KnobRadius.toPx() + (size.width - 2 * KnobRadius.toPx()) * fraction
        drawRoundRect(
            color = playheadColor,
            topLeft = Offset(x - PlayheadWidth.toPx() / 2, 0f),
            size = Size(PlayheadWidth.toPx(), size.height),
            cornerRadius = CornerRadius(PlayheadWidth.toPx() / 2),
        )
        drawCircle(playheadColor, KnobRadius.toPx(), Offset(x, KnobRadius.toPx()))
    }
}

private fun msAt(x: Float, width: Int, inset: Float, durationMs: Long): Long =
    ((x - inset) / (width - 2 * inset) * durationMs).toLong().coerceIn(0, durationMs)
