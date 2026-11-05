package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.AppTextButton
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId

/** The hub's two-pane grid width in cards. */
private const val GRID_COLUMNS = 2

/**
 * The games hub (tag `route.hub`, spec A13/A14): the "N of M done" header (`hub.summary`),
 * then one [HubCard] per playable game in the order given — stacked on phones, a
 * two-column grid in [PlayLayout.TwoPane] — where only the [HubUiState.next] game's
 * button is filled. Below them, "Older puzzles" (`hub.older`, [onOpenHistory]) and the
 * line that more games are on the way (`hub.more_coming`). Stateless; picking a card is
 * [onPick].
 */
@Composable
internal fun GamePicker(
    state: HubUiState,
    layout: PlayLayout,
    onPick: (GameId) -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .testTag("route.hub")
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg, vertical = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = hubSummary(state.doneCount, state.rows.size),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("hub.summary"),
        )
        val columns = if (layout == PlayLayout.TwoPane) GRID_COLUMNS else 1
        state.rows.chunked(columns).forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                line.forEach { row ->
                    HubCard(
                        entry = row,
                        best = state.bestByGame[row.game],
                        isNext = row.game == state.next,
                        onPlay = { onPick(row.game) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // A short last line keeps its cards at grid width.
                repeat(columns - line.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            AppTextButton(
                text = "Older puzzles",
                onClick = onOpenHistory,
                modifier = Modifier.testTag("hub.older"),
            )
            Text(
                text = "More ways to play are coming",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("hub.more_coming"),
            )
        }
    }
}

/** The hub's header: how many of today's puzzles are solved, out of every game. */
internal fun hubSummary(done: Int, total: Int): String = "$done of $total done"

@PuzzleBoxPreviews
@Composable
private fun GamePickerPreview() {
    PuzzleBoxTheme {
        GamePicker(
            state = HubUiState(
                rows = listOf(
                    HubRow(GameId("zip"), "Zip", GameBrand.Neutral.copy(accent = AccentSlot.Blue), 592, TodayStatus.Solved(36_000L)),
                    HubRow(GameId("tango"), "Tango", GameBrand.Neutral.copy(accent = AccentSlot.Orange), 431, TodayStatus.InProgress(42_000L)),
                    HubRow(GameId("queens"), "Queens", GameBrand.Neutral.copy(accent = AccentSlot.Violet), 918, TodayStatus.NotStarted),
                    HubRow(GameId("pinpoint"), "Pinpoint"),
                ),
                bestByGame = mapOf(GameId("zip") to 31_000L),
                doneCount = 1,
                next = GameId("tango"),
            ),
            layout = PlayLayout.SinglePane,
            onPick = {},
            onOpenHistory = {},
        )
    }
}
