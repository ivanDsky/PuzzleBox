package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.play.board.BoardSignIn
import com.ivandsky.puzzlebox.resultui.board.DailyBoardStyle

private const val NONE = "—"

/**
 * The tablet play side panel (A15, prototype 05 · B Stats), top to bottom: today's progress across the
 * daily set, then today's times on its board ([PlayTimesCard]). The timer is in the top bar.
 * Loading values show dashes in the same slots, so nothing moves when data lands.
 * It scrolls when the pane is shorter than the panel. [boardStyle] is the times card's. The streak is on Today.
 */
@Composable
internal fun ColumnScope.PlayStatsPanel(
    state: PlayStatsState,
    signIn: BoardSignIn,
    onSignIn: () -> Unit,
    boardStyle: DailyBoardStyle = DailyBoardStyle.defaults(),
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
        }
        TodayProgressCard(state)
        PlayTimesCard(state.times, signIn = signIn, onSignIn = onSignIn, animated = state.linkedIn, boardStyle = boardStyle)
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
