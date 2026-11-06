package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.tabular

// 40dp outer fits a 43dp calendar column and leaves a 32dp day circle clear inside
// (the stroke's inner edge sits at 17dp radius).
private val RingSize = 40.dp
private val RingStroke = 3.dp

// Arc-end to arc-end, along the ring. Round caps eat one stroke of it, so 8 segments
// still show a 2.5dp gap between neighbours.
private val SegmentGap = 5.5.dp
private const val FULL_TURN = 360f
private const val TWELVE_O_CLOCK = -90f

/**
 * A day's games as a segmented ring around [content] (spec A18): one equal arc per game,
 * clockwise from 12 o'clock, [solved] first in each brand's accent, then empty
 * segments up to [total]. A ring instead of a row of dots, so any number of games fits
 * a calendar column. Solved segments never drop, so [total] below `solved.size` still
 * shows every solve; no segments draw no ring.
 */
@Composable
fun DayRing(
    solved: List<GameBrand>,
    total: Int,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val empty = MaterialTheme.colorScheme.outlineVariant
    val segments = dayRingFills(solved, total).map { brand -> brand?.accent?.colors()?.accent ?: empty }
    Box(
        modifier = modifier
            .size(RingSize)
            .drawBehind {
                if (segments.isEmpty()) return@drawBehind
                val stroke = RingStroke.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val style = Stroke(width = stroke, cap = StrokeCap.Round)
                if (segments.size == 1) {
                    drawCircle(segments.single(), radius = arcSize.width / 2, style = Stroke(stroke))
                    return@drawBehind
                }
                val gap = Math.toDegrees(SegmentGap.toPx() / (arcSize.width / 2).toDouble()).toFloat()
                val step = FULL_TURN / segments.size
                segments.forEachIndexed { index, color ->
                    drawArc(
                        color = color,
                        startAngle = TWELVE_O_CLOCK + index * step + gap / 2,
                        sweepAngle = (step - gap).coerceAtLeast(0f),
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = style,
                    )
                }
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** The segments clockwise: each solved brand, then `null` (an empty segment) up to [total]. */
internal fun dayRingFills(solved: List<GameBrand>, total: Int): List<GameBrand?> =
    solved + List((total - solved.size).coerceAtLeast(0)) { null }

// Preview: totals 3, 5 and 8 with none, some and all solved, then a day with no ring.
@PuzzleBoxPreviews
@Composable
internal fun DayRingPreview() {
    val slots = AccentSlot.entries - AccentSlot.Neutral
    val brands = List(8) { GameBrand.Neutral.copy(accent = slots[it % slots.size]) }
    PuzzleBoxTheme {
        Column(
            modifier = Modifier.background(MaterialTheme.colorScheme.surface).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            listOf(3, 5, 8).forEach { total ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    listOf(0, total / 2, total).forEach { count ->
                        DayRing(solved = brands.take(count), total = total) {
                            Text(text = "$count", style = MaterialTheme.typography.labelLarge.tabular())
                        }
                    }
                }
            }
            DayRing(solved = emptyList(), total = 0) {
                Text(text = "0", style = MaterialTheme.typography.labelLarge.tabular())
            }
        }
    }
}
