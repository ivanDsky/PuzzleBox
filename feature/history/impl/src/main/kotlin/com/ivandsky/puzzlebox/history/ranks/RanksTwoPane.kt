package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.DayMorph
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.ranks.RankScope
import com.ivandsky.puzzlebox.gameui.board.DailyBoardRowModel
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import com.ivandsky.puzzlebox.gameui.board.boardRowValueText
import com.ivandsky.puzzlebox.history.HistoryDayRow
import com.ivandsky.puzzlebox.history.ReplaySolve
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

private const val LIST_WEIGHT = 0.4f
private const val BOARD_WEIGHT = 0.6f
private val ItemHeight = 52.dp
private val ICON = 20.dp

/**
 * The Ranks hub on a tablet: the left pane holds the full month with the lifetime stats under it and the game list
 * (Overall first, the viewer's place on each); the right pane holds the top bar (the board's name and the tune icon),
 * the placement hero card, Your solve for a past day and the board. A pick in the list re-ranks the board in place, so
 * there is no title menu, week strip or swipe here. The calendar is always the month: the shared calendar's fold-to-week
 * is turned back into a month at the door ([onShowCalendar] only ever hears `expanded = true`).
 */
@Composable
internal fun RanksTwoPane(
    state: RanksUiState,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    onPickBoard: (RanksBoard) -> Unit = {},
    onScope: (RankScope) -> Unit = {},
    onWindow: (RanksSelection.Window) -> Unit = {},
    onShowCalendar: (day: LocalDate, expanded: Boolean) -> Unit = { _, _ -> },
    onSelectDay: (LocalDate?) -> Unit = {},
    onPlayAgain: (GameId, LocalDate) -> Unit = { _, _ -> },
    onOpenReplay: (HistoryDayRow, ReplaySolve) -> Unit = { _, _ -> },
    onCompare: (HistoryDayRow) -> Unit = {},
    leftScroll: ScrollState = rememberScrollState(),
    rightScroll: ScrollState = rememberScrollState(),
    onRowClick: ((DailyBoardRowModel) -> Unit)? = null,
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
) {
    var tuning by rememberSaveable { mutableStateOf(false) }
    var howRanksWork by rememberSaveable { mutableStateOf(false) }
    val current = state.places.getOrNull(state.position)
    Row(
        modifier.fillMaxSize().testTag("route.ranks").padding(Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        Column(
            Modifier.weight(LIST_WEIGHT).fillMaxHeight().verticalScroll(leftScroll).testTag("ranks.leftpane"),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            RanksCalendar(state.calendar.copy(expanded = true), state.stats, { day, _ -> onShowCalendar(day, true) }, onSelectDay, foldable = false)
            GameList(state.places, current?.board, onPickBoard, onHowRanksWork = { howRanksWork = true })
        }
        RanksPullToRefresh(refreshing, onRefresh, Modifier.weight(BOARD_WEIGHT).fillMaxHeight()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rightScroll).testTag("ranks.rightpane"),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                RanksTopBar(tuneLabel(state.scope, state.window), onTune = { tuning = true }) {
                    DayMorph(state.board.title, Modifier.padding(start = Spacing.sm)) { title ->
                        Text(
                            title,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("ranks.boardtitle"),
                        )
                    }
                }
                RanksHeroCard(current?.name ?: "Ranks", current?.standing ?: Standing.None, state.board.selfScore())
                YourSolveRow(state.yourSolve, onPlayAgain, onOpenReplay, onCompare)
                RanksBoardSlot(state.board, state.signIn, onSignIn, onRowClick = onRowClick)
            }
        }
    }
    if (tuning) TuneSheet(state.scope, state.window, onScope, onWindow, onDismiss = { tuning = false })
    if (howRanksWork) HowRanksWorkSheet(onDismiss = { howRanksWork = false })
}

/** The viewer's own official row of the shown board as its score column says it; null when they have none there. */
private fun RanksBoardSlice.selfScore(): String? =
    (state as? DailyBoardState.Ranked)?.rows?.firstOrNull { it.isSelf && !it.unofficial }
        ?.let { boardRowValueText(it.scoreValue, order) }

/** Every board with the viewer's place on it, the shown one marked; a pick reports [onPick]. */
@Composable
private fun GameList(places: List<BoardPlace>, selected: RanksBoard?, onPick: (RanksBoard) -> Unit, onHowRanksWork: () -> Unit) {
    OutlinedCard(
        Modifier.fillMaxWidth().testTag("ranks.list"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            places.forEachIndexed { index, place ->
                GameItem(place, place.board == selected, Modifier.testTag("ranks.list.item.$index")) { onPick(place.board) }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = ItemHeight)
                    .selectable(selected = false, role = Role.Button, onClick = onHowRanksWork)
                    .padding(horizontal = Spacing.md)
                    .testTag("ranks.list.how"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Icon(PuzzleIcons.HowTo, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(ICON))
                Text("How ranks work", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun GameItem(place: BoardPlace, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = ItemHeight),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) scheme.secondaryContainer else scheme.surfaceContainerLow,
        contentColor = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.md).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                place.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else null,
                modifier = Modifier.weight(1f),
            )
            Text(place.standing.label(), style = MaterialTheme.typography.labelLarge, color = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant)
        }
    }
}
