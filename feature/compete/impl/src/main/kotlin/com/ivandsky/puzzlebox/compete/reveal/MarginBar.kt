package com.ivandsky.puzzlebox.compete.reveal

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.LocalPrimaryText
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.ResultBeats
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.beatDelayMs
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

private val TrackHeight = 12.dp
private val CentreMarkWidth = 2.dp
private val CentreMarkHeight = 18.dp

/**
 * How far apart a puzzle's results were: a track with a mark at its centre, a fill growing from the mark toward the
 * winner (left for me, right for the opponent; none when [winner] is null) and the [margin]'s label at the end,
 * [labelWidth] wide. The fill traces out when the row arrives, [rowIndex] places it in the stagger.
 */
@Composable
internal fun MarginBar(margin: RowMargin, winner: Side?, rowIndex: Int, labelWidth: Dp, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    val instant = reduced || LocalInspectionMode.current
    val grown = remember { Animatable(if (instant) margin.ratio else 0f) }
    LaunchedEffect(margin.ratio) {
        grown.animateTo(
            margin.ratio,
            tween(
                durationMillis = if (instant) 0 else MotionTokens.DurationExpressive,
                delayMillis = beatDelayMs(ResultBeats.Blocks, rowIndex, reduced),
                easing = MotionTokens.Trace.EasingOut,
            ),
        )
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Box(
            Modifier.weight(1f).height(TrackHeight).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(TrackHeight / 2)),
        ) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
                    if (winner == Side.Mine) Fill(grown.value, LocalPrimaryText.current)
                }
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                    if (winner == Side.Theirs) Fill(grown.value, secondaryText())
                }
            }
            Box(
                Modifier
                    .align(Alignment.Center)
                    .width(CentreMarkWidth)
                    .height(CentreMarkHeight)
                    .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(1.dp)),
            )
        }
        Text(
            text = margin.label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(labelWidth),
        )
    }
}

/** One half of the track filled to [fraction] of its width. */
@Composable
private fun Fill(fraction: Float, color: Color) {
    Box(
        Modifier
            .fillMaxWidth(fraction.coerceIn(0f, 1f))
            .fillMaxHeight()
            .background(color, RoundedCornerShape(TrackHeight / 2)),
    )
}

@PuzzleBoxPreviews
@Composable
internal fun MarginBarPreview() = PuzzleBoxTheme {
    Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        MarginBar(RowMargin(0.6f, "12 s"), Side.Mine, 0, 64.dp, Modifier.width(300.dp))
        MarginBar(RowMargin(0.3f, "7 s"), Side.Theirs, 1, 64.dp, Modifier.width(300.dp))
        MarginBar(RowMargin(0f, "tie"), null, 2, 64.dp, Modifier.width(300.dp))
    }
}
