package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalIce
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing

internal const val WEEK_SOLVED = "Solved"
internal const val WEEK_OPEN = "Not solved"

/** One spoken word for a frozen day, shared by the week strip and the result dot so the two never drift. */
internal const val FrozenLabel = "Frozen"

private val WeekDotHeight = 12.dp
private val FrozenGlyphSize = 8.dp
private val WeekDotMaxWidth = 40.dp
private val WeekGap = 6.dp
private val DashStroke = 1.5.dp
private const val SOLVED_ALPHA = 0.5f

/** The seven week dots in one evenly spaced row. */
@Composable
internal fun ResultWeek(week: List<HeroWeekDot>, accent: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(WeekGap)) {
        week.forEachIndexed { index, day -> WeekDot(index, day, accent, Modifier.weight(1f)) }
    }
}

/** One day of the week: its pill (50 percent accent solved, full today, ice with a snowflake frozen, dashed when open) over its initial. */
@Composable
private fun WeekDot(index: Int, day: HeroWeekDot, accent: Color, modifier: Modifier) {
    val outline = MaterialTheme.colorScheme.outline
    val pill = RoundedCornerShape(WeekDotHeight / 2)
    Column(
        modifier = modifier
            .semantics(mergeDescendants = true) { stateDescription = weekState(day) }
            .testTag("result.side.week.$index"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        val dot = Modifier.widthIn(max = WeekDotMaxWidth).fillMaxWidth().height(WeekDotHeight)
        if (day.frozen) {
            val ice = LocalIce.current
            Box(dot.background(ice.container, pill), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = PuzzleIcons.Snowflake,
                    contentDescription = null,
                    tint = ice.onContainer,
                    modifier = Modifier.size(FrozenGlyphSize),
                )
            }
        } else if (day.solved) {
            Box(dot.background(accent.copy(alpha = if (day.today) 1f else SOLVED_ALPHA), pill))
        } else {
            Box(
                dot
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, pill)
                    .drawBehind {
                        val stroke = DashStroke.toPx()
                        val inset = stroke / 2
                        drawRoundRect(
                            color = outline,
                            topLeft = Offset(inset, inset),
                            size = Size(size.width - stroke, size.height - stroke),
                            cornerRadius = CornerRadius(WeekDotHeight.toPx() / 2),
                            style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(stroke * 3, stroke * 2))),
                        )
                    },
            )
        }
        Text(
            text = day.label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (day.today) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

private fun weekState(day: HeroWeekDot): String = when {
    day.frozen -> FrozenLabel
    day.solved -> WEEK_SOLVED
    else -> WEEK_OPEN
}
