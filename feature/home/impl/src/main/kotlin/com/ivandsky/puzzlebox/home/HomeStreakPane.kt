package com.ivandsky.puzzlebox.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.LocalPrimaryText
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.gameui.FreezeCount
import com.ivandsky.puzzlebox.gameui.WeekStrip
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val PaneVertical = 40.dp
private val PaneHorizontal = 48.dp
private val FlameSize = 44.dp
private val FlameLift = 10.dp
private val PaneRing = 64.dp
private val PaneRingStroke = 5.dp
private val EntranceSlide = 24.dp

/**
 * The tablet detail pane with no day selected (handoff 05-b "Streak & stats pane"): the
 * streak eyebrow and big number, the week strip enlarged and four stat tiles.
 * Everything comes from [state]; nothing new is loaded.
 */
@Composable
internal fun HomeStreakPane(
    state: HomeUiState,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    // The pane sits one step off the app background: lifted in dark, paper-white in light.
    val paneColor = if (scheme.surface.luminance() > scheme.surfaceContainerHighest.luminance()) {
        scheme.surface
    } else {
        scheme.surfaceContainerLow
    }
    val progress = rememberEntrance()
    val slidePx = with(LocalDensity.current) { EntranceSlide.toPx() }
    Column(
        modifier = modifier
            .graphicsLayer {
                alpha = progress
                translationY = (1f - progress) * slidePx
            }
            .clip(MaterialTheme.shapes.extraLarge)
            .background(paneColor)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PaneHorizontal, vertical = PaneVertical)
            .testTag("home.streak_pane"),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        Eyebrow("STREAK")
        BigStreakRow(state.stats)
        WeekStrip(
            days = state.week,
            total = state.today.size,
            ringSize = PaneRing,
            ringStroke = PaneRingStroke,
            modifier = Modifier.testTag("home.pane_week"),
        )
        StatTiles(state.stats)
    }
}

@Composable
private fun rememberEntrance(): Float {
    val reducedMotion = rememberReducedMotion()
    var appeared by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val progress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = if (reducedMotion) {
            tween(MotionTokens.ReducedFadeDuration)
        } else {
            tween(MotionTokens.EnterDuration, easing = MotionTokens.EasingEmphasizedDecelerate)
        },
        label = "home-streak-pane",
    )
    return progress
}

@Composable
private fun Eyebrow(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun BigStreakRow(stats: GameStats) {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(
            imageVector = PuzzleIcons.Flame,
            contentDescription = null,
            tint = LocalPrimaryText.current,
            modifier = Modifier.padding(bottom = FlameLift).size(FlameSize),
        )
        Text(
            text = grouped(stats.currentStreak),
            style = MaterialTheme.typography.displayLarge.tabular(),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("home.pane_streak"),
        )
        Column(Modifier.padding(bottom = Spacing.sm)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = if (stats.currentStreak == 1) "day in a row" else "days in a row",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (stats.currentStreak > 0) stats.freezes?.let { FreezeCount(held = it.held) }
            }
            Text(
                text = "Best ${grouped(stats.bestStreak)}",
                style = MaterialTheme.typography.bodySmall.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatTiles(stats: GameStats) {
    val headline = MaterialTheme.typography.headlineLarge.tabular()
    // Playing since is a date, too long for Headline: Title size. The 34.sp line only sets where its baseline falls
    // before the layout below shifts it onto the headline's.
    val dateValue = MaterialTheme.typography.titleLarge.tabular().copy(lineHeight = 34.sp)
    // The date keeps its smaller type but sits on the headline values' baseline inside a headline-tall box
    // (values are single-line, maxLines = 1, so first and last baseline coincide).
    val measurer = rememberTextMeasurer()
    val headlineRef = remember(measurer, headline) { measurer.measure("0", headline) }
    val dateModifier = Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val offset = headlineRef.firstBaseline.roundToInt() - placeable[LastBaseline]
        layout(placeable.width, headlineRef.size.height) { placeable.placeRelative(0, offset) }
    }
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        StatTile("Solved", grouped(stats.solved), headline, "home.stat_solved", Modifier.weight(1f))
        StatTile("Hint-free", grouped(stats.hintFree), headline, "home.stat_hint_free", Modifier.weight(1f))
        StatTile(
            "Average time",
            stats.averageTimeMs?.let(::formatDuration) ?: NO_VALUE,
            headline,
            "home.stat_average",
            Modifier.weight(1f),
        )
        StatTile(
            "Playing since",
            stats.playingSince?.format(SINCE_DATE) ?: NO_VALUE,
            dateValue,
            "home.stat_since",
            Modifier.weight(1f),
            dateModifier,
        )
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    valueStyle: TextStyle,
    tag: String,
    modifier: Modifier,
    valueModifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(Spacing.lg)
            .testTag(tag),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            modifier = valueModifier,
            style = valueStyle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun grouped(value: Int): String = NumberFormat.getIntegerInstance().format(value)

private const val NO_VALUE = "–"
private val SINCE_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
