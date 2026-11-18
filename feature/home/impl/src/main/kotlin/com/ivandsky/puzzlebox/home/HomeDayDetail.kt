package com.ivandsky.puzzlebox.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.SummaryLinkRow
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import com.ivandsky.puzzlebox.model.GameId

private val RowMinHeight = 72.dp
private val ActionMinHeight = 48.dp

/**
 * The tablet detail pane for a picked Recent day (home-day-detail variant A, Rows), top-aligned like the
 * streak pane: the date and a muted summary line, one card with a row per game, then the links. A game
 * played that day shows its time, hints and place; an opened one offers [onOpenGame] "Finish" and a
 * game not played "Play", both for this [day]. Signed out ([signedIn] false) the places are absent and
 * [onSignIn] is offered; [onOpenDay] opens the day in Ranks.
 */
@Composable
internal fun HomeDayDetail(
    day: HistoryDay,
    signedIn: Boolean,
    onOpenGame: (GameId, String) -> Unit,
    onSignIn: () -> Unit,
    onOpenDay: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PaneHorizontal, vertical = PaneVertical)
            .testTag("home.day_detail"),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = day.headline,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("home.day_headline"),
            )
            Text(
                text = day.summaryLine(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("home.day_summary"),
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth().testTag("home.day_games"),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column {
                day.games.forEachIndexed { index, game ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DayGameRow(game, onOpen = { onOpenGame(game.gameId, day.isoDay) })
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            if (!signedIn) {
                SummaryLinkRow("Sign in to see where you ranked", onSignIn, Modifier.testTag("home.day_sign_in"))
            }
            SummaryLinkRow("See this day in Ranks", { onOpenDay(day.isoDay) }, Modifier.testTag("home.day_open_ranks"))
        }
    }
}

@Composable
private fun DayGameRow(game: HistoryDay.GameResult, onOpen: () -> Unit) {
    val id = game.gameId.value
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            .testTag("home.day_row_$id"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        GameGlyph(game.brand, GameGlyphSize.Tile)
        Column(Modifier.weight(1f)) {
            Text(game.gameName, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = game.outcome.subline(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("home.day_subline_$id"),
            )
        }
        when (val outcome = game.outcome) {
            is HistoryDay.Outcome.Solved -> Text(
                text = formatDuration(outcome.timeMs),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold).tabular(),
                color = MaterialTheme.colorScheme.onSurface,
                softWrap = false,
                modifier = Modifier.testTag("home.day_time_$id"),
            )
            HistoryDay.Outcome.Opened -> Button(
                onClick = onOpen,
                modifier = Modifier.heightIn(min = ActionMinHeight).testTag("home.day_finish_$id"),
            ) { Text("Finish") }
            HistoryDay.Outcome.NotPlayed -> FilledTonalButton(
                onClick = onOpen,
                modifier = Modifier.heightIn(min = ActionMinHeight).testTag("home.day_play_$id"),
            ) { Text("Play") }
        }
    }
}

private fun HistoryDay.Outcome.subline(): String = when (this) {
    is HistoryDay.Outcome.Solved -> listOfNotNull(hintsText(hints), standing?.text()).joinToString(" · ")
    HistoryDay.Outcome.Opened -> "Opened, not solved"
    HistoryDay.Outcome.NotPlayed -> "Not played"
}
