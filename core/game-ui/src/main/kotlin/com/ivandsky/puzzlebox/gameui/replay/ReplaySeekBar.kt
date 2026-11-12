package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp

private val StripHeight = 64.dp
private val KnobRadius = 4.dp
private val PlayheadWidth = 2.dp
private const val UNPLAYED_ALPHA = 0.28f

/** Activity bars over the replay's play time, a bar per slice taller the more moves land in it. The whole strip drags. */
@Composable
fun ReplaySeekBar(
    positionMs: Long,
    durationMs: Long,
    ticksMs: LongArray,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val counts = remember(ticksMs, durationMs) { activityBuckets(ticksMs, durationMs) }
    val seek by rememberUpdatedState(onSeek)
    val clamped = positionMs.coerceIn(0, durationMs.coerceAtLeast(0))
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(StripHeight)
            .testTag(ReplayTags.SEEK)
            .semantics {
                contentDescription = ReplayCopy.SEEK
                progressBarRangeInfo = ProgressBarRangeInfo(clamped.toFloat(), 0f..durationMs.coerceAtLeast(1).toFloat())
                setProgress { ms -> (durationMs > 0).also { if (it) seek(ms.toLong()) } }
            }
            .pointerInput(durationMs) {
                if (durationMs <= 0) return@pointerInput
                detectTapGestures { seek(msAt(it.x, size.width, durationMs)) }
            }
            .pointerInput(durationMs) {
                if (durationMs <= 0) return@pointerInput
                detectHorizontalDragGestures(onDragStart = { seek(msAt(it.x, size.width, durationMs)) }) { change, _ ->
                    change.consume()
                    seek(msAt(change.position.x, size.width, durationMs))
                }
            },
    ) {
        val fraction = if (durationMs > 0) clamped.toFloat() / durationMs else 0f
        drawBarSeries(
            counts = counts,
            fraction = fraction,
            axisY = size.height - 6.dp.toPx(),
            room = 52.dp.toPx(),
            up = true,
            played = colors.primary,
            unplayed = colors.onSurfaceVariant.copy(alpha = UNPLAYED_ALPHA),
        )
        val x = size.width * fraction
        drawRoundRect(
            color = colors.onSurface,
            topLeft = Offset(x - PlayheadWidth.toPx() / 2, 0f),
            size = Size(PlayheadWidth.toPx(), size.height),
            cornerRadius = CornerRadius(PlayheadWidth.toPx() / 2),
        )
        drawCircle(colors.onSurface, KnobRadius.toPx(), Offset(x, KnobRadius.toPx()))
    }
}

private fun msAt(x: Float, width: Int, durationMs: Long): Long = (x / width * durationMs).toLong().coerceIn(0, durationMs)
