package com.ivandsky.puzzlebox.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.bestScoreText

private val PlayAgainHeight = 56.dp

/** Expanded-width detail placeholder until a day is selected (home's DetailPane voice). */
@Composable
internal fun DetailHint(
    cardColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.lg)
            .testTag("history.detail_hint"),
        shape = MaterialTheme.shapes.large,
        color = cardColor,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Pick a puzzle",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Tap a puzzle to see your time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * One game's day (history.md §3): the game, its number and day, the Time / Hints of the
 * solve, and that game's best ([gameBest], its best numbered solve under the game's ranking), then the day's
 * [replays] ([HistoryReplaySection]; [onOpenReplay] opens one, [onCompareReplays] both). The action
 * follows the state: "See result" once solved, "Keep going" while unfinished — both
 * report the (game, day) through [onPlayAgain]; the play store resumes or rebuilds it.
 */
@Composable
internal fun DayDetailPane(
    row: HistoryDayRow,
    gameBest: HistoryDayRow?,
    onBackToDayList: (() -> Unit)?,
    replays: HistoryReplays,
    replayUi: GamePlayUi?,
    onOpenReplay: (ReplaySolve) -> Unit,
    onCompareReplays: () -> Unit,
    onPlayAgain: () -> Unit,
    cardColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("history.detail.${row.gameId.value}.${row.day}"),
    ) {
        CompactTopBar(
            // The header below already names the puzzle: single-pane says where Back
            // goes, two-pane keeps the bar for alignment with the list's (Result's pattern).
            title = if (onBackToDayList != null) "History" else "",
            navigationIcon = onBackToDayList?.let {
                {
                    IconButton(onClick = it) {
                        Icon(
                            imageVector = PuzzleIcons.Back,
                            contentDescription = "Back to days",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = Spacing.xl, end = Spacing.xl, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                GameGlyph(brand = row.brand, size = GameGlyphSize.Hero)
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        text = row.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = row.headline,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    row.archiveNote?.let { note ->
                        Text(
                            text = note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("history.detail.archived"),
                        )
                    }
                }
            }
            ScoreCard(record = row.record, cardColor = cardColor)
            gameBest?.takeIf { it.record != null }?.let { best ->
                Row(
                    modifier = Modifier.testTag("history.detail.best"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        text = "Your best",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (row.isBest) BestPill(row.brand)
                    Text(
                        text = bestScoreText(best.score(), best.rankOrder),
                        style = MaterialTheme.typography.titleMedium.tabular(),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            HistoryReplaySection(replays, replayUi, onOpenReplay, onCompareReplays)
            PlayAgainButton(row, onPlayAgain)
        }
    }
}

/**
 * The detail's one action, full width in the game's accent (the hub's pill pattern): a
 * tonal accent-container "See result" once solved, a filled accent "Keep going" while
 * the day is unfinished.
 */
@Composable
private fun PlayAgainButton(row: HistoryDayRow, onPlayAgain: () -> Unit) {
    val accent = row.brand.accent.colors()
    val size = Modifier.fillMaxWidth().heightIn(min = PlayAgainHeight)
    if (row.record != null) {
        FilledTonalButton(
            onClick = onPlayAgain,
            shape = PillShape,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = accent.container,
                contentColor = accent.onContainer,
            ),
            modifier = size.testTag("history.see_result"),
        ) { Text(text = "See result", style = MaterialTheme.typography.labelLarge) }
    } else {
        Button(
            onClick = onPlayAgain,
            shape = PillShape,
            colors = ButtonDefaults.buttonColors(containerColor = accent.accent, contentColor = accent.onAccent),
            modifier = size.testTag("history.keep_going"),
        ) { Text(text = "Keep going", style = MaterialTheme.typography.labelLarge) }
    }
}

/** Time | Hints of the solve, or a plain "not finished" line before there is one. */
@Composable
private fun ScoreCard(
    record: CompletionRecord?,
    cardColor: Color,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("history.score"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(containerColor = cardColor),
    ) {
        if (record != null) {
            Row(
                modifier = Modifier.padding(Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                ScoreTile(value = formatDuration(record.time), label = "Time", modifier = Modifier.weight(1f))
                ScoreTile(value = "${record.hints}", label = "Hints", modifier = Modifier.weight(1f))
            }
        } else {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = "Not finished yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Finish it to see your time here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ScoreTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium.tabular(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
