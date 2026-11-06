package com.ivandsky.puzzlebox.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalOnFill
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.gameui.GameBrand

/** The badge ring box: 56dp in a phone column, 72dp in a tablet one (handoff 05-b). */
internal enum class TodayBadgeSize(val ring: Dp) {
    Compact(56.dp),
    Expanded(72.dp),
}

private const val GLYPH_SHARE = 0.46f
private const val HALF_SWEEP = 180f
private const val TOP_ANGLE = -90f
private val RingInset = 2.5.dp
private val TrackStroke = 3.dp
private val DashStroke = 2.dp
private val DashOn = 3.dp
private val DashOff = 5.dp

/**
 * One game's ring per [status]: a bare track (NotStarted), the track plus a fixed half arc
 * (InProgress — the data carries no progress fraction), a filled accent disc (Solved, the
 * fill tweening in), or a dashed outline (Offline), with the [brand] glyph centred.
 */
@Composable
internal fun TodayBadgeRing(
    brand: GameBrand,
    status: TodayStatus,
    size: TodayBadgeSize,
    modifier: Modifier = Modifier,
) {
    val accent = brand.accent.colors().accent
    val track = MaterialTheme.colorScheme.outlineVariant
    val outline = MaterialTheme.colorScheme.outline
    val solved = status is TodayStatus.Solved
    val reducedMotion = rememberReducedMotion()
    val fill by animateFloatAsState(
        targetValue = if (solved) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingStandard),
        label = "today-badge-fill",
    )
    Box(modifier.size(size.ring), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size.ring)) {
            val inset = RingInset.toPx()
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            when (status) {
                TodayStatus.Offline -> drawArc(
                    color = outline,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(
                        width = DashStroke.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(DashOn.toPx(), DashOff.toPx())),
                    ),
                )
                else -> {
                    if (fill < 1f) {
                        drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(TrackStroke.toPx()))
                    }
                    if (status is TodayStatus.InProgress) {
                        drawArc(
                            color = accent,
                            startAngle = TOP_ANGLE,
                            sweepAngle = HALF_SWEEP,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(TrackStroke.toPx(), cap = StrokeCap.Round),
                        )
                    }
                    if (fill > 0f) drawCircle(accent.copy(alpha = accent.alpha * fill))
                }
            }
        }
        Icon(
            imageVector = brand.glyph,
            contentDescription = null,
            tint = when (status) {
                TodayStatus.Offline -> MaterialTheme.colorScheme.onSurfaceVariant
                is TodayStatus.Solved -> LocalOnFill.current
                else -> accent
            },
            modifier = Modifier.size(size.ring * GLYPH_SHARE),
        )
    }
}
