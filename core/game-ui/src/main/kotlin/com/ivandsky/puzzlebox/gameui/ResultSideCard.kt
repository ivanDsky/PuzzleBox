package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
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
import com.ivandsky.puzzlebox.domain.SpreadScope

internal const val SIGN_IN_LABEL = "Sign in"

private val FlameTile = 48.dp
private val FlameIcon = 28.dp
private val RankMinHeight = 56.dp

/**
 * The Results side card: the streak with its flame and the
 * week, a hairline, the time spread chart, and the rank row. It draws [model] and nothing
 * else; a block whose model part is null is left out, and a model with none of them draws
 * no card at all (a practice puzzle). [onSignIn] is the rank row's Sign in button for a
 * signed-out player; the route supplies the real action, and without one the row has no button.
 * The chart opens on [initialScope], else on the model's default scope. [contentModifier] is applied to the
 * content column inside the card, before its padding, so the screen can measure the content's own height.
 *
 * The streak, week and heading come in on the blocks beat (the streak counts up as it does, the freeze moment follows);
 * the chart's bars grow on their own stagger, and the rank row rises in last.
 */
@Composable
internal fun ResultSideCard(
    model: ResultHeroModel,
    brand: GameBrand,
    onSignIn: (() -> Unit)?,
    modifier: Modifier = Modifier,
    wide: Boolean = false,
    initialScope: SpreadScope? = null,
    contentModifier: Modifier = Modifier,
) {
    val streak = model.streak
    val spread = model.spread
    val rank = model.rank
    if (streak == null && spread == null && rank == null) return
    val palette = brand.accent.colors()
    OutlinedCard(modifier = modifier.fillMaxWidth().testTag("result.side.card"), shape = MaterialTheme.shapes.large) {
        Column(contentModifier.padding(if (wide) Spacing.xl else Spacing.lg)) {
            if (streak != null) {
                StreakBlock(streak, palette.accent)
                if (spread != null) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = Spacing.lg),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
            if (spread != null) {
                SpreadBlock(spread, palette, initialScope)
            }
            if (rank != null) {
                RankRow(rank, onSignIn, Modifier.padding(top = Spacing.lg).resultBeat(ResultBeats.Blocks, 3))
            }
        }
    }
}

/** The flame tile and the streak sentence (the number counts up), over the seven week dots and the freeze moment. */
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = heroStreakTitle(days),
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold).tabular(),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).testTag("result.side.streak"),
                    )
                    streak.freezesHeld?.let { FreezeCount(it, Modifier.testTag("result.side.streak.freezes")) }
                }
                Text(
                    text = streak.sub,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("result.side.streak.sub"),
                )
            }
        }
        ResultWeek(streak.week, accent, Modifier.fillMaxWidth().padding(top = Spacing.md).resultBeat(ResultBeats.Blocks, 1))
        if (streak.moment != null) {
            FreezeMoment(streak.moment, Modifier.padding(top = Spacing.md).testTag("result.side.streak.moment"))
        }
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
                Column(Modifier.weight(1f).padding(vertical = Spacing.sm)) {
                    Text(
                        text = rank.sub,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (rank.connections != null) {
                        Text(
                            text = rank.connections,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("result.side.rank.connections"),
                        )
                    }
                }
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
