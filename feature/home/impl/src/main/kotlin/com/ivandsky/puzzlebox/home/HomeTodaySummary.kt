package com.ivandsky.puzzlebox.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalPrimaryText
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.navLiftSource
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.gameui.SkeletonLine
import com.ivandsky.puzzlebox.gameui.todayStatusLine
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.api.GameKey

private val Inset = Spacing.sm
private val BadgeColumnGap = 4.dp
private val UpNextMinHeight = 44.dp
private val HeadSkeletonWidth = 140.dp
private val HeadSkeletonHeight = 22.dp
private val LineSkeletonHeight = 12.dp

// A card this wide is the tablet list pane's (576dp content); the phone's is ~380dp.
private val ExpandedFrom = 480.dp

/**
 * Today's set as one quiet card (handoff 05-b): "n of N done", one [TodayBadge] per game in
 * registry order, the up-next line, then the CTA naming [HomeUiState.target] — or the
 * rollover countdown once every game is solved. A badge tap and the CTA are [onOpenGame].
 * Tags: `<tagPrefix>_summary` / `_play` / `_countdown`, badges `<badgeTagPrefix><id>`.
 */
@Composable
internal fun HomeTodaySummary(
    state: HomeUiState,
    onOpenGame: (GameId) -> Unit,
    tagPrefix: String,
    badgeTagPrefix: String,
    modifier: Modifier = Modifier,
) {
    val loading = state.today.any { it.status == null }
    val next = state.target.takeUnless { loading || state.allSolved }
    Surface(
        modifier = modifier.fillMaxWidth().testTag(tagPrefix),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        BoxWithConstraints {
            val badgeSize = if (maxWidth >= ExpandedFrom) TodayBadgeSize.Expanded else TodayBadgeSize.Compact
            Column(
                modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.xl, bottom = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                HeadRow(state, loading, tagPrefix)
                Row(horizontalArrangement = Arrangement.spacedBy(BadgeColumnGap)) {
                    state.today.forEach { row ->
                        TodayBadge(
                            row = row,
                            next = row.game == next?.game,
                            size = badgeSize,
                            onClick = { onOpenGame(row.game) },
                            // Keyed like Home's push, so the board lifts out of this badge (tablet).
                            modifier = Modifier.weight(1f).testTag("$badgeTagPrefix${row.game.value}")
                                .navLiftSource(GameKey(row.game.value)),
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = Inset),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                UpNext(next, loading, state.allSolved)
                Action(state, onOpenGame, tagPrefix)
            }
        }
    }
}

@Composable
private fun HeadRow(state: HomeUiState, loading: Boolean, tagPrefix: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Inset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            SkeletonLine(1f, HeadSkeletonHeight, skeletonColor(), Modifier.width(HeadSkeletonWidth).testTag("${tagPrefix}_loading"))
        } else {
            Text(
                text = doneSummary(state.today),
                style = MaterialTheme.typography.headlineMedium.tabular(),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("${tagPrefix}_summary"),
            )
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
            Eyebrow("TODAY")
        }
    }
}

@Composable
private fun UpNext(next: HomeUiState.TodayRow?, loading: Boolean, allSolved: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = UpNextMinHeight).padding(horizontal = Inset),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        when {
            loading -> {
                SkeletonLine(0.3f, LineSkeletonHeight, skeletonColor())
                SkeletonLine(0.7f, LineSkeletonHeight, skeletonColor())
            }
            allSolved -> {
                Eyebrow("TODAY")
                UpNextLine { append("That's the whole set") }
            }
            next != null -> {
                Eyebrow("UP NEXT")
                val status = next.status
                val primary = LocalPrimaryText.current
                UpNextLine {
                    withStyle(SpanStyle(color = primary)) { append(next.displayName) }
                    if (status != null) append(" · ${todayStatusLine(status)}")
                }
            }
        }
    }
}

@Composable
private fun UpNextLine(build: AnnotatedString.Builder.() -> Unit) {
    Text(
        text = buildAnnotatedString(build),
        style = MaterialTheme.typography.titleSmall.tabular(),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun Eyebrow(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Action(state: HomeUiState, onOpenGame: (GameId) -> Unit, tagPrefix: String) {
    val target = state.target
    when {
        state.allSolved -> RolloverCountdown(
            rolloverAt = state.rolloverAt,
            modifier = Modifier.fillMaxWidth().testTag("${tagPrefix}_countdown"),
        )
        target != null -> PrimaryButton(
            text = ctaLabel(target),
            onClick = { onOpenGame(target.game) },
            modifier = Modifier.fillMaxWidth().testTag("${tagPrefix}_play"),
        )
    }
}
