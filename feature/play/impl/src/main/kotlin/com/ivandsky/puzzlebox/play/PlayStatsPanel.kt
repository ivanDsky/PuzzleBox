package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.gameui.WeekStrip
import com.ivandsky.puzzlebox.gameui.bestScoreText
import com.ivandsky.puzzlebox.play.board.BoardSignIn

private const val NONE = "—"

/**
 * The tablet play side panel (A15, prototype 05 · B Stats), top to bottom: the large live
 * timer, today's progress across the daily set, this game's streak over its week with
 * best and average, then today's times on its board ([PlayTimesCard]). [elapsedMs] is
 * read here, so only the panel recomposes on each tick; null (no running board) reads
 * 0:00. Loading values show dashes in the same slots, so nothing moves when data lands.
 * It scrolls when the pane is shorter than the panel.
 */
@Composable
internal fun ColumnScope.PlayStatsPanel(
    state: PlayStatsState,
    elapsedMs: () -> Long?,
    signIn: BoardSignIn = BoardSignIn.Idle,
    onSignIn: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).testTag("play.stats"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column {
            Text(
                "${state.gameName} · today",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                formatDuration(elapsedMs() ?: 0L),
                style = MaterialTheme.typography.displayLarge.tabular(),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.testTag("play.stats.timer"),
            )
        }
        TodayProgressCard(state)
        StreakCard(state)
        PlayTimesCard(state.times, signIn = signIn, onSignIn = onSignIn)
    }
}

@Composable
private fun TodayProgressCard(state: PlayStatsState) {
    val solved = state.solvedToday
    StatsCard(Modifier.semantics(mergeDescendants = true) {}.testTag("play.stats.progress")) {
        CardLabel("Today's progress")
        // One text, so it reads "2/5 solved" as a whole to TalkBack.
        Text(
            buildAnnotatedString {
                withStyle(MaterialTheme.typography.headlineMedium.tabular().toSpanStyle().copy(fontWeight = FontWeight.Bold)) {
                    append(solved?.toString() ?: NONE)
                }
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                    append("/${state.gamesToday} solved")
                }
            },
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
        )
        LinearProgressIndicator(
            progress = { if (solved == null || state.gamesToday == 0) 0f else solved.toFloat() / state.gamesToday },
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm).clip(PillShape),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun StreakCard(state: PlayStatsState) {
    StatsCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Icon(PuzzleIcons.Flame, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                streakText(state.streak),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.testTag("play.stats.streak"),
            )
        }
        WeekStrip(days = state.week, total = 1, modifier = Modifier.padding(top = Spacing.sm).testTag("play.stats.week"))
        Row(Modifier.padding(top = Spacing.sm)) {
            StatCell(state.best?.let { bestScoreText(it, state.rankOrder) } ?: NONE, "your best", Modifier.testTag("play.stats.best"))
            StatCell(state.averageMs?.let(::formatDuration) ?: NONE, "your average", Modifier.testTag("play.stats.average"))
        }
    }
}

// Loading keeps the streak's own shape, so the line doesn't reflow when the count lands.
private fun streakText(streak: Int?): String = when {
    streak == null -> "$NONE-day streak"
    streak > 0 -> "$streak-day streak"
    else -> "No streak yet — solve today to start one"
}

/** The panel's card: a low container under a hairline outline. */
@Composable
internal fun StatsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, AppShapes.medium)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, AppShapes.medium)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        content = content,
    )
}

@Composable
internal fun CardLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun StatValue(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineMedium.tabular(),
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun RowScope.StatCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.weight(1f).semantics(mergeDescendants = true) {}) {
        StatValue(value)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
