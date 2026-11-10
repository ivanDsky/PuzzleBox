package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.AppTextButton
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.ResultBeats
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.rememberCountUp
import com.ivandsky.puzzlebox.designsystem.resultBeat
import com.ivandsky.puzzlebox.designsystem.tabular

internal const val SIGN_IN_LABEL = "Sign in"
internal const val WEEK_SOLVED = "Solved"
internal const val WEEK_OPEN = "Not solved"

private val FlameTile = 48.dp
private val FlameIcon = 28.dp
private val WeekDotHeight = 12.dp
private val WeekDotMaxWidth = 40.dp
private val WeekGap = 6.dp
private val DashStroke = 1.5.dp
private val RankMinHeight = 56.dp
private const val SOLVED_ALPHA = 0.5f

/**
 * The Results side card (spec: Results hero, side card): the streak with its flame and the
 * week, a hairline, the time spread chart, and the rank row. It draws [model] and nothing
 * else; a block whose model part is null is left out, and a model with none of them draws
 * no card at all (a practice puzzle). [onSignIn] is the rank row's Sign in button for a
 * signed-out player; the route supplies the real action, and without one the row has no button.
 *
 * The streak, week and heading come in on the blocks beat (the streak counts up as it does);
 * the chart's bars grow on their own stagger, and the rank row rises in last.
 */
@Composable
internal fun ResultSideCard(
    model: ResultHeroModel,
    brand: GameBrand,
    onSignIn: (() -> Unit)?,
    modifier: Modifier = Modifier,
    wide: Boolean = false,
) {
    val streak = model.streak
    val spread = model.spread
    val rank = model.rank
    if (streak == null && spread == null && rank == null) return
    val accent = brand.accent.colors().accent
    OutlinedCard(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(if (wide) Spacing.xl else Spacing.lg)) {
            if (streak != null) {
                StreakBlock(streak, accent)
                if (spread != null) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = Spacing.lg),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
            if (spread != null) {
                Text(
                    text = spread.heading,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.resultBeat(ResultBeats.Blocks, 2).testTag("result.side.heading"),
                )
                TimeSpreadChart(spread, accent, Modifier.padding(top = Spacing.sm))
            }
            if (rank != null) {
                RankRow(rank, onSignIn, Modifier.padding(top = Spacing.lg).resultBeat(ResultBeats.Blocks, 3))
            }
        }
    }
}

/** The flame tile and the streak sentence (the number counts up), over the seven week dots. */
@Composable
private fun StreakBlock(streak: HeroStreak, accent: Color) {
    val days = rememberCountUp(streak.days.toLong()).toInt()
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().resultBeat(ResultBeats.Blocks, 0),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(FlameTile)
                    .background(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = PuzzleIcons.Flame,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(FlameIcon),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = heroStreakTitle(days),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold).tabular(),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("result.side.streak"),
                )
                Text(
                    text = streak.sub,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("result.side.streak.sub"),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.md).resultBeat(ResultBeats.Blocks, 1),
            horizontalArrangement = Arrangement.spacedBy(WeekGap),
        ) {
            streak.week.forEachIndexed { index, day -> WeekDot(index, day, accent, Modifier.weight(1f)) }
        }
    }
}

/** One day of the week: its pill (50 percent accent solved, full today, dashed when open) over its initial. */
@Composable
private fun WeekDot(index: Int, day: HeroWeekDot, accent: Color, modifier: Modifier) {
    val outline = MaterialTheme.colorScheme.outline
    val pill = RoundedCornerShape(WeekDotHeight / 2)
    Column(
        modifier = modifier
            .semantics(mergeDescendants = true) { stateDescription = if (day.solved) WEEK_SOLVED else WEEK_OPEN }
            .testTag("result.side.week.$index"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        val dot = Modifier.widthIn(max = WeekDotMaxWidth).fillMaxWidth().height(WeekDotHeight)
        if (day.solved) {
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

/** The rank row: a bold place and its sub line, or the sign-in prompt with its button. No chevron: nothing opens from it. */
@Composable
private fun RankRow(rank: HeroRank, onSignIn: (() -> Unit)?, modifier: Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = RankMinHeight)
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.small)
            .padding(horizontal = Spacing.md)
            .testTag("result.side.rank"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (rank) {
            is HeroRank.Place -> {
                Text(
                    text = rank.value,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold).tabular(),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = rank.sub,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            is HeroRank.SignIn -> {
                Text(
                    text = rank.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(vertical = Spacing.sm),
                )
                if (onSignIn != null) {
                    AppTextButton(text = SIGN_IN_LABEL, onClick = onSignIn, modifier = Modifier.testTag("result.side.rank.signin"))
                }
            }
        }
    }
}
