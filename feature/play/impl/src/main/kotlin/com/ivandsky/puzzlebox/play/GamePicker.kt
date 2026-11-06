package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AppTextButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

/** The hub's grid width in cards, in two-pane and in the phone grid. */
private const val GRID_COLUMNS = 2

/** From this many games a phone hub turns into a grid of tiles, so 7–8 games stay scannable. */
private const val PHONE_GRID_MIN_GAMES = 5

/** The board thumbnail's side in the two-pane grid, where each card has more room. */
private val GridThumbSize = 128.dp

/**
 * The games hub (tag `route.hub`, spec A13/A14): the "N of M done" header (`hub.summary`),
 * then one [HubCard] per playable game in the order given — stacked on phones, a
 * two-column grid in [PlayLayout.TwoPane], and on phones from [PHONE_GRID_MIN_GAMES] games
 * a two-column grid of [HubCardShape.Tile]s — each showing today's board as its game in
 * [games] draws [HubUiState.previews], with the [HubUiState.next] game's card up next.
 * Below them, "Older puzzles" (`hub.older`, [onOpenHistory]) and the line that more games
 * are on the way (`hub.more_coming`). Picking a card is [onPick]. A card with an archive
 * also offers "Past puzzles" (`hub.past.<gameId>`, A22), which opens [PastPuzzlesPicker]
 * over its range; opening a day there is [onPickDay]. Which game's picker is open is the
 * only state held here.
 */
@Composable
internal fun GamePicker(
    state: HubUiState,
    games: GamePlayRegistry,
    layout: PlayLayout,
    onPick: (GameId) -> Unit,
    onPickDay: (GameId, LocalDate) -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pastFor by rememberSaveable { mutableStateOf<String?>(null) }
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
        val twoPane = layout == PlayLayout.TwoPane
        val tiles = !twoPane && state.rows.size >= PHONE_GRID_MIN_GAMES
        val columns = if (twoPane || tiles) GRID_COLUMNS else 1
        state.rows.chunked(columns).forEach { line ->
            // IntrinsicSize.Min + the cards' fillMaxHeight: a row's cards share its tallest's height.
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                line.forEach { row ->
                    val model = state.previews[row.game]
                    val ui = games[row.game]
                    HubCard(
                        entry = row,
                        best = row.best?.timeMs,
                        isNext = row.game == state.next,
                        thumbnail = if (model != null && ui != null) {
                            { thumb -> ui.BoardThumbnail(model, thumb) }
                        } else {
                            null
                        },
                        onPlay = { onPick(row.game) },
                        modifier = Modifier.weight(1f),
                        thumbSize = if (twoPane) GridThumbSize else HubThumbSize,
                        shape = if (tiles) HubCardShape.Tile else HubCardShape.Row,
                        onPast = row.archive?.let { { pastFor = row.game.value } },
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
    val past = state.rows.firstOrNull { it.game.value == pastFor }
    past?.archive?.let { range ->
        PastPuzzlesPicker(
            range = range,
            onOpen = { day ->
                pastFor = null
                onPickDay(past.game, day)
            },
            onDismiss = { pastFor = null },
        )
    }
}

/** The hub's header: how many of today's puzzles are solved, out of every game. */
internal fun hubSummary(done: Int, total: Int): String = "$done of $total done"
